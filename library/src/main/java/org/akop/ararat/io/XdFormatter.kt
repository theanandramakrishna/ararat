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
import org.akop.ararat.core.WordBuilder
import org.akop.ararat.core.buildWord

import java.io.IOException
import java.io.InputStream

/**
 * Formatter for the .xd text format, as used by the New Yorker's puzzles
 * platform. See https://github.com/century-arcade/xd/blob/master/doc/xd-format.md
 * and https://github.com/jpd236/kotwords/blob/master/src/commonMain/kotlin/com/jeffpdavidson/kotwords/formats/Xd.kt
 *
 * Supported sections: Metadata, Grid, Clues, Design (bars/circles), Start
 * (prefilled cells), Notes, and Help.
 */
class XdFormatter : CrosswordFormatter {

    /**
     * Prefilled cells declared in the Start section, available after [read].
     */
    var startState: CrosswordState? = null
        private set

    override fun setEncoding(encoding: String) { }

    @Throws(IOException::class)
    override fun read(builder: Crossword.Builder, inputStream: InputStream) {
        val data = inputStream.readBytes().toString(Charsets.UTF_8)
        parse(builder, data)
    }

    private fun parse(builder: Crossword.Builder, data: String) {
        val sections = mutableMapOf<String, MutableList<String>>()
        var currentSection = mutableListOf<String>()
        for (line in data.lines()) {
            if (line.startsWith("## ")) {
                val name = line.substring(3).trim().lowercase()
                currentSection = sections.getOrPut(name) { mutableListOf() }
            } else if (line.isNotBlank()) {
                currentSection.add(line.trim())
            }
        }

        val metadata = sections.getOrElse("metadata") { emptyList() }
                .map { it.split(": ", limit = 2) }
                .filter { it.size == 2 }
                .associate { it[0].trim() to it[1].trim() }
                .mapValues { (_, value) -> if (value == "N/A") "" else value }

        val acrossClues = mutableMapOf<Int, String>()
        val downClues = mutableMapOf<Int, String>()
        for (line in sections.getOrElse("clues") { emptyList() }) {
            val match = CLUE_REGEX.find(line) ?: continue
            val text = match.groupValues[3].substringBeforeLast(" ~ ").trim()
            if (match.groupValues[1] == "A") {
                acrossClues[match.groupValues[2].toInt()] = text
            } else {
                downClues[match.groupValues[2].toInt()] = text
            }
        }

        val description = (listOf(
                metadata["description"],
                sections.getOrElse("notes") { emptyList() }.joinToString("\n"),
                sections.getOrElse("help") { emptyList() }.joinToString("\n"))
                .firstOrNull { !it.isNullOrBlank() } ?: "")
                .takeIf { it.isNotBlank() }
                ?: metadata["subtitle"]

        val rebusMap = (metadata["rebus"] ?: "")
                .split(" ")
                .filter { it.isNotBlank() }
                .associate {
                    it.substringBefore('=') to it.substringAfter('=')
                }

        val grid = sections.getOrElse("grid") { emptyList() }
        val height = grid.size
        val width = grid.map { it.length }.maxOrNull() ?: 0
        if (height == 0 || width == 0) {
            throw FormatException("Empty grid")
        }

        // Parse the Design section: a <style> block mapping a character to CSS-like
        // properties, followed by a grid of the same dimensions as the puzzle in
        // which each cell is marked with the style character to apply to it.
        val styles = mutableMapOf<Char, Map<String, String>>()
        var styleRows = emptyList<String>()
        val design = sections.getOrElse("design") { emptyList() }
        if (design.isNotEmpty()) {
            val styleSection = design.joinToString("\n")
            for (style in STYLE_REGEX.findAll(styleSection)) {
                styles[style.groupValues[1][0]] = style.groupValues[2]
                        .trim()
                        .split(";\\s*".toRegex())
                        .mapNotNull {
                            val key = it.substringBefore(':').trim()
                            val value = it.substringAfter(':').trim()
                            if (key.isEmpty() || value.isEmpty()) null else key to value
                        }
                        .toMap()
            }
            val gridStart = design.indexOfFirst { it.contains("</style>") } + 1
            styleRows = design.subList(gridStart, design.size)
        }

        val charMap = Array(height) { CharArray(width) { EMPTY } }
        val attrMap = Array(height) { ByteArray(width) }
        for (i in 0 until height) {
            val row = grid[i]
            for (j in 0 until width) {
                val ch = if (j < row.length) row[j] else EMPTY
                if (ch == EMPTY || ch == '#') continue

                charMap[i][j] = ch
                var attrs = 0

                styles[styleRows.getOrNull(i)?.getOrNull(j)]?.let { style ->
                    if (style["bar-top"] == "true") attrs = attrs or Crossword.Cell.ATTR_BAR_TOP
                    if (style["bar-bottom"] == "true") attrs = attrs or Crossword.Cell.ATTR_BAR_BOTTOM
                    if (style["bar-left"] == "true") attrs = attrs or Crossword.Cell.ATTR_BAR_LEFT
                    if (style["bar-right"] == "true") attrs = attrs or Crossword.Cell.ATTR_BAR_RIGHT
                    if (style["background"] == "circle") attrs = attrs or Crossword.Cell.ATTR_CIRCLED
                }

                // Lowercase letters indicate special cells (e.g. circles)
                if (ch.isLowerCase()) attrs = attrs or Crossword.Cell.ATTR_CIRCLED

                attrMap[i][j] = attrs.toByte()
            }
        }

        val startCells = mutableListOf<Pair<Int, Int>>()
        val startRows = sections.getOrElse("start") { emptyList() }
        for (i in startRows.indices) {
            val row = startRows[i]
            for (j in row.indices) {
                val ch = row[j]
                if (ch != EMPTY && ch != '#') {
                    startCells.add(i to j)
                }
            }
        }

        builder.flags = 0
        builder.width = width
        builder.height = height
        builder.title = metadata["title"]
        builder.author = metadata["author"]
        builder.copyright = metadata["copyright"]
        builder.description = description
        builder.comment = sections.getOrElse("notes") { emptyList() }.joinToString("\n")

        buildWords(builder, charMap, attrMap, acrossClues, downClues, rebusMap)

        if (startCells.isNotEmpty()) {
            val state = CrosswordState(width, height)
            for ((i, j) in startCells) {
                state.setCharAt(i, j, charMap[i][j].uppercase().toString())
            }
            startState = state
        }
    }

