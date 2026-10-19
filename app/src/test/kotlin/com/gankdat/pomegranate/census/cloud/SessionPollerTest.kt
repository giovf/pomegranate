package com.gankdat.pomegranate.census.cloud

import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionPollerTest {
    private val api = FakePickerApi()
    private var now = 0L
    private val sleeps = mutableListOf<Long>()
    private val poller = SessionPoller(api, clock = { now }, sleep = { sleeps += it; now += it })

    @Test
    fun `completes when media items are set, backing off between polls`() = runBlocking {
        val session = api.session("s1", pollInterval = 1_000)
        api.sessions["s1"] = session
        var calls = 0
        api.getSessionHook = { if (++calls == 4) api.sessions["s1"] = session.copy(mediaItemsSet = true) }

        val result = poller.pollUntilComplete(session)

        assertTrue(result is PollResult.Complete)
        assertEquals(listOf(1_000L, 1_500L, 2_250L, 3_375L), sleeps)
        assertEquals(4, poller.attempts.value)
    }

    @Test
    fun `interval is capped`() = runBlocking {
        val session = api.session("s1", pollInterval = 9_000, timeout = 100_000)
        api.sessions["s1"] = session
        var calls = 0
        api.getSessionHook = { if (++calls == 3) api.sessions["s1"] = session.copy(mediaItemsSet = true) }

        poller.pollUntilComplete(session)

        assertEquals(listOf(9_000L, 10_000L, 10_000L), sleeps)
    }

    @Test
    fun `times out when the user never finishes`() = runBlocking {
        val session = api.session("s1", pollInterval = 10_000, timeout = 35_000)
        api.sessions["s1"] = session

        assertEquals(PollResult.Timeout, poller.pollUntilComplete(session))
        assertEquals(4, sleeps.size)
    }

    @Test
    fun `transient network errors are retried, persistent ones fail`() = runBlocking {
        val session = api.session("s1")
        api.sessions["s1"] = session
        var calls = 0
        api.getSessionHook = {
            calls++
            if (calls <= 2) throw IOException("blip")
            if (calls == 3) api.sessions["s1"] = session.copy(mediaItemsSet = true)
        }
        assertTrue(poller.pollUntilComplete(session) is PollResult.Complete)

        sleeps.clear()
        api.getSessionHook = { throw IOException("down") }
        val failed = poller.pollUntilComplete(session.copy(mediaItemsSet = false))
        assertTrue(failed is PollResult.Failed)
        assertEquals(SessionPoller.MAX_CONSECUTIVE_FAILURES, sleeps.size)
    }

    @Test
    fun `API errors stop polling immediately`() = runBlocking {
        val session = api.session("s1")
        api.getSessionHook = { throw PickerApiException(401, "expired") }
        val result = poller.pollUntilComplete(session)
        assertTrue(result is PollResult.Failed)
        assertEquals(1, sleeps.size)
    }
}
