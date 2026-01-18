package com.erez.reposync.data.db.entities

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "fingerprints",
    primaryKeys = ["profileId", "relativePath"],
    indices = [Index("profileId")]
)
data class FingerprintEntity(
    val profileId: String,
    val relativePath: String,
    val sizeBytes: Long,
    val modifiedTimeEpochMillis: Long?,
    val sha256: String?
)
