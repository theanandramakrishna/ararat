package org.anandram.xwordapp

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import org.akop.ararat.core.Crossword
import org.akop.ararat.core.CrosswordState
import org.akop.ararat.core.CrosswordStateReader
import org.akop.ararat.core.CrosswordStateWriter
import org.akop.ararat.core.buildCrossword
import org.akop.ararat.io.GuardianJsonFormatter
import org.akop.ararat.io.JsoupHtmlFormatter
import org.akop.ararat.io.PmlJsonFormatter
import org.akop.ararat.io.PuzFormatter
import org.akop.ararat.io.WSJFormatter
import org.akop.ararat.io.XdFormatter
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.UUID

object PuzzleManager {
    private const val TAG = "PuzzleManager"
    private const val DIR_NAME = "puzzles"
    private const val LIST_FILE = "list.json"
    private const val BUNDLED_ID = "bundled"
    private const val BUNDLED_RAW = "puzzle"

    private lateinit var appContext: Context
    private lateinit var dir: File

    /**
     * Optional observer notified after each puzzle is successfully added
     * (via any of the add methods). Callers must clear it when done.
     */
    @Volatile
    var onPuzzleAdded: (() -> Unit)? = null

    @Synchronized
    fun init(context: Context) {
        if (::appContext.isInitialized) return

        appContext = context.applicationContext
        dir = File(appContext.filesDir, DIR_NAME)
        dir.mkdirs()
        ensureBundled()
    }

    private fun ensureBundled() {
        if (getEntry(BUNDLED_ID) != null) return

        val puzFile = File(dir, "$BUNDLED_ID.puz")
        appContext.resources.openRawResource(R.raw.puzzle).use { input ->
            puzFile.outputStream().use { input.copyTo(it) }
        }

        val crossword = parse(puzFile)
        val list = getPuzzlesInternal().toMutableList()
        list += PuzzleEntry(
                id = BUNDLED_ID,
                title = crossword?.title ?: appContext.getString(R.string.bundled_puzzle_title),
                author = crossword?.author,
                fileName = puzFile.name,
                modified = puzFile.lastModified())
        saveList(list)
    }

    fun getBundledId(): String = BUNDLED_ID

    fun getPuzzles(): List<PuzzleEntry> =
            getPuzzlesInternal().sortedByDescending { effectiveModified(it) }.toMutableList()

    private fun effectiveModified(entry: PuzzleEntry): Long {
        val stored = entry.modified
        if (stored > 0) return stored
        return puzFile(entry.id).lastModified()
    }

    fun getEntry(id: String): PuzzleEntry? =
            getPuzzlesInternal().firstOrNull { it.id == id }

    fun hasPuzzleByUrl(url: String): Boolean =
            getPuzzlesInternal().any { it.downloadUrl == url }

    @Synchronized
    fun touch(id: String) {
        val list = getPuzzlesInternal().toMutableList()
        val index = list.indexOfFirst { it.id == id }
        if (index >= 0) {
            list[index] = list[index].copy(modified = System.currentTimeMillis())
            saveList(list)
        }
    }

    @Synchronized
    fun addPuzzle(source: InputStream, format: String = "puz", fallbackTitle: String? = null,
                  sourceName: String? = null, downloadUrl: String? = null): PuzzleEntry? {
        val id = UUID.randomUUID().toString()
        val file = puzzleFile(id, format)
        source.use { input ->
            file.outputStream().use { input.copyTo(it) }
        }

        val crossword = parse(file, format)
        val entry = PuzzleEntry(
                id = id,
                title = crossword?.title ?: fallbackTitle ?: id,
                author = crossword?.author,
                fileName = file.name,
                modified = file.lastModified(),
                source = sourceName,
                downloadUrl = downloadUrl,
                format = format)

        val list = getPuzzlesInternal().toMutableList()
        list += entry
        saveList(list)

        onPuzzleAdded?.invoke()

        return entry
    }

