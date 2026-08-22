package org.anandram.xwordapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScraperExtractionTest {

    // --- GuardianSubscription.matchesPuzzleUrl -----------------------------

    @Test
    fun guardianMatchesCrypticArticleUrls() {
        assertTrue(GuardianSubscription.matchesPuzzleUrl(
                "https://www.theguardian.com/crosswords/cryptic/30077"))
        assertFalse(GuardianSubscription.matchesPuzzleUrl(
                "https://www.theguardian.com/crosswords/series/cryptic"))
        assertFalse(GuardianSubscription.matchesPuzzleUrl(
                "https://www.theguardian.com/crosswords/quick/15432"))
    }

    // --- EverymanSubscription.extractUuid ----------------------------------

    @Test
    fun everymanExtractsUuidFromEscapedPayload() {
        val page = "{\\\"type\\\":\\\"puzzle\\\",\\\"uuid\\\":" +
                "\\\"b92c38fc-1caf-4ce7-a754-3aec5000cdc9\\\"},"
        assertEquals("b92c38fc-1caf-4ce7-a754-3aec5000cdc9",
                EverymanSubscription.extractUuid(page))
    }

    @Test
    fun everymanToleratesUnescapedQuotes() {
        val page = "{\"content\":[{\"type\":\"puzzle\",\"uuid\":\"c6583271-b5cd-4529-b8d0-a126ef29ce31\"}]}"
        assertEquals("c6583271-b5cd-4529-b8d0-a126ef29ce31",
                EverymanSubscription.extractUuid(page))
    }

    @Test
    fun everymanReturnsNullWithoutUuid() {
        assertNull(EverymanSubscription.extractUuid("<html>no puzzle here</html>"))
    }

    // --- IrishNewsSubscription.extractEmbedUrl -----------------------------

    @Test
    fun irishNewsExtractsPaPuzzlesEmbed() {
        val page = "<script>document.write(\"<iframe src=\\\"" +
                "https://www.pa-puzzles.com/puzzles/puzzlegroupembed.php?pid=126&cs=43" +
                "\\\">\");</script>"
        assertEquals("https://www.pa-puzzles.com/puzzles/puzzlegroupembed.php?pid=126&cs=43",
                IrishNewsSubscription.extractEmbedUrl(page))
    }

    @Test
    fun irishNewsIgnoresNonPaIframes() {
        val page = "<iframe src=\"https://example.com/embed\"></iframe>"
        assertNull(IrishNewsSubscription.extractEmbedUrl(page))
    }

    // --- MetroSubscription.extractPuzzleJson -------------------------------

    @Test
    fun metroExtractsEmbeddedJsonBlob() {
        val blob = "{\"pml_id\":1080214,\"name\":\"Cryptic Crossword\",\"game_data\":{" +
                "\"rows\":2,\"cols\":2,\"items\":[]}}"
        val page = "<script>\n  var starting_puzzle = $blob;\n</script>"
        assertEquals(blob, MetroSubscription.extractPuzzleJson(page))

        org.json.JSONObject(MetroSubscription.extractPuzzleJson(page)!!)
    }

    @Test
    fun metroExtractionStopsAtLineEnd() {
        val page = "{\"pml_id\":7}\nvar next = {\"unrelated\":true}"
        assertEquals("{\"pml_id\":7}", MetroSubscription.extractPuzzleJson(page))
    }

    @Test
    fun metroReturnsNullWithoutBlob() {
        assertNull(MetroSubscription.extractPuzzleJson("<html></html>"))
    }

    // --- MyCrosswordSubscription.matchesPuzzleUrl --------------------------

    @Test
    fun myCrosswordMatchesTypeNumberUrls() {
        assertTrue(MyCrosswordSubscription.matchesPuzzleUrl(
                "https://mycrossword.co.uk/cryptic/3822"))
        assertFalse(MyCrosswordSubscription.matchesPuzzleUrl(
                "https://mycrossword.co.uk/crosswords/cryptic"))
    }

    // --- NewYorkerSubscription ---------------------------------------------

    @Test
    fun newYorkerExtractsInlineEmbedGameId() {
        val page = ("\"inline-embed\",{\"props\":{\"id\":" +
                        "\"a4bd0f5e-e6ba-4f99-9e58-1f962ff2e826\",\"width\":600}}")
        assertEquals("a4bd0f5e-e6ba-4f99-9e58-1f962ff2e826",
                NewYorkerSubscription.extractGameId(page))
    }

    @Test
    fun newYorkerMatchesPuzzleAndGamesPages() {
        assertTrue(NewYorkerSubscription.matchesPuzzleUrl(
                "https://www.newyorker.com/puzzles-and-games-dept/crossword/2026/08/16"))
        assertFalse(NewYorkerSubscription.matchesPuzzleUrl(
                "https://www.newyorker.com/culture/the-new-yorker-interview"))
    }
}
