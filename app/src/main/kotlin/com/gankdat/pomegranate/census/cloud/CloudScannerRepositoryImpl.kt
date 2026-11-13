package com.gankdat.pomegranate.census.cloud

import com.gankdat.pomegranate.data.db.dao.CloudItemDao
import com.gankdat.pomegranate.data.db.dao.PickerRoundDao
import com.gankdat.pomegranate.data.db.entity.PickerRoundEntity
import com.gankdat.pomegranate.data.db.entity.SessionRange
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
    /** Receives, per indexed round, how many items were already indexed from an earlier round. */
    private val recordOverlap: suspend (sessionId: String, alreadyIndexed: Int) -> Unit = { _, _ -> },
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
        var alreadyIndexed = 0
        val count = listInto(sessionId) { existing -> if (existing != null && existing.sessionId != sessionId) alreadyIndexed++ }
        recordOverlap(sessionId, alreadyIndexed)
        roundDao.setState(sessionId, PickerRoundState.INDEXED, itemCount = count)
        return count
    }

    override suspend fun refreshExpiringBaseUrls(sessionId: String, marginMillis: Long) {
        val expiring = cloudItemDao.getWithExpiredBaseUrl(nowMillis = clock() + marginMillis).filter { it.sessionId == sessionId }
        if (expiring.isEmpty()) return
        if (isRoundKnownClosed(sessionId)) throw PickerApiException(HTTP_GONE, "round closed")
        try {
            listInto(sessionId)
        } catch (e: PickerApiException) {
            if (e.isGone || isRoundClosed(sessionId) == true) {
                markClosed(sessionId)
                throw PickerApiException(HTTP_GONE, "round closed")
            }
            throw e
        }
    }

    override suspend fun refreshBaseUrls(sessionId: String) {
        if (isRoundKnownClosed(sessionId)) throw PickerApiException(HTTP_GONE, "round closed")
        try {
            listInto(sessionId)
        } catch (e: PickerApiException) {
            if (e.isGone || isRoundClosed(sessionId) == true) {
                markClosed(sessionId)
                throw PickerApiException(HTTP_GONE, "round closed")
            }
            throw e
        }
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_GONE = 410
        const val HTTP_TOO_MANY_REQUESTS = 429
        val CLIENT_ERRORS = 400..499
    }

    override suspend fun isRoundClosed(sessionId: String): Boolean? {
        val closed = try {
            // Google: expireTime is "when access to this session (and its picked media items) will expire".
            api.getSession(sessionId).expireTimeMillis?.let { it <= clock() } == true
        } catch (e: PickerApiException) {
            when {
                e.httpStatus == HTTP_UNAUTHORIZED || e.httpStatus == HTTP_TOO_MANY_REQUESTS -> return null
                e.httpStatus in CLIENT_ERRORS -> true // the token is valid, yet Google will not show this session
                else -> return null
            }
        } catch (_: PickerAuthException) {
            return null
        } catch (_: IOException) {
            return null
        }
        if (closed) markClosed(sessionId)
        return closed
    }

    override suspend fun isRoundKnownClosed(sessionId: String): Boolean = roundDao.getBySessionId(sessionId)?.state == PickerRoundState.EXPIRED

    /** Keeps the round's item count: it was indexed, and the screen says how much of it is stranded. */
    private suspend fun markClosed(sessionId: String) {
        val round = roundDao.getBySessionId(sessionId) ?: return
        if (round.state != PickerRoundState.EXPIRED) roundDao.setState(sessionId, PickerRoundState.EXPIRED, itemCount = round.itemCount)
    }

    override suspend fun checkIndexedRounds(): Int {
        var closed = 0
        for (round in roundDao.getByState(PickerRoundState.INDEXED)) {
            when (isRoundClosed(round.sessionId)) {
                true -> closed++
                false -> Unit
                null -> return closed // offline or signed out: the rest would fail the same way
            }
        }
        return closed
    }

    override suspend fun deleteSession(sessionId: String) {
        try {
            api.deleteSession(sessionId)
        } catch (e: PickerApiException) {
            if (!e.isGone) throw e // Already gone at Google: fine.
        }
    }

    override suspend fun openRound(): PickerSession? {
        val active = roundDao.getByState(PickerRoundState.ACTIVE) // newest first
        active.drop(1).forEach { roundDao.setState(it.sessionId, PickerRoundState.EXPIRED, itemCount = 0) }
        val latest = active.firstOrNull() ?: return null
        val session = try {
            api.getSession(latest.sessionId)
        } catch (e: PickerApiException) {
            if (e.isGone) roundDao.setState(latest.sessionId, PickerRoundState.EXPIRED, itemCount = 0)
            return null
        }
        val expired = session.expireTimeMillis?.let { it <= clock() } == true
        if (expired && !session.mediaItemsSet) {
            roundDao.setState(latest.sessionId, PickerRoundState.EXPIRED, itemCount = 0)
            return null
        }
        return session
    }

    override fun sessionRanges(): Flow<List<SessionRange>> = cloudItemDao.observeSessionRanges()

    override fun coverage(): Flow<CloudCoverage> =
        combine(cloudItemDao.observeCount(), roundDao.observeIndexedCount()) { items, rounds -> CloudCoverage(items, rounds) }

    override fun pickerRounds(): Flow<List<PickerRoundEntity>> = roundDao.observeAll()

    /** Pages through the session and upserts every item, keyed by Google's persistent media item id. */
    private suspend fun listInto(sessionId: String, onExisting: (com.gankdat.pomegranate.data.db.entity.CloudItemEntity?) -> Unit = {}): Int {
        var pageToken: String? = null
        var count = 0
        do {
            val listedAt = clock()
            val page = api.listMediaItems(sessionId, pageToken)
            for (item in page.items) {
                onExisting(cloudItemDao.getByPickerMediaItemId(item.id))
                cloudItemDao.upsert(CloudItemMapper.toEntity(item, sessionId, listedAt))
                count++
            }
            pageToken = page.nextPageToken
        } while (pageToken != null)
        return count
    }
}
