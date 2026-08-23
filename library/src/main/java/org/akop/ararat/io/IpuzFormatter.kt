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
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Formatter for the IPuz JSON format (http://www.ipuz.org/), v1/v2 crossword
 * puzzles. See https://gitlab.com/Hague/forkyz (IPuzIO).
 *
 * Supported: dimensions; puzzle grid with "#" blocks and "0"/empty blanks;
 * optional "solution" grid overlay; named/per-cell styles limited to circles
 * (shapebg=circle) and solid bars (barred "TBLR"); metadata title/author/
 * copyright/publisher/intro/notes; clue lists keyed "Across"/"Down" (other
 * list names fall back to geometry inference); explicit clue "cells" zones;
 * solution checksums, colors, marks, labels, enumerations and non-linear
 * zones are ignored or rejected.
 */
class IpuzFormatter : CrosswordFormatter {

    override fun setEncoding(encoding: String) { }

    @Throws(IOException::class)
    override fun read(builder: Crossword.Builder, inputStream: InputStream) {
        val root = JSONObject(inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })

        checkKind(root)

        val dimensions = root.optJSONObject("dimensions")
                ?: throw FormatException("Missing 'dimensions'")
        val cols = dimensions.optInt("width")
        val rows = dimensions.optInt("height")
        if (cols <= 0 || rows <= 0) {
            throw FormatException("Invalid dimensions: ${cols}x${rows}")
        }

        builder.flags = 0
        builder.width = cols
        builder.height = rows

        readMetadata(root, builder)

        val styles = readNamedStyles(root)
        val blockStr = root.optString("block", "#")
        val emptyStr = root.optString("empty", "0")

        // Open-cell letter sources: "puzzle" grid plus optional "solution"
        // overlay (prize puzzles ship the former without answers).
        val isBlockCell = Array(rows) { BooleanArray(cols) }
        val letterAt = Array(rows) { arrayOfNulls<String>(cols) }
        val attrAt = Array(rows) { IntArray(cols) }
        val startLetters = mutableMapOf<Pair<Int, Int>, String>()

        readGrid(root.getJSONArray("puzzle"), cols, rows, styles, blockStr, emptyStr,
                isBlockCell, attrAt, letterAt, startLetters, builder)
        root.optJSONArray("solution")?.let { solution ->
            readSolutionOverlay(solution, cols, rows, letterAt, blockStr)
        }

        buildStartState(startLetters, builder)

        val numbering = computeStandardNumbering(isBlockCell, attrAt, cols, rows)

        val cluesVal = root.opt("clues")
                ?: throw FormatException("Missing 'clues'")
        when (cluesVal) {
            is JSONObject -> {
                for (listName in cluesVal.keys()) {
                    val list = cluesVal.optJSONArray(listName) ?: continue
                    readNamedClueList(builder, listName, list, numbering,
                            isBlockCell, attrAt, letterAt, cols, rows)
                }
            }
            is JSONArray -> {
                // Legacy layout: first array holds Across clues, second Down.
                val names = listOf("Across", "Down")
                for (i in 0 until cluesVal.length()) {
                    val list = cluesVal.optJSONArray(i) ?: continue
                    val listName = names.getOrElse(i) { "Clues $i" }
                    readNamedClueList(builder, listName, list, numbering,
                            isBlockCell, attrAt, letterAt, cols, rows)
                }
            }
            else -> throw FormatException("Unsupported 'clues' type")
        }
    }

    private fun checkKind(root: JSONObject) {
        val kinds = root.optJSONArray("kind")
                ?: throw FormatException("Missing 'kind'")
        var supported = false
        for (i in 0 until kinds.length()) {
            if (kinds.optString(i).lowercase().startsWith(KIND_CROSSWORD_PREFIX)) {
                supported = true
            }
        }
        if (!supported) throw FormatException(
                "Unsupported ipuz kind: ${root.optJSONArray("kind")}")
    }

    private fun readMetadata(root: JSONObject, builder: Crossword.Builder) {
        builder.title = root.optString("title").ifEmpty { null }
        builder.author = root.optString("author").ifEmpty { null }
        builder.copyright = root.optString("publisher").ifEmpty {
            root.optString("copyright").ifEmpty { null }
        }
        builder.description = listOf("intro", "notes", "explanation")
                .map { root.optString(it) }
                .firstOrNull { it.isNotEmpty() }
                ?.ifEmpty { null }

        val date = root.optString("date")
        if (date.isNotEmpty()) {
            for (pattern in DATE_PATTERNS) {
                try {
                    builder.date = pattern.parse(date)?.time ?: continue
                    break
                } catch (e: ParseException) {
                    // try the next pattern
                }
            }
        }
    }

    private fun readNamedStyles(root: JSONObject): Map<String, JSONObject> {
        val out = mutableMapOf<String, JSONObject>()
        root.optJSONObject("styles")?.let { styles ->
            for (name in styles.keys()) {
                styles.optJSONObject(name)?.let { out[name] = it }
            }
        }
        return out
    }

    private fun readGrid(
            puzzle: JSONArray,
            cols: Int,
            rows: Int,
            styles: Map<String, JSONObject>,
            blockStr: String,
            emptyStr: String,
            isBlockCell: Array<BooleanArray>,
            attrAt: Array<IntArray>,
            letterAt: Array<Array<String?>>,
            startLetters: MutableMap<Pair<Int, Int>, String>,
            builder: Crossword.Builder,
    ) {
        if (puzzle.length() < rows) {
            throw FormatException("Puzzle row count mismatch")
        }

        val alphabet = HashSet(Crossword.ALPHABET_ENGLISH)

        for (r in 0 until rows) {
            val rowCells = puzzle.optJSONArray(r)
                    ?: throw FormatException("Missing puzzle row $r")
            if (rowCells.length() < cols) {
                throw FormatException("Puzzle column mismatch at row $r")
            }

            for (c in 0 until cols) {
                val raw = rowCells.opt(c)
                val cellObj = raw as? JSONObject
                val innerRaw = cellObj?.opt("cell") ?: raw

                // Resolve the display/solution value of this cell.
                when {
                    innerRaw == null -> isBlockCell[r][c] = true
                    innerRaw.toString() == blockStr -> isBlockCell[r][c] = true
                    innerRaw.toString() == emptyStr || innerRaw.toString().isEmpty() -> {}
                    else -> {
                        val letter = innerRaw.toString()
                        if (letter.toIntOrNull() == null) {
                            letterAt[r][c] = letter.uppercase()
                            alphabet.addAll(letter.uppercase().toCharArray().toList())
                        } else {
                            // Numeric cells are numbered blanks.
                        }
                    }
                }

                // Style: inline object or named reference.
                val style = (raw as? JSONObject)?.opt("style")?.let {
                    when (it) {
                        is JSONObject -> it
                        is String -> styles[it]
                        else -> null
                    }
                }

                if (style != null) {
                    var attrs = 0
                    if ("circle".equals(style.optString("shapebg"), true)) {
                        attrs = attrs or Crossword.Cell.ATTR_CIRCLED
                    }
                    style.optString("barred").uppercase().forEach { ch ->
                        when (ch) {
                            'T' -> attrs = attrs or Crossword.Cell.ATTR_BAR_TOP
                            'B' -> attrs = attrs or Crossword.Cell.ATTR_BAR_BOTTOM
                            'L' -> attrs = attrs or Crossword.Cell.ATTR_BAR_LEFT
                            'R' -> attrs = attrs or Crossword.Cell.ATTR_BAR_RIGHT
                        }
                    }
                    attrAt[r][c] = attrs

                    // Publisher-provided prefilled letters.
                    style.optString("label").takeIf { it.isNotEmpty() }?.let {
                        startLetters[r to c] = it.uppercase()
                    }
                }

                cellObj?.optString("value")?.takeIf { it.isNotEmpty() }?.let {
                    startLetters[r to c] = it.uppercase()
                }
            }
        }

        builder.setAlphabet(alphabet)
    }

    private fun readSolutionOverlay(
            solution: JSONArray,
            cols: Int,
            rows: Int,
            letterAt: Array<Array<String?>>,
            blockStr: String,
    ) {
        for (r in 0 until minOf(rows, solution.length())) {
            val row = solution.optJSONArray(r) ?: continue
            for (c in 0 until minOf(cols, row.length())) {
                val cell = row.optString(c)
                if (cell.isNotEmpty() && cell != blockStr && cell != "0") {
                    letterAt[r][c] = cell.uppercase()
                }
            }
        }
    }

    private fun buildStartState(
            startLetters: Map<Pair<Int, Int>, String>,
            builder: Crossword.Builder,
    ) {
        // Exposed through JpzFormatter-style seams when needed; ipuz "label"
        // and "value" prefills land here in a future revision.
    }

    private fun computeStandardNumbering(
            isBlockCell: Array<BooleanArray>,
            attrAt: Array<IntArray>,
            cols: Int,
            rows: Int,
    ): Map<Int, Pair<Int, Int>> {
        val numbering = mutableMapOf<Int, Pair<Int, Int>>()
        var number = 0

        fun isOpen(r: Int, c: Int) =
                r in 0 until rows && c in 0 until cols && !isBlockCell[r][c]

        fun hasBarBetween(r1: Int, c1: Int, r2: Int, c2: Int): Boolean {
            // A bar on either side of the shared edge separates the cells.
            return when {
                r1 == r2 && c2 == c1 + 1 ->
                    (attrAt[r1][c1] and Crossword.Cell.ATTR_BAR_RIGHT) != 0 ||
                            (attrAt[r2][c2] and Crossword.Cell.ATTR_BAR_LEFT) != 0
                c1 == c2 && r2 == r1 + 1 ->
                    (attrAt[r1][c1] and Crossword.Cell.ATTR_BAR_BOTTOM) != 0 ||
                            (attrAt[r2][c2] and Crossword.Cell.ATTR_BAR_TOP) != 0
                else -> false
            }
        }

        for (r in 0 until rows) for (c in 0 until cols) {
            if (!isOpen(r, c)) continue

            val startsAcross = !isOpen(r, c - 1) ||
                    hasBarBetween(r, c - 1, r, c)
            val startsDown = !isOpen(r - 1, c) ||
                    hasBarBetween(r - 1, c, r, c)
            val runLengthAcross = runLength(r, c, 0, 1, ::isOpen, ::hasBarBetween, cols, rows)
            val runLengthDown = runLength(r, c, 1, 0, ::isOpen, ::hasBarBetween, cols, rows)

            if ((startsAcross && runLengthAcross >= 2) ||
                    (startsDown && runLengthDown >= 2)) {
                number++
                numbering[number] = r to c
            }
        }
        return numbering
    }

    private fun runLength(
            row: Int,
            col: Int,
            dr: Int,
            dc: Int,
            isOpen: (Int, Int) -> Boolean,
            hasBarBetween: (Int, Int, Int, Int) -> Boolean,
            cols: Int,
            rows: Int,
    ): Int {
        var length = 0
        var r = row
        var c = col
        while (isOpen(r, c)) {
            length++
            val nr = r + dr
            val nc = c + dc
            if (!isOpen(nr, nc) || hasBarBetween(r, c, nr, nc)) break
            r = nr; c = nc
        }
        return length
    }

    private fun readNamedClueList(
            builder: Crossword.Builder,
            listName: String,
            clues: JSONArray,
            numbering: Map<Int, Pair<Int, Int>>,
            isBlockCell: Array<BooleanArray>,
            attrAt: Array<IntArray>,
            letterAt: Array<Array<String?>>,
            cols: Int,
            rows: Int,
    ) {
        val preferAcross = "across" in listName.lowercase()
        val preferDown = "down" in listName.lowercase()

        for (i in 0 until clues.length()) {
            val clueObj = clues.optJSONObject(i) ?: continue

            // Continued entries repeat another clue's answer; skip them.
            if (clueObj.has("continued")) continue

            val number = clueObj.optString("number").toIntOrNull() ?: continue
            val hint = clueObj.optString("clue").trim()
            val cells = clueObj.optJSONArray("cells")

            val positions = mutableListOf<Pair<Int, Int>>()
            if (cells != null && cells.length() > 0) {
                for (j in 0 until cells.length()) {
                    val pair = cells.optJSONArray(j) ?: continue
                    val r = pair.optInt(0)
                    val c = pair.optInt(1)
                    positions.add(r to c)
                }
                val distinctRows = positions.map { it.first }.distinct().size
                val distinctCols = positions.map { it.second }.distinct().size
                val linear = positions.size <= 1 || distinctRows == 1 || distinctCols == 1
                if (!linear) throw FormatException(
                        "Non-linear clue zone in '$listName' $number")
            } else {
                val start = numbering[number]
                        ?: throw FormatException(
                                "No cell numbered $number for clue in '$listName'")
                val isAcross = if (preferAcross || preferDown) preferAcross else true
                val (dr, dc) = if (isAcross) 0 to 1 else 1 to 0
                var r = start.first
                var c = start.second
                while (r in 0 until rows && c in 0 until cols && !isBlockCell[r][c]) {
                    positions.add(r to c)
                    val nr = r + dr
                    val nc = c + dc
                    if (nr !in 0 until rows || nc !in 0 until cols ||
                            isBlockCell.getOrNull(nr)?.getOrNull(nc) == true) break
                    r = nr; c = nc
                }
            }

            val direction = when {
                positions.size > 1 -> {
                    val distinctRows = positions.map { it.first }.distinct().size
                    if (distinctRows == 1) Crossword.Word.DIR_ACROSS
                    else Crossword.Word.DIR_DOWN
                }
                preferDown -> Crossword.Word.DIR_DOWN
                else -> Crossword.Word.DIR_ACROSS
            }
            val ordered = if (direction == Crossword.Word.DIR_ACROSS)
                positions.sortedWith(compareBy({ it.first }, { it.second }))
            else
                positions.sortedWith(compareBy({ it.first }, { it.second }))

            builder.words += buildWord {
                this.direction = direction
                this.hint = hint
                this.number = number
                startRow = ordered.first().first
                startColumn = ordered.first().second

                for ((r, c) in ordered) {
                    val letter = letterAt.getOrNull(r)?.getOrNull(c)
                    if (letter != null) {
                        addCell(letter, attrAt[r][c])
                    } else {
                        addCell("", Crossword.Cell.ATTR_NO_SOLUTION)
                    }
                }
            }
        }
    }

    companion object {
        private val KIND_CROSSWORD_PREFIX = "http://ipuz.org/crossword"

        private val DATE_PATTERNS = listOf(
                SimpleDateFormat("MM/dd/yyyy", Locale.US),
                SimpleDateFormat("dd/MM/yyyy", Locale.US),
                SimpleDateFormat("M/d/yyyy", Locale.US))
    }
}
