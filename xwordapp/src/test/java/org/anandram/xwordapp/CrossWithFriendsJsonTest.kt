package org.anandram.xwordapp

import org.akop.ararat.core.Crossword
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

/**
 * Guards the JSON the Cross With Friends upload path produces:
 *  - [CrossWithFriendsSubscription.toGridJson] mirrors the solution grid with
 *    '.' for black cells (CWF renders '' as a writable white cell).
 *  - [CrossWithFriendsSubscription.toCluesJson] indexes clues by word number
 *    (index 0 is a dummy), which is the layout the CWF client expects.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CrossWithFriendsJsonTest {

    /**
     * 2x2 with cell (1,1) uncovered by any entry (a block), so the grid and
     * clue serialization both exercise their "missing" paths.
     */
    private fun parseGuardian(): Crossword {
        val crossword = PuzzleManager.parse(ByteArrayInputStream("""
            {"name":"CWF JSON","creator":{"name":"A"},
             "dimensions":{"cols":2,"rows":2},
             "entries":[
               {"number":1,"clue":"Twice (2)","direction":"across","length":2,
                "position":{"x":0,"y":0},"solution":"AB"},
               {"number":2,"clue":"Top","direction":"down","length":2,
                "position":{"x":0,"y":0},"solution":"AC"}]}
        """.trimIndent().toByteArray()), "guardian-json")
        return requireNotNull(crossword)
    }

    @Test
    fun toGridJson_putsSolutionLettersDotsForBlocks() {
        val grid = CrossWithFriendsSubscription.toGridJson(parseGuardian())

        assertEquals(2, grid.length())
        assertEquals("A", grid.getJSONArray(0).getString(0))
        assertEquals("B", grid.getJSONArray(0).getString(1))
        assertEquals("C", grid.getJSONArray(1).getString(0))
        // Uncovered cell must upload as CWF's black marker '.', not '' (an
        // empty string would import as a writable white cell).
        assertEquals(".", grid.getJSONArray(1).getString(1))
    }

    @Test
    fun toCluesJson_indexesByWordNumberWithDummyAtZero() {
        val crossword = parseGuardian()

        val across = CrossWithFriendsSubscription.toCluesJson(crossword.wordsAcross)
        assertEquals(2, across.length())
        assertEquals("", across.getString(0))
        assertEquals("Twice (2)", across.getString(1))

        val down = CrossWithFriendsSubscription.toCluesJson(crossword.wordsDown)
        assertEquals(3, down.length())
        assertEquals("", down.getString(0))
        assertEquals("", down.getString(1))
        assertEquals("Top", down.getString(2))
    }
}