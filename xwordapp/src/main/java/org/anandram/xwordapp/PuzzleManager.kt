package org.anandram.xwordapp

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import org.akop.ararat.core.Crossword
import org.akop.ararat.core.CrosswordState
import org.akop.ararat.core.CrosswordStateReader
import org.akop.ararat.core.CrosswordStateWriter
import org.akop.ararat.core.buildCrossword
import org.akop.ararat.io.PuzFormatter
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
    fun addPuzzle(source: InputStream, fallbackTitle: String? = null): PuzzleEntry? {
        val id = UUID.randomUUID().toString()
        val puzFile = File(dir, "$id.puz")
        source.use { input ->
            puzFile.outputStream().use { input.copyTo(it) }
        }

        val crossword = parse(puzFile)
        val entry = PuzzleEntry(
                id = id,
                title = crossword?.title ?: fallbackTitle ?: id,
                author = crossword?.author,
                fileName = puzFile.name,
                modified = puzFile.lastModified())

        val list = getPuzzlesInternal().toMutableList()
        list += entry
        saveList(list)

        return entry
    }

    @Synchronized
    fun addPuzzleIfNew(source: InputStream, fallbackTitle: String? = null): PuzzleEntry? {
        val bytes = source.readBytes()
        val crossword = parse(ByteArrayInputStream(bytes))
        if (crossword == null && fallbackTitle == null) {
            return null
        }

        val title = crossword?.title ?: fallbackTitle
        if (title != null &&
                getPuzzlesInternal().any { it.title.equals(title, ignoreCase = true) }) {
            return null
        }

        return addPuzzle(ByteArrayInputStream(bytes), fallbackTitle)
    }

    fun parse(file: File): Crossword? = try {
        file.inputStream().use { s ->
            buildCrossword { PuzFormatter().read(this, s) }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to parse ${file.name}", e)
        null
    }

    fun parse(source: InputStream): Crossword? = try {
        source.use { s ->
            buildCrossword { PuzFormatter().read(this, s) }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to parse stream", e)
        null
    }

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

    fun writePuz(id: String, bytes: ByteArray) {
        puzFile(id).writeBytes(bytes)

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