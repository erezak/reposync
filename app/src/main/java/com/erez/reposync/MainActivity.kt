package com.erez.reposync

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.erez.reposync.ui.theme.RepoSyncTheme
import com.erez.reposync.ui.RepoSyncAppNav
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val services by lazy { (application as RepoSyncApp).services }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RepoSyncTheme {
                RepoSyncAppNav()
            }
        }
        handleOAuthRedirect(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            handleOAuthRedirect(intent)
        }
    }

    private fun handleOAuthRedirect(intent: Intent) {
        val data = intent.data ?: return
        lifecycleScope.launch {
            services.githubAuthRepository.handleRedirect(data)
        }
    }
}