package org.anandram.xwordapp

import android.util.Log
import org.jsoup.Jsoup
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object IrishNewsSubscription {
    private const val TAG = "IrishNewsSubscription"

    const val CRYPTIC_NAME = "Irish News Cryptic"
    const val CRYPTIC_URL = "https://www.irishnews.com/puzzles/cryptic-crossword/"
    const val PRIZE_NAME = "Irish News Prize Cryptic"
    const val PRIZE_URL = "https://www.irishnews.com/puzzles/prize-cryptic-crossword/"
    const val PUZZLE_FORMAT = "jsoup-html"
    const val CRYPTIC_FREQUENCY = "Weekdays"
    const val PRIZE_FREQUENCY = "Weekly"

    private val EMBED_URL_REGEX = Regex(
            "src=\\\\\"([^\"]*pa-puzzles\\.com[^\"]*)\\\\\"", RegexOption.IGNORE_CASE)
    private val TITLE_REGEX = Regex("<title>([^<]*)</title>", RegexOption.IGNORE_CASE)

    fun crypticDefault(): Subscription = Subscription(
            name = CRYPTIC_NAME,
            url = CRYPTIC_URL,
            enabled = true,
            fetchFrequency = CRYPTIC_FREQUENCY,
            puzzleFormat = PUZZLE_FORMAT)

    fun prizeCrypticDefault(): Subscription = Subscription(
            name = PRIZE_NAME,
            url = PRIZE_URL,
            enabled = true,
            fetchFrequency = PRIZE_FREQUENCY,
            puzzleFormat = PUZZLE_FORMAT)

    fun download(subscription: Subscription): Int {
        return try {
            val dateStamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val page = String(Jsoup.connect(subscription.url)
                    .ignoreContentType(true)
                    .timeout(30_000)
                    .execute()
                    .bodyAsBytes(), Charsets.UTF_8)

            val embedUrl = EMBED_URL_REGEX.find(page)?.groupValues?.get(1) ?: return 0

            val downloadUrl = "${subscription.url}#$dateStamp"
            if (PuzzleManager.hasPuzzleByUrl(downloadUrl)) return 0

            val body = Jsoup.connect(embedUrl)
                    .ignoreContentType(true)
                    .timeout(30_000)
                    .execute()
                    .bodyAsBytes()

            val pageTitle = TITLE_REGEX.find(page)?.groupValues?.get(1)?.trim()
                    ?.takeIf { it.isNotEmpty() }
            val fallbackTitle = listOfNotNull(pageTitle, dateStamp).joinToString(" ")

            if (PuzzleManager.addPuzzleIfNew(
                            ByteArrayInputStream(body),
                            format = PUZZLE_FORMAT,
                            fallbackTitle = fallbackTitle,
                            sourceName = subscription.name,
                            downloadUrl = downloadUrl) != null) 1 else 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download from ${subscription.name}", e)
            0
        }
    }
}
