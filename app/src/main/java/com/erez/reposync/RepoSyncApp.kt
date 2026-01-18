package com.erez.reposync

import android.app.Application
import net.i2p.crypto.eddsa.EdDSASecurityProvider
import java.security.Security

class RepoSyncApp : Application() {
    lateinit var services: AppServices
        private set

    override fun onCreate() {
        super.onCreate()
        ensureEdDsaProvider()
        services = AppServices(this)
    }

    private fun ensureEdDsaProvider() {
        if (Security.getProvider(EdDSASecurityProvider.PROVIDER_NAME) == null) {
            Security.addProvider(EdDSASecurityProvider())
        }
    }
}