    private fun buildWords(cb: Crossword.Builder,
                           charMap: Array<CharArray>,
                           attrMap: Array<ByteArray>,
                           acrossClues: Map<Int, String>,
                           downClues: Map<Int, String>,
                           rebusMap: Map<String, String>) {
        val alphabet = HashSet(Crossword.ALPHABET_ENGLISH)

        var number = 0
        val iend = charMap.lastIndex
        for (i in 0..iend) {
            val jend = charMap[i].lastIndex
            for (j in 0..jend) {
                if (charMap[i][j] == EMPTY) continue
                alphabet.add(charMap[i][j].uppercaseChar())

                var incremented = false
                if (WordBuilder.startsAcross(charMap, attrMap, i, j)) {
                    number++
                    incremented = true

                    cb.words += buildWord {
                        direction = Crossword.Word.DIR_ACROSS
                        hint = acrossClues[number] ?: ""
                        this.number = number
                        startRow = i
                        startColumn = j

                        var k = j
                        while (k <= jend && charMap[i][k] != EMPTY) {
                            val ch = charMap[i][k]
                            val solution = rebusMap[ch.toString()]
                                    ?: ch.uppercase().toString()
                            addCell(solution, attrMap[i][k].toInt())
                            if (WordBuilder.endsAcross(attrMap, i, k, jend)) break
                            k++
                        }
                    }
                }

                if (WordBuilder.startsDown(charMap, attrMap, i, j)) {
                    if (!incremented) number++

                    cb.words += buildWord {
                        direction = Crossword.Word.DIR_DOWN
                        hint = downClues[number] ?: ""
                        this.number = number
                        startRow = i
                        startColumn = j

                        var k = i
                        while (k <= iend && charMap[k][j] != EMPTY) {
                            val ch = charMap[k][j]
                            val solution = rebusMap[ch.toString()]
                                    ?: ch.uppercase().toString()
                            addCell(solution, attrMap[k][j].toInt())
                            if (WordBuilder.endsDown(attrMap, k, j, iend)) break
                            k++
                        }
                    }
                }
            }
        }

        cb.setAlphabet(alphabet)
    }

    companion object {
        private const val EMPTY = '.'

        private val CLUE_REGEX = Regex("^([AD])(\\d+)\\. (.*)$")
        private val STYLE_REGEX = Regex("\\s*(.)\\s*\\{([^}]*)\\}")
    }
}