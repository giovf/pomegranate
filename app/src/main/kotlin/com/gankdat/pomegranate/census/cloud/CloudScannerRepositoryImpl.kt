package com.gankdat.pomegranate.census.cloud

import com.gankdat.pomegranate.data.db.dao.CloudItemDao
import com.gankdat.pomegranate.data.db.dao.PickerRoundDao
import com.gankdat.pomegranate.data.db.entity.PickerRoundEntity
import com.gankdat.pomegranate.data.db.entity.PickerRoundState
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

internal class CloudScannerRepositoryImpl(
    private val api: PickerApi,
    private val poller: SessionPoller,
    private val cloudItemDao: CloudItemDao,
    private val roundDao: PickerRoundDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : CloudScannerRepository {

    override suspend fun startPickerRound(): PickerSession {
        val session = api.createSession()
        roundDao.insert(PickerRoundEntity(sessionId = session.id, createdAt = clock(), state = PickerRoundState.ACTIVE))
        return session
    }

    override suspend fun pollSession(sessionId: String): SessionState {
        val session = try {
            api.getSession(sessionId)
        } catch (e: PickerApiException) {
            if (e.isGone) roundDao.setState(sessionId, PickerRoundState.EXPIRED, itemCount = 0)
            return SessionState.Error("Google Photos returned ${e.httpStatus}")
        } catch (_: PickerAuthException) {
            return SessionState.Error("Google sign-in is no longer valid")
        } catch (_: IOException) {
            return SessionState.Error("Could not reach Google Photos")
        }
        if (session.mediaItemsSet) return SessionState.Complete
        return when (val result = poller.pollUntilComplete(session)) {
            is PollResult.Complete -> SessionState.Complete
            PollResult.Timeout -> {
                roundDao.setState(sessionId, PickerRoundState.EXPIRED, itemCount = 0)
                SessionState.Timeout
            }
            is PollResult.Failed -> SessionState.Error(result.message)
        }
    }

    override suspend fun indexSession(sessionId: String): Int {
        val count = listInto(sessionId)
        roundDao.setState(sessionId, PickerRoundState.INDEXED, itemCount = count)
        return count
    }

    override suspend fun refreshExpiringBaseUrls(sessionId: String, marginMillis: Long) {
        val expiring = cloudItemDao.getWithExpiredBaseUrl(nowMillis = clock() + marginMillis).filter { it.sessionId == sessionId }
        if (expiring.isEmpty()) return
        try {
            listInto(sessionId)
        } catch (e: PickerApiException) {
            if (e.isGone) roundDao.setState(sessionId, PickerRoundState.EXPIRED, itemCount = expiring.size)
            throw e
        }
    }

    override suspend fun deleteSession(sessionId: String) {
        try {
            api.deleteSession(sessionId)
        } catch (e: PickerApiException) {
            if (!e.isGone) throw e // Already gone at Google: fine.
        }
    }

    override fun coverage(): Flow<CloudCoverage> =
        combine(cloudItemDao.observeCount(), roundDao.observeIndexedCount()) { items, rounds -> CloudCoverage(items, rounds) }

    override fun pickerRounds(): Flow<List<PickerRoundEntity>> = roundDao.observeAll()

    /** Pages through the session and upserts every item, keyed by Google's persistent media item id. */
    private suspend fun listInto(sessionId: String): Int {
        var pageToken: String? = null
        var count = 0
        do {
            val listedAt = clock()
            val page = api.listMediaItems(sessionId, pageToken)
            for (item in page.items) {
                cloudItemDao.upsert(CloudItemMapper.toEntity(item, sessionId, listedAt))
                count++
            }
            pageToken = page.nextPageToken
        } while (pageToken != null)
        return count
    }
}
