package com.erez.reposync.data.git

import android.content.Context
import java.io.File

class KnownHostsStore(context: Context) {
    private val file = File(context.filesDir, "known_hosts")

    fun getFile(): File = file

    fun hasAnyHosts(): Boolean = file.exists() && file.readText().isNotBlank()

    fun isTrusted(host: String, port: Int): Boolean {
        if (!file.exists()) return false
        val hostLabel = if (port == 22) host else "[$host]:$port"
        return file.readLines().any { it.startsWith("$hostLabel ") }
    }

    fun addHost(host: String, port: Int, keyType: String, keyBase64: String) {
        val hostLabel = if (port == 22) host else "[$host]:$port"
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            file.createNewFile()
        }
        file.appendText("$hostLabel $keyType $keyBase64\n")
    }
}
