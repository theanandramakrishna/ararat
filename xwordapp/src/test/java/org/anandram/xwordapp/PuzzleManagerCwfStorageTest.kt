package org.anandram.xwordapp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards [PuzzleManager.setCwfGame] persistence: the game id/URL must survive
 * on the puzzle entry (list.json) and, when a state file exists, on the state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PuzzleManagerCwfStorageTest {

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    @Test
    fun setCwfGame_persistsOnEntry() {
        PuzzleManager.initForTests(context())
        val id = PuzzleManager.getBundledId()

        PuzzleManager.setCwfGame(id, "gid-1", "https://crosswithfriends.com/?gid=gid-1")

        val entry = PuzzleManager.getEntry(id)!!
        assertEquals("gid-1", entry.cwfGid)
        assertEquals("https://crosswithfriends.com/?gid=gid-1", entry.cwfGameUrl)
    }

    @Test
    fun setCwfGame_withNullsClearsEntry() {
        PuzzleManager.initForTests(context())
        val id = PuzzleManager.getBundledId()

        PuzzleManager.setCwfGame(id, "gid-9", "https://crosswithfriends.com/?gid=gid-9")
        PuzzleManager.setCwfGame(id, null, null)

        assertNull(PuzzleManager.getEntry(id)!!.cwfGid)
        assertNull(PuzzleManager.getEntry(id)!!.cwfGameUrl)
    }

    @Test
    fun setCwfGame_persistsOnStateFile() {
        PuzzleManager.initForTests(context())
        val id = PuzzleManager.getBundledId()

        // Give the puzzle a state file so setCwfGame has a state to update.
        PuzzleManager.stateFile(id).delete()
        val state = PuzzleManager.loadState(id)
                ?: PuzzleManager.parse(PuzzleManager.puzzleFile(id, "puz"))!!.newState()
        PuzzleManager.saveState(id, state)

        PuzzleManager.setCwfGame(id, "gid-s", "https://crosswithfriends.com/?gid=gid-s")

        val reloaded = PuzzleManager.loadState(id)!!
        assertEquals("gid-s", reloaded.cwfGid)
        assertEquals("https://crosswithfriends.com/?gid=gid-s", reloaded.cwfGameUrl)
    }

    @Test
    fun entryJson_carriesCwfFields() {
        PuzzleManager.initForTests(context())
        PuzzleManager.setCwfGame(PuzzleManager.getBundledId(), "gid-j",
                "https://crosswithfriends.com/?gid=gid-j")

        // Guards a Gson regression silently dropping the new entry fields.
        // (Values use contains(), not exact match: Gson HTML-escapes the '='
        // in the URL to \u003d.) Round-trip of the values is checked above.
        val json = PuzzleManager.listJson()
        assertTrue("listJson = $json", json.contains("\"cwfGid\""))
        assertTrue("listJson = $json", json.contains("gid-j"))
        assertTrue("listJson = $json", json.contains("\"cwfGameUrl\""))
    }
}