package org.anandram.xwordapp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.FileInputStream

/**
 * Guards the format registry: every subscription format id must have a real
 * branch in [PuzzleManager.parse]. A missing branch silently falls through to
 * PuzFormatter and rejects valid files (see the lost "jpz" branch incident),
 * so each format here is proven with a minimal known-good payload.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PuzzleManagerFormatWiringTest {

    private val SAMPLES: Map<String, () -> java.io.InputStream> = mapOf()

    private fun parse(format: String, body: String) =
            PuzzleManager.parse(ByteArrayInputStream(body.toByteArray()), format)

    private fun assertParses(format: String, body: String) {
        assertNotNull(
                "format '$format' has no parse branch (fell through to PuzFormatter)",
                parse(format, body))
    }

    @Test
    fun DIAG_wsjDirect() {
        val body = SAMPLES.getValue("wsj-json")
        try {
            org.akop.ararat.core.buildCrossword {
                org.akop.ararat.io.WSJFormatter().read(this, body.byteInputStream())
            }
            println("DIAG OK")
        } catch (t: Throwable) {
            println("DIAG FAIL: " + t)
            t.printStackTrace()
            throw t
        }
    }

    @Test
    fun puz_parsesBundledSeed() {
        PuzzleManager.initForTests(ApplicationProvider.getApplicationContext<Context>())
        val bundled = PuzzleManager.puzFile(PuzzleManager.getBundledId())
        assertNotNull(PuzzleManager.parse(bundled.inputStream(), "puz"))
    }

    @Test
    fun xd_isRegistered() {
        assertParses("xd", """
            ## Metadata
            Title: Tiny

            ## Grid
            AB
            CD

            ## Clues
            A1. top clue
            D1. side clue
        """.trimIndent())
    }

    @Test
    fun guardianJson_isRegistered() {
        assertParses("guardian-json", """
            {"name":"Tiny","creator":{"name":"A"},
             "dimensions":{"cols":2,"rows":1},
             "entries":[{"number":1,"clue":"Twice (2)","direction":"across","length":2,
                         "position":{"x":0,"y":0},"solution":"AB"}]}
        """.trimIndent())
    }

    @Test
    fun wsjJson_isRegistered() {
        assertParses("wsj-json", """
            {"data":{"copy":{
                "title":"Tiny","byline":"A",
                "date-publish":"Sunday, 22 March 2026",
                "gridsize":{"cols":2,"rows":1},
                "clues":[
                  {"title":"Across","clues":[{"word":1,"number":1,"clue":"top"}]},
                  {"title":"Down","clues":[{"word":2,"number":2,"clue":"side"}]}],
                "words":[{"id":1,"x":"1-2","y":"1"},{"id":2,"x":"1","y":"1-2"}]},
              "grid":[[{"Letter":"A"},{"Letter":"B"}],
                      [{"Letter":"C"},{"Letter":"D"}]]}}
        """.trimIndent())
    }

    @Test
    fun jsoupHtml_isRegistered() {
        assertParses("jsoup-html", """
            <div id="puzzle-grid">
              <div class="row">
                <div id='cell-1-1' class='cell'><span class="cell-number">1</span><div class='cell-input' data-solution='A'></div></div>
                <div id='cell-2-1' class='cell'><div class='cell-input' data-solution='B'></div></div>
              </div>
              <div class="row">
                <div id='cell-1-2' class='cell'><div class='cell-input' data-solution='C'></div></div>
                <div id='cell-2-2' class='cell'><div class='cell-input' data-solution='D'></div></div>
              </div>
            </div>
            <div class="clues">
              <h2>Across</h2><div class="clues-across">
                <li data-direction="across" data-cluenum="1"><b>1</b> One across</li>
              </div>
              <h2>Down</h2><div class="clues-down">
                <li data-direction="down" data-cluenum="1"><b>1</b> One down</li>
              </div>
            </div>
        """.trimIndent())
    }

    @Test
    fun pmlJson_isRegistered() {
        assertParses("pml-json", """
            {"name":"Tiny","rdate":"22 Aug 2026",
             "game_data":{"rows":1,"cols":2,"items":[
               {"num":1,"start":1,"dir":0,"clue":"Twice (2)","answer":"AB"}]}}
        """.trimIndent())
    }

    @Test
    fun amuseJson_isRegistered() {
        assertParses("amuse-json", """
            {"title":"Tiny","author":"A","w":2,"h":1,"publishTime":1787337000000,
             "box":[["A"],["B"]],
             "placedWords":[{"word":"AB","x":"0","y":"0","acrossNotDown":"True",
                             "direction":"E","clueNum":"1","nBoxes":"2",
                             "clue":{"clue":"Twice (2)"}}]}
        """.trimIndent())
    }

    @Test
    fun jpz_isRegistered() {
        assertParses("jpz", """
            <?xml version="1.0" encoding="UTF-8"?>
            <crossword-compiler><rectangular-puzzle>
              <metadata><title>Tiny</title><creator>A</creator></metadata>
              <crossword>
                <grid width="2" height="1">
                  <cell x="1" y="1" number="1" solution="A"/>
                  <cell x="2" y="1" solution="B"/>
                </grid>
                <word id="w1" x="1-2" y="1"/>
                <clues><title>Across</title>
                  <clue number="1" word="w1">Twice (2)</clue>
                </clues>
              </crossword>
            </rectangular-puzzle></crossword-compiler>
        """.trimIndent())
    }
}
