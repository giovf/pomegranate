package com.gankdat.pomegranate.data.model

/** Which cloud items a pull run should include. */
public enum class PullScope {
    /** Every indexed cloud item not yet verified on this phone. */
    ALL,

    /** Only items with no device copy. */
    CLOUD_ONLY,

    /** Items the user ticked in the library. */
    SELECTED,
}
