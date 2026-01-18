package com.erez.reposync.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.erez.reposync.data.db.entities.SyncLogEntity

@Dao
interface SyncLogDao {
    @Query("SELECT * FROM sync_logs WHERE profileId = :profileId ORDER BY startedAtEpochMillis DESC")
    fun observeForProfile(profileId: String): Flow<List<SyncLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SyncLogEntity)

    @Query("DELETE FROM sync_logs WHERE profileId = :profileId")
    suspend fun deleteForProfile(profileId: String)
}
