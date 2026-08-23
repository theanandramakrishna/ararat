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

import org.akop.ararat.io.AmuseLabsJsonFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TestAmuseLabsJsonFormatter : BaseTest() {

    val crossword = AmuseLabsJsonFormatter().load("hindu-amuse.json")

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
                squareCount = 145,
                title = "14879",
                flags = 0,
                description = "",
                author = "Afterdark",
                copyright = "",
                comment = null,
                date = 1787337000000,
                hash = "467901ebade39d8f10ff9fef5a9d0fb29b7cc7c4")
        val layout = arrayOf(
                "#S#L#P#I#L#M#D#",
                "CARAFE#SEAWATER",
                "#B#W#R#L#S#S#M#",
                "SOON#STEPSISTER",
                "#T#T#I#######A#",
                "HAZE#ASSORTMENT",
                "#G#N#N#N#E#O###",
                "NEON#CRASS#TBAR",
                "###I#A#R#I#O#I#",
                "JETSETTERS#RARE",
                "#N#######T#S#C#",
                "CANDELABRA#POOR",
                "#B#R#I#U#N#O#V#",
                "CLUELESS#CORKER",
                "#E#W#D#H#E#T#R#")
        val hints = arrayOf(
                "8A.A rule introduced in eatery for getting a pitcher",
                "9A.Eater was supplied brine, perhaps",
                "10A.Shortly, issue will be all over head-office",
                "11A.Relative's action is engaging; first song better in second part",
                "12A.Confusion after he hid a last letter",
                "13A.Lot of kind people entering a street",
                "17A.Kept time post noon to get gas",
                "18A.Stupid, caught robbing primarily, idiot",
                "19A.Bowled in pitch to get lift, say",
                "21A.Wealthy global travellers seen with black dogs, say",
                "23A.Unusual file type extracted in the beginning",
                "24A.Deal gone wrong in prison, branch siphoned 50% of device to hold lights",
                "28A.Bankrupt Penny gathering ducks to have dinner at the end",
                "29A.Foolishly, use cell at the beginning of show if ignorant",
                "30A.Outstanding person in York loses head, breaks college equipment at the entrance and runs",
                "1D.Drunk sailor aboard, argues frequently leading to disruption",
                "2D.Game's rule; point 10 is covering advantage essentially",
                "3D.No charge included in removing parasite from pet, perhaps",
                "4D.Lewis Lennox is detaining man, say",
                "5D.Girl tumbler loses cap",
                "6D.According to sources, many anarchists support seething crowd",
                "7D.Humble democrat remains oddly away",
                "14D.Son managed to withdraw Euros and wire",
                "15D.Opposition is surrounded by others, one apprehends alderman ultimately",
                "16D.Say, racing to promoters, to redeem debenture ultimately and consolidate",
                "20D.Arrive hurriedly to take CO into protection, perhaps",
                "22D.St. Helena blenders seized with warrant",
                "25D.Sketched a water droplet right inside",
                "26D.Made up a story and ushered first person inside",
                "27D.Former president of America spotted in Belize")
    }
}
