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
import org.json.JSONObject

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/**
 * Formatter for the AmuseLabs player JSON obtained by decoding the "rawc"
 * payload embedded in AmuseLabs crossword player pages (e.g. The Hindu's
 * cryptic at cdn3.amuselabs.com/hindu/crossword?id=...&set=hindu-cryptic).
 * See https://gitlab.com/Hague/forkyz (PuzzleMeStreamScraper).
 *
 * Schema highlights: w/h dimensions; box[][] is COLUMN-major (box[c][r]) and
 * holds solution letters with NUL or empty strings for blocks; placedWords[]
 * carries 0-based start ("x"/"y" as strings), extent ("nBoxes", falling back
 * to the answer length), direction ("E"/"S" or acrossNotDown), a string clue
 * number and a nested clue object. Publishers may withhold the answer strings;
 * cells are then built from the grid alone.
 */
class AmuseLabsJsonFormatter : CrosswordFormatter {

    override fun setEncoding(encoding: String) { }

    @Throws(IOException::class)
    override fun read(builder: Crossword.Builder, inputStream: InputStream) {
        val json = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = JSONObject(json)

        val cols = root.optInt("w")
        val rows = root.optInt("h")
        if (cols <= 0 || rows <= 0) {
            throw FormatException("Invalid dimensions: ${cols}x${rows}")
        }

        val boxArray = root.optJSONArray("box") ?: throw FormatException("Missing 'box'")
        if (boxArray.length() != cols) {
            throw FormatException("Box column count mismatch (${boxArray.length()} vs $cols)")
        }

        builder.flags = 0
        builder.width = cols
        builder.height = rows
        builder.title = root.optString("title")
        builder.author = root.optString("author")
        builder.description = root.optString("description")
        builder.copyright = root.optString("copyright")
        builder.date = root.optLong("publishTime")

        // NOTE: box is COLUMN-major - box[c][r].
        val letterAt = Array(rows) { arrayOfNulls<Char>(cols) }
        for (c in 0 until cols) {
            val colArr = boxArray.optJSONArray(c)
                    ?: throw FormatException("Missing 'box[$c]'")
            if (colArr.length() != rows) {
                throw FormatException("Box row mismatch at column $c")
            }
            for (r in 0 until rows) {
                val cell = colArr.optString(r)
                val ch = cell.trim().firstOrNull() ?: continue
                if (ch != BLOCK) letterAt[r][c] = ch.uppercaseChar()
            }
        }

        val placedWords = root.optJSONArray("placedWords")
                ?: throw FormatException("Missing 'placedWords'")

        for (i in 0 until placedWords.length()) {
            val item = Item(placedWords.optJSONObject(i)
                    ?: throw FormatException("Missing 'placedWords[$i]'"))

            builder.words += buildWord {
                direction = item.direction
                hint = item.clue
                number = item.number
                startRow = item.row
                startColumn = item.column

                for (offset in 0 until item.length) {
                    val r = if (item.isAcross) item.row else item.row + offset
                    val c = if (item.isAcross) item.column + offset else item.column
                    if (r >= rows || c >= cols) {
                        throw FormatException("Placed word ${item.number} is out of bounds")
                    }
                    val ch = letterAt[r][c]
                    if (ch != null) {
                        addCell(ch.toString())
                    } else {
                        addCell("", Crossword.Cell.ATTR_NO_SOLUTION)
                    }
                }
            }
        }
    }

    private fun items(placedWords: org.json.JSONArray): List<Item> =
            (0 until placedWords.length()).map {
                Item(placedWords.optJSONObject(it)
                        ?: throw FormatException("Missing 'placedWords[$it]'"))
            }

    private class Item(itemObj: JSONObject) {

        val direction: Int
        val isAcross: Boolean
        val number: Int
        val clue: String
        val row: Int
        val column: Int

        /**
         * Word extent: "nBoxes" when present, else the answer's own length.
         * The answer string itself may be withheld by the publisher; cells
         * then come from the box grid alone.
         */
        val length: Int

        init {
            isAcross = when (val dir = itemObj.optString("direction")) {
                "E", "e", "across" -> true
                "S", "s", "down" -> false
                else -> when (val and = itemObj.optString("acrossNotDown")) {
                    "true", "True", "TRUE" -> true
                    "false", "False", "FALSE" -> false
                    else -> throw FormatException(
                            "Invalid direction '$dir'/'$and' for placed word")
                }
            }
            direction = if (isAcross) Crossword.Word.DIR_ACROSS else Crossword.Word.DIR_DOWN

            clue = when (val clueVal = itemObj.opt("clue")) {
                is JSONObject -> clueVal.optString("clue")
                is String -> clueVal
                else -> ""
            }.trim()

            number = itemObj.optString("clueNum").toIntOrNull()
                    ?: throw FormatException(
                            "Invalid clue number '${itemObj.optString("clueNum")}'")
            row = itemObj.optString("y").toIntOrNull()
                    ?: throw FormatException("Invalid y for clue $number")
            column = itemObj.optString("x").toIntOrNull()
                    ?: throw FormatException("Invalid x for clue $number")

            val word = itemObj.optString("word").trim().uppercase()
            length = itemObj.optString("nBoxes").toIntOrNull()
                    ?: word.length.takeIf { it > 0 }
                    ?: throw FormatException("No length for placed word entry $number")

            if (number <= 0) {
                throw FormatException("Invalid placed word entry $number")
            }
        }
    }

