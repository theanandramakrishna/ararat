// Copyright (c) Anand Ramakrishna
package org.akop.ararat.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Guards the persistent [CrosswordState] format. v4 carries the Cross With
 * Friends game linkage; older readers must keep working on the shared fields.
 */
class TestCrosswordState {

    private fun roundTrip(state: CrosswordState): CrosswordState {
        val bytes = ByteArrayOutputStream().use { out ->
            CrosswordStateWriter(out).use { it.write(state) }
            out.toByteArray()
        }
        return ByteArrayInputStream(bytes).use { input ->
            CrosswordStateReader(input).use { it.read() }
        }
    }

    @Test
    fun roundTripsCharsAndCwfFields() {
        val state = CrosswordState(2, 2)
        state.setCharAt(0, 0, "A")
        state.setCharAt(1, 1, "Q")
        state.cwfGid = "gid-x"
        state.cwfGameUrl = "https://crosswithfriends.com/?gid=gid-x"

        val loaded = roundTrip(state)

        assertEquals("A", loaded.charAt(0, 0))
        assertEquals("Q", loaded.charAt(1, 1))
        assertEquals("gid-x", loaded.cwfGid)
        assertEquals("https://crosswithfriends.com/?gid=gid-x", loaded.cwfGameUrl)
    }

    @Test
    fun roundTripsWithoutCwfFields() {
        val state = CrosswordState(1, 1)
        state.setCharAt(0, 0, "Z")

        val loaded = roundTrip(state)

        assertEquals("Z", loaded.charAt(0, 0))
        assertNull(loaded.cwfGid)
        assertNull(loaded.cwfGameUrl)
    }
}