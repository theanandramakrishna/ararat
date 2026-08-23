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

package org.akop.ararat.formatter

import org.akop.ararat.io.JpzFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TestJpzFormatter : BaseTest() {

    val crossword = JpzFormatter().load("independent.jpz")

    @Test
    fun crossword_testMetadata() {
        assertMetadata(crossword, metadata)
    }

    @Test
    fun crossword_testLayout() {
        assertLayout(crossword, Array(layout.size) { row ->
            layout[row].chunked(1).map { when (it) { "#" -> null else -> it } }.toTypedArray()
        })
    }

    @Test
    fun crossword_testHints() {
        assertHints(crossword, hints)
    }

    @Test
    fun crossword_circleAndStartState() {
        // F is at row 1, col 1 (0-based) with background-shape=circle.
        val fCell = crossword.cellMap[1][1]
        assertNotNull(fCell)
        assertTrue(fCell!!.isCircled)

        val fresh = JpzFormatter()
        fresh.load("independent.jpz")
        val start = requireNotNull(fresh.startState)
        assertEquals("A", start.charAt(0, 0))
    }

    @Test
    fun zipWrappedFileParsesIdentically() {
        val zipped = JpzFormatter().load("independent-zip.jpz")
        assertLayout(zipped, Array(layout.size) { row ->
            layout[row].chunked(1).map { when (it) { "#" -> null else -> it } }.toTypedArray()
        })
        assertEquals(crossword.hash, zipped.hash)
    }

    @Test
    fun barredGridSegmentsWordsAtBars() {
        val barred = JpzFormatter().load("independent-bars.jpz")
        val hints = (barred.wordsAcross + barred.wordsDown)
                .joinToString("|") { "${it.number}${if (it.direction == 0) "A" else "D"}.${it.hint}" }
        assertEquals(
                "1A.ab|3A.cd|5A.ef|7A.gh|9A.ij|11A.kl|" +
                        "1D.aei|2D.bfj|3D.cgk|4D.dhl", hints)
        assertEquals(12, barred.squareCount)
    }

    companion object {
        val metadata = Metadata(
                width = 4,
                height = 4,
                squareCount = 16,
                title = "Independent Test",
                flags = 0,
                description = "A test description",
                author = "Tester",
                copyright = "IRN",
                comment = "Well done!\n\nAcross 1: definition of ABCD",
                date = 0,
                hash = "140d05468fdcbe82b573480a3de1e61f65cf5917")
        val layout = arrayOf(
                "ABCD",
                "EFGH",
                "IJKL",
                "MNOP")
        val hints = arrayOf(
                "1A.Sea letters (4)",
                "2A.Second row",
                "3A.Third row",
                "4A.Fourth row",
                "1D.First column",
                "2D.Second column",
                "3D.Third column",
                "4D.Fourth column")
    }
}
