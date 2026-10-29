package com.gankdat.pomegranate.reconcile

import com.gankdat.pomegranate.data.db.dao.CloudItemDao
import com.gankdat.pomegranate.data.db.dao.DeviceItemDao
import com.gankdat.pomegranate.data.db.dao.GroupPlan
import com.gankdat.pomegranate.data.db.dao.MatchDao
import com.gankdat.pomegranate.data.db.dao.MediaGroupDao
import com.gankdat.pomegranate.data.db.entity.CloudItemEntity
import com.gankdat.pomegranate.data.db.entity.DeviceItemEntity
import com.gankdat.pomegranate.data.db.entity.MatchConfidence
import com.gankdat.pomegranate.data.db.entity.MatchEntity
import com.gankdat.pomegranate.data.model.MatchDecision
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
    private val decisions: MatchDecisionStore = MatchDecisionStore.NONE,
) : ReconciliationRepository {
    private val lock = Mutex()

    override suspend fun reconcile(): ReconciliationResult = lock.withLock {
        withContext(ioDispatcher) {
            val started = clock()
            val cloud = cloudItemDao.getAll()
            val device = deviceItemDao.getAll()

            // Hash-confirmed matches and the user's verdicts are the only state preserved across runs.
            val hashConfirmed = matchDao.getAll().filter { Evidence.fromJson(it.evidence)?.method == MatchMethod.HASH_CONFIRMED }
            val (userConfirmed, rejected) = applyDecisions(cloud, device, hashConfirmed)
            val confirmed = hashConfirmed + userConfirmed
            val confirmedCloud = confirmed.map { it.cloudItemId }.toSet()
            val confirmedDevice = confirmed.map { it.deviceItemId }.toSet()

            val proposals = MatchingEngine.match(
                cloud.filter { it.id !in confirmedCloud },
                device.filter { it.id !in confirmedDevice },
                rejected,
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

    /** The user's "same" verdicts as EXACT matches, and their "different" verdicts as pairs to exclude. */
    private suspend fun applyDecisions(cloud: List<CloudItemEntity>, device: List<DeviceItemEntity>, already: List<MatchEntity>): Pair<List<MatchEntity>, Set<Pair<Long, Long>>> {
        val verdicts = decisions.all()
        if (verdicts.isEmpty()) return emptyList<MatchEntity>() to emptySet()
        val cloudIds = cloud.associate { it.pickerMediaItemId to it.id }
        val deviceIds = device.associate { (it.volume to it.mediaStoreId) to it.id }
        val takenCloud = already.map { it.cloudItemId }.toMutableSet()
        val takenDevice = already.map { it.deviceItemId }.toMutableSet()
        val same = ArrayList<MatchEntity>()
        val rejected = HashSet<Pair<Long, Long>>()
        for (d in verdicts) {
            val c = cloudIds[d.pickerMediaItemId] ?: continue
            val v = deviceIds[d.volume to d.mediaStoreId] ?: continue
            if (!d.same) {
                rejected += c to v
            } else if (takenCloud.add(c) && takenDevice.add(v)) {
                same += MatchEntity(c, v, MatchConfidence.EXACT, USER_EVIDENCE.toJson())
            }
        }
        return same to rejected
    }

    override suspend fun decide(cloudItemId: Long, deviceItemId: Long, same: Boolean) = lock.withLock {
        withContext(ioDispatcher) {
            val cloud = cloudItemDao.getById(cloudItemId) ?: return@withContext
            val device = deviceItemDao.getById(deviceItemId) ?: return@withContext
            decisions.record(MatchDecision(cloud.pickerMediaItemId, device.volume, device.mediaStoreId, same))
            val existing = matchDao.getForCloudItem(cloudItemId).filter { it.deviceItemId == deviceItemId }
            existing.forEach { matchDao.delete(it) }
            if (same) {
                val evidence = existing.firstNotNullOfOrNull { Evidence.fromJson(it.evidence) }?.copy(method = MatchMethod.USER_CONFIRMED) ?: USER_EVIDENCE
                matchDao.insert(MatchEntity(cloudItemId, deviceItemId, MatchConfidence.EXACT, evidence.toJson()))
            }
        }
    }

    /**
     * Device-side siblings are grouped by filename; a matched cloud item
     * joins its device twin's group so both sides see one capture. Cloud
     * items without a device twin are grouped among themselves.
     */
    private suspend fun rebuildGroups(deviceFiles: List<SiblingFile>, cloudFiles: List<SiblingFile>, deviceToCloud: Map<Long, Long>): Int {
        val matchedCloud = deviceToCloud.values.toSet()
        val plans = buildList {
            for (group in SiblingGrouper.group(deviceFiles)) {
                val deviceIds = group.members.map { (it.key as DeviceKey).id }
                add(GroupPlan(group.kind, deviceIds, deviceIds.mapNotNull { deviceToCloud[it] }))
            }
            for (group in SiblingGrouper.group(cloudFiles.filter { (it.key as CloudKey).id !in matchedCloud })) {
                add(GroupPlan(group.kind, emptyList(), group.members.map { (it.key as CloudKey).id }))
            }
        }
        groupDao.replaceAll(plans)
        return plans.size
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
        if (Evidence.fromJson(match.evidence)?.method == MatchMethod.USER_CONFIRMED) return match.confidence // the user's word stands
        val evidence = Evidence.fromJson(match.evidence) ?: Evidence(MatchMethod.PRIMARY, false, null, TimestampAgreement.UNKNOWN, false)
        return if (deviceHash.equals(cloudSha256, ignoreCase = true)) {
            matchDao.insert(match.copy(confidence = MatchConfidence.EXACT, evidence = evidence.copy(method = MatchMethod.HASH_CONFIRMED, hashMatched = true).toJson()))
            MatchConfidence.EXACT
        } else {
            matchDao.delete(match)
            null
        }
    }

    private companion object {
        val USER_EVIDENCE = Evidence(MatchMethod.USER_CONFIRMED, filenameMatched = false, timestampDeltaMs = null, timestamp = TimestampAgreement.UNKNOWN, dimensionsMatched = false, note = "confirmed by you")
    }

    private data class DeviceKey(val id: Long)

    private data class CloudKey(val id: Long)
}
