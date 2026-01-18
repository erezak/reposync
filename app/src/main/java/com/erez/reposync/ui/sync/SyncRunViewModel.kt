package com.erez.reposync.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erez.reposync.AppServices
import com.erez.reposync.data.model.SyncStatus
import com.erez.reposync.data.repo.SyncMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class SyncRunViewModel(services: AppServices) : ViewModel() {
    private val syncRepository = services.syncRepository

    private val _state = MutableStateFlow(SyncRunState())
    val state: StateFlow<SyncRunState> = _state

    fun runSync(profileId: String, mode: SyncMode = SyncMode.FULL) {
        viewModelScope.launch {
            update { copy(status = SyncStatus.RUNNING, message = "Running sync...", currentStep = "Starting") }
            val result = syncRepository.syncNow(profileId, mode) { step ->
                update { copy(currentStep = step) }
            }
            val logText = result.logPath?.let { path ->
                runCatching { File(path).readText() }.getOrNull()
            } ?: ""
            val conflicts = extractConflicts(logText)
            update {
                copy(
                    status = result.status,
                    message = result.errorMessage ?: result.status.name,
                    logText = logText,
                    conflictFiles = conflicts,
                    currentStep = result.status.name
                )
            }
        }
    }

    private fun extractConflicts(logText: String): List<String> {
        if (logText.isBlank()) return emptyList()
        return logText.lines()
            .filter { it.trim().startsWith("-") }
            .map { it.removePrefix("-").trim() }
            .filter { it.isNotBlank() }
    }

    private fun update(block: SyncRunState.() -> SyncRunState) {
        _state.update { it.block() }
    }
}

data class SyncRunState(
    val status: SyncStatus = SyncStatus.IDLE,
    val message: String = "",
    val logText: String = "",
    val conflictFiles: List<String> = emptyList(),
    val currentStep: String = ""
)
