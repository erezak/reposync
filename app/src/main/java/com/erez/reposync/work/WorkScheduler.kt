package com.erez.reposync.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.BackoffPolicy
import com.erez.reposync.data.model.SyncPolicyType
import com.erez.reposync.data.model.Profile
import java.util.concurrent.TimeUnit

class WorkScheduler(private val context: Context) {
    fun schedule(profile: Profile) {
        if (profile.syncPolicy.type != SyncPolicyType.PERIODIC) {
            cancel(profile.id)
            return
        }
        val interval = profile.syncPolicy.intervalMinutes ?: 60
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (profile.syncPolicy.requiresUnmetered) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresCharging(profile.syncPolicy.requiresCharging)
            .setRequiresBatteryNotLow(profile.syncPolicy.requiresBatteryNotLow)
            .build()
        val request = PeriodicWorkRequestBuilder<SyncWorker>(interval, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            workName(profile.id),
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel(profileId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(profileId))
    }

    private fun workName(profileId: String) = "sync_$profileId"
}
