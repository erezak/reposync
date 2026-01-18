package com.erez.reposync.data.git

import android.content.Context
import org.eclipse.jgit.transport.sshd.SshdSessionFactory
import org.eclipse.jgit.transport.sshd.SshdSessionFactoryBuilder
import java.io.Closeable
import java.io.File
import java.util.UUID

class RepoSyncSshSessionFactory private constructor(
    val factory: SshdSessionFactory,
    private val sshDir: File
) : Closeable {
    override fun close() {
        sshDir.deleteRecursively()
    }

    companion object {
        fun create(
            context: Context,
            privateKeyPkcs8Base64: String,
            publicKeyOpenSsh: String,
            knownHostsStore: KnownHostsStore
        ): RepoSyncSshSessionFactory {
            val sshDir = File(context.cacheDir, "ssh-${UUID.randomUUID()}")
            sshDir.mkdirs()
            val privateKeyFile = File(sshDir, "id_ed25519")
            val publicKeyFile = File(sshDir, "id_ed25519.pub")
            privateKeyFile.writeText(toPem(privateKeyPkcs8Base64))
            publicKeyFile.writeText(publicKeyOpenSsh.trim())
            privateKeyFile.setReadable(true, true)
            privateKeyFile.setWritable(true, true)
            val knownHostsFile = knownHostsStore.getFile()
            if (knownHostsFile.exists()) {
                val target = File(sshDir, "known_hosts")
                knownHostsFile.copyTo(target, overwrite = true)
            }
            val builder = SshdSessionFactoryBuilder()
                .setHomeDirectory(sshDir)
                .setSshDirectory(sshDir)
                .setPreferredAuthentications("publickey")
            val factory = builder.build(org.eclipse.jgit.transport.sshd.JGitKeyCache())
            return RepoSyncSshSessionFactory(factory, sshDir)
        }

        private fun toPem(base64: String): String {
            val chunked = base64.chunked(64).joinToString("\n")
            return "-----BEGIN PRIVATE KEY-----\n$chunked\n-----END PRIVATE KEY-----\n"
        }
    }
}
