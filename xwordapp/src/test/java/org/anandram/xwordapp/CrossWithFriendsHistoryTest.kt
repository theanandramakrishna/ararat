package org.anandram.xwordapp

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards the Cross With Friends board reconciliation logic:
 *  - [CrossWithFriendsConnection.applyHistory] rebuilds the board from both the
 *    solved-game snapshot (one create event carrying `params.game.grid`) and
 *    the updateCell replay, in server timestamp order.
 *  - [CrossWithFriendsConnection.eventValue] maps JSON nulls/blanks to board
 *    clears (regression: JSONObject.NULL previously decoded as "null").
 *  - [CrossWithFriendsConnection.buildUpdateCellEvent] builds the wire payload.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CrossWithFriendsHistoryTest {

    private class RecordingListener : CrossWithFriendsConnection.Listener {
        val updates = mutableListOf<Triple<Int, Int, String?>>()
        var syncCompletes = 0

        override fun onCellUpdated(row: Int, column: Int, value: String?) {
            updates += Triple(row, column, value)
        }

        override fun onSyncComplete() {
            syncCompletes++
        }
    }

    private val listener = RecordingListener()
    private val connection = CrossWithFriendsConnection("test-gid", listener)

    private fun updateCell(r: Int, c: Int, value: String?): JSONObject =
            JSONObject()
                    .put("type", "updateCell")
                    .put("params", JSONObject()
                            .put("cell", JSONObject().put("r", r).put("c", c))
                            .put("value", value ?: JSONObject.NULL)
                            .put("autocheck", false)
                            .put("id", "player"))

    private fun createWithGrid(grid: JSONArray?): JSONObject {
        val obj = JSONObject()
                .put("user", "")
                .put("timestamp", 0)
                .put("type", "create")
        val params = JSONObject().put("pid", "1").put("version", 1)
        if (grid != null) {
            params.put("game", JSONObject().put("grid", grid))
        }
        return obj.put("params", params)
    }

    private fun eventsOf(vararg events: JSONObject) =
            JSONArray().apply { events.forEach { put(it) } }

    private fun chatEvent() =
            JSONObject().put("type", "chat").put("from", "player").put("chat", "hi")

    @Test
    fun replay_appliesUpdateCellsInOrderSkippingOthers() {
        connection.applyHistory(eventsOf(
                createWithGrid(null),
                // displayName is persisted in history but does not touch cells.
                JSONObject().put("type", "updateDisplayName")
                        .put("id", "player").put("displayName", "P"),
                updateCell(0, 0, "A"),
                updateCell(1, 1, null),
                // Negative coordinates never reach the listener.
                updateCell(-1, 0, "Z"),
                // A later edit to an earlier cell wins (timestamp order).
                updateCell(0, 0, "B"),
                chatEvent()))
        assertEquals(0, listener.syncCompletes) // applyHistory never calls sync complete

        // Out-of-range bounds (e.g. a bad 99,0) are the view's problem: the
        // connection forwards every non-negative cell it receives.
        assertEquals(listOf(
                Triple(0, 0, "A"),
                Triple(1, 1, null),
                Triple(0, 0, "B")),
                listener.updates)
    }

    @Test
    fun replay_isTakenEvenWhenHistoryStartsWithCreateWithGrid() {
        // A game with history (more than one event) must not be mistaken for a
        // solved snapshot: the static create grid is ignored, events replayed.
        connection.applyHistory(eventsOf(
                createWithGrid(JSONArray().put("Q")),
                updateCell(0, 0, "S")))

        assertEquals(listOf(Triple(0, 0, "S")), listener.updates)
    }

    @Test
    fun snapshotGrid_appliesCellValues() {
        val grid = JSONArray()
                .put(JSONArray()
                        .put(JSONObject().put("value", "Q"))
                        .put(JSONObject().put("value", "")))
                .put(JSONArray()
                        .put("R")
                        .put(JSONObject.NULL))

        connection.applyHistory(eventsOf(createWithGrid(grid)))

        // Object cells via "value", plain-string cells verbatim; blank and
        // JSON-null-valued cells decode as clears; JSONObject.NULL cell skipped.
        assertEquals(listOf(
                Triple(0, 0, "Q"),
                Triple(0, 1, null),
                Triple(1, 0, "R")),
                listener.updates)
    }

    @Test
    fun singleCreateWithoutGrid_appliesNothing() {
        connection.applyHistory(eventsOf(createWithGrid(null)))
        assertEquals(listOf<Triple<Int, Int, String?>>(), listener.updates)
    }

    @Test
    fun emptyHistory_appliesNothing() {
        connection.applyHistory(eventsOf())
        assertEquals(listOf<Triple<Int, Int, String?>>(), listener.updates)
    }

    @Test
    fun eventValue_mapsNullsAndBlanksToClears() {
        val params = JSONObject()
        assertNull(connection.eventValue(updateCell(0, 0, null).getJSONObject("params")))
        assertNull(connection.eventValue(JSONObject().put("value", "")))
        assertNull(connection.eventValue(params))
        assertEquals("Q", connection.eventValue(JSONObject().put("value", "Q")))
    }

    @Test
    fun buildUpdateCellEvent_carriesEdit() {
        val event = connection.buildUpdateCellEvent(3, 4, "X")
        val params = event.getJSONObject("params")

        assertEquals("updateCell", event.getString("type"))
        assertEquals(3, params.getJSONObject("cell").getInt("r"))
        assertEquals(4, params.getJSONObject("cell").getInt("c"))
        assertEquals("X", params.getString("value"))
        assertEquals(false, params.getBoolean("autocheck"))
        assertTrue(params.getString("id").startsWith("anon-"))
    }

    @Test
    fun buildUpdateCellEvent_clearUsesJsonNull() {
        val event = connection.buildUpdateCellEvent(0, 0, null)
        assertEquals(JSONObject.NULL, event.getJSONObject("params").get("value"))
    }
}