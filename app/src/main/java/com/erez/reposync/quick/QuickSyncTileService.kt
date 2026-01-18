package com.erez.reposync.quick

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.erez.reposync.MainActivity
import com.erez.reposync.RepoSyncApp
import com.erez.reposync.work.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import android.os.Handler
import android.os.Looper

class QuickSyncTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onClick() {
        super.onClick()
        val app = applicationContext as RepoSyncApp
        scope.launch {
            val profiles = app.services.profileRepository.listAll()
            when (profiles.size) {
                0 -> {
                    showToast("No profiles configured")
                    openApp()
                }
                1 -> {
                    val profileId = profiles.first().id
                    val request = OneTimeWorkRequestBuilder<SyncWorker>()
                        .setInputData(SyncWorker.inputData(profileId))
                        .build()
                    WorkManager.getInstance(applicationContext).enqueue(request)
                    showToast("Sync started")
                }
                else -> {
                    showToast("Multiple profiles. Open app to sync.")
                    openApp()
                }
            }
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.state = Tile.STATE_ACTIVE
        qsTile?.updateTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivityAndCollapse(intent)
    }

    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
}
