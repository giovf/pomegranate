package com.gankdat.pomegranate.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A set of sibling files that make up one logical capture (RAW+JPEG, the two
 * halves of a motion photo, an original and its edit). Membership lives in
 * [DeviceItemGroupCrossRef] and [CloudItemGroupCrossRef].
 */
@Entity(tableName = "media_group")
internal data class MediaGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: MediaGroupKind,
)

/** Membership of a device item in a media group. */
@Entity(
    tableName = "device_item_group_cross_ref",
    primaryKeys = ["deviceItemId", "groupId"],
    indices = [Index("groupId")],
    foreignKeys = [
        ForeignKey(
            entity = DeviceItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["deviceItemId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MediaGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class DeviceItemGroupCrossRef(
    val deviceItemId: Long,
    val groupId: Long,
)

/** Membership of a cloud item in a media group. */
@Entity(
    tableName = "cloud_item_group_cross_ref",
    primaryKeys = ["cloudItemId", "groupId"],
    indices = [Index("groupId")],
    foreignKeys = [
        ForeignKey(
            entity = CloudItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["cloudItemId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MediaGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class CloudItemGroupCrossRef(
    val cloudItemId: Long,
    val groupId: Long,
)
