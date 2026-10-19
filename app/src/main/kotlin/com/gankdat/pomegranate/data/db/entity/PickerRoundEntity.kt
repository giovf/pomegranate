package com.gankdat.pomegranate.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One Google Photos Picker session. The Picker caps each session at 2,000
 * items, so coverage of a large library is built up over several rounds.
 */
@Entity(tableName = "picker_round")
internal data class PickerRoundEntity(
    /** Picker API session id. */
    @PrimaryKey val sessionId: String,
    /** Epoch millis when the session was created. */
    val createdAt: Long,
    /** Number of items the user selected in this round. */
    val itemCount: Int = 0,
    val state: PickerRoundState = PickerRoundState.ACTIVE,
)
