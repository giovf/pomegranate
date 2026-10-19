package com.gankdat.pomegranate.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.gankdat.pomegranate.data.db.entity.OriginRuleEntity
import com.gankdat.pomegranate.data.db.entity.OriginSource

@Dao
internal interface OriginRuleDao {
    @Insert
    suspend fun insert(rule: OriginRuleEntity): Long

    @Insert
    suspend fun insertAll(rules: List<OriginRuleEntity>): List<Long>

    @Update
    suspend fun update(rule: OriginRuleEntity)

    @Delete
    suspend fun delete(rule: OriginRuleEntity)

    @Query("SELECT * FROM origin_rule ORDER BY confidence DESC, id")
    suspend fun getAll(): List<OriginRuleEntity>

    @Query("SELECT * FROM origin_rule WHERE id = :id")
    suspend fun getById(id: Long): OriginRuleEntity?

    @Query("SELECT * FROM origin_rule WHERE source = :source ORDER BY confidence DESC, id")
    suspend fun getBySource(source: OriginSource): List<OriginRuleEntity>

    @Query("DELETE FROM origin_rule WHERE source = :source")
    suspend fun deleteBySource(source: OriginSource)
}
