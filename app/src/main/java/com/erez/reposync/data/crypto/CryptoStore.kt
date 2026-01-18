package com.erez.reposync.data.crypto

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class CryptoStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun putSecret(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun getSecret(key: String): String? {
        return prefs.getString(key, null)
    }

    fun removeSecret(key: String) {
        prefs.edit().remove(key).apply()
    }
}
