package org.anandram.xwordapp

import android.util.Log
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.ByteArrayInputStream

object MyCrosswordSubscription {
    private const val TAG = "MyCrosswordSubscription"

    const val NAME = "MyCrossword.co.uk Cryptic"
    const val URL = "https://mycrossword.co.uk/"
    const val PUZZLE_FORMAT = "guardian-json"
    const val FETCH_FREQUENCY = "Daily"

    private const val MAX_PER_SWEEP = 10
    private val PUZZLE_PATH = Regex("/cryptic/\\d+$")
    private const val DATA_SUFFIX = "?_data=routes%2Fcryptic.%24crosswordId"

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
                    val body = String(Jsoup.connect(url + DATA_SUFFIX)
                            .ignoreContentType(true)
                            .timeout(30_000)
                            .execute()
                            .bodyAsBytes(), Charsets.UTF_8)

                    val data = JSONObject(body)
                            ?.optJSONObject("crossword")
                            ?.optJSONObject("data") ?: continue

                    if (PuzzleManager.addPuzzleIfNew(
                                    ByteArrayInputStream(data.toString().toByteArray(Charsets.UTF_8)),
                                    format = PUZZLE_FORMAT,
                                    sourceName = subscription.name,
                                    downloadUrl = url) != null) {
                        count++
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to fetch MyCrossword puzzle $url", e)
                }
            }
            count
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download from ${subscription.name}", e)
            0
        }
    }
}
