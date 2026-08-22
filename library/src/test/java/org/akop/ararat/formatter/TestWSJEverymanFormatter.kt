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

import org.akop.ararat.io.WSJFormatter
import org.junit.Test


class TestWSJEverymanFormatter : BaseTest() {

    val crossword = WSJFormatter().load("everyman.json")

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
                squareCount = 158,
                title = "Observer Everyman 4144",
                flags = 0,
                description = "Plain",
                author = "Test Setter",
                copyright = "",
                comment = null,
                date = 1774162800000,
                hash = "522e9693a2be8f005c44056ec011f05c6ad7684e")
        val layout = arrayOf(
                "ARCS#PROCEDURES",
                "P#A###U#O#O#A#I",
                "OINK#CLAMOURING",
                "C#O#K#I#E#B#D#N",
                "RAFAELNADAL####",
                "Y#W#E#G#I#E#S#S",
                "PROMPTS#CASHCOW",
                "H#R#I#####C#H#E",
                "ARMENIA#PROVOKE",
                "L#S#G#N#R#T#O#P",
                "####WAKEUPCALLS",
                "S#F#A#L#S#H#W#T",
                "CHARTREUSE#CODA",
                "U#I#C#T#I###R#K",
                "MURPHYSLAW#SKYE")
        val hints = arrayOf(
                "1A.Cockney listens - and bows",
                "3A.Reproduces dodgy systems",
                "9A.Sound from pen - why pen no use?",
                "10A.Clutch, clutching loved one, making racket",
                "12A.Playing Lendl: a four-time French tennis ace",
                "15A.Spurs very quietly storm off ...",
                "16A.... decrepit coach, exhausted, wants steady source of income",
                "17A.Beggar me! Niagara's surrounding country",
                "19A.In favour of 'very average'!? Everyman's beginning to annoy",
                "20A.Warnings as Spooner relays outcome of birthday-party food fight",
                "23A.Once again, apply following diagram depicting something intoxicating",
                "24A.End-piece: a physician taken aback",
                "25A.It says you'll go wrong adding potato to cabbage salad",
                "26A.Island on vacation - Sark - yippee!",
                "1D.Silly Carol, happy to be uncertain",
                "2D.Tricky situation: farm with no cows to flounder",
                "4D.Decrees - when you've repeatedly drawn a line?",
                "5D.Some welcome Dickensian characters being amusing",
                "6D.Twin to put lid on some whisky",
                "7D.Most of logbook picked up in police swoop",
                "8D.Notice function making noise",
                "11D.What guard's doing: not selling retirement gift?",
                "13D.Assignments making reformed crooks howl",
                "14D.Chimney-cleaners draw in drawing of lots",
                "18D.Bling: grotesque slanket",
                "19D.Initially powerful realm; ultimately subservient state ignobly abolished!",
                "21D.Mucky film showing Special Constable with hesitant expression",
                "22D.Decent trade show")
    }
}
