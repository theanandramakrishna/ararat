package org.anandram.xwordapp

import android.util.Log
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.ByteArrayInputStream

object GuardianSubscription {
    private const val TAG = "GuardianSubscription"

    const val NAME = "The Guardian"
    const val URL = "https://www.theguardian.com/crosswords/series/cryptic"
    const val PUZZLE_FORMAT = "guardian-json"
    const val FETCH_FREQUENCY = "Daily"

    private const val MAX_PER_SWEEP = 30
    private val PUZZLE_PATH = Regex("/crosswords/cryptic/\\d+$")

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
                    val page = Jsoup.connect(url).timeout(30_000).get()
                    val island = page.selectFirst("gu-island[name=\"CrosswordComponent\"]")
                            ?: continue
                    val props = island.attr("props")
                    if (props.isBlank()) continue
                    val data = JSONObject(props).optJSONObject("data") ?: continue
                    if (data.optJSONArray("entries") == null) continue

                    if (PuzzleManager.addPuzzleIfNew(
                                    ByteArrayInputStream(data.toString().toByteArray(Charsets.UTF_8)),
                                    format = PUZZLE_FORMAT,
                                    sourceName = subscription.name,
                                    downloadUrl = url) != null) {
                        count++
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to fetch Guardian puzzle $url", e)
                }
            }
            count
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download from ${subscription.name}", e)
            0
        }
    }
}
