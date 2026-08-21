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

package org.akop.ararat.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TestWordBuilder {

    private fun charMap(vararg rows: String): Array<CharArray> =
            Array(rows.size) { i -> rows[i].toCharArray() }

    private fun attrMap(width: Int, height: Int,
                        bars: Map<Pair<Int, Int>, Int> = emptyMap()): Array<ByteArray> {
        val map = Array(height) { ByteArray(width) }
        bars.forEach { (pos, bits) ->
            map[pos.first][pos.second] = bits.toByte()
        }
        return map
    }

    @Test
    fun noBars_blackCellsOnly() {
        // A B . C D
        // E F . G H
        val cm = charMap("AB.CD", "EF.GH")
        val am = attrMap(5, 2)

        assertTrue(WordBuilder.startsAcross(cm, am, 0, 0))
        assertFalse(WordBuilder.startsAcross(cm, am, 0, 1))
        assertTrue(WordBuilder.startsAcross(cm, am, 0, 3))
        assertFalse(WordBuilder.startsAcross(cm, am, 0, 4))
        assertFalse(WordBuilder.endsAcross(am, 0, 1, 4))
        assertTrue(WordBuilder.startsDown(cm, am, 0, 0))
        assertFalse(WordBuilder.startsDown(cm, am, 1, 0))
    }

    @Test
    fun barRight_endsAndStartsAcross() {
        // A B | C D
        // E F | G H
        val cm = charMap("ABCD", "EFGH")
        val am = attrMap(4, 2, mapOf(
                (0 to 1) to Crossword.Cell.ATTR_BAR_RIGHT,
                (1 to 1) to Crossword.Cell.ATTR_BAR_RIGHT))

        assertTrue(WordBuilder.startsAcross(cm, am, 0, 0))
        assertFalse(WordBuilder.startsAcross(cm, am, 0, 1))
        assertTrue(WordBuilder.startsAcross(cm, am, 0, 2))

        assertFalse(WordBuilder.endsAcross(am, 0, 0, 3))
        assertTrue(WordBuilder.endsAcross(am, 0, 1, 3))
        assertFalse(WordBuilder.endsAcross(am, 0, 2, 3))
        assertFalse(WordBuilder.endsAcross(am, 0, 3, 3))

        assertTrue(WordBuilder.startsDown(cm, am, 0, 0))
        assertFalse(WordBuilder.startsDown(cm, am, 1, 0))
    }

    @Test
    fun barBottom_endsAndStartsDown() {
        // A B
        // -----
        // C D
        // E F
        val cm = charMap("AB", "CD", "EF")
        val am = attrMap(2, 3, mapOf(
                (0 to 0) to Crossword.Cell.ATTR_BAR_BOTTOM,
                (0 to 1) to Crossword.Cell.ATTR_BAR_BOTTOM))

        // A is a single cell above the bar, so it is not a down entry
        assertFalse(WordBuilder.startsDown(cm, am, 0, 0))
        assertTrue(WordBuilder.startsDown(cm, am, 1, 0))
        assertFalse(WordBuilder.startsDown(cm, am, 2, 0))

        assertTrue(WordBuilder.endsDown(am, 0, 0, 2))
        assertFalse(WordBuilder.endsDown(am, 1, 0, 2))
    }

    @Test
    fun barLeft_onRightCell_blocksBoundary() {
        // bar declared as ATTR_BAR_LEFT on the cell right of the boundary
        val cm = charMap("ABCD")
        val am = attrMap(4, 1, mapOf(
                (0 to 2) to Crossword.Cell.ATTR_BAR_LEFT))

        assertTrue(WordBuilder.isAcrossBoundary(am, 0, 2))
        assertTrue(WordBuilder.endsAcross(am, 0, 1, 3))
        assertTrue(WordBuilder.startsAcross(cm, am, 0, 2))
    }

    @Test
    fun barOnTop_onLowerCell_blocksBoundary() {
        val cm = charMap("AB", "CD", "EF")
        val am = attrMap(2, 3, mapOf(
                (1 to 0) to Crossword.Cell.ATTR_BAR_TOP))

        assertTrue(WordBuilder.isDownBoundary(am, 1, 0))
        assertTrue(WordBuilder.endsDown(am, 0, 0, 2))
        assertTrue(WordBuilder.startsDown(cm, am, 1, 0))
        assertFalse(WordBuilder.startsDown(cm, am, 2, 0))
    }

    @Test
    fun bar_singleCellDoesNotStartAcrossOrDown() {
        // A | B . C
        val cm = charMap("AB.C")
        val am = attrMap(4, 1, mapOf(
                (0 to 0) to Crossword.Cell.ATTR_BAR_RIGHT))

        assertTrue(WordBuilder.endsAcross(am, 0, 0, 3))
        // B is isolated: the bar blocks the left, but nothing follows on the right
        assertFalse(WordBuilder.startsAcross(cm, am, 0, 1))
    }

    @Test
    fun bar_singleCell_betweenTwoBarsIsNotAWord() {
        // A | B | C
        val cm = charMap("ABC")
        val am = attrMap(3, 1, mapOf(
                (0 to 0) to Crossword.Cell.ATTR_BAR_RIGHT,
                (0 to 2) to Crossword.Cell.ATTR_BAR_LEFT))

        // A and C are single cells; B is sandwiched between bars
        assertFalse(WordBuilder.startsAcross(cm, am, 0, 0))
        assertFalse(WordBuilder.startsAcross(cm, am, 0, 1))
        assertFalse(WordBuilder.startsAcross(cm, am, 0, 2))
        assertTrue(WordBuilder.endsAcross(am, 0, 0, 2))
    }
}