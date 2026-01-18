package com.erez.reposync.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val targetTreeUri: String,
    val remoteUrl: String,
    val branch: String,
    val authMethod: String,
    val httpsUsername: String,
    val authorName: String,
    val authorEmail: String,
    val commitMessageTemplate: String,
    val propagateDeletes: Boolean,
    val syncPolicy: String,
    val ignorePatterns: String,
    val ignorePreset: String?
)
