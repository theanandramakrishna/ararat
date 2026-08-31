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

import org.akop.ararat.io.CwfGameJsonFormatter
import org.junit.Test


class TestCwfGameJsonFormatter : BaseTest() {

    val crossword = CwfGameJsonFormatter().load("cwfg.json")

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
                width = 15,
                height = 15,
                squareCount = 160,
                flags = 0,
                title = "January, 2003",
                description = """Across:
  1 SNUG (GUNS rev)
  3 E + RA(DICA)TED (ACID rev)
  9 A + BET
10 S(CAN + T)LINGS
12 LACE TRIMMING ("TRACE LIMNING" spoonerism)
15 PORT + RAY
16 CHAR + ADE
17 CR('EM)ATE
19 S(KY)WARD
20 APPRECIATION (AN APRICOT PIE anag)
23 ENCHILADAS (EACH ISLAND anag)
24 U + NUM
25 STOLE + N + KISS (SKIS, move the first S)
26 B(R)AN

Down:
  1 S(NAIL) + SPACE
  2 UNESCORTED (NOT RESCUED anag)
  4 RECTIFY (CITY REF anag)
  5 DYNAMIC (CANDY I'M anag)
  6 CA(LEND + A + RYE + A)R
  7 TANG (GNAT rev)
  8 DASH (SHAD anag)
11 STARS + A + PP + HIRE
13 VA + CAT + I + ONE + R
14 LEAD IN G-MAN (pun)
18 E + AR(MAR)K
19 SO(CIAL)S (LAIC rev)
21 TEAS ("TEASE" hom)
22 ECHO (hidden)""",
                author = "Kegler",
                copyright = "2003, Kegler",
                comment = null,
                date = 0,
                hash = "a96af4c52eebb24c1c47811376e2cf0aeff2a1da")
        val layout = arrayOf(
                "SNUG#ERADICATED",
                "N#N###E#Y#A#A#A",
                "ABET#SCANTLINGS",
                "I#S#S#T#A#E#G#H",
                "LACETRIMMING###",
                "S#O#A#F#I#D#V#L",
                "PORTRAY#CHARADE",
                "A#T#S#####R#C#A",
                "CREMATE#SKYWARD",
                "E#D#P#A#O#E#T#I",
                "###APPRECIATION",
                "T#E#H#M#I#R#O#G",
                "ENCHILADAS#UNUM",
                "A#H#R#R#L###E#A",
                "STOLENKISS#BRAN")
        val hints = arrayOf(
                "1A.Comfortable with return of weapons (4)",
                "3A.Wiped out, he finally considered ingesting LSD on the way back (10)",
                "9A.Assist with a wager (4)",
                "10A.Throws around axe, starting to topple small beams (10)",
                "12A.Frilly borders follow outlining for Reverend Spooner (4,8)",
                "15A.Depict harbor town fish (7)",
                "16A.Cleaning woman with drink in a clue like this? (7)",
                "17A.Incinerate 'em in box (7)",
                "19A.Up in grassy area around Kentucky (7)",
                "20A.An apricot pie, unusual for Thanksgiving (12)",
                "23A.Each island prepared Mexican food (10)",
                "24A.Arguing, in essence, half of number one in Latin (4)",
                "25A.Wrap new skis, front to back, for unexpected show of affection (6,4)",
                "26A.Stop swallowing last of your meal (4)",
                "1D.Brad entering small room at an extremely slow rate (6,4)",
                "2D.Stag, unfortunately, not rescued (10)",
                "4D.Correct confused city ref (7)",
                "5D.Potent candy I'm stirring (7)",
                "6D.Furnish a drink to one in vehicle January to December (8,4)",
                "7D.It has a little bite, either way? (4)",
                "8D.Swimming shad move quickly (4)",
                "11D.Gem highlights a very quiet engagement (4,8)",
                "13D.Virginia jazz fan is, at first, one regular tourist (10)",
                "14D.Result of a gunshot to FBI agent hero? (7,3)",
                "18D.Identify damage found in back of the ark (7)",
                "19D.Help with comeback of secular parties (7)",
                "21D.Reportedly ridicule drinks (4)",
                "22D.Imitation exhibited by screech owl (4)")
    }
}