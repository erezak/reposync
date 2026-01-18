package com.erez.reposync.ui.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.erez.reposync.data.repo.SyncMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncRunScreen(
    profileId: String,
    viewModel: SyncRunViewModel,
    onBack: () -> Unit,
    onViewConflicts: (List<String>) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(profileId) {
        viewModel.runSync(profileId, SyncMode.FULL)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Sync") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Status: ${state.status}")
            if (state.currentStep.isNotBlank()) {
                Text("Current step: ${state.currentStep}")
            }
            if (state.message.isNotBlank()) {
                Text(state.message)
            }
            if (state.logText.isNotBlank()) {
                Text("Log:")
                Text(state.logText)
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (state.status == com.erez.reposync.data.model.SyncStatus.CONFLICT) {
                Button(onClick = { onViewConflicts(state.conflictFiles) }) {
                    Text("View conflicts")
                }
            }
            if (state.logText.isNotBlank()) {
                Button(onClick = {
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, state.logText)
                    }
                    context.startActivity(android.content.Intent.createChooser(intent, "Share log"))
                }) {
                    Text("Share log")
                }
            }
        }
    }
}
