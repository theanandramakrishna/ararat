package org.anandram.xwordapp

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import java.io.File

object SubscriptionManager {
    private const val TAG = "SubscriptionManager"
    private const val FILE_NAME = "subscriptions.json"
    private const val DEFAULT_NAME = "Kegler's Block Style"
    private const val DEFAULT_URL = "https://kegler.gitlab.io/Block_style/"
    private const val PRIVATE_EYE_NAME = "Private Eye Cryptics"
    private const val PRIVATE_EYE_URL = "https://www.private-eye.co.uk/pictures/crossword/download/"

    private lateinit var appContext: Context
    private lateinit var file: File

    @Synchronized
    fun init(context: Context) {
        if (::appContext.isInitialized) return

        appContext = context.applicationContext
        file = File(appContext.filesDir, FILE_NAME)
        ensureDefaults()
    }

    private fun ensureDefaults() {
        if (file.exists()) return

        saveSubscriptions(listOf(
                Subscription(name = DEFAULT_NAME, url = DEFAULT_URL, enabled = true),
                Subscription(name = PRIVATE_EYE_NAME, url = PRIVATE_EYE_URL, enabled = true)))
    }

    @Synchronized
    fun getSubscriptions(): List<Subscription> {
        if (!file.exists()) return emptyList()

        return try {
            Gson().fromJson(file.readText(), Array<Subscription>::class.java)
                    ?.toList()
                    ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read subscriptions", e)
            emptyList()
        }
    }

    @Synchronized
    fun saveSubscriptions(subscriptions: List<Subscription>) {
        file.writeText(Gson().toJson(subscriptions))
    }
}