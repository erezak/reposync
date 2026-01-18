package com.erez.reposync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.erez.reposync.ui.theme.RepoSyncTheme
import com.erez.reposync.ui.RepoSyncAppNav

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RepoSyncTheme {
                RepoSyncAppNav()
            }
        }
    }
}