package com.gankdat.pomegranate.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gankdat.pomegranate.data.db.entity.MatchConfidence
import com.gankdat.pomegranate.data.db.entity.MatchEntity
import kotlinx.coroutines.flow.Flow

@Dao
internal interface MatchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(match: MatchEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(matches: List<MatchEntity>)

    @Delete
    suspend fun delete(match: MatchEntity)

    @Query("SELECT * FROM `match` ORDER BY cloudItemId, deviceItemId")
    suspend fun getAll(): List<MatchEntity>

    @Query("SELECT * FROM `match` ORDER BY cloudItemId, deviceItemId")
    fun observeAll(): Flow<List<MatchEntity>>

    @Query("SELECT * FROM `match` WHERE cloudItemId = :cloudItemId")
    suspend fun getForCloudItem(cloudItemId: Long): List<MatchEntity>

    @Query("SELECT * FROM `match` WHERE deviceItemId = :deviceItemId")
    suspend fun getForDeviceItem(deviceItemId: Long): List<MatchEntity>

    @Query("SELECT * FROM `match` WHERE confidence = :confidence")
    suspend fun getByConfidence(confidence: MatchConfidence): List<MatchEntity>

    @Query("SELECT COUNT(*) FROM `match`")
    suspend fun count(): Int

    @Query("DELETE FROM `match` WHERE cloudItemId = :cloudItemId")
    suspend fun deleteForCloudItem(cloudItemId: Long)

    @Query("DELETE FROM `match`")
    suspend fun deleteAll()
}
