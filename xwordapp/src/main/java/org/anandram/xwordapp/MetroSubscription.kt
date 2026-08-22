package org.anandram.xwordapp

import android.util.Log
import org.jsoup.Jsoup
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MetroSubscription {
    private const val TAG = "MetroSubscription"

    const val NAME = "Metro Cryptic"
    const val URL = "https://metro.co.uk/puzzles/cryptic-crossword"
    const val PUZZLE_FORMAT = "pml-json"
    const val FETCH_FREQUENCY = "Weekdays"

    private val EMBED_JSON_REGEX = Regex("\\{\"pml_id\".*\\}")
    private const val FALLBACK_TITLE = NAME

    fun default(): Subscription = Subscription(
            name = NAME,
            url = URL,
            enabled = true,
            fetchFrequency = FETCH_FREQUENCY,
            puzzleFormat = PUZZLE_FORMAT)

    fun download(subscription: Subscription): Int {
        return try {
            val dateStamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val page = String(Jsoup.connect(subscription.url)
                    .ignoreContentType(true)
                    .timeout(30_000)
                    .execute()
                    .bodyAsBytes(), Charsets.UTF_8)

            val puzzleJson = EMBED_JSON_REGEX.find(page)?.groupValues?.get(0)
                    ?: return 0

            val downloadUrl = "${subscription.url}#$dateStamp"
            if (PuzzleManager.hasPuzzleByUrl(downloadUrl)) return 0

            if (PuzzleManager.addPuzzleIfNew(
                            ByteArrayInputStream(puzzleJson.toByteArray(Charsets.UTF_8)),
                            format = PUZZLE_FORMAT,
                            fallbackTitle = "$FALLBACK_TITLE $dateStamp",
                            sourceName = subscription.name,
                            downloadUrl = downloadUrl) != null) 1 else 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download from ${subscription.name}", e)
            0
        }
    }
}
