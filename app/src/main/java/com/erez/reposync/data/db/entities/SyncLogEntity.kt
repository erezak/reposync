package com.erez.reposync.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_logs")
data class SyncLogEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val status: String,
    val startedAtEpochMillis: Long,
    val finishedAtEpochMillis: Long,
    val summaryAdded: Int,
    val summaryModified: Int,
    val summaryDeleted: Int,
    val summaryConflicts: Int,
    val logPath: String?,
    val errorMessage: String?
)
