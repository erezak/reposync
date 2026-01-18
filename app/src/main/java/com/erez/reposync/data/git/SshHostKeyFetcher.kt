package com.erez.reposync.data.git

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.security.MessageDigest
import java.security.PublicKey
import java.util.Collections

class SshHostKeyFetcher {
    suspend fun fetch(host: String, port: Int): HostKeyInfo = withContext(Dispatchers.IO) {
        val client = SSHClient()
        var captured: PublicKey? = null
        client.addHostKeyVerifier(object : HostKeyVerifier {
            override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
                captured = key
                return true
            }

            override fun findExistingAlgorithms(hostname: String, port: Int): MutableList<String> {
                return Collections.emptyList()
            }
        })
        try {
            client.connect(host, port)
        } finally {
            try {
                client.disconnect()
            } catch (_: Exception) {
                // ignore
            }
        }
        val key = captured ?: throw IllegalStateException("Unable to fetch host key")
        val keyType = KeyType.fromKey(key).toString()
        val fingerprint = sha256Fingerprint(key)
        val keyBase64 = Base64.encodeToString(key.encoded, Base64.NO_WRAP)
        HostKeyInfo(host, port, keyType, fingerprint, keyBase64)
    }

    private fun sha256Fingerprint(key: PublicKey): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.encoded)
        val fp = Base64.encodeToString(digest, Base64.NO_WRAP)
        return "SHA256:$fp"
    }

    data class HostKeyInfo(
        val host: String,
        val port: Int,
        val keyType: String,
        val fingerprint: String,
        val keyBase64: String
    )
}
