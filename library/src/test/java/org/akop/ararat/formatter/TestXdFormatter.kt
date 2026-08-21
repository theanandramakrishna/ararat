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

import org.akop.ararat.core.Crossword
import org.akop.ararat.core.CrosswordReader
import org.akop.ararat.core.CrosswordWriter
import org.akop.ararat.io.CrosswordFormatter
import org.akop.ararat.io.XdFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

class TestXdFormatter : BaseTest() {

    private val xdRoot = File(root, "xd")

    private fun CrosswordFormatter.loadXd(path: String) =
            buildCrossword { read(this, File(xdRoot, path).inputStream()) }

    private fun buildCrossword(block: org.akop.ararat.core.Crossword.Builder.() -> Unit) =
            org.akop.ararat.core.buildCrossword(block)

    @Test
    fun metadataAndWords_standardCrossword() {
        val cw = XdFormatter().loadXd("crossword_20260819.xd")

        assertEquals("The Crossword: Wednesday, August 19, 2026", cw.title)
        assertEquals("Patrick Berry", cw.author)
        assertEquals(15, cw.width)
        assertEquals(15, cw.height)

        // Across A1 BRAM, A5 CHIRP; Down D1 BLOWPIPE
        val a1 = cw.wordsAcross.first { it.number == 1 }
        assertEquals(Crossword.Word.DIR_ACROSS, a1.direction)
        assertEquals(0, a1.startRow)
        assertEquals(0, a1.startColumn)
        assertEquals("BRAM", a1.cells.joinToString("") { it.chars })
        assertEquals("“Dracula” author Stoker", a1.hint)

        val a5 = cw.wordsAcross.first { it.number == 5 }
        assertEquals("CHIRP", a5.cells.joinToString("") { it.chars })

        val d1 = cw.wordsDown.first { it.number == 1 }
        assertEquals("BLOWPIPE", d1.cells.joinToString("") { it.chars })

        assertNotNull(cw.cellMap[0][0])
        assertNull(cw.cellMap[0][4]) // black square in row 0 between BRAM and CHIRP? CHIRP starts at col 5
    }

    @Test
    fun wordBoundariesBarredCryptic() {
        val cw = XdFormatter().loadXd("cryptic_sondheim.xd")

        assertEquals(8, cw.width)
        assertEquals(10, cw.height)
        assertEquals("The Cryptic Crossword: Sondheim Edition", cw.title)

        // A1 CAPS at (0,0), A4 SWIG at (0,4)
        val a1 = cw.wordsAcross.first { it.number == 1 }
        assertEquals(0, a1.startRow)
        assertEquals(0, a1.startColumn)
        assertEquals("CAPS", a1.cells.joinToString("") { it.chars })

        val a4 = cw.wordsAcross.first { it.number == 4 }
        assertEquals(0, a4.startRow)
        assertEquals(4, a4.startColumn)
        assertEquals("SWIG", a4.cells.joinToString("") { it.chars })

        // A7 ARMADA at (1,2)
        val a7 = cw.wordsAcross.first { it.number == 7 }
        assertEquals(1, a7.startRow)
        assertEquals(2, a7.startColumn)
        assertEquals("ARMADA", a7.cells.joinToString("") { it.chars })

        // No single-letter entries in row 1
        assertTrue(cw.wordsAcross.none { it.startRow == 1 && it.length == 1 })
        assertTrue(cw.wordsAcross.none { it.length < 2 })

        // Bars: cell (1,2) has no left bar, but (0,4) has a left bar (boundary of SWIG)
        val swigStart = cw.cellMap[0][4]!!
        assertTrue(swigStart.isBarLeft)
    }

    @Test
    fun barAttrsSetOnGrid() {
        val cw = XdFormatter().loadXd("cryptic_sondheim.xd")

        // Design style B { bar-left: true; bar-top: true }, marked at (3,5)
        val bCell = cw.cellMap[3][5]!!
        assertTrue(bCell.isBarLeft)
        assertTrue(bCell.isBarTop)

        // A { bar-top: true } at (2,3)
        val aCell = cw.cellMap[2][3]!!
        assertTrue(aCell.isBarTop)
    }

    @Test
    fun downEntriesInBarredCryptic() {
        val cw = XdFormatter().loadXd("cryptic_sondheim.xd")

        // D1 CALLUS at (0,0)
        val d1 = cw.wordsDown.first { it.number == 1 }
        assertEquals(0, d1.startRow)
        assertEquals(0, d1.startColumn)
        assertEquals("CALLUS", d1.cells.joinToString("") { it.chars })

        // D16 PART at (6,0)
        val d16 = cw.wordsDown.first { it.number == 16 }
        assertEquals(6, d16.startRow)
        assertEquals(0, d16.startColumn)
        assertEquals("PART", d16.cells.joinToString("") { it.chars })
    }

    @Test
    fun cwRoundTripBarredCryptic() {
        val cw = XdFormatter().loadXd("cryptic_sondheim.xd")

        val bytes = ByteArrayOutputStream().use { out ->
            CrosswordWriter(out).use { it.write(cw) }
            out.toByteArray()
        }

        val read = CrosswordReader(ByteArrayInputStream(bytes)).use { it.read() }

        assertEquals(cw.title, read.title)
        assertEquals(cw.author, read.author)
        assertEquals(cw.width, read.width)
        assertEquals(cw.height, read.height)
        for (r in 0 until cw.height) {
            for (c in 0 until cw.width) {
                assertEquals("grid char mismatch at $r,$c",
                        cw.cellMap[r][c]?.chars, read.cellMap[r][c]?.chars)
            }
        }

        // Bars survive the round trip
        for (r in 0 until cw.height) {
            for (c in 0 until cw.width) {
                val before = cw.cellMap[r][c]
                val after = read.cellMap[r][c]
                assertEquals("bar mismatch at $r,$c",
                        before?.attrFlags ?: 0, after?.attrFlags ?: 0)
            }
        }

        assertEquals(cw.wordsAcross.size, read.wordsAcross.size)
        assertEquals(cw.wordsDown.size, read.wordsDown.size)
        for (i in cw.wordsAcross.indices) {
            assertEquals("across word $i",
                    cw.wordsAcross[i].number, read.wordsAcross[i].number)
            assertEquals("across hint $i",
                    cw.wordsAcross[i].hint, read.wordsAcross[i].hint)
        }
    }

    @Test
    fun cwRoundTripStandard() {
        val cw = XdFormatter().loadXd("crossword_20260819.xd")

        val bytes = ByteArrayOutputStream().use { out ->
            CrosswordWriter(out).use { it.write(cw) }
            out.toByteArray()
        }

        val read = CrosswordReader(ByteArrayInputStream(bytes)).use { it.read() }

        assertEquals(cw.title, read.title)
        assertEquals(cw.width, read.width)
        assertEquals(cw.height, read.height)
        assertEquals(cw.wordsAcross.size, read.wordsAcross.size)
        assertEquals(cw.wordsDown.size, read.wordsDown.size)
        for (r in 0 until cw.height) {
            for (c in 0 until cw.width) {
                assertEquals("grid char mismatch at $r,$c",
                        cw.cellMap[r][c]?.chars, read.cellMap[r][c]?.chars)
            }
        }
    }
}