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

import org.akop.ararat.io.IpuzFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TestIpuzFormatter : BaseTest() {

    val crossword = IpuzFormatter().load("tiny.ipuz")

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
    fun crossword_circleApplied() {
        val circled = crossword.cellMap[0][1]
        assertTrue(circled != null && circled.isCircled)
    }

    @Test
    fun solutionOverlayFillsBlankPuzzle() {
        val cw = IpuzFormatter().load("tiny-solution.ipuz")
        assertEquals("XY", cw.wordsAcross[0].cells.joinToString("") { it.chars })
    }

    @Test
    fun writeRoundTripsThroughRead() {
        val bytes = java.io.ByteArrayOutputStream().use { out ->
            IpuzFormatter().write(crossword, out)
            out.toByteArray()
        }

        val reparsed = java.io.ByteArrayInputStream(bytes).use { inp ->
            org.akop.ararat.core.buildCrossword { IpuzFormatter().read(this, inp) }
        }

        assertLayout(reparsed, Array(layout.size) { row ->
            layout[row].chunked(1).map { when (it) { "#" -> null else -> it } }.toTypedArray()
        })
        assertHints(reparsed, hints)
        val circled = reparsed.cellMap[0][1]
        assertTrue(circled != null && circled.isCircled)
    }

    companion object {
        val metadata = Metadata(
                width = 2,
                height = 2,
                squareCount = 4,
                title = null,
                flags = 0,
                description = null,
                author = null,
                copyright = null,
                comment = null,
                date = 0,
                hash = "c84b1960668e8f536964f618185a77a09e5bf7cd")
        val layout = arrayOf(
                "CA",
                "RT")
        val hints = arrayOf(
                "1A.Across one",
                "2A.Across two",
                "1D.Down one",
                "2D.Down two")
    }
}
