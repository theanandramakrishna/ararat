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
import org.akop.ararat.util.stripHtmlEntities
import org.json.JSONArray
import org.json.JSONObject

import java.io.IOException
import java.io.InputStream
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Formatter for the Guardian's crossword JSON, as embedded in the "data" prop of
 * the CrosswordComponent gu-island element on guardian.com crossword pages.
 * See https://github.com/jpd236/kotwords/blob/master/src/commonMain/kotlin/com/jeffpdavidson/kotwords/formats/Guardian.kt
 *
 * The grid is not stored explicitly; it is derived from [Entry] positions and
 * lengths. Cells not covered by any entry are blocks. Entry numbers are used
 * verbatim (they are not necessarily sequential). Entries may share cells;
 * overlapping entries must agree on their solutions.
 */
class GuardianJsonFormatter : CrosswordFormatter {

    override fun setEncoding(encoding: String) { }

    @Throws(IOException::class)
    override fun read(builder: Crossword.Builder, inputStream: InputStream) {
        val json = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = JSONObject(json)

        val dimensions = root.optJSONObject("dimensions")
                ?: throw FormatException("Missing 'dimensions'")
        val cols = dimensions.optInt("cols")
        val rows = dimensions.optInt("rows")
        if (cols <= 0 || rows <= 0) {
            throw FormatException("Invalid dimensions: ${cols}x${rows}")
        }

        val entriesJson = root.optJSONArray("entries")
                ?: throw FormatException("Missing 'entries'")

        builder.flags = 0
        builder.width = cols
        builder.height = rows
        builder.title = root.optString("name")
        builder.author = root.optJSONObject("creator")?.optString("name")
        builder.description = root.optString("instructions").stripHtmlEntities()

        // guardian.com carries the date as epoch millis; other publishers
        // using the same schema (e.g. MyCrossword.co.uk) use ISO 8601.
        when (val dateValue = root.opt("date")) {
            is Number -> builder.date = dateValue.toLong()
            is String -> builder.date = parseIsoDate(dateValue)
            else -> builder.date = 0L
        }

        val entries = (0 until entriesJson.length()).map { i ->
            Entry(entriesJson.optJSONObject(i)
                    ?: throw FormatException("Missing 'entries[$i]'"), cols, rows)
        }

        // Lay out the solutions into a sparse grid first, verifying that
        // entries which share a cell agree on its solution.
        val solutionAt = Array(rows) { arrayOfNulls<String>(cols) }
        for (entry in entries) {
            for (i in 0 until entry.length) {
                val r = if (entry.isAcross) entry.row else entry.row + i
                val c = if (entry.isAcross) entry.column + i else entry.column
                val solution = entry.solutionAt(i)
                val existing = solutionAt[r][c]
                when {
                    existing == null -> solutionAt[r][c] = solution
                    solution != null && !existing.equals(solution, ignoreCase = true) ->
                        throw FormatException(
                                "Solution mismatch at ($r, $c): '$existing' vs '$solution'")
                }
            }
        }

        for (entry in entries) {
            builder.words += buildWord {
                direction = entry.direction
                hint = entry.clue
                number = entry.number
                startRow = entry.row
                startColumn = entry.column

                for (i in 0 until entry.length) {
                    val r = if (entry.isAcross) entry.row else entry.row + i
                    val c = if (entry.isAcross) entry.column + i else entry.column
                    val solution = solutionAt[r][c]
                    when {
                        solution != null -> addCell(solution.uppercase())
                        else -> addCell("", Crossword.Cell.ATTR_NO_SOLUTION)
                    }
                }
            }
        }
    }

    private class Entry(entryObj: JSONObject, cols: Int, rows: Int) {

        val direction: Int
        val isAcross: Boolean
        val number: Int
        val clue: String
        val row: Int
        val column: Int
        val length: Int
        private val solution: String

        init {
            direction = when (val dir = entryObj.optString("direction")) {
                "across" -> Crossword.Word.DIR_ACROSS
                "down" -> Crossword.Word.DIR_DOWN
                else -> throw FormatException("Invalid direction: '$dir'")
            }
            isAcross = direction == Crossword.Word.DIR_ACROSS
            number = entryObj.optInt("number")
            clue = entryObj.optString("clue").stripHtmlEntities()
            val position = entryObj.optJSONObject("position")
                    ?: throw FormatException("Missing 'position' for entry $number")
            column = position.optInt("x")
            row = position.optInt("y")
            length = entryObj.optInt("length")
            solution = entryObj.optString("solution")

            if (number <= 0 || length <= 0) {
                throw FormatException("Invalid number/length for entry $number")
            }
            val endColumn = column + if (isAcross) length - 1 else 0
            val endRow = row + if (isAcross) 0 else length - 1
            if (column < 0 || row < 0 || endColumn >= cols || endRow >= rows) {
                throw FormatException("Entry $number is out of bounds")
            }
        }

        fun solutionAt(offset: Int): String? = solution.getOrNull(offset)?.toString()
    }

    companion object {
        private val ISO_DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)

        init {
            ISO_DATE_FORMAT.timeZone = TimeZone.getTimeZone("UTC")
        }

        /**
         * Parse an ISO 8601 date string, e.g. "2026-08-05T00:00:00.000Z".
         * Fractional seconds and the zone designator are ignored; the time
         * is interpreted as UTC.
         */
        private fun parseIsoDate(value: String): Long = try {
            val withoutFraction = value.substringBefore('.')
                    .removeSuffix("Z")
            ISO_DATE_FORMAT.parse(withoutFraction)?.time ?: 0L
        } catch (e: ParseException) {
            0L
        }
    }
}
