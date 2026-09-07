// Copyright (c) Anand Ramakrishna
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.

package org.anandram.xwordapp

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Spannable
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat

import org.akop.ararat.core.Crossword
import org.akop.ararat.core.CrosswordState
import org.akop.ararat.io.IpuzFormatter
import org.akop.ararat.view.CrosswordView

import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity(), CrosswordView.OnLongPressListener, CrosswordView.OnStateChangeListener, CrosswordView.OnSelectionChangeListener {
    companion object {
        private const val TAG = "XwordApp"
        const val EXTRA_PUZZLE_ID = "puzzle_id"
    }

    private lateinit var crosswordView: CrosswordView
    private var hint: TextView? = null
    private var cwfGameLink: TextView? = null
    private var cwfGameLinkRow: View? = null
    private var cwfShareButton: ImageButton? = null
    private lateinit var keyboard: CrosswordKeyboardView
    private lateinit var puzzleId: String
    private var puzzleComment: String? = null

    private var cwfConnection: CrossWithFriendsConnection? = null
    private var cwfSnapshot: Array<Array<String?>>? = null
    private var cwfSynced = false
    private var cwfApplyingRemote = false

    private var timerRunning = false
    private var timerSessionBase: Long = 0
    private var timerStartRealtime: Long = 0
    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerTick = object : Runnable {
        override fun run() {
            if (timerRunning) timerHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)
        applySystemBarInsets()

        PuzzleManager.init(this)

        puzzleId = intent.getStringExtra(EXTRA_PUZZLE_ID) ?: PuzzleManager.getBundledId()
        val entry = PuzzleManager.getEntry(puzzleId)
        PuzzleManager.touch(puzzleId)
        FirebaseStats.setCustomKey("last_puzzle_format", entry?.format ?: "puz")
        FirebaseStats.log("puzzle_open ${entry?.format ?: "puz"} ${entry?.source.orEmpty()}")

        crosswordView = findViewById(R.id.crossword)
        hint = findViewById(R.id.hint)
        cwfGameLink = findViewById(R.id.cwf_game_link)
        cwfGameLinkRow = findViewById(R.id.cwf_game_link_row)
        cwfShareButton = findViewById(R.id.cwf_share_button)
        cwfShareButton?.setOnClickListener {
            val url = PuzzleManager.getEntry(puzzleId)?.cwfGameUrl
            if (url == null) {
                Log.w(TAG, "CWF share icon: no game URL for $puzzleId")
                FirebaseStats.log("cwf_share_icon no_url")
                return@setOnClickListener
            }
            val gid = CrossWithFriendsSubscription.gidFromGameUrl(url)
            Log.i(TAG, "CWF share icon: sharing gid=${gid?.take(8)}")
            FirebaseStats.log("cwf_share_icon ${gid?.take(8)}")
            val appLink = gid?.let { CrossWithFriendsSubscription.appGameUrl(it) }
            val message = if (appLink != null) {
                getString(R.string.cwf_share_app_link, appLink) + "\n\n" +
                        getString(R.string.cwf_share_message, url)
            } else {
                getString(R.string.cwf_share_message, url)
            }
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
            }
            startActivity(Intent.createChooser(send, getString(R.string.share_game)))
        }
        keyboard = findViewById(R.id.keyboard)

        val puzzle = entry?.let { PuzzleManager.parse(
                PuzzleManager.puzzleFile(it.id, it.format), it.format) }
                ?: PuzzleManager.parse(PuzzleManager.puzFile(PuzzleManager.getBundledId()))
        puzzleComment = puzzle?.comment

        FirebaseStats.setCustomKey("last_puzzle_format", entry?.format ?: "puz")
        FirebaseStats.log("puzzle_open id=${puzzleId.take(8)} " +
                "format=${entry?.format ?: "puz"} title=${entry?.title}")

        title = when {
            entry != null && !entry.author.isNullOrEmpty() ->
                getString(R.string.title_by_author, entry.title, entry.author)
            entry != null -> entry.title
            else -> getString(R.string.app_name)
        }

        val caveat = ResourcesCompat.getFont(this, R.font.caveat)
        with (crosswordView) {
            crossword = puzzle
            onLongPressListener = this@MainActivity
            onStateChangeListener = this@MainActivity
            onSelectionChangeListener = this@MainActivity
            inputValidator = { ch -> !ch.first().isISOControl() }
            undoMode = CrosswordView.UNDO_NONE
            markerDisplayMode = CrosswordView.MARKER_CHEAT
            inputMode = CrosswordView.INPUT_MODE_NONE
            answerTypeface = when {
                caveat == null -> null
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
                    Typeface.create(caveat, 700, false)
                else -> Typeface.create(caveat, Typeface.BOLD)
            }
        }

        keyboard.listener = object : CrosswordKeyboardView.Listener {
            override fun onKeyPress(ch: Char) {
                crosswordView.inputChar(ch)
            }

            override fun onBackspace() {
                crosswordView.backspace()
            }

            override fun onDirectionToggle() {
                crosswordView.switchWordDirection()
            }
        }

        PuzzleManager.loadState(puzzleId)?.let { saved ->
            try {
                crosswordView.restoreState(saved)
            } catch (e: RuntimeException) {
                Log.w(TAG, "Failed to restore saved state for $puzzleId", e)
                FirebaseStats.recordException(e,
                        "restore_state_${entry?.format ?: "puz"}")
            }
        }

        onSelectionChanged(crosswordView,
                crosswordView.selectedWord,
                crosswordView.selectedCell)
    }

    override fun onResume() {
        super.onResume()
        startTimer()
        updateCwfGameLink()
        // Reconnect to an in-progress CWF room after returning to the puzzle.
        val gid = PuzzleManager.getEntry(puzzleId)?.cwfGid
        if (gid != null && (cwfConnection == null || !cwfConnection!!.isConnected)) {
            FirebaseStats.log("cwf_reconnect gid=${gid.take(8)}")
            connectCwf(gid)
        }
    }

    override fun onPause() {
        super.onPause()
        pauseTimer()
        crosswordView.state?.let { PuzzleManager.saveState(puzzleId, it) }
        cwfConnection?.disconnect()
        cwfConnection = null
    }

    override fun onDestroy() {
        super.onDestroy()
        pauseTimer()
        cwfConnection?.disconnect()
        cwfConnection = null
    }

    private fun startTimer() {
        if (timerRunning) return
        if (PuzzleManager.solvedPercent(puzzleId) >= 100) return
        timerSessionBase = PuzzleManager.getTimeSpent(puzzleId)
        timerStartRealtime = SystemClock.elapsedRealtime()
        timerRunning = true
        timerHandler.postDelayed(timerTick, 1000)
    }

    private fun pauseTimer() {
        if (!timerRunning) return
        timerRunning = false
        timerHandler.removeCallbacks(timerTick)
        PuzzleManager.setTimeSpent(puzzleId,
                timerSessionBase + (SystemClock.elapsedRealtime() - timerStartRealtime))
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)

        try {
            crosswordView.restoreState(
                    savedInstanceState.getParcelable("state", CrosswordState::class.java)!!)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to restore instance state for $puzzleId", e)
            FirebaseStats.recordException(e, "restore_state")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putParcelable("state", crosswordView.state)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.menu_view_notes).isVisible = !puzzleComment.isNullOrBlank()
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_restart -> crosswordView.reset()
            R.id.menu_solve_cell -> crosswordView.solveChar(
                    crosswordView.selectedWord!!,
                    crosswordView.selectedCell)
            R.id.menu_solve_word -> crosswordView.solveWord(
                    crosswordView.selectedWord!!)
            R.id.menu_solve_puzzle -> crosswordView.solveCrossword()
            R.id.menu_view_notes -> showNotesDialog()
            R.id.menu_export -> sharePuzzleAsIpuz()
            R.id.menu_play_cwf -> startCwfGame()
            else -> return super.onOptionsItemSelected(item)
        }

        return true
    }

    private fun showNotesDialog() {
        AlertDialog.Builder(this)
                .setTitle(R.string.view_notes)
                .setMessage(puzzleComment)
                .setPositiveButton(R.string.close, null)
                .show()
    }

    private fun sharePuzzleAsIpuz() {
        val crossword = crosswordView.crossword ?: return
        try {
            val dir = File(cacheDir, "shared_puzzles").apply { mkdirs() }
            val baseName = (crossword.title ?: "")
                    .replace(Regex("[^A-Za-z0-9 _-]"), "")
                    .trim().replace(Regex("\\s+"), "_")
                    .ifEmpty { "puzzle" }
            val file = File(dir, "$baseName.ipuz")
            FileOutputStream(file).use { out ->
                IpuzFormatter().write(crossword, out)
            }

            val uri = FileProvider.getUriForFile(this,
                    "${packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/x-ipuz"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.export)))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to export puzzle", e)
            FirebaseStats.recordException(e, "ipuz_export")
            Toast.makeText(this, R.string.export_failed,
                    Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCellLongPressed(view: CrosswordView,
                                   word: Crossword.Word, cell: Int) {
        val row = when (word.direction) {
            Crossword.Word.DIR_ACROSS -> word.startRow
            else -> word.startRow + cell
        }
        val column = when (word.direction) {
            Crossword.Word.DIR_ACROSS -> word.startColumn + cell
            else -> word.startColumn
        }

        val cellRect = view.getCellRect(word, cell) ?: return
        val viewLoc = IntArray(2)
        view.getLocationOnScreen(viewLoc)

        val anchor = View(this)
        anchor.layout(0, 0, 1, 1)
        val decor = window.decorView as ViewGroup
        val decorLoc = IntArray(2)
        decor.getLocationOnScreen(decorLoc)
        anchor.x = (viewLoc[0] + cellRect.centerX() - decorLoc[0]).toFloat()
        anchor.y = (viewLoc[1] + cellRect.centerY() - decorLoc[1]).toFloat()
        decor.addView(anchor, ViewGroup.LayoutParams(1, 1))

        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.activity_cell_popup, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.menu_rebus) {
                showRebusDialog(row, column)
                true
            } else {
                false
            }
        }
        popup.setOnDismissListener {
            decor.removeView(anchor)
        }
        try {
            popup.show()
        } catch (e: Exception) {
            decor.removeView(anchor)
        }
    }

    private fun showRebusDialog(row: Int, column: Int) {
        val input = EditText(this)
        val padding = (16 * resources.displayMetrics.density).toInt()
        input.setPadding(padding, padding, padding, padding)

        AlertDialog.Builder(this)
                .setTitle(R.string.rebus)
                .setView(input)
                .setPositiveButton(R.string.ok) { _, _ ->
                    val text = input.text?.toString()?.trim().orEmpty()
                    if (text.isNotEmpty()) {
                        crosswordView.setCellText(row, column, text)
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
    }

    override fun onCrosswordChanged(view: CrosswordView) {
        if (!cwfApplyingRemote) syncLocalToRemote()
    }

    override fun onCrosswordSolved(view: CrosswordView) {
        pauseTimer()
        val entry = PuzzleManager.getEntry(puzzleId)
        FirebaseStats.logEvent(FirebaseStats.EVENT_PUZZLE_COMPLETED, mapOf(
                "format" to (entry?.format ?: "puz"),
                "source" to (entry?.source.orEmpty()),
                "time_seconds" to (PuzzleManager.getTimeSpent(puzzleId) / 1000)))
        Toast.makeText(this, R.string.youve_solved_the_puzzle,
                Toast.LENGTH_SHORT).show()
    }

    override fun onCrosswordUnsolved(view: CrosswordView) { }

    override fun onSelectionChanged(view: CrosswordView,
                                    word: Crossword.Word?, position: Int) {
        hint!!.text = when (word?.direction) {
            Crossword.Word.DIR_ACROSS -> getString(R.string.across, word.number, word.hint)
            Crossword.Word.DIR_DOWN -> getString(R.string.down, word.number, word.hint)
            else -> ""
        }
    }

    private fun startCwfGame() {
        Toast.makeText(this, R.string.cwf_game_starting, Toast.LENGTH_SHORT).show()
        val gid = PuzzleManager.getEntry(puzzleId)?.cwfGid
        FirebaseStats.log("cwf_start_game ${gid?.take(8) ?: "new"}")
        Log.i(TAG, "cwf start: gid=${gid?.take(8)}")

        Thread {
            val url = CrossWithFriendsSubscription.createGame(puzzleId)
            runOnUiThread {
                if (url == null) {
                    Log.w(TAG, "cwf start: createGame returned null")
                    FirebaseStats.log("cwf_start_game failed")
                    Toast.makeText(this, R.string.cwf_game_failed,
                            Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }

                Log.i(TAG, "cwf start: ok $url")
                Toast.makeText(this, R.string.cwf_game_started,
                        Toast.LENGTH_SHORT).show()

                PuzzleManager.getEntry(puzzleId)?.cwfGid?.let { connectCwf(it) }
                updateCwfGameLink()
            }
        }.start()
    }

    /**
     * Shows the live-game link below the puzzle title. Tapping the URL opens
     * the CWF room; starting a game never opens it automatically.
     */
    private fun updateCwfGameLink() {
        val link = cwfGameLink ?: return
        val row = cwfGameLinkRow ?: return
        val url = PuzzleManager.getEntry(puzzleId)?.cwfGameUrl ?: run {
            link.text = null
            row.visibility = View.GONE
            return
        }

        val text = SpannableString(getString(R.string.cwf_live_game, url))
        val start = text.indexOf(url)
        if (start >= 0) {
            text.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (e: Exception) {
                        Log.w(TAG, "No browser available for $url", e)
                        FirebaseStats.recordException(e, "browser_open")
                    }
                }
            }, start, start + url.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        link.text = text
        link.movementMethod = LinkMovementMethod.getInstance()
        row.visibility = View.VISIBLE
    }

    private val cwfListener = object : CrossWithFriendsConnection.Listener {
        override fun onCellUpdated(row: Int, column: Int, value: String?) {
            runOnUiThread {
                val cw = crosswordView.crossword ?: return@runOnUiThread
                if (row >= cw.height || column >= cw.width) return@runOnUiThread

                cwfApplyingRemote = true
                crosswordView.setCellText(row, column, value ?: "")
                cwfApplyingRemote = false
                updateCwfSnapshot()
            }
        }

        override fun onSyncComplete() {
            runOnUiThread {
                cwfSynced = true
                updateCwfSnapshot()
            }
        }
    }

    private fun connectCwf(gid: String) {
        cwfConnection?.disconnect()
        cwfSynced = false
        FirebaseStats.log("cwf_reconnect ${gid.take(8)}")
        val connection = CrossWithFriendsConnection(gid, cwfListener)
        cwfConnection = connection
        connection.connect()
    }

    private fun updateCwfSnapshot() {
        val cw = crosswordView.crossword ?: return
        val state = crosswordView.state ?: return
        cwfSnapshot = Array(cw.height) { r ->
            Array(cw.width) { c -> state.charAt(r, c) }
        }
    }

    /** Sends any locally entered letters that differ from the last snapshot. */
    private fun syncLocalToRemote() {
        if (!cwfSynced) return
        val connection = cwfConnection ?: return
        if (!connection.isConnected) return

        val cw = crosswordView.crossword ?: return
        val state = crosswordView.state ?: return
        val prev = cwfSnapshot ?: return

        for (r in 0 until cw.height) {
            for (c in 0 until cw.width) {
                val now = state.charAt(r, c)
                if (now != prev[r][c]) {
                    prev[r][c] = now
                    connection.updateCell(r, c, now)
                }
            }
        }
    }
}