package com.gankdat.pomegranate.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.gankdat.pomegranate.data.db.entity.CloudItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
internal interface CloudItemDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: CloudItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(items: List<CloudItemEntity>): List<Long>

    /**
     * Insert, or update in place when [CloudItemEntity.pickerMediaItemId] is
     * already indexed (the item was re-selected in a later Picker round).
     * Returns the row id either way.
     */
    @Transaction
    suspend fun upsert(item: CloudItemEntity): Long {
        val existing = getByPickerMediaItemId(item.pickerMediaItemId) ?: return insert(item)
        update(item.copy(id = existing.id))
        return existing.id
    }

    @Update
    suspend fun update(item: CloudItemEntity)

    @Delete
    suspend fun delete(item: CloudItemEntity)

    @Query("SELECT * FROM cloud_item ORDER BY createTime DESC")
    suspend fun getAll(): List<CloudItemEntity>

    @Query("SELECT * FROM cloud_item ORDER BY createTime DESC")
    fun observeAll(): Flow<List<CloudItemEntity>>

    @Query("SELECT * FROM cloud_item WHERE id = :id")
    suspend fun getById(id: Long): CloudItemEntity?

    @Query("SELECT * FROM cloud_item WHERE pickerMediaItemId = :pickerMediaItemId")
    suspend fun getByPickerMediaItemId(pickerMediaItemId: String): CloudItemEntity?

    @Query("SELECT * FROM cloud_item WHERE sessionId = :sessionId ORDER BY createTime DESC")
    suspend fun getBySession(sessionId: String): List<CloudItemEntity>

    @Query("SELECT * FROM cloud_item WHERE baseUrlExpiresAt <= :nowMillis")
    suspend fun getWithExpiredBaseUrl(nowMillis: Long): List<CloudItemEntity>

    @Query("UPDATE cloud_item SET baseUrl = :baseUrl, baseUrlExpiresAt = :expiresAt WHERE id = :id")
    suspend fun updateBaseUrl(id: Long, baseUrl: String, expiresAt: Long)

    @Query("UPDATE cloud_item SET userOverrideOrigin = :origin WHERE id = :id")
    suspend fun setUserOverrideOrigin(id: Long, origin: String?)

    @Query("SELECT COUNT(*) FROM cloud_item")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM cloud_item")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM cloud_item")
    suspend fun deleteAll()
}
