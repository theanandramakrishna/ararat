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

/**
 * Helpers for deriving word boundaries from a grid and its cell attribute map.
 *
 * A word ends/starts not only at black squares but also at barred edges. Bars are
 * stored per-cell via [Crossword.Cell.ATTR_BAR_LEFT], [Crossword.Cell.ATTR_BAR_RIGHT],
 * [Crossword.Cell.ATTR_BAR_TOP] and [Crossword.Cell.ATTR_BAR_BOTTOM]. A boundary between
 * two adjacent cells is considered blocked if either cell declares a bar on the shared edge.
 *
 * The [charMap] uses '.' for black (non-playable) squares, matching the convention
 * used by the .puz formatter.
 */
object WordBuilder {

    private const val EMPTY = '.'

    fun hasBarTop(attrMap: Array<ByteArray>, row: Int, col: Int): Boolean =
            attrMap[row][col].toInt() and Crossword.Cell.ATTR_BAR_TOP != 0

    fun hasBarBottom(attrMap: Array<ByteArray>, row: Int, col: Int): Boolean =
            attrMap[row][col].toInt() and Crossword.Cell.ATTR_BAR_BOTTOM != 0

    fun hasBarLeft(attrMap: Array<ByteArray>, row: Int, col: Int): Boolean =
            attrMap[row][col].toInt() and Crossword.Cell.ATTR_BAR_LEFT != 0

    fun hasBarRight(attrMap: Array<ByteArray>, row: Int, col: Int): Boolean =
            attrMap[row][col].toInt() and Crossword.Cell.ATTR_BAR_RIGHT != 0

    /** Whether the edge between cell (row, col-1) and (row, col) is blocked by a bar. */
    fun isAcrossBoundary(attrMap: Array<ByteArray>, row: Int, col: Int): Boolean =
            hasBarRight(attrMap, row, col - 1) || hasBarLeft(attrMap, row, col)

    /** Whether the edge between cell (row-1, col) and (row, col) is blocked by a bar. */
    fun isDownBoundary(attrMap: Array<ByteArray>, row: Int, col: Int): Boolean =
            hasBarBottom(attrMap, row - 1, col) || hasBarTop(attrMap, row, col)

    /**
     * Whether an across word starts at cell (row, col). A word must extend to at
     * least two cells, so a cell isolated by bars on both sides is not a word start.
     */
    fun startsAcross(charMap: Array<CharArray>, attrMap: Array<ByteArray>,
                     row: Int, col: Int): Boolean {
        val jend = charMap[row].lastIndex
        val blockedLeft = col == 0 || charMap[row][col - 1] == EMPTY
                || isAcrossBoundary(attrMap, row, col)
        return blockedLeft && col + 1 <= jend && charMap[row][col + 1] != EMPTY
                && !isAcrossBoundary(attrMap, row, col + 1)
    }

    /**
     * Whether a down word starts at cell (row, col). A word must extend to at
     * least two cells, so a cell isolated by bars on both sides is not a word start.
     */
    fun startsDown(charMap: Array<CharArray>, attrMap: Array<ByteArray>,
                   row: Int, col: Int): Boolean {
        val iend = charMap.lastIndex
        val blockedTop = row == 0 || charMap[row - 1][col] == EMPTY
                || isDownBoundary(attrMap, row, col)
        return blockedTop && row + 1 <= iend && charMap[row + 1][col] != EMPTY
                && !isDownBoundary(attrMap, row + 1, col)
    }

    /** Whether an across word ends at cell (row, col) due to a bar on its right edge. */
    fun endsAcross(attrMap: Array<ByteArray>, row: Int, col: Int, jend: Int): Boolean =
            hasBarRight(attrMap, row, col) || (col + 1 <= jend && hasBarLeft(attrMap, row, col + 1))

    /** Whether a down word ends at cell (row, col) due to a bar on its bottom edge. */
    fun endsDown(attrMap: Array<ByteArray>, row: Int, col: Int, iend: Int): Boolean =
            hasBarBottom(attrMap, row, col) || (row + 1 <= iend && hasBarTop(attrMap, row + 1, col))
}