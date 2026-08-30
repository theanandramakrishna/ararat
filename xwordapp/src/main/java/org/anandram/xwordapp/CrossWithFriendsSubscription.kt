package org.anandram.xwordapp

import android.util.Log
import org.akop.ararat.core.Crossword
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Connection
import org.jsoup.Jsoup
import io.socket.client.Ack
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONException
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

/**
 * Cross With Friends (CWF) integration. "Downloading" a CWF subscription
 * uploads the newest puzzle that doesn't yet have a game and starts a shared
 * game room for it. Puzzles are uploaded anonymously (no auth header); the
 * game URL is recorded on both the [PuzzleEntry] and the [org.akop.ararat.core.CrosswordState].
 *
 * The server's `pid` column is numeric but the backend passes a UUID hex
 * string, so numeric pids are generated client-side and retried on duplicate.
 */
object CrossWithFriendsSubscription {
    const val TAG = "CrossWithFriendsSubscription"

    const val NAME = "Cross With Friends"
    const val URL = "https://crosswithfriends.com"
    const val PUZZLE_FORMAT = "cwf"

    const val SOCKET_URL = "https://downforacross-com.onrender.com"

    private const val API = "https://crosswithfriends.com/api"
    private const val MIN_PID = 900000
    private const val MAX_PID = 999999
    private const val MAX_UPLOAD_ATTEMPTS = 5

    fun default(): Subscription = Subscription(
            name = NAME,
            url = URL,
            fetchFrequency = "One-Time",
            puzzleFormat = PUZZLE_FORMAT)

    fun gameUrl(gid: String): String = "https://www.crosswithfriends.com/beta/game/$gid"

    /**
     * Sweep entry point: start a CWF game for the newest puzzle that doesn't
     * have one yet. Returns 1 when a game was created, else 0.
     */
    fun download(subscription: Subscription): Int {
        return try {
            val candidate = PuzzleManager.getPuzzles().firstOrNull { it.cwfGid == null }
            if (candidate != null && createGame(candidate.id) != null) 1 else 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start CWF game", e)
            0
        }
    }

    /**
     * Uploads puzzle [id] to CWF and starts a game, recording the game URL on
     * the puzzle entry and its state. Returns the shareable game URL.
     */
    fun createGame(id: String): String? {
        val entry = PuzzleManager.getEntry(id) ?: return null
        val crossword = PuzzleManager.parse(PuzzleManager.puzzleFile(entry.id, entry.format))
                ?: return null

        val pid = uploadPuzzle(crossword) ?: return null
        val gid = createGameForPid(pid) ?: return null
        val url = gameUrl(gid)

        PuzzleManager.setCwfGame(id, gid, url)
        Log.i(TAG, "CWF game started: $url")
        return url
    }

