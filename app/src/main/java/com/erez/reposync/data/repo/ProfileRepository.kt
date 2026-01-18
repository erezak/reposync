package com.erez.reposync.data.repo

import com.erez.reposync.data.crypto.CryptoStore
import com.erez.reposync.data.db.dao.ProfileDao
import com.erez.reposync.data.db.entities.ProfileEntity
import com.erez.reposync.data.model.AuthMethod
import com.erez.reposync.data.model.IgnoreRules
import com.erez.reposync.data.model.Profile
import com.erez.reposync.data.model.SyncPolicy
import com.erez.reposync.data.model.SyncPolicyType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ProfileRepository(
    private val profileDao: ProfileDao,
    private val cryptoStore: CryptoStore
) {
    fun observeAll(): Flow<List<Profile>> = profileDao.observeAll().map { list ->
        list.map { it.toModel() }
    }

    suspend fun getById(id: String): Profile? = profileDao.getById(id)?.toModel()

    suspend fun listAll(): List<Profile> = profileDao.getAll().map { it.toModel() }

    suspend fun upsert(profile: Profile) {
        profileDao.upsert(profile.toEntity())
    }

    suspend fun delete(id: String) {
        profileDao.delete(id)
        cryptoStore.removeSecret(tokenKey(id))
        cryptoStore.removeSecret(sshPrivateKeyKey(id))
        cryptoStore.removeSecret(sshPublicKeyKey(id))
    }

    fun saveHttpsToken(profileId: String, token: String) {
        cryptoStore.putSecret(tokenKey(profileId), token)
    }

    fun getHttpsToken(profileId: String): String? = cryptoStore.getSecret(tokenKey(profileId))

    fun removeHttpsToken(profileId: String) {
        cryptoStore.removeSecret(tokenKey(profileId))
    }

    fun saveSshKeyPair(profileId: String, privateKeyPkcs8: String, publicKeyOpenSsh: String) {
        cryptoStore.putSecret(sshPrivateKeyKey(profileId), privateKeyPkcs8)
        cryptoStore.putSecret(sshPublicKeyKey(profileId), publicKeyOpenSsh)
    }

    fun getSshPrivateKey(profileId: String): String? = cryptoStore.getSecret(sshPrivateKeyKey(profileId))

    fun getSshPublicKey(profileId: String): String? = cryptoStore.getSecret(sshPublicKeyKey(profileId))

    private fun tokenKey(profileId: String) = "token_$profileId"
    private fun sshPrivateKeyKey(profileId: String) = "ssh_priv_$profileId"
    private fun sshPublicKeyKey(profileId: String) = "ssh_pub_$profileId"
}

private fun ProfileEntity.toModel(): Profile {
    val policyParts = syncPolicy.split("|")
    val type = when (policyParts.getOrNull(0)) {
        "PERIODIC" -> SyncPolicyType.PERIODIC
        else -> SyncPolicyType.MANUAL
    }
    val interval = policyParts.getOrNull(1)?.toLongOrNull()
    val unmetered = policyParts.getOrNull(2)?.toBoolean() ?: false
    val charging = policyParts.getOrNull(3)?.toBoolean() ?: false
    val batteryNotLow = policyParts.getOrNull(4)?.toBoolean() ?: false
    return Profile(
        id = id,
        name = name,
        targetTreeUri = targetTreeUri,
        remoteUrl = remoteUrl,
        branch = branch,
        authMethod = AuthMethod.valueOf(authMethod),
        httpsUsername = httpsUsername,
        authorName = authorName,
        authorEmail = authorEmail,
        commitMessageTemplate = commitMessageTemplate,
        propagateDeletes = propagateDeletes,
        syncPolicy = SyncPolicy(type, interval, unmetered, charging, batteryNotLow),
        ignoreRules = IgnoreRules(
            patterns = ignorePatterns.split("\n").filter { it.isNotBlank() },
            preset = ignorePreset
        )
    )
}

private fun Profile.toEntity(): ProfileEntity {
    val syncPolicyString = listOf(
        syncPolicy.type.name,
        syncPolicy.intervalMinutes?.toString() ?: "",
        syncPolicy.requiresUnmetered.toString(),
        syncPolicy.requiresCharging.toString(),
        syncPolicy.requiresBatteryNotLow.toString()
    ).joinToString("|")
    return ProfileEntity(
        id = id,
        name = name,
        targetTreeUri = targetTreeUri,
        remoteUrl = remoteUrl,
        branch = branch,
        authMethod = authMethod.name,
        httpsUsername = httpsUsername,
        authorName = authorName,
        authorEmail = authorEmail,
        commitMessageTemplate = commitMessageTemplate,
        propagateDeletes = propagateDeletes,
        syncPolicy = syncPolicyString,
        ignorePatterns = ignoreRules.patterns.joinToString("\n"),
        ignorePreset = ignoreRules.preset
    )
}
