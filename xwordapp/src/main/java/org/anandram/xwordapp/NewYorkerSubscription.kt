package org.anandram.xwordapp

import android.util.Log
import org.json.JSONObject
import org.jsoup.Jsoup

object NewYorkerSubscription {
    private const val TAG = "NewYorkerSubscription"

    const val NAME = "The New Yorker"
    const val URL = "https://www.newyorker.com/puzzles-and-games-dept/cryptic-crossword"
    const val PUZZLE_FORMAT = "XD"
    const val FETCH_FREQUENCY = "Daily"

    private const val API_URL = "https://puzzles-games-api.gp-prod.conde.digital/api/v1/games/%s"
    private const val MAX_PER_SWEEP = 30
    private val PUZZLE_PATH = Regex(
            "/puzzles-and-games-dept/(crossword|cryptic-crossword)/\\d{4}/\\d{2}/\\d{2}")
    private val GAME_ID = Regex(
            "\"inline-embed\",\\s*\\{\"props\":\\{\"id\":\"([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\"")

    /** Whether [url] points at a New Yorker puzzles-and-games page. */
    fun matchesPuzzleUrl(url: String): Boolean = PUZZLE_PATH.containsMatchIn(url)

    /** Extract the inline-embed game id from a New Yorker puzzle page. */
    fun extractGameId(pageHtml: String): String? =
            GAME_ID.find(pageHtml)?.groupValues?.get(1)

    fun default(): Subscription = Subscription(
            name = NAME,
            url = URL,
            fetchFrequency = FETCH_FREQUENCY,
            puzzleFormat = PUZZLE_FORMAT)

    fun download(subscription: Subscription): Int {
        return try {
            val maxPerSweep = FirebaseStats
                    .sweepCap("max_per_sweep_newyorker", MAX_PER_SWEEP.toLong())
                    .toInt()
            val document = Jsoup.connect(subscription.url).get()
            val puzzleUrls = document.select("a[href]").mapNotNull { link ->
                val href = link.absUrl("href")
                if (matchesPuzzleUrl(href)) href else null
            }.distinct().sortedDescending().take(maxPerSweep)

            var count = 0
            for (url in puzzleUrls) {
                if (PuzzleManager.hasPuzzleByUrl(url)) continue
                try {
                    val page = String(Jsoup.connect(url)
                            .ignoreContentType(true)
                            .timeout(30_000)
                            .execute()
                            .bodyAsBytes(), Charsets.UTF_8)
                    val id = extractGameId(page) ?: continue
                    val body = String(Jsoup.connect(String.format(API_URL, id))
                            .ignoreContentType(true)
                            .timeout(30_000)
                            .execute()
                            .bodyAsBytes(), Charsets.UTF_8)
                    val xd = JSONObject(body).optString("data")
                    if (xd.isBlank()) continue

                    if (PuzzleManager.addXdIfNew(xd,
                                    sourceName = subscription.name, downloadUrl = url) != null) {
                        FirebaseStats.scrapeLog("NY added url=$url id=$id")
                        count++
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to fetch New Yorker puzzle $url", e)
                    FirebaseStats.scrapeLog("NY fetch_fail url=$url")
                }
            }
            count
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download from ${subscription.name}", e)
            0
        }
    }
}