    @Synchronized
    fun addPuzzleIfNew(source: InputStream, format: String = "puz", fallbackTitle: String? = null,
                       sourceName: String? = null, downloadUrl: String? = null): PuzzleEntry? {
        val bytes = source.readBytes()
        val crossword = parse(ByteArrayInputStream(bytes), format)
        if (crossword == null && fallbackTitle == null) {
            return null
        }

        val title = crossword?.title ?: fallbackTitle
        if (title != null &&
                getPuzzlesInternal().any { it.title.equals(title, ignoreCase = true) }) {
            return null
        }

        return addPuzzle(ByteArrayInputStream(bytes), format, fallbackTitle, sourceName, downloadUrl)
    }

    @Synchronized
    fun addXdIfNew(xdText: String, sourceName: String? = null,
                   downloadUrl: String? = null): PuzzleEntry? {
        val formatter = XdFormatter()
        val crossword = try {
            buildCrossword {
                formatter.read(this, ByteArrayInputStream(xdText.toByteArray(Charsets.UTF_8)))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse XD", e)
            return null
        }

        val entry = addPuzzleIfNew(ByteArrayInputStream(xdText.toByteArray(Charsets.UTF_8)),
                format = "xd", fallbackTitle = crossword.title,
                sourceName = sourceName, downloadUrl = downloadUrl)
        if (entry != null) {
            formatter.startState?.let { start ->
                if (!stateFile(entry.id).exists()) {
                    saveState(entry.id, start)
                }
            }
        }
        return entry
    }

    fun solvedPercent(id: String): Int {
        val state = loadState(id) ?: return 0
        if (state.squareCount <= 0) return 0

        val solved = state.squaresSolved + state.squaresCheated
        return (solved.toFloat() / state.squareCount * 100).toInt()
    }

fun parse(file: File, format: String = "puz"): Crossword? = try {
    file.inputStream().use { s -> parse(s, format) }
} catch (e: Exception) {
    Log.e(TAG, "Failed to parse ${file.name}", e)
    null
}

fun parse(source: InputStream, format: String = "puz"): Crossword? = try {
    when (format) {
        "xd" -> source.use { s -> buildCrossword { XdFormatter().read(this, s) } }
        "guardian-json" -> source.use { s -> buildCrossword { GuardianJsonFormatter().read(this, s) } }
        "wsj-json" -> source.use { s -> buildCrossword { WSJFormatter().read(this, s) } }
        "jsoup-html" -> source.use { s -> buildCrossword { JsoupHtmlFormatter().read(this, s) } }
        "pml-json" -> source.use { s -> buildCrossword { PmlJsonFormatter().read(this, s) } }
        else -> source.use { s -> buildCrossword { PuzFormatter().read(this, s) } }
    }
} catch (e: Exception) {
    Log.e(TAG, "Failed to parse stream", e)
    null
}

fun puzzleFile(id: String, format: String = "puz"): File = File(dir, "$id.$format")

fun puzFile(id: String): File = File(dir, "$id.puz")

    fun stateFile(id: String): File = File(dir, "$id.state")

    fun loadState(id: String): CrosswordState? {
        val file = stateFile(id)
        if (!file.exists()) return null

        return try {
            file.inputStream().use { s ->
                CrosswordStateReader(s).use { it.read() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load state for $id", e)
            null
        }
    }

    fun saveState(id: String, state: CrosswordState) {
        try {
            stateFile(id).outputStream().use { s ->
                CrosswordStateWriter(s).use { it.write(state) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save state for $id", e)
        }
    }

    fun listJson(): String = Gson().toJson(getPuzzles())

    @Synchronized
    fun saveList(entries: List<PuzzleEntry>) {
        File(dir, LIST_FILE).writeText(Gson().toJson(entries))
    }

    fun writePuzzle(id: String, format: String, bytes: ByteArray) {
        puzzleFile(id, format).writeBytes(bytes)

        val list = getPuzzlesInternal().toMutableList()
        val index = list.indexOfFirst { it.id == id }
        if (index >= 0) {
            list[index] = list[index].copy(modified = System.currentTimeMillis())
            saveList(list)
        }
    }

    fun writeState(id: String, bytes: ByteArray) {
        stateFile(id).writeBytes(bytes)
    }

    private fun getPuzzlesInternal(): List<PuzzleEntry> {
        val file = File(dir, LIST_FILE)
        if (!file.exists()) return emptyList()

        return try {
            Gson().fromJson(file.readText(), Array<PuzzleEntry>::class.java)
                    ?.toList()
                    ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read puzzle list", e)
            emptyList()
        }
    }
}