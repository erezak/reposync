package com.erez.reposync.ui.conflict

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConflictScreen(
    conflictFiles: List<String>,
    onBack: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Conflicts") },
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
            Text("Conflicts detected. Resolve them using a desktop Git client, then retry sync.")
            if (conflictFiles.isNotEmpty()) {
                Text("Conflicting files:")
                conflictFiles.forEach { Text("- $it") }
                Button(onClick = {
                    clipboard.setText(AnnotatedString(conflictFiles.joinToString("\n")))
                }) {
                    Text("Copy filename list")
                }
            }
        }
    }
}
