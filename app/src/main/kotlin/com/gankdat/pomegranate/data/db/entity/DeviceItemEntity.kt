package com.gankdat.pomegranate.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One photo or video found on this phone by the MediaStore census.
 *
 * Identity across rescans is `(volume, mediaStoreId)`; the surrogate [id] is
 * what the rest of the schema references so a MediaStore re-index never
 * breaks matches or group membership.
 */
@Entity(
    tableName = "device_item",
    indices = [
        Index(value = ["volume", "mediaStoreId"], unique = true),
        Index("fingerprint"),
        Index("bucket"),
        Index("dateTaken"),
    ],
)
internal data class DeviceItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** MediaStore `_ID`, unique only within a volume. */
    val mediaStoreId: Long,
    /** MediaStore volume name, e.g. `external_primary` or an SD-card UUID. */
    val volume: String,
    /** MediaStore `RELATIVE_PATH`, e.g. `DCIM/Camera/`. */
    val relativePath: String,
    /** MediaStore `BUCKET_DISPLAY_NAME`, the containing folder's name. */
    val bucket: String,
    val displayName: String,
    val mime: String,
    val size: Long,
    /** Epoch millis from `DATE_TAKEN`, falling back to the scanner's best estimate. */
    val dateTaken: Long,
    /** Epoch millis from `DATE_MODIFIED`. */
    val dateModified: Long,
    val width: Int,
    val height: Int,
    /** Video duration; null for photos. */
    val durationMs: Long? = null,
    /** Cheap identity derived from name, size, date taken and dimensions. */
    val fingerprint: String,
    /** Hex SHA-256, computed only when a match is ambiguous. */
    val sha256: String? = null,
)
