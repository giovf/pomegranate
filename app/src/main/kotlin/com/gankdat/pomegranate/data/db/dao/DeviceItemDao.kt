package com.gankdat.pomegranate.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.gankdat.pomegranate.data.db.entity.DeviceItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
internal interface DeviceItemDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: DeviceItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(items: List<DeviceItemEntity>): List<Long>

    /**
     * Insert, or update in place when the same `(volume, mediaStoreId)` is
     * already indexed. Returns the row id either way, so matches and group
     * membership keep pointing at the same row across rescans.
     *
     * Implemented explicitly rather than with `@Upsert`, which keys on the
     * surrogate id and cannot be exercised on the host JVM.
     */
    @Transaction
    suspend fun upsert(item: DeviceItemEntity): Long {
        val existing = getByMediaStoreId(item.volume, item.mediaStoreId) ?: return insert(item)
        update(item.copy(id = existing.id))
        return existing.id
    }

    @Update
    suspend fun update(item: DeviceItemEntity)

    @Update
    suspend fun updateAll(items: List<DeviceItemEntity>)

    @Delete
    suspend fun delete(item: DeviceItemEntity)

    @Query("SELECT * FROM device_item ORDER BY dateTaken DESC")
    suspend fun getAll(): List<DeviceItemEntity>

    @Query("SELECT * FROM device_item ORDER BY dateTaken DESC")
    fun observeAll(): Flow<List<DeviceItemEntity>>

    @Query("SELECT * FROM device_item WHERE id = :id")
    suspend fun getById(id: Long): DeviceItemEntity?

    @Query("SELECT * FROM device_item WHERE volume = :volume AND mediaStoreId = :mediaStoreId")
    suspend fun getByMediaStoreId(volume: String, mediaStoreId: Long): DeviceItemEntity?

    /** Callers chunk [mediaStoreIds] (see [MAX_BIND_ARGS]) to stay under SQLite's bind-variable limit. */
    @Query("SELECT * FROM device_item WHERE volume = :volume AND mediaStoreId IN (:mediaStoreIds)")
    suspend fun getByMediaStoreIds(volume: String, mediaStoreIds: List<Long>): List<DeviceItemEntity>

    @Query("SELECT mediaStoreId FROM device_item WHERE volume = :volume")
    suspend fun getMediaStoreIdsForVolume(volume: String): List<Long>

    @Query("SELECT * FROM device_item WHERE fingerprint = :fingerprint")
    suspend fun getByFingerprint(fingerprint: String): List<DeviceItemEntity>

    @Query("SELECT * FROM device_item WHERE bucket = :bucket ORDER BY dateTaken DESC")
    suspend fun getByBucket(bucket: String): List<DeviceItemEntity>

    @Query("SELECT COUNT(*) FROM device_item")
    suspend fun count(): Int

    /** Callers chunk [mediaStoreIds] (see [MAX_BIND_ARGS]). */
    @Query("DELETE FROM device_item WHERE volume = :volume AND mediaStoreId IN (:mediaStoreIds)")
    suspend fun deleteByMediaStoreIds(volume: String, mediaStoreIds: List<Long>): Int

    @Query("DELETE FROM device_item")
    suspend fun deleteAll()

    companion object {
        /** Safe chunk size for `IN (:list)` queries on every supported SQLite build. */
        const val MAX_BIND_ARGS: Int = 500
    }
}
