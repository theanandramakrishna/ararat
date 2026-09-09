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
import org.akop.ararat.util.stripHtmlEntities
import java.io.InputStream
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Reads the Puzzazz/BrainsOnly `ARCHIVE` text format served by the NYT
 * syndicated feed:
 *
 * ```
 * ARCHIVE
 *
 * <yyMMdd date>
 *
 * <title>
 *
 * <author, may embed <NOTEPAD>notes</NOTEPAD>>
 *
 * <width>
 *
 * <height>
 *
 * <across clue count>
 *
 * <down clue count>
 *
 * <height grid rows; #/. = block, % = circled next, ^ = shaded next,
 *  , = append next char to previous cell (rebus)>
 *
 * <across clues, one per line, blank-terminated>
 * <down clues, one per line, blank-terminated>
 * ```
 *
 * Words are numbered and clue-matched exactly like [PuzFormatter]: standard
 * numbering (runs of length >= 2), clues assigned to across/down starts in
 * row-major order. The two clue-count lines are redundant and ignored.
 * Shaded cells (`^`) have no slot in the model and are kept as plain letters.
 */
class NytArchiveFormatter : CrosswordFormatter {

    private var encoding = Charset.forName(DEFAULT_ENCODING)

    override fun setEncoding(encoding: String) {
        this.encoding = Charset.forName(encoding)
    }

    override fun read(builder: Crossword.Builder,
                      inputStream: InputStream) {
        val lines = inputStream.bufferedReader(encoding).use { it.readLines() }
                .map { it.trim() }
        var pos = 0
        fun next(): String =
                if (pos < lines.size) lines[pos++] else throw FormatException("Truncated archive")
        fun peek(): String? = lines.getOrNull(pos)

        if (lines.isEmpty() || lines[0] != "ARCHIVE") {
            throw FormatException("Not an NYT archive puzzle")
        }

        builder.date = try {
            DATE_FORMAT.parse(nextAt(lines, 2))?.time ?: 0
        } catch (e: Exception) {
            0
        }
        builder.title = nextAt(lines, 4).stripHtmlEntities()

        val authorLine = nextAt(lines, 6)
        val upper = authorLine.uppercase(Locale.US)
        val notesStart = upper.indexOf(NOTEPAD_START_TAG)
        if (notesStart >= 0) {
            val notesEnd = upper.indexOf(NOTEPAD_END_TAG, notesStart)
            builder.comment = authorLine.substring(
                    notesStart + NOTEPAD_START_TAG.length,
                    if (notesEnd >= 0) notesEnd else authorLine.length)
                    .stripHtmlEntities()
            builder.author = authorLine.substring(0, notesStart).stripHtmlEntities()
        } else {
            builder.author = authorLine.stripHtmlEntities()
        }

        val width = nextAt(lines, 8).toIntOrNull()
                ?: throw FormatException("Bad archive width")
        val height = nextAt(lines, 10).toIntOrNull()
                ?: throw FormatException("Bad archive height")
        if (width <= 0 || height <= 0) {
            throw FormatException("Puzzle has bad dimensions (${width}x${height})")
        }
        builder.width = width
        builder.height = height

        // Skip the blank/count/blank/count/blank separator block.
        pos = 16

        val solution = Array(height) { Array(width) { "" } }
        val blocked = Array(height) { BooleanArray(width) }
        val circled = Array(height) { BooleanArray(width) }
        for (r in 0 until height) {
            val line = next()
            var c = 0
            var i = 0
            var pendingCircle = false
            var appendNext = false
            while (i < line.length) {
                when (val ch = line[i]) {
                    '%' -> pendingCircle = true
                    '^' -> { /* shaded: no model slot, keep the letter */ }
                    ',' -> appendNext = true
                    else -> {
                        if (appendNext) {
                            if (c == 0) throw FormatException("Rebus append with no cell")
                            solution[r][c - 1] = solution[r][c - 1] + ch
                        } else {
                            if (c >= width) {
                                throw FormatException("Grid row too wide: \"$line\"")
                            }
                            if (ch == '#' || ch == '.') {
                                blocked[r][c] = true
                            } else {
                                solution[r][c] = ch.toString()
                                circled[r][c] = pendingCircle
                            }
                            c++
                        }
                        pendingCircle = false
                        appendNext = false
                    }
                }
                i++
            }
            if (c != width) {
                throw FormatException("Unexpected line length for width $width grid: \"$line\"")
            }
        }

        // Consume the blank separator, then blank-terminated clue sections.
        if (peek()?.isBlank() == true) pos++
        val acrossClues = mutableListOf<String>()
        while (peek()?.isNotBlank() == true) acrossClues.add(next().stripHtmlEntities())
        if (peek()?.isBlank() == true) pos++
        val downClues = mutableListOf<String>()
        while (peek()?.isNotBlank() == true) downClues.add(next().stripHtmlEntities())

        val charMap = Array(height) { r ->
            CharArray(width) { c ->
                if (blocked[r][c]) '.' else solution[r][c].firstOrNull() ?: '.'
            }
        }
        val attrMap = Array(height) { r ->
            ByteArray(width) { c ->
                if (circled[r][c]) Crossword.Cell.ATTR_CIRCLED.toByte() else 0
            }
        }

        var number = 0
        var acrossIdx = 0
        var downIdx = 0
        for (r in 0 until height) {
            for (c in 0 until width) {
                if (blocked[r][c]) continue
                var incremented = false
                if (WordBuilder.startsAcross(charMap, attrMap, r, c)) {
                    number++
                    incremented = true
                    val hint = acrossClues.getOrNull(acrossIdx++)
                            ?: throw FormatException("Missing across clue #${acrossIdx}")
                    builder.addWord(buildWord {
                        startColumn = c
                        startRow = r
                        this.number = number
                        direction = Crossword.Word.DIR_ACROSS
                        this.hint = hint
                        var k = c
                        while (k < width && !blocked[r][k]) {
                            addCell(solution[r][k],
                                    if (circled[r][k]) Crossword.Cell.ATTR_CIRCLED else 0)
                            if (WordBuilder.endsAcross(attrMap, r, k, width - 1)) break
                            k++
                        }
                    })
                }
                if (WordBuilder.startsDown(charMap, attrMap, r, c)) {
                    if (!incremented) number++
                    val hint = downClues.getOrNull(downIdx++)
                            ?: throw FormatException("Missing down clue #${downIdx}")
                    builder.addWord(buildWord {
                        startColumn = c
                        startRow = r
                        this.number = number
                        direction = Crossword.Word.DIR_DOWN
                        this.hint = hint
                        var k = r
                        while (k < height && !blocked[k][c]) {
                            addCell(solution[k][c],
                                    if (circled[k][c]) Crossword.Cell.ATTR_CIRCLED else 0)
                            if (WordBuilder.endsDown(attrMap, k, c, height - 1)) break
                            k++
                        }
                    })
                }
            }
        }
    }

    companion object {
        private const val DEFAULT_ENCODING = "ISO-8859-1"

        private const val NOTEPAD_START_TAG = "<NOTEPAD>"
        private const val NOTEPAD_END_TAG = "</NOTEPAD>"

        private val DATE_FORMAT = SimpleDateFormat("yyMMdd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        private fun nextAt(lines: List<String>, index: Int): String =
                lines.getOrNull(index)
                        ?: throw FormatException("Truncated archive header")
    }
}
