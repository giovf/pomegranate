package com.gankdat.pomegranate.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One media item the user surfaced through the Google Photos Picker.
 *
 * [pickerMediaItemId] is Google's identifier and is unique here, so an item
 * re-selected in a later round updates this row rather than duplicating it;
 * [sessionId] then records the most recent round it was seen in.
 */
@Entity(
    tableName = "cloud_item",
    indices = [
        Index(value = ["pickerMediaItemId"], unique = true),
        Index("sessionId"),
        Index("createTime"),
    ],
    foreignKeys = [
        ForeignKey(
            entity = PickerRoundEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class CloudItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pickerMediaItemId: String,
    val sessionId: String,
    val filename: String,
    val mime: String,
    /** Picker API media type, e.g. `PHOTO` or `VIDEO`. */
    val type: String,
    /** Epoch millis of the Picker API `createTime`. */
    val createTime: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long? = null,
    val cameraMake: String? = null,
    val cameraModel: String? = null,
    /** Download base URL; only valid until [baseUrlExpiresAt]. */
    val baseUrl: String,
    /** Epoch millis after which [baseUrl] must be refreshed by re-listing the session. */
    val baseUrlExpiresAt: Long,
    /** Origin label guessed by the inference engine, e.g. `Camera` or `WhatsApp`. */
    val inferredOrigin: String? = null,
    /** Origin label the user chose by hand; wins over [inferredOrigin] when set. */
    val userOverrideOrigin: String? = null,
)
