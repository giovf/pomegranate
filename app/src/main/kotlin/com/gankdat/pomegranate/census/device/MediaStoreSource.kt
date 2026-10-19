package com.gankdat.pomegranate.census.device

/**
 * Read-only view of the device's media index. [DeviceScanner] implements it
 * over MediaStore; tests substitute an in-memory fake.
 */
internal interface MediaStoreSource {
    /** Volume names to scan, primary first. */
    fun volumes(): List<String>

    /** Number of images and videos on [volume] matching the optional modified-after filter (seconds). */
    fun count(volume: String, modifiedAfterSeconds: Long? = null): Int

    /** MediaStore ids of every image and video currently on [volume]; used to detect deletions cheaply. */
    fun ids(volume: String): List<Long>

    /** Streams rows for [volume], optionally only those modified after [modifiedAfterSeconds]. */
    fun forEach(volume: String, modifiedAfterSeconds: Long? = null, consumer: (ScannedMediaItem) -> Unit)
}
