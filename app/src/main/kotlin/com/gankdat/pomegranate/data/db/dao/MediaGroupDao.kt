package com.gankdat.pomegranate.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.gankdat.pomegranate.data.db.entity.CloudItemGroupCrossRef
import com.gankdat.pomegranate.data.db.entity.DeviceItemGroupCrossRef
import com.gankdat.pomegranate.data.db.entity.MediaGroupEntity
import com.gankdat.pomegranate.data.db.entity.MediaGroupWithItems

@Dao
internal interface MediaGroupDao {
    @Insert
    suspend fun insert(group: MediaGroupEntity): Long

    @Delete
    suspend fun delete(group: MediaGroupEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addDeviceItem(ref: DeviceItemGroupCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addCloudItem(ref: CloudItemGroupCrossRef)

    @Delete
    suspend fun removeDeviceItem(ref: DeviceItemGroupCrossRef)

    @Delete
    suspend fun removeCloudItem(ref: CloudItemGroupCrossRef)

    @Query("SELECT * FROM media_group WHERE id = :id")
    suspend fun getById(id: Long): MediaGroupEntity?

    @Transaction
    @Query("SELECT * FROM media_group WHERE id = :id")
    suspend fun getWithItems(id: Long): MediaGroupWithItems?

    @Transaction
    @Query("SELECT * FROM media_group ORDER BY id")
    suspend fun getAllWithItems(): List<MediaGroupWithItems>

    @Transaction
    @Query(
        "SELECT media_group.* FROM media_group " +
            "JOIN device_item_group_cross_ref ON device_item_group_cross_ref.groupId = media_group.id " +
            "WHERE device_item_group_cross_ref.deviceItemId = :deviceItemId",
    )
    suspend fun getGroupsForDeviceItem(deviceItemId: Long): List<MediaGroupWithItems>

    @Transaction
    @Query(
        "SELECT media_group.* FROM media_group " +
            "JOIN cloud_item_group_cross_ref ON cloud_item_group_cross_ref.groupId = media_group.id " +
            "WHERE cloud_item_group_cross_ref.cloudItemId = :cloudItemId",
    )
    suspend fun getGroupsForCloudItem(cloudItemId: Long): List<MediaGroupWithItems>

    @Query("SELECT COUNT(*) FROM media_group")
    suspend fun count(): Int
}
