package org.css_apps_m3.password_manager.data

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object SyncCrypto {
    private const val VERSION_V1 = "v1"
    private const val VERSION_V2 = "v2"
    private const val KDF_ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val IV_SIZE = 12
    private val FIXED_SALT = "org.css_apps_m3.password_manager.sync.v2".toByteArray(StandardCharsets.UTF_8)

    private val random = SecureRandom()

    class Session internal constructor(internal val key: SecretKeySpec)

    fun newSession(passphrase: String): Session = Session(deriveKey(passphrase, FIXED_SALT))

    fun encrypt(plainText: String, session: Session): String {
        val iv = ByteArray(IV_SIZE).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, session.key, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        return listOf(
            VERSION_V2,
            Base64.encodeToString(iv, Base64.NO_WRAP),
            Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        ).joinToString(":")
    }

    fun decrypt(payload: String, session: Session, passphrase: String): String {
        val parts = payload.split(":")
        return when {
            parts.size == 3 && parts[0] == VERSION_V2 -> {
                val iv = Base64.decode(parts[1], Base64.NO_WRAP)
                val ciphertext = Base64.decode(parts[2], Base64.NO_WRAP)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, session.key, GCMParameterSpec(128, iv))
                String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
            }
            parts.size == 4 && parts[0] == VERSION_V1 -> {
                val salt = Base64.decode(parts[1], Base64.NO_WRAP)
                val iv = Base64.decode(parts[2], Base64.NO_WRAP)
                val ciphertext = Base64.decode(parts[3], Base64.NO_WRAP)
                val legacyKey = deriveKey(passphrase, salt)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, legacyKey, GCMParameterSpec(128, iv))
                String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
            }
            else -> payload
        }
    }

    fun versionOf(payload: String): String? {
        val idx = payload.indexOf(':')
        if (idx <= 0) return null
        return payload.substring(0, idx)
    }

    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, KDF_ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val encoded = factory.generateSecret(spec).encoded
        return SecretKeySpec(encoded, "AES")
    }
}
