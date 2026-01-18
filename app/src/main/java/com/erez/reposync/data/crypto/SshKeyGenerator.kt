package com.erez.reposync.data.crypto

import android.util.Base64
import net.i2p.crypto.eddsa.EdDSAPrivateKey
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAParameterSpec
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.KeyPairGenerator

class SshKeyGenerator {
    fun generateEd25519(comment: String = "reposync"): KeyPairResult {
        val spec: EdDSAParameterSpec = EdDSANamedCurveTable.getByName("Ed25519")
        val generator = KeyPairGenerator.getInstance("EdDSA")
        generator.initialize(spec)
        val keyPair = generator.generateKeyPair()
        val privateKey = keyPair.private as EdDSAPrivateKey
        val publicKey = keyPair.public as EdDSAPublicKey
        val privateKeyPkcs8 = Base64.encodeToString(privateKey.encoded, Base64.NO_WRAP)
        val publicKeyOpenSsh = buildOpenSshPublicKey(publicKey, comment)
        return KeyPairResult(privateKeyPkcs8, publicKeyOpenSsh)
    }

    private fun buildOpenSshPublicKey(publicKey: EdDSAPublicKey, comment: String): String {
        val keyType = "ssh-ed25519"
        val rawPublicKey = publicKey.abyte
        val buffer = ByteArrayOutputStream()
        buffer.write(sshString(keyType.toByteArray()))
        buffer.write(sshString(rawPublicKey))
        val encoded = Base64.encodeToString(buffer.toByteArray(), Base64.NO_WRAP)
        return "$keyType $encoded $comment"
    }

    private fun sshString(bytes: ByteArray): ByteArray {
        val length = ByteBuffer.allocate(4).putInt(bytes.size).array()
        return length + bytes
    }

    data class KeyPairResult(
        val privateKeyPkcs8Base64: String,
        val publicKeyOpenSsh: String
    )
}