    companion object {

        private const val BLOCK = '\u0000'

        private val RAWC_REGEX = Regex("\"rawc\"\\s*:\\s*\"([^\"]+)\"")

        /** Extract the rawc payload from an AmuseLabs player page. */
        fun extractRawc(pageHtml: String): String? =
                RAWC_REGEX.find(pageHtml)?.groupValues?.get(1)

        /**
         * Decode a rawc payload into its JSON string. Some payloads are plain
         * Base64; others are obfuscated by reversing chunks whose lengths are
         * given by a 7-digit key (algorithm credited to xword-dl and Kotwords
         * via Forkyz). Returns null if undecodable within the time budget.
         */
        fun decodeRawc(rawc: String): String? {
            plainDecode(rawc)?.let { return it }

            val startTime = System.currentTimeMillis()
            val queue = ArrayDeque<List<Int>>(listOf(emptyList()))
            while (queue.isNotEmpty()) {
                if (System.currentTimeMillis() - startTime > DEOBFUSCATE_TIMEOUT_MS) {
                    return null
                }
                val candidate = queue.removeFirst()
                if (candidate.size == KEY_LEN) {
                    deobfuscateWithKey(rawc, candidate)?.let { return it }
                    continue
                }
                val remaining = KEY_LEN - candidate.size - 1
                for (digit in 2..MAX_KEY_VALUE) {
                    val next = candidate + digit
                    val minSpacing = 2 * remaining
                    val maxSpacing = MAX_KEY_VALUE * remaining
                    if ((minSpacing..maxSpacing).any { isValidKeyPrefix(rawc, next, it) }) {
                        queue.addLast(next)
                    }
                }
            }
            return null
        }

        private fun plainDecode(rawc: String): String? =
                base64Decode(rawc)?.let { validDecodedJson(strictUtf8(it)) }

        private fun deobfuscateWithKey(rawc: String, key: List<Int>): String? {
            val buffer = rawc.toMutableList()
            var i = 0
            var segmentCount = 0
            while (i < buffer.size - 1) {
                val segmentLength = minOf(key[segmentCount % key.size], buffer.size - i)
                segmentCount++
                var left = i
                var right = i + segmentLength - 1
                while (left < right) {
                    val tmp = buffer[left]
                    buffer[left] = buffer[right]
                    buffer[right] = tmp
                    left++
                    right--
                }
                i += segmentLength
            }
            return base64Decode(buffer.joinToString(""))?.let { validDecodedJson(strictUtf8(it)) }
        }

        /** A candidate is accepted only if it is strict UTF-8 and parses as JSON. */
        private fun validDecodedJson(text: String?): String? =
                text?.takeIf { it.trimStart().startsWith("{") }?.let { candidate ->
                    try {
                        JSONObject(candidate)
                        candidate
                    } catch (e: Exception) {
                        null
                    }
                }

        /**
         * Validates that a partial key could produce valid Base64/UTF-8 output,
         * simulating reversal across plausible spacings of the remaining digits.
         */
        private fun isValidKeyPrefix(rawc: String, prefix: List<Int>, spacing: Int): Boolean {
            return try {
                var pos = 0
                while (pos < rawc.length) {
                    val startPos = pos
                    var keyIndex = 0
                    val chunk = mutableListOf<String>()
                    while (keyIndex < prefix.size && pos < rawc.length) {
                        val chunkLength = minOf(prefix[keyIndex], rawc.length - pos)
                        chunk.add(rawc.substring(pos, pos + chunkLength).reversed())
                        pos += chunkLength
                        keyIndex++
                    }
                    val chunkStr = chunk.joinToString("")
                    val base64Start = ((startPos + 3) / 4) * 4 - startPos
                    val base64End = (pos / 4) * 4 - startPos
                    if (base64Start >= chunkStr.length || base64End <= base64Start) {
                        pos += spacing
                        continue
                    }
                    val decoded = base64Decode(chunkStr.substring(base64Start, base64End))
                            ?: return false
                    if (strictUtf8(decoded)?.any { it.code == 0 } != false) return false
                    pos += spacing
                }
                true
            } catch (e: Exception) {
                false
            }
        }

        private const val BASE64_ALPHABET =
                "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

        private fun base64Decode(input: String): ByteArray? {
            val out = ByteArrayOutputStream()
            var acc = 0
            var bits = 0
            for (ch in input) {
                if (ch == '=' || ch == '\n' || ch == '\r') continue
                val value = BASE64_ALPHABET.indexOf(ch)
                if (value < 0) return null
                acc = (acc shl 6) or value
                bits += 6
                if (bits >= 8) {
                    bits -= 8
                    out.write((acc shr bits) and 0xFF)
                }
            }
            return out.toByteArray()
        }

        private fun strictUtf8(bytes: ByteArray): String? = try {
            Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: Exception) {
            null
        }

        private const val KEY_LEN = 7
        private const val MAX_KEY_VALUE = 20
        private const val DEOBFUSCATE_TIMEOUT_MS = 30000L
    }
}
