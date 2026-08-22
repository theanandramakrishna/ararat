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

import org.akop.ararat.io.PmlJsonFormatter
import org.junit.Test


class TestPmlJsonFormatter : BaseTest() {

    val crossword = PmlJsonFormatter().load("metro-pml.json")

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

    companion object {
        val metadata = Metadata(
                width = 13,
                height = 13,
                squareCount = 116,
                title = null,
                flags = 0,
                description = null,
                author = null,
                copyright = null,
                comment = null,
                date = 1787295600000,
                hash = "9566c4044b3537dd44f3c302381c71b8af0eabde")
        val layout = arrayOf(
                "#SIMON#IMAGO#",
                "W#N#P#A#A#L#F",
                "HALVE#FERRARI",
                "A#I#R#T#T#D#R",
                "LIMEADE#INERT",
                "E#B###R#A###H",
                "#MOUND#PLUTO#",
                "T###O#S###O#U",
                "YEARS#AILERON",
                "R#D#W#N#A#P#F",
                "EMINENT#THERE",
                "S#E#A#A#I#D#D",
                "#QUITE#ENROL#")
        val hints = arrayOf(
                "1A.Some parsimonious boy (5)",
                "4A.Partly maim a gorgeous adult insect (5)",
                "10A.Divide five in fine fettle outside (5)",
                "11A.Prestigious car, rarer model in Formula One (7)",
                "12A.Tell fibs about drink being cordial (7)",
                "13A.Chemically unreactive nitre compound (5)",
                "14A.Fashion knight for large bank (5)",
                "16A.Dog with heavenly body (5)",
                "21A.Unknown organs lasting for ages (5)",
                "23A.Eccentric role in a flap (7)",
                "25A.Distinguished nine met, unexpectedly (7)",
                "26A.Article on engineer is yonder (5)",
                "27A.Fairly quiet, perhaps (5)",
                "28A.Some sailor needs to return to enlist (5)",
                "2D.Trendy car carrying bachelor up in the air? (2,5)",
                "3D.Hope Ravel piece is musical drama (5)",
                "5D.Alarm, it disturbed military (7)",
                "6D.For example turns on boy in clearing (5)",
                "7D.Hear the cry of huge creature (5)",
                "8D.Looking for support without leader (5)",
                "9D.Tree article shortened by inlet (5)",
                "15D.Anti-perspirant unnecessary – it's not a problem! (2,5)",
                "17D.Worryingly roped to explosive (7)",
                "18D.Rubber? Some normality restored! (5)",
                "19D.Southern social worker, a present-bringer (5)",
                "20D.A foreign FBI agent given nothing to eat (5)",
                "22D.Farewell and fade in gold (5)",
                "24D.It was the talk of Rome (5)")
    }
}
