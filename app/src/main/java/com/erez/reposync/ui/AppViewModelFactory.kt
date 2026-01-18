package com.erez.reposync.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.erez.reposync.AppServices
import com.erez.reposync.ui.home.HomeViewModel
import com.erez.reposync.ui.profile.ProfileEditorViewModel
import com.erez.reposync.ui.sync.SyncRunViewModel

class AppViewModelFactory(private val services: AppServices) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> HomeViewModel(services) as T
            modelClass.isAssignableFrom(ProfileEditorViewModel::class.java) -> ProfileEditorViewModel(services) as T
            modelClass.isAssignableFrom(SyncRunViewModel::class.java) -> SyncRunViewModel(services) as T
            else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
