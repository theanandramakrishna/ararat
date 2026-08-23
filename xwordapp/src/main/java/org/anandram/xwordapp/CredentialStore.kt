package org.anandram.xwordapp

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores per-subscription credentials, encrypted at rest with a key held in
 * the Android Keystore. Credentials are keyed by subscription name and are
 * deliberately not covered by Drive backup.
 */
object CredentialStore {
    private const val FILE_NAME = "credentials_secure"

    private lateinit var prefs: SharedPreferences

    @Synchronized
    fun init(context: Context) {
        if (::prefs.isInitialized) return

        val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

        prefs = EncryptedSharedPreferences.create(
                context,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }

    @Synchronized
    fun set(name: String, username: String, password: String) {
        prefs.edit()
                .putString(key(name, "username"), username)
                .putString(key(name, "password"), password)
                .apply()
    }

    @Synchronized
    fun get(name: String): Pair<String, String>? {
        val username = prefs.getString(key(name, "username"), null) ?: return null
        val password = prefs.getString(key(name, "password"), null) ?: return null
        return username to password
    }

    @Synchronized
    fun has(name: String): Boolean = get(name) != null

    private fun key(name: String, field: String): String = "$name.$field"
}
