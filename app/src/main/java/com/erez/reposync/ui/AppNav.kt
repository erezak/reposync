package com.erez.reposync.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.erez.reposync.RepoSyncApp
import com.erez.reposync.ui.home.HomeScreen
import com.erez.reposync.ui.home.HomeViewModel
import com.erez.reposync.ui.profile.ProfileEditorScreen
import com.erez.reposync.ui.profile.ProfileEditorViewModel
import com.erez.reposync.ui.conflict.ConflictScreen
import com.erez.reposync.ui.sync.SyncRunScreen
import com.erez.reposync.ui.sync.SyncRunViewModel

@Composable
fun RepoSyncAppNav() {
    val navController = rememberNavController()
    val app = LocalContext.current.applicationContext as RepoSyncApp
    val factory = AppViewModelFactory(app.services)
    val navigateHome: () -> Unit = {
        val popped = navController.popBackStack()
        if (!popped) {
            navController.navigate("home") {
                popUpTo("home") { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            val vm: HomeViewModel = viewModel(factory = factory)
            val profiles by vm.profiles.collectAsState()
            HomeScreen(
                profiles = profiles,
                onAddProfile = { navController.navigate("edit") },
                onOpenProfile = { id -> navController.navigate("edit/$id") },
                onSyncProfile = { id -> navController.navigate("sync/$id") }
            )
        }
        composable("edit") {
            val vm: ProfileEditorViewModel = viewModel(factory = factory)
            ProfileEditorScreen(
                viewModel = vm,
                onBack = navigateHome,
                onOpenSync = { id -> navController.navigate("sync/$id") }
            )
        }
        composable(
            route = "edit/{profileId}",
            arguments = listOf(navArgument("profileId") { type = NavType.StringType })
        ) { entry ->
            val vm: ProfileEditorViewModel = viewModel(factory = factory)
            val profileId = entry.arguments?.getString("profileId")
            LaunchedEffect(profileId) {
                vm.loadProfile(profileId)
            }
            ProfileEditorScreen(
                viewModel = vm,
                onBack = navigateHome,
                onOpenSync = { id -> navController.navigate("sync/$id") }
            )
        }
        composable(
            route = "sync/{profileId}",
            arguments = listOf(navArgument("profileId") { type = NavType.StringType })
        ) { entry ->
            val vm: SyncRunViewModel = viewModel(factory = factory)
            val profileId = entry.arguments?.getString("profileId") ?: return@composable
            SyncRunScreen(
                profileId = profileId,
                viewModel = vm,
                onBack = navigateHome,
                onViewConflicts = { conflicts ->
                    navController.currentBackStackEntry?.savedStateHandle?.set("conflicts", ArrayList(conflicts))
                    navController.navigate("conflicts")
                }
            )
        }
        composable("conflicts") {
            val conflicts = navController.previousBackStackEntry
                ?.savedStateHandle
                ?.get<ArrayList<String>>("conflicts")
                ?: arrayListOf()
            ConflictScreen(conflictFiles = conflicts, onBack = navigateHome)
        }
    }
}
