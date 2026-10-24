package com.gankdat.pomegranate.reconcile

import com.gankdat.pomegranate.data.db.dao.CloudItemDao
import com.gankdat.pomegranate.data.db.dao.DeviceItemDao
import com.gankdat.pomegranate.data.db.dao.MatchDao
import com.gankdat.pomegranate.data.db.dao.MediaGroupDao
import com.gankdat.pomegranate.data.db.entity.CloudItemGroupCrossRef
import com.gankdat.pomegranate.data.db.entity.DeviceItemGroupCrossRef
import com.gankdat.pomegranate.data.db.entity.MatchConfidence
import com.gankdat.pomegranate.data.db.entity.MatchEntity
import com.gankdat.pomegranate.data.db.entity.MediaGroupEntity
import com.gankdat.pomegranate.data.model.MediaState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class ReconciliationRepositoryImpl(
    private val cloudItemDao: CloudItemDao,
    private val deviceItemDao: DeviceItemDao,
    private val matchDao: MatchDao,
    private val groupDao: MediaGroupDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) : ReconciliationRepository {
    private val lock = Mutex()

    override suspend fun reconcile(): ReconciliationResult = lock.withLock {
        withContext(ioDispatcher) {
            val started = clock()
            val cloud = cloudItemDao.getAll()
            val device = deviceItemDao.getAll()

            // Hash-confirmed verdicts are the only state worth preserving across runs.
            val confirmed = matchDao.getAll().filter { Evidence.fromJson(it.evidence)?.method == MatchMethod.HASH_CONFIRMED }
            val confirmedCloud = confirmed.map { it.cloudItemId }.toSet()
            val confirmedDevice = confirmed.map { it.deviceItemId }.toSet()

            val proposals = MatchingEngine.match(
                cloud.filter { it.id !in confirmedCloud },
                device.filter { it.id !in confirmedDevice },
            )
            matchDao.replaceAll(confirmed + proposals.map { MatchEntity(it.cloudItemId, it.deviceItemId, it.confidence, it.evidence.toJson()) })

            val groups = rebuildGroups(
                deviceFiles = device.map { SiblingFile(DeviceKey(it.id), it.displayName) },
                cloudFiles = cloud.map { SiblingFile(CloudKey(it.id), it.filename) },
                deviceToCloud = (confirmed + proposals.map { MatchEntity(it.cloudItemId, it.deviceItemId, it.confidence, "") })
                    .filter { it.confidence in ReconciliationRepository.CONFIRMED }
                    .associate { it.deviceItemId to it.cloudItemId },
            )

            ReconciliationResult(
                cloudCount = cloud.size,
                deviceCount = device.size,
                exact = proposals.count { it.confidence == MatchConfidence.EXACT } + confirmed.size,
                likely = proposals.count { it.confidence == MatchConfidence.LIKELY },
                ambiguous = proposals.count { it.confidence == MatchConfidence.AMBIGUOUS },
                groups = groups,
                durationMillis = clock() - started,
            )
        }
    }

    /**
     * Device-side siblings are grouped by filename; a matched cloud item
     * joins its device twin's group so both sides see one capture. Cloud
     * items without a device twin are grouped among themselves.
     */
    private suspend fun rebuildGroups(deviceFiles: List<SiblingFile>, cloudFiles: List<SiblingFile>, deviceToCloud: Map<Long, Long>): Int {
        groupDao.deleteAll()
        val matchedCloud = deviceToCloud.values.toSet()
        var count = 0
        for (group in SiblingGrouper.group(deviceFiles)) {
            val groupId = groupDao.insert(MediaGroupEntity(kind = group.kind))
            for (member in group.members) {
                val deviceId = (member.key as DeviceKey).id
                groupDao.addDeviceItem(DeviceItemGroupCrossRef(deviceId, groupId))
                deviceToCloud[deviceId]?.let { groupDao.addCloudItem(CloudItemGroupCrossRef(it, groupId)) }
            }
            count++
        }
        for (group in SiblingGrouper.group(cloudFiles.filter { (it.key as CloudKey).id !in matchedCloud })) {
            val groupId = groupDao.insert(MediaGroupEntity(kind = group.kind))
            for (member in group.members) groupDao.addCloudItem(CloudItemGroupCrossRef((member.key as CloudKey).id, groupId))
            count++
        }
        return count
    }

    override fun observeStates(): Flow<StateIndex> =
        combine(cloudItemDao.observeIds(), deviceItemDao.observeIds(), matchDao.observeAll()) { cloudIds, deviceIds, matches ->
            val bothCloud = HashSet<Long>()
            val bothDevice = HashSet<Long>()
            for (m in matches) {
                if (m.confidence in ReconciliationRepository.CONFIRMED) {
                    bothCloud += m.cloudItemId
                    bothDevice += m.deviceItemId
                }
            }
            StateIndex(
                cloudStates = cloudIds.associateWith { if (it in bothCloud) MediaState.BOTH else MediaState.CLOUD_ONLY },
                deviceStates = deviceIds.associateWith { if (it in bothDevice) MediaState.BOTH else MediaState.DEVICE_ONLY },
            )
        }

    override fun observeAmbiguousMatches(): Flow<List<AmbiguousMatch>> =
        matchDao.observeAll().map { all ->
            all.filter { it.confidence == MatchConfidence.AMBIGUOUS }.map { AmbiguousMatch(it, Evidence.fromJson(it.evidence)) }
        }

    override suspend fun confirmWithCloudHash(cloudItemId: Long, cloudSha256: String): MatchConfidence? = lock.withLock {
        val match = matchDao.getForCloudItem(cloudItemId).firstOrNull() ?: return null
        val device = deviceItemDao.getById(match.deviceItemId) ?: return null
        val deviceHash = device.sha256 ?: return match.confidence // Device side not hashed yet; nothing to compare.
        val evidence = Evidence.fromJson(match.evidence) ?: Evidence(MatchMethod.PRIMARY, false, null, TimestampAgreement.UNKNOWN, false)
        return if (deviceHash.equals(cloudSha256, ignoreCase = true)) {
            matchDao.insert(match.copy(confidence = MatchConfidence.EXACT, evidence = evidence.copy(method = MatchMethod.HASH_CONFIRMED, hashMatched = true).toJson()))
            MatchConfidence.EXACT
        } else {
            matchDao.delete(match)
            null
        }
    }

    private data class DeviceKey(val id: Long)

    private data class CloudKey(val id: Long)
}
