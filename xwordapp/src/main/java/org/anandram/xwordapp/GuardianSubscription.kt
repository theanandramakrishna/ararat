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

    /** Whether [url] points at a Guardian cryptic puzzle page. */
    fun matchesPuzzleUrl(url: String): Boolean = PUZZLE_PATH.containsMatchIn(url)

    fun default(): Subscription = Subscription(
            name = NAME,
            url = URL,
            fetchFrequency = FETCH_FREQUENCY,
            puzzleFormat = PUZZLE_FORMAT)

    fun download(subscription: Subscription): Int {
        return try {
            val maxPerSweep = FirebaseStats
                    .sweepCap("max_per_sweep_guardian", MAX_PER_SWEEP.toLong())
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
                        FirebaseStats.scrapeLog("Guardian added url=$url")
                        count++
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to fetch Guardian puzzle $url", e)
                    FirebaseStats.scrapeLog("Guardian fetch_fail url=$url")
                }
            }
            count
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download from ${subscription.name}", e)
            0
        }
    }
}
