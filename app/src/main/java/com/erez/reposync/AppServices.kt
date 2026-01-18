package com.erez.reposync

import android.content.Context
import androidx.room.Room
import com.erez.reposync.data.crypto.CryptoStore
import com.erez.reposync.data.db.AppDatabase
import com.erez.reposync.data.git.GitClient
import com.erez.reposync.data.repo.ProfileRepository
import com.erez.reposync.data.repo.SyncRepository
import com.erez.reposync.data.saf.SafRepository

class AppServices(context: Context) {
    val appContext: Context = context.applicationContext

    val database: AppDatabase = Room.databaseBuilder(
        appContext,
        AppDatabase::class.java,
        "reposync.db"
    ).addMigrations(AppDatabase.MIGRATION_1_2)
        .build()

    val cryptoStore: CryptoStore = CryptoStore(appContext)

    val safRepository: SafRepository = SafRepository(appContext)

    val gitClient: GitClient = GitClient(appContext, cryptoStore)

    val profileRepository: ProfileRepository = ProfileRepository(
        database.profileDao(),
        cryptoStore
    )

    val syncRepository: SyncRepository = SyncRepository(
        appContext,
        database.fingerprintDao(),
        database.syncLogDao(),
        profileRepository,
        safRepository,
        gitClient,
        cryptoStore
    )
}
