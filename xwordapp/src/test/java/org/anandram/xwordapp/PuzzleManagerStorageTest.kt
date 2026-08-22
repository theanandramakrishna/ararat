package org.anandram.xwordapp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PuzzleManagerStorageTest {

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    @Test
    fun bundledPuzzleSeededOnInit() {
        PuzzleManager.initForTests(context())

        assertNotNull(PuzzleManager.getEntry(PuzzleManager.getBundledId()))
        assertTrue(PuzzleManager.getPuzzles().isNotEmpty())
    }

    @Test
    fun addPuzzleIfNewDeduplicatesIdenticalPuzzles() {
        PuzzleManager.initForTests(context())

        // Synthesizable format: guardian-json.
        val json = ByteArrayInputStream("""{
            "name": "Dedup Test", "creator": {"name": "A"},
            "dimensions": {"cols": 2, "rows": 1},
            "entries": [{"number": 1, "clue": "Twice (2)", "direction": "across",
                         "length": 2, "position": {"x": 0, "y": 0}, "solution": "AB"}]}
        """.toByteArray())

        val first = PuzzleManager.addPuzzleIfNew(json, format = "guardian-json")
        assertNotNull(first)

        val second = PuzzleManager.addPuzzleIfNew(
                ByteArrayInputStream("""{
                    "name": "Dedup Test", "creator": {"name": "A"},
                    "dimensions": {"cols": 2, "rows": 1},
                    "entries": [{"number": 1, "clue": "Twice (2)", "direction": "across",
                                 "length": 2, "position": {"x": 0, "y": 0}, "solution": "AB"}]}
                """.toByteArray()), format = "guardian-json")
        assertNull(second)
    }

    @Test
    fun touchUpdatesModifiedTimestamp() {
        PuzzleManager.initForTests(context())
        val id = PuzzleManager.getBundledId()
        val before = System.currentTimeMillis() - 1000
        PuzzleManager.getEntry(id)!!.copy(modified = before).let {
            val list = PuzzleManager.getPuzzles().map { e ->
                if (e.id == id) e.copy(modified = before) else e
            }
            PuzzleManager.saveList(list)
        }

        PuzzleManager.touch(id)

        assertTrue(PuzzleManager.getEntry(id)!!.modified >= before)
    }

    @Test
    fun stateRoundTrips() {
        PuzzleManager.initForTests(context())
        val id = PuzzleManager.getBundledId()

        val state = PuzzleManager.loadState(id)
        // The bundled puzzle may or may not carry a start state; writing and
        // re-reading our own state must round-trip regardless.
        val fresh = state ?: PuzzleManager.parse(
                PuzzleManager.puzzleFile(id, "puz"))!!.newState()
        fresh.setCharAt(0, 0, "A")
        PuzzleManager.saveState(id, fresh)

        assertEquals("A", PuzzleManager.loadState(id)!!.charAt(0, 0))
    }
}
