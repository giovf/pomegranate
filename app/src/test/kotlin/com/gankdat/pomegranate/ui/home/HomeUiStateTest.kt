package com.gankdat.pomegranate.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeUiStateTest {
    @Test
    fun `a new user is asked to connect first`() {
        assertEquals(JourneyStep.CONNECT, HomeUiState(isLoaded = true, phoneOnly = 500).step)
    }

    @Test
    fun `connected with nothing chosen asks to choose photos`() {
        assertEquals(JourneyStep.CHOOSE, HomeUiState(isConnected = true).step)
    }

    @Test
    fun `chosen items that are only in Google ask to be copied`() {
        assertEquals(JourneyStep.COPY, HomeUiState(isConnected = true, chosen = 300, waitingToCopy = 120).step)
    }

    @Test
    fun `a running copy wins over everything else, even a lapsed sign-in`() {
        assertEquals(JourneyStep.COPYING, HomeUiState(isConnected = false, chosen = 300, waitingToCopy = 100, copying = 20).step)
    }

    @Test
    fun `a lapsed sign-in asks to connect again even when items are waiting`() {
        assertEquals(JourneyStep.CONNECT, HomeUiState(isConnected = false, chosen = 300, waitingToCopy = 120).step)
    }

    @Test
    fun `nothing left to copy is done`() {
        val state = HomeUiState(isConnected = true, chosen = 300, copied = 120)
        assertEquals(JourneyStep.DONE, state.step)
        assertTrue(state.isCopyFinished)
    }

    @Test
    fun `items in closed batches keep the copy step unfinished`() {
        val state = HomeUiState(isConnected = true, chosen = 300, copied = 100, needChoosingAgain = 20)
        assertEquals(JourneyStep.DONE, state.step)
        assertFalse(state.isCopyFinished)
    }

    @Test
    fun `nothing chosen is never finished`() {
        assertFalse(HomeUiState(isConnected = true).isCopyFinished)
    }
}
