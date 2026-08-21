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

    fun default(): Subscription = Subscription(
            name = NAME,
            url = URL,
            enabled = true,
            fetchFrequency = FETCH_FREQUENCY,
            puzzleFormat = PUZZLE_FORMAT)

    fun download(subscription: Subscription): Int {
        return try {
            val document = Jsoup.connect(subscription.url).get()
            val puzzleUrls = document.select("a[href]").mapNotNull { link ->
                val href = link.absUrl("href")
                if (PUZZLE_PATH.containsMatchIn(href)) href else null
            }.distinct().sortedDescending().take(MAX_PER_SWEEP)

            var count = 0
            for (url in puzzleUrls) {
                if (PuzzleManager.hasPuzzleByUrl(url)) continue
                try {
                    val page = String(Jsoup.connect(url)
                            .ignoreContentType(true)
                            .timeout(30_000)
                            .execute()
                            .bodyAsBytes(), Charsets.UTF_8)
                    val id = GAME_ID.find(page)?.groupValues?.get(1) ?: continue
                    val body = String(Jsoup.connect(String.format(API_URL, id))
                            .ignoreContentType(true)
                            .timeout(30_000)
                            .execute()
                            .bodyAsBytes(), Charsets.UTF_8)
                    val xd = JSONObject(body).optString("data")
                    if (xd.isBlank()) continue

                    if (PuzzleManager.addXdIfNew(xd,
                                    sourceName = subscription.name, downloadUrl = url) != null) {
                        count++
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to fetch New Yorker puzzle $url", e)
                }
            }
            count
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download from ${subscription.name}", e)
            0
        }
    }
}