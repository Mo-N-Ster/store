package com.vibe.store.infrastructure.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.vibe.store.application.security.SecretAdapter
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Trusted composition only. Envelope never exposed in a public DTO or log. */
class KeystoreSecrets internal constructor(private val alias: String) : SecretAdapter {
    constructor() : this("store.smtp.v1")
    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    @Synchronized override fun seal(value: ByteArray): ByteArray {
        require(value.size <= 4096)
        try {
            val key = (store().getKey(alias, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true).setKeySize(256).build())
            }.generateKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key)
            check(cipher.iv.size == 12)
            return byteArrayOf(1) + cipher.iv + cipher.doFinal(value)
        } catch (_: Exception) { throw IllegalStateException("SECRET_UNAVAILABLE") }
    }
    @Synchronized override fun open(envelope: ByteArray): ByteArray? {
        if (envelope.size !in 29..4125 || envelope[0] != 1.toByte()) return null
        return try {
            // Never generate a replacement key while decrypting existing data.
            val key = store().getKey(alias, null) as? SecretKey ?: return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, envelope.copyOfRange(1, 13)))
            cipher.doFinal(envelope, 13, envelope.size - 13)
        } catch (_: Exception) { null }
    }
}
