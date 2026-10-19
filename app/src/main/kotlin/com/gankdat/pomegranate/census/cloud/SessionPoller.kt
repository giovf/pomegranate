package com.gankdat.pomegranate.census.cloud

import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.currentCoroutineContext

/**
 * Polls `sessions.get` until the user finishes picking. Backoff starts at
 * the interval Google suggests, grows by 1.5x up to [MAX_INTERVAL_MILLIS],
 * and transient network errors are retried until the timeout.
 */
internal class SessionPoller(
    private val api: PickerApi,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    private val _attempts = MutableStateFlow(0)
    val attempts: StateFlow<Int> = _attempts.asStateFlow()

    suspend fun pollUntilComplete(session: PickerSession): PollResult {
        val deadline = clock() + session.timeoutMillis
        var interval = session.pollIntervalMillis.coerceIn(MIN_INTERVAL_MILLIS, MAX_INTERVAL_MILLIS)
        var consecutiveFailures = 0
        _attempts.value = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            if (clock() >= deadline) return PollResult.Timeout
            sleep(interval)
            _attempts.value += 1
            try {
                val latest = api.getSession(session.id)
                consecutiveFailures = 0
                if (latest.mediaItemsSet) return PollResult.Complete(latest)
            } catch (e: PickerApiException) {
                return PollResult.Failed(if (e.isAuthFailure) "Google sign-in is no longer valid" else "Google Photos returned ${e.httpStatus}")
            } catch (_: PickerAuthException) {
                return PollResult.Failed("Google sign-in is no longer valid")
            } catch (_: IOException) {
                if (++consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) return PollResult.Failed("Could not reach Google Photos")
            }
            interval = (interval * BACKOFF_FACTOR).toLong().coerceAtMost(MAX_INTERVAL_MILLIS)
        }
    }

    companion object {
        const val MIN_INTERVAL_MILLIS: Long = 1_000
        const val MAX_INTERVAL_MILLIS: Long = 10_000
        const val BACKOFF_FACTOR: Double = 1.5
        const val MAX_CONSECUTIVE_FAILURES: Int = 5
    }
}
