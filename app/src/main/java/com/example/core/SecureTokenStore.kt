package com.example.core

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts Meta Instagram & WhatsApp Cloud API access tokens locally using Android Keystore AES-GCM.
 * Tokens never leave the device into Firestore documents.
 */
class SecureTokenStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    fun saveSecret(userId: String, key: String, plainValue: String) {
        if (plainValue.isBlank()) {
            clearSecret(userId, key)
            return
        }
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plainValue.toByteArray(Charsets.UTF_8))
            val encoded = Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
            prefs.edit().putString("${userId}_$key", encoded).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed for $key", e)
        }
    }

    fun getSecret(userId: String, key: String): String {
        val encoded = prefs.getString("${userId}_$key", null) ?: return ""
        return try {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            if (combined.size <= IV_SIZE_BYTES) return ""
            val iv = combined.copyOfRange(0, IV_SIZE_BYTES)
            val cipherText = combined.copyOfRange(IV_SIZE_BYTES, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed for $key", e)
            ""
        }
    }

    fun clearSecret(userId: String, key: String) {
        prefs.edit().remove("${userId}_$key").apply()
    }

    fun hasSecret(userId: String, key: String): Boolean = getSecret(userId, key).isNotBlank()

    companion object {
        private const val TAG = "SecureTokenStore"
        private const val PREFS_NAME = "leadfinder_secure_tokens"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "leadfinder_meta_token_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE_BYTES = 12
        private const val GCM_TAG_BITS = 128

        const val KEY_INSTAGRAM_TOKEN = "instagram_access_token"
        const val KEY_META_APP_ID = "meta_app_id"
        const val KEY_WHATSAPP_TOKEN = "whatsapp_access_token"
        const val KEY_WEBHOOK_VERIFY_TOKEN = "webhook_verify_token"
    }
}
