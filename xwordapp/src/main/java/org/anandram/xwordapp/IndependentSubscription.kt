package org.anandram.xwordapp

import android.util.Log
import org.jsoup.Jsoup
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object IndependentSubscription {
    private const val TAG = "IndependentSubscription"

    const val NAME = "Independent Cryptic"
    const val URL = "https://puzzles.independent.co.uk/games/cryptic-crossword-independent"
    const val PUZZLE_FORMAT = "jpz"
    const val FETCH_FREQUENCY = "Daily"

    private const val TAG_LOG = "IndependentSubscription"

    // The daily cryptic lives at a date-stamped URL on the games CDN.
    private const val BASE_URL =
            "https://ams.cdn.arkadiumhosted.com/assets/gamesfeed/independent/daily-crossword"

    /** Overridable for tests. */
    internal var baseUrl = BASE_URL

    private const val USER_AGENT = ("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36" +
            " (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36")

    private const val MAX_DAYS_BACK = 6

    fun default(): Subscription = Subscription(
            name = NAME,
            url = URL,
            enabled = true,
            fetchFrequency = FETCH_FREQUENCY,
            puzzleFormat = PUZZLE_FORMAT)

    fun download(subscription: Subscription): Int {
        return try {
            var count = 0
            val cal = Calendar.getInstance()
            for (daysBack in 0 until MAX_DAYS_BACK) {
                val dateStamp = SimpleDateFormat("yyMMdd", Locale.US).format(cal.time)
                val url = "$baseUrl/c_$dateStamp.xml"

                if (!PuzzleManager.hasPuzzleByUrl(url)) {
                    count += tryDownload(subscription, url)
                }
                cal.add(Calendar.DAY_OF_MONTH, -1)
            }
            count
        } catch (e: Exception) {
            Log.e(TAG_LOG, "Failed to download from ${subscription.name}", e)
            0
        }
    }

    private fun tryDownload(subscription: Subscription, url: String): Int {
        return try {
            val bytes = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .ignoreContentType(true)
                    .timeout(30_000)
                    .execute()
                    .bodyAsBytes()

            if (PuzzleManager.addPuzzleIfNew(
                            ByteArrayInputStream(bytes),
                            format = PUZZLE_FORMAT,
                            sourceName = subscription.name,
                            downloadUrl = url) != null) 1 else 0
        } catch (e: Exception) {
            Log.i(TAG_LOG, "No puzzle at $url", e)
            0
        }
    }
}
