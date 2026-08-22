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

import org.akop.ararat.io.JsoupHtmlFormatter
import org.junit.Test


class TestJsoupHtmlFormatter : BaseTest() {

    val crossword = JsoupHtmlFormatter().load("irishnews-pa.html")

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
                squareCount = 112,
                title = null,
                flags = 0,
                description = null,
                author = null,
                copyright = null,
                comment = null,
                date = 0,
                hash = "0be7af25844f5dbe9fe931f96da28ee133e1f20e")
        val layout = arrayOf(
                "DONS#C#R#I##S",
                "#V#PARLIAMENT",
                "#E#A#A#V#P##E",
                "BRUNETTE#LAST",
                "#M###E#T#O#U#",
                "BALLAD#STRIPS",
                "#N#O#####E#E#",
                "STATES#DESERT",
                "#E#H#E#I###V#",
                "PLEA#DISCLAIM",
                "E##R#A#C#I#S#",
                "CABINTRUNK#O#",
                "K##O#E#S#EZRA")
        val hints = arrayOf(
                "1A.Spaniards on the university staff (4)",
                "8A.Assembly that puts fellows in partial disarray (10)",
                "9A.Nevertheless she's a member of the fair sex (8)",
                "10A.The French way to survive (4)",
                "12A.Everybody interrupting deplorable song (6)",
                "14A.Takes off for an excursion in the vessel (6)",
                "15A.Says tastes differ (6)",
                "17A.Leave a great deal of waste (6)",
                "18A.To sing after this request is enjoyable (4)",
                "19A.Repudiate Diana's insurance demand (8)",
                "21A.You'll need a taxi in case there's heavy luggage (5,5)",
                "22A.The last character to break up a generation in the Old Testament (4)",
                "2D.A feature of the Victorian room above the gas-fitting, we hear (10)",
                "3D.Part of a bridge nine inches long (4)",
                "4D.Packed into a box car, maybe, with Edward (6)",
                "5D.Strive to break the fasteners (6)",
                "6D.What the beggar does could be simple or involved (8)",
                "7D.Leave it in the waste tip (4)",
                "11D.Excellent face-saving device for the foreman (10)",
                "13D.Playful philanderer left before Horatio got upset (8)",
                "16D.To quieten down when badly teased (6)",
                "17D.In the event, the record is given to you and me (6)",
                "18D.Nibble a quantity of grain (4)",
                "20D.Enjoy showing a resemblance (4)")
    }
}
