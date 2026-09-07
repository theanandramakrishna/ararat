package org.anandram.xwordapp

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream

/**
 * Exercises [PuzzleManager] against the real on-device files directory
 * (Robolectric covers the logic; this covers real file I/O, paths, and
 * list/state persistence). Entries are tagged `sourceName = "DeviceTest"`
 * and removed in [tearDown] so the host app's library is left untouched.
 */
@RunWith(AndroidJUnit4::class)
class PuzzleManagerDeviceTest {

    private val created = mutableListOf<Pair<String, String>>()

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PuzzleManager.initForTests(context)
    }

    @After
    fun tearDown() {
        for ((id, format) in created) {
            PuzzleManager.saveList(PuzzleManager.getPuzzles().filter { it.id != id })
            PuzzleManager.puzzleFile(id, format).delete()
            PuzzleManager.stateFile(id).delete()
        }
        created.clear()
    }

    private fun addTestPuzzle(): PuzzleEntry {
        val bytes = PuzzleManager.puzzleFile(PuzzleManager.getBundledId()).readBytes()
        val entry = PuzzleManager.addPuzzle(
                ByteArrayInputStream(bytes),
                fallbackTitle = "DeviceTest puzzle",
                sourceName = "DeviceTest",
                downloadUrl = "device-test://${System.nanoTime()}")!!
        created += entry.id to entry.format
        return entry
    }

    @Test
    fun addedEntryPersistsOnDisk() {
        val entry = addTestPuzzle()

        assertNotNull(PuzzleManager.getEntry(entry.id))
        assertTrue(PuzzleManager.puzzleFile(entry.id, entry.format).exists())
    }

    @Test
    fun cwfBindingSurvivesReinitAndClears() {
        val entry = addTestPuzzle()
        val gid = "12345678-1234-1234-1234-1234567890ab"
        val url = CrossWithFriendsSubscription.gameUrl(gid)

        PuzzleManager.setCwfGame(entry.id, gid, url)
        assertEquals(gid, PuzzleManager.getEntry(entry.id)?.cwfGid)
        assertEquals(url, PuzzleManager.getEntry(entry.id)?.cwfGameUrl)

        // A fresh init (as after a process restart) must see the binding.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PuzzleManager.initForTests(context)
        assertEquals(gid, PuzzleManager.getEntry(entry.id)?.cwfGid)

        // Clearing (what the live-game probe does for a gone room) sticks.
        PuzzleManager.setCwfGame(entry.id, null, null)
        PuzzleManager.initForTests(context)
        assertNull(PuzzleManager.getEntry(entry.id)?.cwfGid)
        assertNull(PuzzleManager.getEntry(entry.id)?.cwfGameUrl)
    }

    @Test
    fun timeSpentRoundTripsAcrossReinit() {
        val entry = addTestPuzzle()

        PuzzleManager.setTimeSpent(entry.id, 90_000L)
        assertEquals(90_000L, PuzzleManager.getTimeSpent(entry.id))

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PuzzleManager.initForTests(context)
        assertEquals(90_000L, PuzzleManager.getTimeSpent(entry.id))
    }
}
