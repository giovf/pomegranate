package com.gankdat.pomegranate.reconcile.origin

import com.gankdat.pomegranate.data.db.dao.CloudItemDao
import com.gankdat.pomegranate.data.db.dao.DeviceItemDao
import com.gankdat.pomegranate.data.db.dao.OriginRuleDao
import com.gankdat.pomegranate.data.db.entity.OriginSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal interface OriginRepository {
    /** Re-learns rules from the device census and re-infers every cloud item's origin. Overrides are untouched. */
    suspend fun refresh(): Int

    suspend fun inferOrigin(filename: String, bucket: String? = null): OriginResult?

    /** Labels the user can pick from: every learned and built-in origin plus any override already in use. */
    fun knownOrigins(): Flow<List<String>>

    suspend fun setUserOverride(cloudItemId: Long, origin: String?)
}

internal class OriginRepositoryImpl(
    private val deviceDao: DeviceItemDao,
    private val cloudDao: CloudItemDao,
    private val ruleDao: OriginRuleDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : OriginRepository {
    private val lock = Mutex()

    override suspend fun refresh(): Int = lock.withLock {
        withContext(ioDispatcher) {
            val rules = OriginLearner.learn(deviceDao.getAll().map { CensusFile(it.bucket, it.displayName) })
            ruleDao.deleteBySource(OriginSource.LEARNED)
            if (rules.isNotEmpty()) ruleDao.insertAll(rules)
            val inferrer = OriginInferrer(rules)
            var changed = 0
            for (item in cloudDao.getAll()) {
                val inferred = inferrer.infer(item.filename)?.origin
                if (inferred != item.inferredOrigin) {
                    cloudDao.setInferredOrigin(item.id, inferred)
                    changed++
                }
            }
            changed
        }
    }

    override suspend fun inferOrigin(filename: String, bucket: String?): OriginResult? =
        OriginInferrer(ruleDao.getBySource(OriginSource.LEARNED)).infer(filename, bucket)

    override fun knownOrigins(): Flow<List<String>> = cloudDao.observeAll().map { items ->
        (ruleDao.getAll().map { it.originLabel } + BuiltinPatterns.rules.map { it.origin } + items.mapNotNull { it.userOverrideOrigin })
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    override suspend fun setUserOverride(cloudItemId: Long, origin: String?) {
        cloudDao.setUserOverrideOrigin(cloudItemId, origin?.trim()?.takeIf { it.isNotEmpty() })
    }
}
