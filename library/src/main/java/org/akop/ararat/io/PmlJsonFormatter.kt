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
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Formatter for the JSON crossword format used by PML puzzles (e.g. Metro's
 * online crosswords). See https://gitlab.com/Hague/forkyz (PMLIO).
 *
 * The grid is not stored explicitly; it is derived from [Item]s: each item's
 * answer is laid out from its 1-based linear start index in the given
 * direction. Cells not covered by any item are blocks. Items which share a
 * cell must agree on its letter.
 *
 * The puzzle title is intentionally left unset - the embedded name is a
 * generic series label ("Cryptic Crossword").
 */
class PmlJsonFormatter : CrosswordFormatter {

    override fun setEncoding(encoding: String) { }

    @Throws(IOException::class)
    override fun read(builder: Crossword.Builder, inputStream: InputStream) {
        val json = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = JSONObject(json)

        val gameData = root.optJSONObject("game_data")
                ?: throw FormatException("Missing 'game_data'")
        val cols = gameData.optInt("cols")
        val rows = gameData.optInt("rows")
        if (cols <= 0 || rows <= 0) {
            throw FormatException("Invalid dimensions: ${cols}x${rows}")
        }

        val itemsJson = gameData.optJSONArray("items")
                ?: throw FormatException("Missing 'items'")

        builder.flags = 0
        builder.width = cols
        builder.height = rows

        val publishDate = root.optString("rdate")
        if (publishDate.isNotEmpty()) {
            try {
                builder.date = PUBLISH_DATE_FORMAT.parse(publishDate)?.time ?: 0
            } catch (e: Exception) {
                // oh well
            }
        }

        val items = (0 until itemsJson.length()).map { i ->
            Item(itemsJson.optJSONObject(i)
                    ?: throw FormatException("Missing 'items[$i]'"), cols, rows)
        }

        // Lay out the answers into a sparse grid first, verifying that items
        // which share a cell agree on its letter.
        val letterAt = Array(rows) { arrayOfNulls<Char>(cols) }
        for (item in items) {
            for ((offset, ch) in item.answer.withIndex()) {
                val r = if (item.isAcross) item.row else item.row + offset
                val c = if (item.isAcross) item.column + offset else item.column
                if (r >= rows || c >= cols) {
                    throw FormatException("Item ${item.number} is out of bounds")
                }

                val existing = letterAt[r][c]
                val letter = ch.uppercaseChar()
                when {
                    existing == null -> letterAt[r][c] = letter
                    existing != letter -> throw FormatException(
                            "Letter mismatch at ($r, $c): '$existing' vs '$letter'")
                }
            }
        }

        for (item in items) {
            builder.words += buildWord {
                direction = item.direction
                hint = item.clue
                number = item.number
                startRow = item.row
                startColumn = item.column

                for (offset in item.answer.indices) {
                    val r = if (item.isAcross) item.row else item.row + offset
                    val c = if (item.isAcross) item.column + offset else item.column
                    addCell(letterAt[r][c].toString())
                }
            }
        }
    }

    private class Item(itemObj: JSONObject, cols: Int, rows: Int) {

        val direction: Int
        val isAcross: Boolean
        val number: Int
        val clue: String
        val row: Int
        val column: Int
        val answer: String

        init {
            direction = when (itemObj.optInt("dir")) {
                0 -> Crossword.Word.DIR_ACROSS
                1 -> Crossword.Word.DIR_DOWN
                else -> throw FormatException(
                        "Invalid direction for item ${itemObj.optInt("num")}")
            }
            isAcross = direction == Crossword.Word.DIR_ACROSS
            number = itemObj.optInt("num")
            clue = itemObj.optString("clue")
            answer = itemObj.optString("answer")

            val start = itemObj.optInt("start") - 1
            if (number <= 0 || start < 0 || start >= rows * cols) {
                throw FormatException("Invalid bounds for item $number")
            }
            column = start % cols
            row = start / cols

            if (answer.isEmpty()) {
                throw FormatException("Missing answer for item $number")
            }
        }
    }

    companion object {
        internal val PUBLISH_DATE_FORMAT = SimpleDateFormat("d MMM yyyy", Locale.US)
    }
}
