package com.quickdrop.core.security

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface ApiKeyVault {
    fun hasApiKey(): Boolean
    fun getMaskedKeyPreview(): String?
    fun getDecryptedApiKey(): String?
    fun saveApiKey(rawKey: String)
    fun removeApiKey()
}

/**
 * AES-256-GCM authenticated encryption engine used by both Android Keystore storage
 * and JVM unit test verification.
 *
 * Ensures:
 * - Fresh 12-byte IV per encryption operation
 * - 128-bit authentication tag
 * - Key material and ciphertext can be wiped cleanly on removal or Clear Local Data
 */
class AesGcmKeyVault(
    private val secretKeyProvider: () -> SecretKey,
    private val onDestroyKeyMaterial: () -> Unit = {}
) : ApiKeyVault {

    companion object {
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128

        fun generateEphemeralSecretKey(): SecretKey {
            val keyGen = KeyGenerator.getInstance("AES")
            keyGen.init(256)
            return keyGen.generateKey()
        }

        fun maskApiKey(rawKey: String): String {
            val trimmed = rawKey.trim()
            if (trimmed.isEmpty()) return ""
            if (trimmed.length <= 6) return "••••••"
            val tail = trimmed.takeLast(4)
            return "••••••••••••••••$tail"
        }
    }

    private val secureRandom = SecureRandom()
    private var encryptedPayloadBase64: String? = null

    override fun hasApiKey(): Boolean = !encryptedPayloadBase64.isNullOrBlank()

    override fun getMaskedKeyPreview(): String? {
        val raw = getDecryptedApiKey() ?: return null
        return maskApiKey(raw)
    }

    override fun saveApiKey(rawKey: String) {
        val clean = rawKey.trim()
        require(clean.isNotEmpty()) { "API key must not be blank" }
        val iv = ByteArray(GCM_IV_BYTES).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKeyProvider(), GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(clean.toByteArray(Charsets.UTF_8))

        val combined = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
        encryptedPayloadBase64 = Base64.getEncoder().encodeToString(combined)
    }

    override fun getDecryptedApiKey(): String? {
        val encoded = encryptedPayloadBase64 ?: return null
        return try {
            val combined = Base64.getDecoder().decode(encoded)
            if (combined.size <= GCM_IV_BYTES) return null
            val iv = combined.copyOfRange(0, GCM_IV_BYTES)
            val ciphertext = combined.copyOfRange(GCM_IV_BYTES, combined.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKeyProvider(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    override fun removeApiKey() {
        encryptedPayloadBase64 = null
        onDestroyKeyMaterial()
    }
}
