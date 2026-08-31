package org.anandram.xwordapp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards the per-puzzle timer: formatting and persist/read round-trip.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PuzzleTimerTest {

    @Before
    fun setUp() {
        PuzzleManager.initForTests(ApplicationProvider.getApplicationContext<Context>())
    }

    @Test
    fun formatZero() {
        assertEquals("0:00:00", PuzzleManager.formatTime(0))
    }

    @Test
    fun formatSeconds() {
        assertEquals("0:00:01", PuzzleManager.formatTime(1000))
        assertEquals("0:01:01", PuzzleManager.formatTime(61000))
    }

    @Test
    fun formatHours() {
        assertEquals("1:01:01", PuzzleManager.formatTime(3661000))
        assertEquals("25:00:00", PuzzleManager.formatTime(90_000_000))
    }

    @Test
    fun timeSpentStartsAtZero() {
        assertEquals(0L, PuzzleManager.getTimeSpent(PuzzleManager.getBundledId()))
    }

    @Test
    fun timeSpentRoundTrip() {
        val id = PuzzleManager.getBundledId()
        PuzzleManager.setTimeSpent(id, 123_456)
        assertEquals(123_456L, PuzzleManager.getTimeSpent(id))
    }
}