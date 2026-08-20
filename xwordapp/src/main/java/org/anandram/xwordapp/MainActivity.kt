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

import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

import org.akop.ararat.core.Crossword
import org.akop.ararat.core.CrosswordState
import org.akop.ararat.view.CrosswordView

class MainActivity : AppCompatActivity(), CrosswordView.OnLongPressListener, CrosswordView.OnStateChangeListener, CrosswordView.OnSelectionChangeListener {
    companion object {
        private const val TAG = "XwordApp"
        const val EXTRA_PUZZLE_ID = "puzzle_id"
    }

    private lateinit var crosswordView: CrosswordView
    private var hint: TextView? = null
    private lateinit var keyboard: CrosswordKeyboardView
    private lateinit var puzzleId: String
    private var puzzleComment: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)
        applySystemBarInsets()

        PuzzleManager.init(this)

        puzzleId = intent.getStringExtra(EXTRA_PUZZLE_ID) ?: PuzzleManager.getBundledId()
        val entry = PuzzleManager.getEntry(puzzleId)
        PuzzleManager.touch(puzzleId)

        crosswordView = findViewById(R.id.crossword)
        hint = findViewById(R.id.hint)
        keyboard = findViewById(R.id.keyboard)

        val puzzle = entry?.let { PuzzleManager.parse(PuzzleManager.puzFile(it.id)) }
                ?: PuzzleManager.parse(PuzzleManager.puzFile(PuzzleManager.getBundledId()))
        puzzleComment = puzzle?.comment

        title = when {
            entry != null && !entry.author.isNullOrEmpty() ->
                getString(R.string.title_by_author, entry.title, entry.author)
            entry != null -> entry.title
            else -> getString(R.string.app_name)
        }

        with (crosswordView) {
            crossword = puzzle
            onLongPressListener = this@MainActivity
            onStateChangeListener = this@MainActivity
            onSelectionChangeListener = this@MainActivity
            inputValidator = { ch -> !ch.first().isISOControl() }
            undoMode = CrosswordView.UNDO_NONE
            markerDisplayMode = CrosswordView.MARKER_CHEAT
            inputMode = CrosswordView.INPUT_MODE_NONE
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
            }
        }

        onSelectionChanged(crosswordView,
                crosswordView.selectedWord,
                crosswordView.selectedCell)
    }

    override fun onPause() {
        super.onPause()
        crosswordView.state?.let { PuzzleManager.saveState(puzzleId, it) }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)

        crosswordView.restoreState(savedInstanceState.getParcelable("state", CrosswordState::class.java)!!)
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

    override fun onCrosswordChanged(view: CrosswordView) {}

    override fun onCrosswordSolved(view: CrosswordView) {
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
}