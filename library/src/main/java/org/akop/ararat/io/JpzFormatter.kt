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
import org.akop.ararat.core.CrosswordState
import org.akop.ararat.core.buildWord

import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.zip.ZipInputStream

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser

/**
 * Formatter for the JPZ (Crossword Compiler XML) format. See
 * https://gitlab.com/Hague/forkyz (JPZIO).
 *
 * Supported: metadata, rectangular grids with block/void cells, solutions,
 * explicit cell numbers, circles, bar edges, publisher solve-state (exposed
 * via [startState]), multiple clue lists with word zones (ranges and cell
 * lists), clue "format" suffixes, "citation" notes, completion messages, and
 * link-clue pairs (continuations are skipped).
 *
 * Intentionally unsupported: background colors, corner number marks, arrow
 * shapes, acrostic puzzles, and clue lists whose zones are non-linear.
 * ZIP-wrapped files are unwrapped transparently.
 */
class JpzFormatter : CrosswordFormatter {

    /**
     * Publisher-provided starting letters from solve-state attributes,
     * available after [read] when the puzzle declares any.
     */
    var startState: CrosswordState? = null
        private set

    override fun setEncoding(encoding: String) { }

    @Throws(JpzException::class)
    override fun read(builder: Crossword.Builder, inputStream: java.io.InputStream) {
        val xmlBytes = unwrapZip(inputStream.readBytes())
        val doc = Jsoup.parse(xmlBytes.inputStream(), null, "", Parser.xmlParser())

        val puzzleEl = doc.selectFirst("rectangular-puzzle")
                ?: throw JpzException("Missing 'rectangular-puzzle'")

        readMetadata(builder, puzzleEl)

        val gridEl = puzzleEl.selectFirst("grid")
                ?: throw JpzException("Missing 'grid'")
        val cols = gridEl.attr("width").toIntOrNull()
                ?: throw JpzException("Invalid grid width")
        val rows = gridEl.attr("height").toIntOrNull()
                ?: throw JpzException("Invalid grid height")
        if (cols <= 0 || rows <= 0) {
            throw JpzException("Invalid dimensions: ${cols}x${rows}")
        }

        builder.flags = 0
        builder.width = cols
        builder.height = rows

        val grid = Grid(cols, rows)
        for (cellEl in gridEl.select("cell")) {
            grid.parseCell(cellEl)
        }

        startState = grid.buildStartState()

        val zones = readZones(puzzleEl)
        val citations = mutableListOf<String>()
        for (cluesEl in puzzleEl.select("clues")) {
            readClueList(builder, cluesEl, grid, zones, citations)
        }

        readCompletion(puzzleEl, builder, citations)

        builder.setAlphabet(grid.usedAlphabet())
    }

    private class JpzException(message: String) : IOException(message)

    private fun readMetadata(builder: Crossword.Builder, puzzleEl: Element) {
        puzzleEl.selectFirst("metadata")?.let { metadata ->
            builder.title = metadata.selectFirst("title")?.text()?.trim()
            builder.author = metadata.selectFirst("creator")?.text()?.trim()
            builder.copyright = metadata.selectFirst("copyright")?.text()?.trim()
            builder.description = metadata.selectFirst("description")?.text()?.trim()
        }
    }

    private fun readCompletion(
            puzzleEl: Element,
            builder: Crossword.Builder,
            citations: List<String>,
    ) {
        val parts = mutableListOf<String>()
        puzzleEl.selectFirst("completion")?.text()?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { parts.add(it) }
        parts.addAll(citations)
        if (parts.isNotEmpty()) {
            builder.comment = parts.joinToString("\n\n")
        }
    }

