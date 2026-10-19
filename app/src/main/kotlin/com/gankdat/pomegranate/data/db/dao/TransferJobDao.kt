package com.gankdat.pomegranate.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.gankdat.pomegranate.data.db.entity.TransferJobEntity
import com.gankdat.pomegranate.data.db.entity.TransferState
import kotlinx.coroutines.flow.Flow

@Dao
internal interface TransferJobDao {
    @Insert
    suspend fun insert(job: TransferJobEntity): Long

    @Insert
    suspend fun insertAll(jobs: List<TransferJobEntity>): List<Long>

    @Update
    suspend fun update(job: TransferJobEntity)

    @Delete
    suspend fun delete(job: TransferJobEntity)

    @Query("SELECT * FROM transfer_job ORDER BY id")
    suspend fun getAll(): List<TransferJobEntity>

    @Query("SELECT * FROM transfer_job ORDER BY id")
    fun observeAll(): Flow<List<TransferJobEntity>>

    @Query("SELECT * FROM transfer_job WHERE id = :id")
    suspend fun getById(id: Long): TransferJobEntity?

    @Query("SELECT * FROM transfer_job WHERE cloudItemId = :cloudItemId ORDER BY id")
    suspend fun getForCloudItem(cloudItemId: Long): List<TransferJobEntity>

    @Query("SELECT * FROM transfer_job WHERE state = :state ORDER BY id")
    suspend fun getByState(state: TransferState): List<TransferJobEntity>

    @Query("SELECT * FROM transfer_job WHERE state = :state ORDER BY id LIMIT :limit")
    suspend fun getNextByState(state: TransferState, limit: Int): List<TransferJobEntity>

    @Query("UPDATE transfer_job SET state = :state WHERE id = :id")
    suspend fun setState(id: Long, state: TransferState)

    @Query("UPDATE transfer_job SET bytesWritten = :bytesWritten WHERE id = :id")
    suspend fun setBytesWritten(id: Long, bytesWritten: Long)

    @Query("SELECT COUNT(*) FROM transfer_job WHERE state = :state")
    suspend fun countByState(state: TransferState): Int

    @Query("SELECT cloudItemId FROM transfer_job WHERE state != 'FAILED'")
    suspend fun getActiveOrDoneCloudItemIds(): List<Long>

    @Query("UPDATE transfer_job SET state = :to WHERE state = :from")
    suspend fun setStateForAll(from: TransferState, to: TransferState): Int

    @Query("SELECT * FROM transfer_job WHERE state = 'DONE' ORDER BY completedAt")
    suspend fun getDone(): List<TransferJobEntity>

    @Query("DELETE FROM transfer_job")
    suspend fun deleteAll()
}
