package org.anandram.xwordapp

import android.util.Log
import org.jsoup.Jsoup
import java.io.ByteArrayInputStream

object EverymanSubscription {
    private const val TAG = "EverymanSubscription"

    const val NAME = "Everyman Observer"
    const val URL = "https://observer.co.uk/topics/everyman"
    const val PUZZLE_FORMAT = "wsj-json"
    const val FETCH_FREQUENCY = "Weekly"

    private const val MAX_PER_SWEEP = 30
    private const val API_URL =
            "https://content-api.slowdownwiseup.co.uk/api/mobile/v1/puzzle-data/%s/file/data.json"
    private val PUZZLE_PATH = Regex("/puzzles/everyman/article/[a-z0-9-]+$")
    private val UUID_REGEX = Regex(
            "\\\\{0,2}\"uuid\\\\{0,2}\"\\s*:\\s*\\\\{0,2}\\s*\"" +
                    "([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})")

    /**
     * Extract the SlowDownWiseUp puzzle uuid from an observer.co.uk article
     * page. The payload escapes its quotes, but bare quotes are tolerated.
     */
    fun extractUuid(pageHtml: String): String? =
            UUID_REGEX.find(pageHtml)?.groupValues?.get(1)

    fun default(): Subscription = Subscription(
            name = NAME,
            url = URL,
            fetchFrequency = FETCH_FREQUENCY,
            puzzleFormat = PUZZLE_FORMAT)

    fun download(subscription: Subscription): Int {
        return try {
            val maxPerSweep = FirebaseStats
                    .sweepCap("max_per_sweep_everyman", MAX_PER_SWEEP.toLong())
                    .toInt()
            val document = Jsoup.connect(subscription.url).get()
            val puzzleUrls = document.select("a[href]").mapNotNull { link ->
                val href = link.absUrl("href")
                if (PUZZLE_PATH.containsMatchIn(href)) href else null
            }.distinct().sortedDescending()
                    .take(FirebaseStats.maxPerSweep("everyman", MAX_PER_SWEEP))

            var count = 0
            for (url in puzzleUrls) {
                if (PuzzleManager.hasPuzzleByUrl(url)) continue
                try {
                    val page = String(Jsoup.connect(url)
                            .ignoreContentType(true)
                            .timeout(30_000)
                            .execute()
                            .bodyAsBytes(), Charsets.UTF_8)
                    val id = extractUuid(page) ?: continue
                    val body = Jsoup.connect(String.format(API_URL, id))
                            .ignoreContentType(true)
                            .timeout(30_000)
                            .execute()
                            .bodyAsBytes()

                    if (PuzzleManager.addPuzzleIfNew(
                                    ByteArrayInputStream(body),
                                    format = PUZZLE_FORMAT,
                                    sourceName = subscription.name,
                                    downloadUrl = url) != null) {
                        FirebaseStats.scrapeLog("Everyman added url=$url")
                        count++
                        if (FirebaseStats.verboseScrapeLogs()) FirebaseStats.log("added $url")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to fetch Everyman puzzle $url", e)
                    if (FirebaseStats.verboseScrapeLogs())
                        FirebaseStats.log("fetch_fail $url ${e.message.orEmpty().take(120)}")
                }
            }
            count
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download from ${subscription.name}", e)
            0
        }
    }
}
