package com.erez.reposync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.erez.reposync.data.crypto.CryptoStore
import com.erez.reposync.data.git.GitClient
import com.erez.reposync.data.model.AuthMethod
import com.erez.reposync.data.model.Profile
import kotlinx.coroutines.runBlocking
import org.eclipse.jgit.api.Git
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class GitClientInstrumentedTest {
    @Test
    fun cloneInitAndPush() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val crypto = CryptoStore(context)
        val gitClient = GitClient(context, crypto)

        val tempDir = File(context.cacheDir, "git-test")
        if (tempDir.exists()) tempDir.deleteRecursively()
        tempDir.mkdirs()
        val bareRemote = File(tempDir, "remote.git")
        Git.init().setBare(true).setDirectory(bareRemote).call()

        val profile = Profile(
            name = "Test",
            targetTreeUri = "content://test",
            remoteUrl = bareRemote.toURI().toString(),
            branch = "main",
            authMethod = AuthMethod.HTTPS_TOKEN,
            authorName = "Test",
            authorEmail = "test@example.com"
        )

        val repoDir = gitClient.ensureRepoDir(profile.id)
        val git = gitClient.cloneRepository(profile, repoDir)
        val testFile = File(git.repository.workTree, "hello.txt")
        testFile.writeText("hello")
        gitClient.stageAll(git)
        gitClient.commitIfNeeded(git, profile.authorName, profile.authorEmail, profile.commitMessageTemplate)
        gitClient.push(git, profile)

        assertTrue(File(bareRemote, "refs/heads/main").exists())
    }
}
