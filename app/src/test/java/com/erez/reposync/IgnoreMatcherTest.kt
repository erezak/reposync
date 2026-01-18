package com.erez.reposync

import com.erez.reposync.data.model.AuthMethod
import com.erez.reposync.data.model.Profile
import com.erez.reposync.data.repo.IgnoreMatcher
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class IgnoreMatcherTest {
    @Test
    fun matchesDefaultIgnorePatterns() {
        val profile = Profile(
            name = "Test",
            targetTreeUri = "content://test",
            remoteUrl = "https://example.com/repo.git",
            branch = "main",
            authMethod = AuthMethod.HTTPS_TOKEN,
            authorName = "Test",
            authorEmail = "test@example.com"
        )
        val matcher = IgnoreMatcher.fromProfile(profile, File("."))
        assertTrue(matcher.isIgnored(".DS_Store", false))
        assertTrue(matcher.isIgnored("Thumbs.db", false))
        assertFalse(matcher.isIgnored("notes.txt", false))
    }

    @Test
    fun matchesCustomIgnorePattern() {
        val profile = Profile(
            name = "Test",
            targetTreeUri = "content://test",
            remoteUrl = "https://example.com/repo.git",
            branch = "main",
            authMethod = AuthMethod.HTTPS_TOKEN,
            authorName = "Test",
            authorEmail = "test@example.com",
            ignoreRules = com.erez.reposync.data.model.IgnoreRules(patterns = listOf("*.log"))
        )
        val matcher = IgnoreMatcher.fromProfile(profile, File("."))
        assertTrue(matcher.isIgnored("debug.log", false))
        assertFalse(matcher.isIgnored("debug.txt", false))
    }
}
