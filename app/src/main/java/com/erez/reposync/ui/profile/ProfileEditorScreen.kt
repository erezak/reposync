package com.erez.reposync.ui.profile

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.erez.reposync.data.model.AuthMethod
import com.erez.reposync.ui.profile.IgnorePreset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorScreen(
    viewModel: ProfileEditorViewModel,
    onBack: () -> Unit,
    onOpenSync: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val repoDateFormatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy") }
    LaunchedEffect(Unit) {
        viewModel.clearGitHubError()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            viewModel.updateTreeUri(uri)
        }
    }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAdvancedAuth by remember { mutableStateOf(false) }
    var showRepoPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Profile") },
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
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::updateName,
                label = { Text("Profile name") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = { picker.launch(null) }) {
                val label = if (state.targetTreeUri.isBlank()) {
                    "Pick target folder"
                } else if (state.targetTreeName.isNotBlank()) {
                    "Folder: ${state.targetTreeName}"
                } else {
                    "Folder selected"
                }
                Text(label)
            }
            OutlinedTextField(
                value = state.remoteUrl,
                onValueChange = viewModel::updateRemoteUrl,
                label = { Text("Remote URL") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.branch,
                onValueChange = viewModel::updateBranch,
                label = { Text("Branch") },
                modifier = Modifier.fillMaxWidth()
            )
            Text("GitHub (recommended)", style = MaterialTheme.typography.titleMedium)
            Text(
                if (state.githubAuthenticated) {
                    if (state.githubLogin.isNotBlank()) "Logged in as ${state.githubLogin}" else "Logged in"
                } else {
                    "Not logged in"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.githubAuthError.isNotBlank()) {
                Text(
                    "GitHub error: ${state.githubAuthError}",
                    color = MaterialTheme.colorScheme.error
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        viewModel.clearGitHubError()
                        val url = viewModel.startGitHubLogin()
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    },
                    enabled = !state.githubAuthLoading
                ) {
                    Text(if (state.githubAuthenticated) "Re-authenticate" else "Login with GitHub")
                }
                if (state.githubAuthenticated) {
                    OutlinedButton(onClick = viewModel::logoutGitHub) {
                        Text("Log out")
                    }
                }
            }
            if (state.githubAuthLoading) {
                CircularProgressIndicator()
            }
            if (state.githubAuthenticated) {
                Button(onClick = {
                    showRepoPicker = true
                    viewModel.loadGitHubRepos(reset = true)
                }) {
                    Text("Select Repository")
                }
                if (state.authMethod != AuthMethod.GITHUB_OAUTH) {
                    OutlinedButton(onClick = { viewModel.updateAuthMethod(AuthMethod.GITHUB_OAUTH) }) {
                        Text("Use GitHub login")
                    }
                }
            }
            Text("Advanced authentication", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { showAdvancedAuth = !showAdvancedAuth }) {
                Text(if (showAdvancedAuth) "Hide advanced" else "Show advanced")
            }
            if (showAdvancedAuth) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RadioButton(selected = state.authMethod == AuthMethod.HTTPS_TOKEN, onClick = { viewModel.updateAuthMethod(AuthMethod.HTTPS_TOKEN) })
                    Text("HTTPS token")
                    RadioButton(selected = state.authMethod == AuthMethod.SSH_KEY, onClick = { viewModel.updateAuthMethod(AuthMethod.SSH_KEY) })
                    Text("SSH key")
                }
                if (state.authMethod == AuthMethod.HTTPS_TOKEN) {
                    OutlinedTextField(
                        value = state.httpsUsername,
                        onValueChange = viewModel::updateHttpsUsername,
                        label = { Text("HTTPS username") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = state.httpsToken,
                        onValueChange = viewModel::updateHttpsToken,
                        label = { Text("Token") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.hasSavedToken
                    )
                    Button(onClick = {
                        viewModel.saveToken(state.httpsToken)
                    }, enabled = !state.isBusy && !state.hasSavedToken) {
                        Text("Save token")
                    }
                    if (state.hasSavedToken) {
                        Button(onClick = viewModel::deleteToken, enabled = !state.isBusy) {
                            Text("Delete token")
                        }
                    }
                }
                if (state.authMethod == AuthMethod.SSH_KEY) {
                    Button(onClick = viewModel::generateSshKey) {
                        Text(if (state.sshPublicKey.isBlank()) "Generate SSH key" else "Regenerate SSH key")
                    }
                    if (state.sshPublicKey.isNotBlank()) {
                        Text("Public key:")
                        Text(state.sshPublicKey)
                    }
                }
            }
            OutlinedTextField(
                value = state.authorName,
                onValueChange = viewModel::updateAuthorName,
                label = { Text("Commit author name") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.authorEmail,
                onValueChange = viewModel::updateAuthorEmail,
                label = { Text("Commit author email") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.commitTemplate,
                onValueChange = viewModel::updateCommitTemplate,
                label = { Text("Commit template") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.ignorePatterns,
                onValueChange = viewModel::updateIgnorePatterns,
                label = { Text("Ignore patterns") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            Text("Ignore presets", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.applyIgnorePreset(IgnorePreset.GENERIC) }) {
                    Text("Generic")
                }
                Button(onClick = { viewModel.applyIgnorePreset(IgnorePreset.OBSIDIAN) }) {
                    Text("Obsidian")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(checked = state.propagateDeletes, onCheckedChange = viewModel::updatePropagateDeletes)
                Text("Propagate deletes")
            }
            Text("Background sync", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(checked = state.periodicEnabled, onCheckedChange = viewModel::updatePeriodicEnabled)
                Text("Enable periodic sync")
            }
            if (state.periodicEnabled) {
                Text("Interval (minutes)")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.updateIntervalMinutes(15) }) { Text("15") }
                    Button(onClick = { viewModel.updateIntervalMinutes(60) }) { Text("60") }
                    Button(onClick = { viewModel.updateIntervalMinutes(360) }) { Text("360") }
                    Button(onClick = { viewModel.updateIntervalMinutes(1440) }) { Text("1440") }
                }
                Text("Selected: ${state.intervalMinutes} min")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Switch(checked = state.requiresUnmetered, onCheckedChange = viewModel::updateRequiresUnmetered)
                    Text("Unmetered only")
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Switch(checked = state.requiresCharging, onCheckedChange = viewModel::updateRequiresCharging)
                    Text("Charging only")
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Switch(checked = state.requiresBatteryNotLow, onCheckedChange = viewModel::updateRequiresBatteryNotLow)
                    Text("Battery not low")
                }
            }
            Text("Setup mode", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RadioButton(selected = state.setupMode == SetupMode.CLONE, onClick = { viewModel.updateSetupMode(SetupMode.CLONE) })
                Text("Clone remote")
                RadioButton(selected = state.setupMode == SetupMode.IMPORT, onClick = { viewModel.updateSetupMode(SetupMode.IMPORT) })
                Text("Import target folder")
            }
            Button(onClick = viewModel::testConnection) {
                Text("Test connection")
            }
            val pendingHostKey = state.pendingHostKey
            if (pendingHostKey != null) {
                Text("SSH host fingerprint: ${pendingHostKey.fingerprint}")
                Button(onClick = viewModel::trustPendingHostKey) {
                    Text("Trust host key")
                }
            }
            if (state.connectionStatus.isNotBlank()) {
                Text("Status: ${state.connectionStatus}")
            }
            if (state.isBusy) {
                CircularProgressIndicator()
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { viewModel.saveProfile { onBack() } }, enabled = !state.isBusy) {
                    Text("Save")
                }
                Button(onClick = { viewModel.setupRepository { onOpenSync(it) } }, enabled = !state.isBusy) {
                    Text("Setup & Sync")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (state.id.isNotBlank()) {
                OutlinedButton(onClick = { showDeleteDialog = true }) {
                    Text("Delete profile")
                }
            }
            OutlinedButton(onClick = onBack) {
                Text("Back")
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete profile") },
            text = {
                Text("Choose whether to delete only the profile or also delete the folder contents.")
            },
            confirmButton = {
                Button(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteProfile(deleteContent = true, onComplete = onBack)
                }) {
                    Text("Delete profile + files")
                }
            },
            dismissButton = {
                Button(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteProfile(deleteContent = false, onComplete = onBack)
                }) {
                    Text("Delete profile only")
                }
            }
        )
    }

    if (showRepoPicker) {
        AlertDialog(
            onDismissRequest = { showRepoPicker = false },
            title = { Text("Select repository") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.githubReposError.isNotBlank()) {
                        Text(
                            "Error: ${state.githubReposError}",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (state.githubReposLoading && state.githubRepos.isEmpty()) {
                        CircularProgressIndicator()
                    }
                    LazyColumn(modifier = Modifier.height(320.dp)) {
                        items(state.githubRepos) { repo ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.selectGitHubRepo(repo)
                                        showRepoPicker = false
                                    }
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(repo.fullName, style = MaterialTheme.typography.titleMedium)
                                val updated = repo.updatedAt
                                    .atZone(ZoneId.systemDefault())
                                    .format(repoDateFormatter)
                                Text(
                                    "Updated $updated",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    if (state.githubHasNextPage) {
                        Button(
                            onClick = { viewModel.loadGitHubRepos(reset = false) },
                            enabled = !state.githubReposLoading
                        ) {
                            Text(if (state.githubReposLoading) "Loading..." else "Load more")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRepoPicker = false }) {
                    Text("Close")
                }
            }
        )
    }
}
