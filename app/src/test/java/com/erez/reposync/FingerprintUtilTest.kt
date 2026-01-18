package com.erez.reposync

import com.erez.reposync.data.repo.FingerprintUtil
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class FingerprintUtilTest {
    @Test
    fun computesStableSha256() {
        val temp = File.createTempFile("reposync", ".txt")
        temp.writeText("hello world")
        val hash1 = FingerprintUtil.sha256(temp)
        val hash2 = FingerprintUtil.sha256(temp)
        assertEquals(hash1, hash2)
        temp.delete()
    }
}