    private fun uploadPuzzle(crossword: Crossword): String? {
        val puzzle = JSONObject()
                .put("grid", toGridJson(crossword))
                .put("info", JSONObject()
                        .put("title", crossword.title ?: "")
                        .put("author", crossword.author ?: "")
                        .put("copyright", crossword.copyright ?: "")
                        .put("description", crossword.comment ?: ""))
                .put("clues", JSONObject()
                        .put("across", toCluesJson(crossword.wordsAcross))
                        .put("down", toCluesJson(crossword.wordsDown)))
                .put("circles", JSONArray())
                .put("shades", JSONArray())

        for (attempt in 0 until MAX_UPLOAD_ATTEMPTS) {
            val pid = ThreadLocalRandom.current().nextInt(MIN_PID, MAX_PID + 1).toString()
            val body = JSONObject()
                    .put("puzzle", puzzle)
                    .put("isPublic", false)
                    .put("pid", pid)

            try {
                val response = Jsoup.connect("$API/puzzle")
                        .method(Connection.Method.POST)
                        .header("Content-Type", "application/json")
                        .requestBody(body.toString())
                        .ignoreContentType(true)
                        .timeout(30_000)
                        .execute()

                if (response.statusCode() == 200) {
                    val data = JSONObject(response.body())
                    if (data.optString("pid").isNotEmpty()) return data.optString("pid")
                } else if (response.body().contains("duplicate key")) {
                    continue
                } else {
                    Log.e(TAG, "Upload failed (${response.statusCode()}): ${response.body().take(300)}")
                    return null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to upload puzzle (attempt $attempt)", e)
            }
        }
        return null
    }

    private fun createGameForPid(pid: String): String? {
        val gid = UUID.randomUUID().toString()
        val body = JSONObject()
                .put("gid", gid)
                .put("pid", pid)

        return try {
            val response = Jsoup.connect("$API/game")
                    .method(Connection.Method.POST)
                    .header("Content-Type", "application/json")
                    .requestBody(body.toString())
                    .ignoreContentType(true)
                    .timeout(30_000)
                    .execute()

            if (response.statusCode() == 200) {
                val data = JSONObject(response.body())
                val resolved = data.optString("gid", gid)
                if (resolved.isNotEmpty()) resolved else null
            } else {
                Log.e(TAG, "Create game failed (${response.statusCode()}): ${response.body().take(300)}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create game", e)
            null
        }
    }

    internal fun toGridJson(crossword: Crossword): JSONArray {
        val grid = JSONArray()
        for (r in 0 until crossword.height) {
            val row = JSONArray()
            for (c in 0 until crossword.width) {
                val cell = crossword.cellMap[r][c]
                row.put(when {
                    cell == null -> "."
                    !cell.isEmpty -> cell.chars[0].toString()
                    else -> ""
                })
            }
            grid.put(row)
        }
        return grid
    }

    internal fun toCluesJson(words: List<Crossword.Word>): JSONArray {
        val maxNumber = words.maxOfOrNull { it.number } ?: 0
        val clues = JSONArray()
        for (i in 0..maxNumber) clues.put("")
        for (word in words) {
            if (word.number in 0..maxNumber) {
                clues.put(word.number, word.hint ?: "")
            }
        }
        return clues
    }
}

/**
 * Maintains a Socket.io connection to a CWF game room for two-way cell sync.
 * Guests are allowed via `auth.dfacId`; [Listener] receives remote updates.
 */
class CrossWithFriendsConnection(
        private val gid: String,
        private val listener: Listener? = null) {

    interface Listener {
        /** A remote player set/cleared [value] (null = clear) at [row]/[column]. */
        fun onCellUpdated(row: Int, column: Int, value: String?)

        /** Full game history has been replayed; the board now mirrors the room. */
        fun onSyncComplete()
    }

    private val uid = "anon-${UUID.randomUUID().toString().replace("-", "").take(8)}"
    private var socket: Socket? = null

    fun connect() {
        if (socket?.connected() == true) return

        val options = IO.Options.builder()
                .setTransports(arrayOf("websocket"))
                .setAuth(mapOf("dfacId" to uid))
                .build()

        val s = IO.socket(CrossWithFriendsSubscription.SOCKET_URL, options)

        s.on(Socket.EVENT_CONNECT) {
            Log.i(CrossWithFriendsSubscription.TAG, "CWF socket connected")
            // join_game first (required for rooms/bans); its ack carries no error
            // on success, then we pull the full event history.
            s.emit("join_game", gid, Ack { args ->
                val error = (args.firstOrNull() as? JSONObject)?.optString("error")
                if (error.isNullOrEmpty()) {
                    syncAllEvents(s)
                } else {
                    Log.e(CrossWithFriendsSubscription.TAG, "CWF join_game failed: $error")
                }
            })
        }
        s.on("game_event") { args ->
            val event = args.firstOrNull() as? JSONObject ?: return@on
            if (event.optString("type") == "updateCell") {
                val params = event.optJSONObject("params") ?: return@on
                val cell = params.optJSONObject("cell") ?: return@on
                val r = cell.optInt("r", -1)
                val c = cell.optInt("c", -1)
                if (r >= 0 && c >= 0) {
                    listener?.onCellUpdated(r, c, eventValue(params))
                    Log.i(CrossWithFriendsSubscription.TAG,
                            "CWF <- cell($r,$c) = ${eventValue(params)}")
                }
            }
        }
        s.on(Socket.EVENT_DISCONNECT) { Log.i(CrossWithFriendsSubscription.TAG, "CWF socket disconnected") }
        s.on(Socket.EVENT_CONNECT_ERROR) { args ->
            Log.e(CrossWithFriendsSubscription.TAG,
                    "CWF socket error: ${args.joinToString { it.toString() }}")
        }

        socket = s
        s.connect()
    }

    private fun syncAllEvents(s: Socket) {
        s.emit("sync_all_game_events", gid, Ack { args ->
val events = args.firstOrNull() as? JSONArray
                if (events != null) {
                    applyHistory(events)
                    Log.i(CrossWithFriendsSubscription.TAG, "CWF sync: ${events.length()} events replayed")
                } else {
                Log.e(CrossWithFriendsSubscription.TAG,
                        "sync_all_game_events returned no event list")
            }
            listener?.onSyncComplete()
        })
    }

    /**
     * Reconciles the board with the room. When a solved-game snapshot was kept
     * (one create event returned with `params.game.grid`), the grid cells carry
     * the live board. Otherwise the server returns the full event history in
     * timestamp order and we replay the updateCell events.
     */
    internal fun applyHistory(events: JSONArray) {
        val list = (0 until events.length()).map { events.getJSONObject(it) }

        if (list.size == 1 && list[0].optString("type") == "create") {
            val grid = list[0].optJSONObject("params")
                    ?.optJSONObject("game")
                    ?.optJSONArray("grid")
            if (grid != null) {
                for (r in 0 until grid.length()) {
                    val row = grid.optJSONArray(r) ?: continue
                    for (c in 0 until row.length()) {
                        when (val cell = row.opt(c)) {
                            is JSONObject ->
                                listener?.onCellUpdated(r, c,
                                        cell.optString("value").ifEmpty { null })
                            is String -> listener?.onCellUpdated(r, c, cell.ifEmpty { null })
                            else -> {}
                        }
                    }
                }
                return
            }
        }

        for (event in list) {
            if (event.optString("type") != "updateCell") continue
            val params = event.optJSONObject("params") ?: continue
            val cell = params.optJSONObject("cell") ?: continue
            val r = cell.optInt("r", -1)
            val c = cell.optInt("c", -1)
            if (r >= 0 && c >= 0) {
                listener?.onCellUpdated(r, c, eventValue(params))
            }
        }
    }

    internal fun eventValue(params: JSONObject): String? =
            if (params.isNull("value")) null else params.optString("value").ifEmpty { null }

    fun disconnect() {
        socket?.disconnect()
        socket?.off()
        socket = null
    }

    val isConnected: Boolean
        get() = socket?.connected() == true

    /** Announce [value] (null = clear) at [row]/[column] to the room. */
    fun updateCell(row: Int, column: Int, value: String?) {
        socket ?: return
        if (!isConnected) return

        try {
            socket?.emit("game_event", JSONObject()
                    .put("gid", gid)
                    .put("event", buildUpdateCellEvent(row, column, value)))
            Log.i(CrossWithFriendsSubscription.TAG, "CWF -> cell($row,$column) = $value")
        } catch (e: JSONException) {
            Log.e(CrossWithFriendsSubscription.TAG, "Failed to serialize updateCell", e)
        }
    }

    /** Builds the wire payload for a board edit (null = clear). */
    internal fun buildUpdateCellEvent(row: Int, column: Int, value: String?): JSONObject =
            JSONObject()
                    .put("type", "updateCell")
                    .put("params", JSONObject()
                            .put("cell", JSONObject().put("r", row).put("c", column))
                            .put("value", value ?: JSONObject.NULL)
                            .put("autocheck", false)
                            .put("id", uid))
}