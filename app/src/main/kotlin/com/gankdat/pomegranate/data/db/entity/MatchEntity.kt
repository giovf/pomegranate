package com.gankdat.pomegranate.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * The reconciler's verdict that a cloud item and a device item are the same
 * media. [evidence] is a JSON document describing which fields agreed, kept
 * so the UI can show *why* an item is marked as "both".
 */
@Entity(
    tableName = "match",
    primaryKeys = ["cloudItemId", "deviceItemId"],
    indices = [Index("deviceItemId"), Index("confidence")],
    foreignKeys = [
        ForeignKey(
            entity = CloudItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["cloudItemId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = DeviceItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["deviceItemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class MatchEntity(
    val cloudItemId: Long,
    val deviceItemId: Long,
    val confidence: MatchConfidence,
    /** JSON-encoded evidence; parsed into a typed model by the reconcile layer. */
    val evidence: String,
)
