package com.gankdat.pomegranate.ui.home

/** The one thing the Home screen asks the user to do next. */
internal enum class JourneyStep {
    /** Not signed in to Google yet, or the sign-in has lapsed. */
    CONNECT,

    /** Signed in, nothing chosen in Google Photos yet. */
    CHOOSE,

    /** Chosen items are waiting to be copied to the phone. */
    COPY,

    /** A copy is in progress. */
    COPYING,

    /** Everything chosen so far has a checked copy. */
    DONE,
}

/**
 * Everything the Home screen shows: where the photos are, and how far along
 * the move out of Google Photos is. Pure data so the step logic is unit-testable.
 */
internal data class HomeUiState(
    /** False until the first numbers have arrived, so the screen does not flash zeros. */
    val isLoaded: Boolean = false,
    val phoneOnly: Int = 0,
    val cloudOnly: Int = 0,
    val both: Int = 0,
    val isConnected: Boolean = false,
    /** Items picked in Google Photos so far, over all batches. */
    val chosen: Int = 0,
    /** Google-only items that can be copied right now. */
    val waitingToCopy: Int = 0,
    /** Copies queued, running, being checked or paused. */
    val copying: Int = 0,
    /** Copies written and checked. */
    val copied: Int = 0,
    /** Google-only items whose batch Google has closed: they must be chosen again before they can be copied. */
    val needChoosingAgain: Int = 0,
) {
    val step: JourneyStep
        get() = when {
            copying > 0 -> JourneyStep.COPYING
            !isConnected -> JourneyStep.CONNECT
            chosen == 0 -> JourneyStep.CHOOSE
            waitingToCopy > 0 -> JourneyStep.COPY
            else -> JourneyStep.DONE
        }

    val hasChosen: Boolean get() = chosen > 0

    /** True once nothing chosen is left to copy; closed batches still need the user, so they do not count as finished. */
    val isCopyFinished: Boolean get() = hasChosen && waitingToCopy == 0 && copying == 0 && needChoosingAgain == 0
}
