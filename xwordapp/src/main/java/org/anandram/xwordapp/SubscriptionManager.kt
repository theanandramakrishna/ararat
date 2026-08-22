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
    private const val CRU_NAME = "Cru Cryptic Archive"
    private const val CRU_URL = "https://archive.nytimes.com/www.nytimes.com/premium/xword/cryptic-archive.html"
    private const val WJ_NAME = "Will Johnston Cryptics"
    private const val WJ_URL = "https://www.fleetingimage.com/wij/puzzles/wij-cryptic.html"

    private lateinit var appContext: Context
    private lateinit var file: File

    @Synchronized
    fun init(context: Context) {
        if (::appContext.isInitialized) return

        appContext = context.applicationContext
        file = File(appContext.filesDir, FILE_NAME)
        ensureDefaults()
    }

    private val DEFAULT_SUBSCRIPTIONS = listOf(
            Subscription(name = DEFAULT_NAME, url = DEFAULT_URL, enabled = true),
            Subscription(name = PRIVATE_EYE_NAME, url = PRIVATE_EYE_URL, enabled = true),
            Subscription(name = CRU_NAME, url = CRU_URL, enabled = true),
            Subscription(name = WJ_NAME, url = WJ_URL, enabled = true),
            NewYorkerSubscription.default(),
            GuardianSubscription.default(),
            EverymanSubscription.default(),
            IrishNewsSubscription.crypticDefault(),
            IrishNewsSubscription.prizeCrypticDefault(),
            MetroSubscription.default(),
            MyCrosswordSubscription.default())

    private fun ensureDefaults() {
        if (!file.exists()) {
            saveSubscriptions(DEFAULT_SUBSCRIPTIONS)
            return
        }

        val existing = getSubscriptions()
        val missing = DEFAULT_SUBSCRIPTIONS.filter { def ->
            existing.none { it.name == def.name }
        }
        if (missing.isNotEmpty()) {
            saveSubscriptions(existing + missing)
        }
    }

    @Synchronized
    fun getSubscriptions(): List<Subscription> {
        if (!file.exists()) return emptyList()

        return try {
            Gson().fromJson(file.readText(), Array<Subscription>::class.java)
                    ?.map { it.normalized() }
                    ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read subscriptions", e)
            emptyList()
        }
    }

    private fun Subscription.normalized(): Subscription {
        val frequency = fetchFrequency.ifEmpty { "One-Time" }
        val format = puzzleFormat.ifEmpty { "puz" }
        return copy(fetchFrequency = frequency, lastDownloadDate = lastDownloadDate ?: "",
                puzzleFormat = format)
    }

    @Synchronized
    fun markDownloadStarted(name: String, date: String) {
        val list = getSubscriptions().toMutableList()
        val index = list.indexOfFirst { it.name == name }
        if (index >= 0) {
            list[index] = list[index].copy(lastDownloadDate = date)
            saveSubscriptions(list)
        }
    }

    @Synchronized
    fun saveSubscriptions(subscriptions: List<Subscription>) {
        file.writeText(Gson().toJson(subscriptions))
    }
}