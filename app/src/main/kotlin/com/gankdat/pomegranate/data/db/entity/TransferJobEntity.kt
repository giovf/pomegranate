package com.gankdat.pomegranate.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One attempt to copy a cloud item to the user's chosen destination. A job
 * reaches [TransferState.DONE] only after the written file's size and
 * SHA-256 have been verified; [sha256] is the hash of the bytes on disk.
 *
 * Deliberately not a foreign key to `cloud_item`: a DONE job is the proof of
 * copy and must outlive the cloud index, which Disconnect wipes. The source
 * identity is therefore copied in ([sourceId], [originalFilename], [mime]).
 */
@Entity(
    tableName = "transfer_job",
    indices = [Index("state"), Index("cloudItemId"), Index("sourceId")],
)
internal data class TransferJobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Row id of the cloud item at enqueue time; may no longer exist after Disconnect. */
    val cloudItemId: Long,
    /** Google's persistent media item id. */
    val sourceId: String,
    val originalFilename: String,
    val mime: String,
    val state: TransferState = TransferState.QUEUED,
    /** SAF document URI of the written file; empty until the file is created. */
    val destinationUri: String = "",
    /** Path under the destination root, e.g. `Pomegranate/2024/2024-03/Camera/IMG_1.jpg`; empty until created. */
    val relativePath: String = "",
    val bytesExpected: Long = 0,
    val bytesWritten: Long = 0,
    /** Hex SHA-256 of the verified file; null until verification succeeds. */
    val sha256: String? = null,
    val fidelity: Fidelity,
    val attempts: Int = 0,
    /** Short, non-identifying description of the last failure. */
    val lastError: String? = null,
    /** Epoch millis when verification passed; null until DONE. */
    val completedAt: Long? = null,
)
