package com.gankdat.pomegranate.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.gankdat.pomegranate.data.db.entity.CloudItemEntity
import com.gankdat.pomegranate.data.db.entity.SessionRange
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

    @Query("UPDATE cloud_item SET inferredOrigin = :origin WHERE id = :id")
    suspend fun setInferredOrigin(id: Long, origin: String?)

    /** Applies many origin changes in one transaction so observers see a single update, not one per item. */
    @Transaction
    suspend fun setInferredOrigins(changes: Map<Long, String?>) {
        for ((id, origin) in changes) setInferredOrigin(id, origin)
    }

    @Query("UPDATE cloud_item SET userOverrideOrigin = :origin WHERE id = :id")
    suspend fun setUserOverrideOrigin(id: Long, origin: String?)

    @Query("SELECT COUNT(*) FROM cloud_item")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM cloud_item")
    fun observeCount(): Flow<Int>

    @Query("SELECT id FROM cloud_item")
    fun observeIds(): Flow<List<Long>>

    /** Per round: item count and the date span of its photos. An item picked again moves to its latest round. */
    @Query(
        "SELECT sessionId, COUNT(*) AS itemCount, " +
            "MIN(CASE WHEN createTime > 0 THEN createTime END) AS oldest, " +
            "MAX(CASE WHEN createTime > 0 THEN createTime END) AS newest " +
            "FROM cloud_item GROUP BY sessionId",
    )
    fun observeSessionRanges(): Flow<List<SessionRange>>

    @Query("DELETE FROM cloud_item")
    suspend fun deleteAll()
}
