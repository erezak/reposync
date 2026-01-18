package com.erez.reposync.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.erez.reposync.data.db.entities.FingerprintEntity

@Dao
interface FingerprintDao {
    @Query("SELECT * FROM fingerprints WHERE profileId = :profileId")
    suspend fun getAllForProfile(profileId: String): List<FingerprintEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<FingerprintEntity>)

    @Query("DELETE FROM fingerprints WHERE profileId = :profileId")
    suspend fun deleteForProfile(profileId: String)
}
