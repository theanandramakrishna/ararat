package org.anandram.xwordapp

import android.util.Log
import org.jsoup.Jsoup
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * New York Times Syndicated crossword via Puzzazz's `nytsyn` feed — the same
 * syndicated puzzles the Seattle Times publishes (hence the `cwd_seattle`
 * slot). The response is raw `.puz` bytes, like Forkyz's `BrainsOnlyIO`
 * sources. Syndicated puzzles run a week behind the daily paper.
 */
object NytSubscription {
    private const val TAG = "NytSubscription"

    const val NAME = "New York Times Syndicated"
    const val URL = "https://www.seattletimes.com/games-nytimes-crossword/"
    const val PUZZLE_FORMAT = "nyt"
    const val FETCH_FREQUENCY = "Daily"

    /** Overridable for tests; production points at Puzzazz. */
    var apiBaseUrl = "https://nytsyn.pzzl.com/"
    private const val API_PATH = "nytsyn-crossword-mh/nytsyncrossword?date="

    private const val SYNDICATION_DELAY_DAYS = 7

    fun default(): Subscription = Subscription(
            name = NAME,
            url = URL,
            fetchFrequency = FETCH_FREQUENCY,
            puzzleFormat = PUZZLE_FORMAT)

    /** `yyMMdd` stamp of the syndicated puzzle ([SYNDICATION_DELAY_DAYS] back). */
    internal fun dateStamp(now: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        cal.add(Calendar.DAY_OF_YEAR, -SYNDICATION_DELAY_DAYS)
        return SimpleDateFormat("yyMMdd", Locale.US).format(cal.time)
    }

    fun download(subscription: Subscription): Int =
            download(subscription, System.currentTimeMillis())

    internal fun download(subscription: Subscription, now: Long): Int {
        return try {
            val apiUrl = "$apiBaseUrl$API_PATH${dateStamp(now)}"
            if (PuzzleManager.hasPuzzleByUrl(apiUrl)) return 0

            val body = Jsoup.connect(apiUrl)
                    .ignoreContentType(true)
                    .timeout(30_000)
                    .execute()
                    .bodyAsBytes()

            if (PuzzleManager.addPuzzleIfNew(
                            ByteArrayInputStream(body),
                            format = PUZZLE_FORMAT,
                            sourceName = subscription.name,
                            downloadUrl = apiUrl) != null) {
                if (FirebaseStats.verboseScrapeLogs()) FirebaseStats.log("NYT added $apiUrl")
                1
            } else 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download from ${subscription.name}", e)
            0
        }
    }
}
