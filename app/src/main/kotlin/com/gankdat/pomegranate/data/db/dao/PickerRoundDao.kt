package com.gankdat.pomegranate.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.gankdat.pomegranate.data.db.entity.PickerRoundEntity
import com.gankdat.pomegranate.data.db.entity.PickerRoundState
import kotlinx.coroutines.flow.Flow

@Dao
internal interface PickerRoundDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(round: PickerRoundEntity)

    @Update
    suspend fun update(round: PickerRoundEntity)

    /** Deletes the round and, through the foreign key, every cloud item indexed in it. */
    @Delete
    suspend fun delete(round: PickerRoundEntity)

    @Query("SELECT * FROM picker_round ORDER BY createdAt DESC")
    suspend fun getAll(): List<PickerRoundEntity>

    @Query("SELECT * FROM picker_round ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PickerRoundEntity>>

    @Query("SELECT * FROM picker_round WHERE sessionId = :sessionId")
    suspend fun getBySessionId(sessionId: String): PickerRoundEntity?

    @Query("SELECT * FROM picker_round WHERE state = :state ORDER BY createdAt DESC")
    suspend fun getByState(state: PickerRoundState): List<PickerRoundEntity>

    @Query("UPDATE picker_round SET state = :state, itemCount = :itemCount WHERE sessionId = :sessionId")
    suspend fun setState(sessionId: String, state: PickerRoundState, itemCount: Int)

    @Query("DELETE FROM picker_round")
    suspend fun deleteAll()
}
