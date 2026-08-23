package org.anandram.xwordapp

import android.util.Log
import org.akop.ararat.io.AmuseLabsJsonFormatter
import org.jsoup.Jsoup

object HinduSubscription {
    private const val TAG = "HinduSubscription"

    const val DAILY_NAME = "Hindu Daily Cryptic"
    const val DAILY_URL = "https://www.thehindu.com/crosswords/hindu-cryptic"
    const val DAILY_FREQUENCY = "Weekdays"
    const val SUNDAY_NAME = "Hindu Sunday Cryptic"
    const val SUNDAY_URL = "https://www.thehindu.com/crosswords/hindu-cryptic-sunday"
    const val SUNDAY_FREQUENCY = "Weekly"
    const val PUZZLE_FORMAT = "amuse-json"

    private const val USER_AGENT = ("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36" +
            " (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36")

    /** Overridable for tests. */
    internal var playerPageBaseUrl = "https://cdn3.amuselabs.com/hindu/crossword"

    /** Whether [url] is a Hindu crossword listing page. */
    fun matchesListingUrl(url: String): Boolean =
            url.startsWith("https://www.thehindu.com/crosswords")

    /** Extract the current puzzle id from a listing/index page. */
    fun extractPuzzleId(pageHtml: String, seriesSlug: String): String? =
            Regex(Regex.escape(seriesSlug) + "[/\\\\]+([a-f0-9]{8})")
                    .find(pageHtml)?.groupValues?.get(1)

    fun dailyDefault(): Subscription = Subscription(
            name = DAILY_NAME,
            url = DAILY_URL,
            enabled = true,
            fetchFrequency = DAILY_FREQUENCY,
            puzzleFormat = PUZZLE_FORMAT)

    fun sundayDefault(): Subscription = Subscription(
            name = SUNDAY_NAME,
            url = SUNDAY_URL,
            enabled = true,
            fetchFrequency = SUNDAY_FREQUENCY,
            puzzleFormat = PUZZLE_FORMAT)

    private fun fetchPage(url: String): String =
            String(Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .ignoreContentType(true)
                    .timeout(30_000)
                    .execute()
                    .bodyAsBytes(), Charsets.UTF_8)

    /**
     * The index page's SSR is inconsistent: it sometimes carries the current
     * puzzle id and sometimes renders a bare shell. Try the series page, then
     * the crosswords index, before giving up for today.
     */
    private fun discoverCurrentPuzzleId(
            subscription: Subscription,
            seriesSlug: String,
            idRegex: Regex,
    ): String? {
        val candidates = listOf(subscription.url,
                "${subscription.url}/",
                "https://www.thehindu.com/crosswords")
        for (url in candidates) {
            try {
                val id = idRegex.find(fetchPage(url))?.groupValues?.get(1)
                Log.i(TAG, "Discovered puzzle id $id from $url")
                if (id != null) return id
            } catch (e: Exception) {
                Log.i(TAG, "Discovery fetch failed for $url", e)
            }
        }
        return null
    }

    fun download(subscription: Subscription): Int {
        return try {
            val seriesSlug = subscription.url.trimEnd('/').substringAfterLast('/')
            val idRegex = Regex(Regex.escape(seriesSlug) + "[/\\\\]+([a-f0-9]{8})")

            val id = discoverCurrentPuzzleId(subscription, seriesSlug, idRegex) ?: run {
                Log.i(TAG, "No puzzle id discovered; skipping this sweep")
                return 0
            }
            val playerUrl = "$playerPageBaseUrl?id=$id&set=$seriesSlug&embed=1"

            if (PuzzleManager.hasPuzzleByUrl(playerUrl)) {
                Log.i(TAG, "Puzzle already downloaded: $playerUrl")
                return 0
            }

            val playerHtml = fetchPage(playerUrl)

            val rawc = AmuseLabsJsonFormatter.extractRawc(playerHtml) ?: run {
                Log.i(TAG, "Player page contained no rawc payload")
                return 0
            }
            val json = AmuseLabsJsonFormatter.decodeRawc(rawc) ?: run {
                Log.i(TAG, "Could not decode rawc")
                return 0
            }
            Log.i(TAG, "Decoded puzzle json (${json.length} chars)")

            val added = PuzzleManager.addPuzzleIfNew(
                    java.io.ByteArrayInputStream(json.toByteArray(Charsets.UTF_8)),
                    format = PUZZLE_FORMAT,
                    sourceName = subscription.name,
                    downloadUrl = playerUrl)
            Log.i(TAG, "Added puzzle: ${added != null}")
            if (added != null) 1 else 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download from ${subscription.name}", e)
            0
        }
    }
}
