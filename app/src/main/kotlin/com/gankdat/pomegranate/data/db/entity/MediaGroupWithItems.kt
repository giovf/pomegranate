package com.gankdat.pomegranate.data.db.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

/** A media group together with every device and cloud item that belongs to it. */
internal data class MediaGroupWithItems(
    @Embedded val group: MediaGroupEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = DeviceItemGroupCrossRef::class,
            parentColumn = "groupId",
            entityColumn = "deviceItemId",
        ),
    )
    val deviceItems: List<DeviceItemEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = CloudItemGroupCrossRef::class,
            parentColumn = "groupId",
            entityColumn = "cloudItemId",
        ),
    )
    val cloudItems: List<CloudItemEntity>,
)
