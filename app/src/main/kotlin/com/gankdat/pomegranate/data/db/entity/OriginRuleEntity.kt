package com.gankdat.pomegranate.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A filename or folder pattern that maps media to an origin app, used to
 * choose the `<Origin>` folder for pulled files.
 */
@Entity(
    tableName = "origin_rule",
    indices = [Index("pattern"), Index("originLabel")],
)
internal data class OriginRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: OriginSource,
    /** Regular expression matched against the filename (and bucket for learned rules). */
    val pattern: String,
    /** Human-readable origin, e.g. `Camera`, `Screenshots`, `WhatsApp`. */
    val originLabel: String,
    /** 0.0 to 1.0; higher means the pattern predicts the origin more reliably. */
    val confidence: Float,
)