    private fun unwrapZip(bytes: ByteArray): ByteArray {
        if (bytes.size < 4 || bytes[0] != 0x50.toByte() || bytes[1] != 0x4B.toByte()) {
            return bytes
        }
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) return zip.readBytes()
                entry = zip.nextEntry
            }
        }
        throw JpzException("ZIP container held no entries")
    }

    private class CellData {
        var solution: Char? = null
        var isBlockCell: Boolean = false
        var attrFlags: Int = 0
        var startLetter: Char? = null
    }

    private class Grid(val cols: Int, val rows: Int) {

        val cells = Array(rows) { arrayOfNulls<CellData>(cols) }
        private var startCount = 0

        fun parseCell(cellEl: Element) {
            val x = cellEl.attr("x").toIntOrNull() ?: return
            val y = cellEl.attr("y").toIntOrNull() ?: return
            val col = x - 1
            val row = y - 1
            if (col < 0 || row < 0 || col >= cols || row >= rows) return

            val cell = CellData()

            when (cellEl.attr("type").lowercase()) {
                "block", "void", "clue" -> cell.isBlockCell = true
            }

            cellEl.attr("solution").trim().takeIf { it.isNotEmpty() }?.let {
                if (!cell.isBlockCell) cell.solution = it.uppercase().firstOrNull()
            }

            cellEl.attr("solve-state").trim().takeIf { it.isNotEmpty() }?.let {
                if (!cell.isBlockCell) {
                    cell.startLetter = it.uppercase().firstOrNull()
                    startCount++
                }
            }

            cellEl.attr("number").takeIf { it.isNotEmpty() }?.let {
                cell.attrFlags = cell.attrFlags or NUMBERED_MARKER
            }

            if ("circle".equals(cellEl.attr("background-shape"), ignoreCase = true)) {
                cell.attrFlags = cell.attrFlags or Crossword.Cell.ATTR_CIRCLED
            }

            var bars = 0
            if (cellEl.attr("top-bar").equals("true", true))
                bars = bars or Crossword.Cell.ATTR_BAR_TOP
            if (cellEl.attr("bottom-bar").equals("true", true))
                bars = bars or Crossword.Cell.ATTR_BAR_BOTTOM
            if (cellEl.attr("left-bar").equals("true", true))
                bars = bars or Crossword.Cell.ATTR_BAR_LEFT
            if (cellEl.attr("right-bar").equals("true", true))
                bars = bars or Crossword.Cell.ATTR_BAR_RIGHT
            cell.attrFlags = cell.attrFlags or bars

            cells[row][col] = cell
        }

        /** Marks numbered starts so geometry-fallback runs can find them. */
        fun isNumbered(row: Int, col: Int): Boolean =
                cells[row][col]?.attrFlags?.and(NUMBERED_MARKER) != 0

        fun usedAlphabet(): Set<Char> {
            val alphabet = HashSet(Crossword.ALPHABET_ENGLISH)
            for (rowCells in cells) for (cell in rowCells) {
                cell?.solution?.let { alphabet.add(it) }
            }
            return alphabet
        }

        fun buildStartState(): CrosswordState? {
            if (startCount == 0) return null
            val state = CrosswordState(cols, rows)
            for (row in 0 until rows) for (col in 0 until cols) {
                cells[row][col]?.startLetter?.let {
                    state.setCharAt(row, col, it.toString())
                }
            }
            return state
        }

        companion object {
            const val NUMBERED_MARKER = 64
            val NUMBERED_MARKER_INV = NUMBERED_MARKER.inv()
        }

        fun cellAt(row: Int, col: Int): CellData? =
                cells.getOrNull(row)?.getOrNull(col)

        /**
         * Fallback for clues without word zones: extend from the numbered
         * cell while the title gives a direction and cells remain open.
         */
        fun findRunFromNumber(
                number: Int,
                listTitle: String,
        ): List<Pair<Int, Int>>? {
            val title = listTitle.lowercase()
            val across = when {
                "across" in title -> true
                "down" in title -> false
                else -> return null
            }
            for (row in 0 until rows) for (col in 0 until cols) {
                if (!isNumbered(row, col)) continue
                val positions = mutableListOf<Pair<Int, Int>>()
                if (across) {
                    var c = col
                    while (c < cols && cellAt(row, c)?.isBlockCell != true) {
                        positions.add(row to c); c++
                    }
                } else {
                    var r = row
                    while (r < rows && cellAt(r, col)?.isBlockCell != true) {
                        positions.add(r to col); r++
                    }
                }
                if (positions.size >= 2) return positions
                positions.clear()
            }
            return null
        }
    }

    private fun readZones(puzzleEl: Element): Map<String, List<Pair<Int, Int>>> {
        val zones = mutableMapOf<String, List<Pair<Int, Int>>>()
        for (wordEl in puzzleEl.select("word")) {
            val id = wordEl.attr("id")
            if (id.isEmpty()) continue

            val positions = mutableListOf<Pair<Int, Int>>()
            collectRange(wordEl, positions)
            for (cellsEl in wordEl.select("cells")) {
                collectRange(cellsEl, positions)
            }
            if (positions.isNotEmpty()) zones[id] = positions
        }
        return zones
    }

    private fun collectRange(element: Element, out: MutableList<Pair<Int, Int>>) {
        fun expand(axis: String): IntRange? {
            val parts = axis.split("-")
            val start = parts[0].toIntOrNull() ?: return null
            val end = parts.getOrNull(1)?.toIntOrNull() ?: start
            return if (end >= start) start..end else null
        }

        val xs = expand(element.attr("x")) ?: return
        val ys = expand(element.attr("y")) ?: return
        // 1-based inclusive ranges.
        for (y in ys) for (x in xs) out.add((y - 1) to (x - 1))
    }

    private fun readClueList(
            builder: Crossword.Builder,
            cluesEl: Element,
            grid: Grid,
            zones: Map<String, List<Pair<Int, Int>>>,
            citations: MutableList<String>,
    ) {
        val listTitle = cluesEl.selectFirst("title")?.text()?.trim() ?: ""

        for (clueEl in cluesEl.select("clue")) {
            // Link continuations repeat another clue's answer; skip them.
            if (clueEl.attr("is-link").isNotEmpty()) continue

            val number = clueEl.attr("number").toIntOrNull() ?: continue
            var hint = clueEl.text().trim()
            clueEl.attr("format").takeIf { it.isNotEmpty() }?.let { hint = "$hint ($it)" }
            clueEl.attr("citation").takeIf { it.isNotEmpty() }?.let {
                citations.add("$listTitle $number: ${it.trim()}")
            }

            val zoneId = clueEl.attr("word")
            val zone = zones[zoneId]
                    ?: run {
                        // No zone: fall back to a straight run from the
                        // numbered cell when the list title names a direction.
                        grid.findRunFromNumber(number, listTitle)
                    }
            if (zone == null) continue

            val direction = resolveDirection(listTitle, zone)
            val seen = HashSet<Pair<Int, Int>>()

            builder.words += buildWord {
                this.direction = direction
                this.hint = hint
                this.number = number
                startRow = zone.first().first
                startColumn = zone.first().second

                for ((r, c) in zone) {
                    if (!seen.add(r to c)) continue
                    val cell = grid.cellAt(r, c)
                    val attrs = (cell?.attrFlags ?: 0) and Grid.NUMBERED_MARKER_INV
                    when {
                        cell == null -> addCell("", Crossword.Cell.ATTR_NO_SOLUTION)
                        cell.solution != null ->
                            addCell(cell.solution!!.toString(), attrs)
                        else -> addCell("", attrs or Crossword.Cell.ATTR_NO_SOLUTION)
                    }
                }
            }
        }
    }

    private fun resolveDirection(listTitle: String, zone: List<Pair<Int, Int>>): Int {
        val title = listTitle.lowercase()
        if ("across" in title) return Crossword.Word.DIR_ACROSS
        if ("down" in title) return Crossword.Word.DIR_DOWN

        if (zone.size > 1) {
            val distinctRows = zone.map { it.first }.distinct().size
            val distinctCols = zone.map { it.second }.distinct().size
            if (distinctRows == 1) return Crossword.Word.DIR_ACROSS
            if (distinctCols == 1) return Crossword.Word.DIR_DOWN
        }
        throw JpzException("Non-linear or ambiguous clue zone in '$listTitle'")
    }

}