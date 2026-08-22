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
import org.akop.ararat.core.WordBuilder
import org.akop.ararat.core.buildWord

import java.io.IOException
import java.io.InputStream

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Formatter for HTML crossword grids as served by PA Puzzles embed pages
 * (e.g. https://www.pa-puzzles.com/puzzles/puzzlegroupembed.php?pid=...&cs=...),
 * used by irishnews.com puzzle pages. See also
 * https://gitlab.com/Hague/forkyz (PAPuzzlesIO).
 *
 * Format summary:
 * <div id="puzzle-grid">
 *   <div class="row">
 *     <div id="cell-{col}-{row}" class="cell blank"></div>          <!-- block -->
 *     <div id="cell-{col}-{row}" class="cell">
 *       <span class="cell-number">{number}</span>
 *       <div class="cell-input" data-solution="{solution}"/>
 *     </div>
 *
 * Clue lists live under .clues-across/.clues-down with li elements carrying
 * data-direction/data-cluenum/data-islink attributes. Cell indices are 1-based.
 *
 * Split clues ("2/14", direction "across-down") share one answer spanning two
 * directions; the full hint is attached to the first component only, and
 * continuation entries (data-islink="1") are skipped.
 */
class JsoupHtmlFormatter : CrosswordFormatter {

    override fun setEncoding(encoding: String) { }

    @Throws(IOException::class)
    override fun read(builder: Crossword.Builder, inputStream: InputStream) {
        val doc = Jsoup.parse(inputStream, Charsets.UTF_8.name(), "")

        val grid = doc.selectFirst("#puzzle-grid")
                ?: throw FormatException("No #puzzle-grid found")

        var maxRow = -1
        var maxCol = -1
        val solutions = HashMap<Pair<Int, Int>, String>()
        val numbers = HashMap<Pair<Int, Int>, Int>()

        for (cell in grid.select(".cell")) {
            if (cell.hasClass("blank")) continue

            val pos = parseCellPosition(cell) ?: continue
            maxRow = maxOf(maxRow, pos.first)
            maxCol = maxOf(maxCol, pos.second)

            cell.selectFirst(".cell-input")?.attr("data-solution")
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { solutions[pos] = it.trim() }

            cell.selectFirst(".cell-number")?.text()?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { it.toIntOrNull()?.takeIf { n -> n > 0 } }
                    ?.let { numbers[pos] = it }
        }

        if (maxRow < 0 || maxCol < 0) {
            throw FormatException("Empty grid")
        }

        builder.flags = 0
        builder.width = maxCol + 1
        builder.height = maxRow + 1

        val charMap = Array(builder.height) { CharArray(builder.width) { EMPTY } }
        val attrMap = Array(builder.height) { ByteArray(builder.width) }
        for ((pos, solution) in solutions) {
            val ch = solution.firstOrNull() ?: continue
            charMap[pos.first][pos.second] = ch.uppercaseChar()
        }

        val acrossClues = HashMap<Int, String>()
        val downClues = HashMap<Int, String>()
        readClues(doc, "clues-across", acrossClues)
        readClues(doc, "clues-down", downClues)

        buildWords(builder, charMap, attrMap, numbers, acrossClues, downClues)
    }

    private fun buildWords(cb: Crossword.Builder,
                           charMap: Array<CharArray>,
                           attrMap: Array<ByteArray>,
                           numbers: Map<Pair<Int, Int>, Int>,
                           acrossClues: Map<Int, String>,
                           downClues: Map<Int, String>) {
        val iend = charMap.lastIndex

        for (row in 0..iend) {
            val jend = charMap[row].lastIndex
            for (col in 0..jend) {
                val number = numbers[row to col] ?: continue

                if (WordBuilder.startsAcross(charMap, attrMap, row, col)) {
                    cb.words += buildWord {
                        direction = Crossword.Word.DIR_ACROSS
                        hint = acrossClues[number] ?: ""
                        this.number = number
                        startRow = row
                        startColumn = col

                        var k = col
                        while (k <= jend && charMap[row][k] != EMPTY) {
                            addCell(charMap[row][k].toString())
                            if (WordBuilder.endsAcross(attrMap, row, k, jend)) break
                            k++
                        }
                    }
                }

                if (WordBuilder.startsDown(charMap, attrMap, row, col)) {
                    cb.words += buildWord {
                        direction = Crossword.Word.DIR_DOWN
                        hint = downClues[number] ?: ""
                        this.number = number
                        startRow = row
                        startColumn = col

                        var k = row
                        while (k <= iend && charMap[k][col] != EMPTY) {
                            addCell(charMap[k][col].toString())
                            if (WordBuilder.endsDown(attrMap, k, col, iend)) break
                            k++
                        }
                    }
                }
            }
        }
    }

    private fun readClues(doc: Element, listClass: String,
                          clues: MutableMap<Int, String>) {
        val clueList = doc.selectFirst(".clues .$listClass") ?: return

        for (clue in clueList.select("li")) {
            if (clue.attr("data-islink") == "1") continue

            val directions = clue.attr("data-direction").lowercase().split("-")
            val primaryNumber = clue.attr("data-cluenum").split("/")
                    .firstNotNullOfOrNull { it.trim().toIntOrNull() } ?: continue
            val hint = clue.text()?.replaceFirst(CLUE_NUMBER_PREFIX_REGEX, "")
                    ?.trim() ?: continue
            if (hint.isEmpty()) continue

            when (directions.firstOrNull()) {
                "across" -> clues[primaryNumber] = hint
                "down" -> clues[primaryNumber] = hint
            }
        }
    }

    private fun parseCellPosition(cell: Element): Pair<Int, Int>? {
        val match = CELL_ID_REGEX.matchEntire(cell.id()) ?: return null
        val col = match.groupValues[1].toInt()
        val row = match.groupValues[2].toInt()
        if (col <= 0 || row <= 0) return null
        return (row - 1) to (col - 1)
    }

    companion object {
        private const val EMPTY = '.'

        private val CELL_ID_REGEX = Regex("cell-(\\d+)-(\\d+)")
        private val CLUE_NUMBER_PREFIX_REGEX = Regex("^\\s*[\\d/]+\\s*")
    }
}
