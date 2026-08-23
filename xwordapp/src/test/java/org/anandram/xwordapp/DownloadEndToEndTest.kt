package org.anandram.xwordapp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Drives real subscription download() flows against a local HTTP server,
 * covering listing -> extraction -> parse -> persistence -> dedup.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DownloadEndToEndTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        PuzzleManager.initForTests(ApplicationProvider.getApplicationContext<Context>())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun todayStamp(): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    // --- Metro --------------------------------------------------------------

    private fun metroPage(): String {
        val blob = ("{\"pml_id\":1080214,\"name\":\"Cryptic Crossword\"," +
                "\"rdate\":\"22 Aug 2026\",\"game_data\":{\"rows\":1,\"cols\":2," +
                "\"items\":[{\"num\":1,\"start\":1,\"dir\":0," +
                "\"clue\":\"Twice (2)\",\"answer\":\"AB\"}]}}")
        return "<html><body><script>\nvar starting_puzzle = $blob;\n</script></body></html>"
    }

    @Test
    fun metroDownloadAddsDatedPuzzleOncePerDay() {
        server.enqueue(MockResponse().setBody(metroPage()))
        val baseUrl = server.url("/puzzles/cryptic-crossword").toString()
        val sub = MetroSubscription.default().copy(url = baseUrl)

        assertEquals(1, MetroSubscription.download(sub))

        val expectedUrl = "$baseUrl#${todayStamp()}"
        val entries = PuzzleManager.getPuzzles().filter { it.downloadUrl == expectedUrl }
        assertEquals(1, entries.size)
        val entry = entries[0]
        assertEquals("pml-json", entry.format)
        assertTrue(entry.title.startsWith("Metro Cryptic "))
        assertTrue(entry.title.contains(todayStamp()))

        val crossword = PuzzleManager.parse(
                PuzzleManager.puzzleFile(entry.id, entry.format), entry.format)
        assertEquals(2, crossword!!.width)
        assertEquals(1, crossword.height)
        assertTrue(crossword.date > 0)

        // Same-day rerun dedupes on the dated URL without re-fetching.
        val before = PuzzleManager.getPuzzles().size
        assertEquals(0, MetroSubscription.download(sub))
        assertEquals(before, PuzzleManager.getPuzzles().size)
    }

    // --- MyCrossword ----------------------------------------------------------

    private fun dataEndpointBody(): String {
        val data = ("{\"name\":\"Cryptic crossword No 3,822\"," +
                "\"creator\":{\"name\":\"Laccaria\"}," +
                "\"date\":\"2026-08-21T11:53:05.690Z\"," +
                "\"instructions\":\"\"," +
                "\"dimensions\":{\"cols\":2,\"rows\":1}," +
                "\"entries\":[{\"number\":1,\"clue\":\"Twice (2)\"," +
                "\"direction\":\"across\",\"length\":2," +
                "\"position\":{\"x\":0,\"y\":0},\"solution\":\"AB\",\"group\":[\"1-across\"]}]}")
        return "{\"crossword\":{\"data\":$data}}"
    }

    @Test
    fun myCrosswordDownloadsViaDataEndpoint() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: ""
                return when {
                    path == "/" || path == "/?_data=routes%2Fcryptic.%24crosswordId" ->
                        MockResponse().setBody("<html><body>" +
                                "<a href=\"/cryptic/3822\">Cryptic</a></body></html>")
                    path.startsWith("/cryptic/3822") &&
                            request.requestUrl?.queryParameter("_data") != null ->
                        MockResponse().setBody(dataEndpointBody())
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val sub = MyCrosswordSubscription.default().copy(url = server.url("/").toString())

        assertEquals(1, MyCrosswordSubscription.download(sub))

        val entries = PuzzleManager.getPuzzles()
                .filter { it.source == "MyCrossword.co.uk Cryptic" }
        assertEquals(1, entries.size)
        val entry = entries[0]
        assertEquals("guardian-json", entry.format)
        assertEquals("Cryptic crossword No 3,822", entry.title)
        assertEquals("Laccaria", entry.author)
        assertNotNull(entry.downloadUrl)
        assertTrue(entry.downloadUrl!!.endsWith("/cryptic/3822"))

        // ISO-string date tolerance in GuardianJsonFormatter ("...T11:53:05.690Z"
        // -> epoch millis at second precision).
        val crossword = PuzzleManager.parse(
                PuzzleManager.puzzleFile(entry.id, entry.format), entry.format)
        assertEquals(1787313185000L, crossword!!.date)
        assertEquals(2, crossword.width)

        // Puzzle URLs are stable, so a rerun adds nothing.
        val before = PuzzleManager.getPuzzles().size
        assertEquals(0, MyCrosswordSubscription.download(sub))
        assertEquals(before, PuzzleManager.getPuzzles().size)
    }

    @Test
    fun hinduDownloadAddsCurrentPuzzle() {
        // The player page embeds rawc; use a plain-Base64 blob so the fast
        // decode path runs (the obfuscated path is covered in the library).
        val hinduJson = ("{\"title\":\"Sunday #70\",\"author\":\"Setter\",\"w\":2,\"h\":1," +
                "\"publishTime\":1787337000000," +
                "\"box\":[[\"A\"],[\"B\"]]," +
                "\"placedWords\":[{\"word\":\"AB\",\"x\":\"0\",\"y\":\"0\"," +
                "\"acrossNotDown\":\"True\",\"direction\":\"E\",\"clueNum\":\"1\"," +
                "\"clue\":{\"clue\":\"Twice (2)\"},\"clueSection\":\"Across\"}]}")
        val hinduRawc = java.util.Base64.getEncoder().encodeToString(
                hinduJson.toByteArray())

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: ""
                return when {
                    path == "/crosswords/hindu-cryptic-sunday" ->
                        MockResponse().setBody("<html><body>" +
                                "<a href=\"/crosswords/hindu-cryptic-sunday/8035ebe8\">Cryptic</a>" +
                                "</body></html>")
                    path.startsWith("/hindu/crossword") && request.requestUrl?.queryParameter("set") == "hindu-cryptic-sunday" ->
                        MockResponse().setBody("<html><body><script>var p = " +
                                "{\"rawc\": \"$hinduRawc\"};</script></body></html>")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        HinduSubscription.playerPageBaseUrl = server.url("/hindu/crossword").toString()
        assertEquals(HinduSubscription.FETCH_FREQUENCY, "Weekly")
        assertEquals(HinduSubscription.NAME, "Hindu Sunday Cryptic")
        val hinduSub = HinduSubscription.default()
                .copy(url = server.url("/crosswords/hindu-cryptic").toString())

        assertEquals(1, HinduSubscription.download(hinduSub))

        val hinduEntries = PuzzleManager.getPuzzles()
                .filter { it.source == "Hindu Sunday Cryptic" }
        assertEquals(1, hinduEntries.size)
        val hinduEntry = hinduEntries[0]
        assertEquals("amuse-json", hinduEntry.format)
        assertEquals("Sunday #70", hinduEntry.title)
        assertEquals("Setter", hinduEntry.author)

        val hinduCw = PuzzleManager.parse(
                PuzzleManager.puzzleFile(hinduEntry.id, hinduEntry.format),
                hinduEntry.format)
        assertEquals(2, hinduCw!!.width)
        assertEquals(1787337000000L, hinduCw.date)

        // Stable player URL: rerun adds nothing.
        val beforeHindu = PuzzleManager.getPuzzles().size
        assertEquals(0, HinduSubscription.download(hinduSub))
        assertEquals(beforeHindu, PuzzleManager.getPuzzles().size)
    }

    @Test
    fun independentDownloadsDatedJpz() {
        fun jpzBody(dateStamp: String): String {
        val title = "Indy Test $dateStamp"
        return ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<crossword-compiler><rectangular-puzzle>" +
                "<metadata><title>$title</title><creator>Setter</creator></metadata>" +
                "<crossword><grid width=\"2\" height=\"1\">" +
                "<cell x=\"1\" y=\"1\" number=\"1\" solution=\"A\"/>" +
                "<cell x=\"2\" y=\"1\" solution=\"B\"/></grid>" +
                "<word id=\"w1\" x=\"1-2\" y=\"1\"/>" +
                "<clues><title>Across</title>" +
                "<clue number=\"1\" word=\"w1\">Twice (2)</clue></clues>" +
                "</crossword></rectangular-puzzle></crossword-compiler>")

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: ""
                val dateMatch = Regex("c_(\\d{6})\\.xml").find(path)
                return if (dateMatch != null)
                    MockResponse().setBody(jpzBody(dateMatch.groupValues[1]))
                else MockResponse().setResponseCode(404)
            }
        }

        IndependentSubscription.baseUrl = server.url("/daily-crossword").toString()
        val sub = IndependentSubscription.default()

        // Sweeps up to six days back; the mock answers every date.
        assertEquals(6, IndependentSubscription.download(sub))

        val entries = PuzzleManager.getPuzzles()
                .filter { it.source == "Independent Cryptic" }
        assertEquals(6, entries.size)
        val entry = entries[0]
        assertEquals("jpz", entry.format)
        assertTrue(entry.title.startsWith("Indy Test "))

        val crossword = PuzzleManager.parse(
                PuzzleManager.puzzleFile(entry.id, entry.format), entry.format)
        assertEquals(2, crossword!!.width)
        val word = crossword.wordsAcross[0]
        assertEquals(2, word.length)
        assertEquals("AB", (0 until word.length).joinToString("") { word.cellAt(it).chars })
    }
}}
