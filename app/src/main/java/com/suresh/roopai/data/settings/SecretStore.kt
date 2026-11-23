package com.suresh.roopai.data.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Small encrypted key-value store for API keys. */
interface SecretStore {
    suspend fun get(name: String): String?

    /** Stores [value]; null or blank removes the entry. */
    suspend fun put(name: String, value: String?)
}

/**
 * Values are encrypted with AES-256-GCM using a key that lives in the Android Keystore
 * (hardware-backed where available) and never leaves it. Only ciphertext and IV are written to
 * a private preferences file, which is excluded from backups.
 */
@Singleton
class KeystoreSecretStore @Inject constructor(@ApplicationContext context: Context) : SecretStore {
    private val prefs = context.getSharedPreferences("secure_store", Context.MODE_PRIVATE)

    override suspend fun get(name: String): String? = withContext(Dispatchers.IO) {
        val stored = prefs.getString(name, null) ?: return@withContext null
        try {
            val parts = stored.split(':')
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val data = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(data), Charsets.UTF_8)
        } catch (e: Exception) {
            // key was invalidated (e.g. device restore): the value is unrecoverable, drop it
            Log.w(TAG, "stored secret could not be decrypted", e)
            prefs.edit { remove(name) }
            null
        }
    }

    override suspend fun put(name: String, value: String?) = withContext(Dispatchers.IO) {
        if (value.isNullOrBlank()) {
            prefs.edit { remove(name) }
            return@withContext
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val data = cipher.doFinal(value.trim().toByteArray(Charsets.UTF_8))
        val encoded = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(data, Base64.NO_WRAP)
        prefs.edit { putString(name, encoded) }
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private companion object {
        const val TAG = "SecretStore"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "roopai_secrets_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
