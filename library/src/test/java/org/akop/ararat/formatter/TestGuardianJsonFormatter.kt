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

import org.akop.ararat.io.GuardianJsonFormatter
import org.junit.Test


class TestGuardianJsonFormatter : BaseTest() {

    val crossword = GuardianJsonFormatter().load("guardian.json")

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
                width = 5,
                height = 4,
                squareCount = 16,
                title = "Sample Guardian Cryptic",
                flags = 0,
                description = "Sample & instructions",
                author = "Test Setter",
                copyright = null,
                comment = null,
                date = 1785888000000,
                hash = "5f8562f61c6a61983ab7d11477709889cb70fa8a")
        val layout = arrayOf(
                "SPAIN",
                "#A#D#",
                "TREAT",
                "CA#TS")
        val hints = arrayOf(
                "1A.Iberian land (5)",
                "4A.Enjoy a meal (5)",
                "5A.Musical felines (4)",
                "6A.See 5",
                "2D.Dad (3)",
                "3D.MGM's lion (3)")
    }
}
