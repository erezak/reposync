package com.erez.reposync.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erez.reposync.AppServices
import com.erez.reposync.data.model.Profile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(services: AppServices) : ViewModel() {
    private val profileRepository = services.profileRepository

    val profiles: StateFlow<List<Profile>> = profileRepository.observeAll()
        .map { it.sortedBy { profile -> profile.name.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
