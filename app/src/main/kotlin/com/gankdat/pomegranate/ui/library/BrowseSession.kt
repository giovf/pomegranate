package com.gankdat.pomegranate.ui.library

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One page of the swipeable detail view: a phone file or a Google Photos item. */
internal data class BrowseRef(val key: String, val deviceItemId: Long?, val cloudItemId: Long?, val displayName: String) {
    companion object {
        fun of(entry: LibraryEntry) = BrowseRef(entry.key, entry.deviceItemId, entry.cloudItemId, entry.displayName)
    }
}

/**
 * The run of items the user is swiping through, set by the library when an
 * item is opened. Held in memory only: too large for navigation arguments,
 * and after process death the pager simply closes.
 */
internal class BrowseSession {
    @Volatile
    var entries: List<BrowseRef> = emptyList()
}

/** Google Photos items ticked in the library for the next pull (cloud item row ids). In memory only. */
internal class PullSelection {
    private val _ids = MutableStateFlow<Set<Long>>(emptySet())
    val ids: StateFlow<Set<Long>> = _ids.asStateFlow()

    fun set(ids: Set<Long>) {
        _ids.value = ids
    }

    fun clear() {
        _ids.value = emptySet()
    }
}
