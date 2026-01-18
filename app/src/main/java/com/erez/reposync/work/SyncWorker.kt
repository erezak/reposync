package com.erez.reposync.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.erez.reposync.RepoSyncApp
import com.erez.reposync.data.repo.SyncMode

class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val profileId = inputData.getString(KEY_PROFILE_ID) ?: return Result.failure()
        val app = applicationContext as RepoSyncApp
        val result = app.services.syncRepository.syncNow(profileId, SyncMode.FULL)
        return when (result.status) {
            com.erez.reposync.data.model.SyncStatus.SUCCESS -> Result.success()
            com.erez.reposync.data.model.SyncStatus.CONFLICT -> Result.failure()
            else -> Result.retry()
        }
    }

    companion object {
        const val KEY_PROFILE_ID = "profileId"

        fun inputData(profileId: String): Data {
            return Data.Builder().putString(KEY_PROFILE_ID, profileId).build()
        }
    }
}
