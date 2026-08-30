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

package org.akop.ararat.io

import org.akop.ararat.core.Crossword
import org.akop.ararat.core.buildWord
import org.json.JSONArray
import org.json.JSONObject

import java.io.IOException
import java.io.InputStream

/**
 * Formatter for the Cross With Friends game JSON ("cwfg") format. The stored
 * document is the `game` object carried by a game room's `create` event
 * (`params.game`):
 *
 *     { grid:   [[{black, value, number, parents:{across,down}}, ...], ...],
 *       solution: [["A", ...], ...],
 *       clues:   { across: ["", "clue", ...], down: [...] },
 *       info:    { title, author, copyright, description },
 *       circles: [flatIndex, ...] }
 *
 * Words are reconstructed from each cell's `parents.across` / `parents.down`,
 * which preserves CWF's per-cell clue numbering exactly (a clue number is
 * distinct from a definite-cell position in the grid). Cells not part of any
 * word in a direction (parents == 0) are omitted from that direction's list.
 */
class CwfGameJsonFormatter : CrosswordFormatter {

    override fun setEncoding(encoding: String) { }

    @Throws(IOException::class)
    override fun read(builder: Crossword.Builder, inputStream: InputStream) {
        val data = inputStream.readBytes().toString(Charsets.UTF_8)
        parse(builder, data)
    }

    private fun parse(builder: Crossword.Builder, data: String) {
        val game = JSONObject(data)
        val grid = game.optJSONArray("grid")
                ?: throw FormatException("Missing 'grid'")

        val height = grid.length()
        var width = 0
        for (r in 0 until height) {
            grid.optJSONArray(r)?.let { width = maxOf(width, it.length()) }
        }
        if (height == 0 || width == 0) {
            throw FormatException("Empty grid")
        }

        val solution = game.optJSONArray("solution")
        val info = game.optJSONObject("info")
        val clues = game.optJSONObject("clues")

        builder.flags = 0
        builder.width = width
        builder.height = height
        builder.title = info?.optString("title")?.ifEmpty { null }
        builder.author = info?.optString("author")?.ifEmpty { null }
        builder.copyright = info?.optString("copyright")?.ifEmpty { null }
        builder.description = info?.optString("description")
                ?.replace("\r\n", "\n")
                ?.replace("\r", "\n")
                ?.ifEmpty { null }

        val circular = BooleanArray(height * width)
        game.optJSONArray("circles")?.let { circles ->
            for (i in 0 until circles.length()) {
                val index = circles.optInt(i, -1)
                if (index in 0 until height * width) circular[index] = true
            }
        }

        // acrossParent/downParent[r][c] hold the clue number the cell belongs
        // to in each direction (0 = no word of length >= 2 in that direction).
        val acrossParent = Array(height) { IntArray(width) }
        val downParent = Array(height) { IntArray(width) }
        for (r in 0 until height) {
            val row = grid.optJSONArray(r) ?: continue
            for (c in 0 until minOf(row.length(), width)) {
                val cell = row.optJSONObject(c) ?: continue
                if (cell.optBoolean("black", true)) continue
                cell.optJSONObject("parents")?.let { parents ->
                    acrossParent[r][c] = parents.optInt("across")
                    downParent[r][c] = parents.optInt("down")
                }
            }
        }

        val acrossClues = clues?.optJSONArray("across")
        val downClues = clues?.optJSONArray("down")

        for (word in wordsByNumber(acrossParent, across = true)) {
            val (row, colLo, colHi, number) = word
            builder.words += buildWord {
                direction = Crossword.Word.DIR_ACROSS
                this.number = number
                hint = clue(acrossClues, number)
                startRow = row
                startColumn = colLo
                for (c in colLo..colHi) {
                    addCell(letter(solution, row, c), attrs(circular, row, c, width))
                }
            }
        }

        for (word in wordsByNumber(downParent, across = false)) {
            val (col, rowLo, rowHi, number) = word
            builder.words += buildWord {
                direction = Crossword.Word.DIR_DOWN
                this.number = number
                hint = clue(downClues, number)
                startRow = rowLo
                startColumn = col
                for (r in rowLo..rowHi) {
                    addCell(letter(solution, r, col), attrs(circular, r, col, width))
                }
            }
        }
    }

    /**
     * Merges adjacent cells sharing the same clue number into word runs.
     * For across, cells are grouped by (row, number); for down, by
     * (column, number). Returns runs as (lead, from, to, number) where
     * [from]/[to] span columns (across) or rows (down).
     */
    private fun wordsByNumber(parentMap: Array<IntArray>, across: Boolean): List<List<Int>> {
        val groups = mutableMapOf<Pair<Int, Int>, IntArray>()
        for (i in parentMap.indices) {
            for (j in 0 until parentMap[i].size) {
                val number = parentMap[i][j]
                if (number <= 0) continue
                val key = if (across) i to number else j to number
                val span = groups.getOrPut(key) { IntArray(2) { -1 } }
                val pos = if (across) j else i
                span[0] = if (span[0] < 0) pos else minOf(span[0], pos)
                span[1] = maxOf(span[1], pos)
            }
        }
        return groups
                .map { (key, span) -> listOf(key.first, span[0], span[1], key.second) }
                .sortedWith(compareBy({ it[0] }, { it[1] }))
    }

    private fun clue(clues: JSONArray?, number: Int): String? {
        if (clues == null) return null
        if (number >= 0 && number < clues.length()) {
            return clues.optString(number).ifEmpty { null }
        }
        return null
    }

    private fun letter(solution: JSONArray?, row: Int, col: Int): String {
        val rowArr = solution?.optJSONArray(row) ?: return ""
        if (col >= rowArr.length()) return ""
        val ch = rowArr.optString(col)
        return when {
            ch.isEmpty() -> ch
            ch[0] == '.' || ch[0] == '-' -> ""
            else -> ch.uppercase()
        }
    }

    private fun attrs(circular: BooleanArray, row: Int, col: Int, width: Int): Int =
            if (circular[row * width + col]) Crossword.Cell.ATTR_CIRCLED else 0
}