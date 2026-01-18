package com.erez.reposync.data.model

import java.time.Instant
import java.util.UUID

enum class AuthMethod {
    HTTPS_TOKEN,
    SSH_KEY
}

enum class SyncPolicyType {
    MANUAL,
    PERIODIC
}

enum class SyncStatus {
    IDLE,
    RUNNING,
    SUCCESS,
    FAILED,
    CONFLICT
}

data class SyncPolicy(
    val type: SyncPolicyType,
    val intervalMinutes: Long? = null,
    val requiresUnmetered: Boolean = false,
    val requiresCharging: Boolean = false,
    val requiresBatteryNotLow: Boolean = false
)

data class IgnoreRules(
    val patterns: List<String> = emptyList(),
    val preset: String? = null
)

data class Profile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val targetTreeUri: String,
    val remoteUrl: String,
    val branch: String,
    val authMethod: AuthMethod,
    val httpsUsername: String = "token",
    val authorName: String,
    val authorEmail: String,
    val commitMessageTemplate: String = "Sync <timestamp> (<device>)",
    val propagateDeletes: Boolean = false,
    val syncPolicy: SyncPolicy = SyncPolicy(SyncPolicyType.MANUAL),
    val ignoreRules: IgnoreRules = IgnoreRules()
)

data class SyncSummary(
    val added: Int,
    val modified: Int,
    val deleted: Int,
    val conflicts: Int
)

data class SyncResult(
    val profileId: String,
    val status: SyncStatus,
    val startedAt: Instant,
    val finishedAt: Instant,
    val summary: SyncSummary,
    val logPath: String? = null,
    val errorMessage: String? = null
)

data class Fingerprint(
    val profileId: String,
    val relativePath: String,
    val sizeBytes: Long,
    val modifiedTimeEpochMillis: Long?,
    val sha256: String? = null
)

data class MirrorDiff(
    val added: List<String>,
    val modified: List<String>,
    val deleted: List<String>
)
