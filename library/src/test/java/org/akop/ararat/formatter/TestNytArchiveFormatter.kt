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

import org.akop.ararat.io.NytArchiveFormatter
import org.junit.Test


class TestNytArchiveFormatter : BaseTest() {

    val crossword = NytArchiveFormatter().load("nyt-archive.txt")

    @Test
    fun crossword_testMetadata() {
        assertMetadata(crossword, metadata)
    }

    @Test
    fun crossword_testLayout() {
        assertLayout(crossword, layout)
    }

    @Test
    fun crossword_testAttrLayout() {
        assertAttrLayout(crossword, attrLayout)
    }

    @Test
    fun crossword_testHints() {
        assertHints(crossword, hints)
    }

    companion object {
        val metadata = Metadata(
                width = 3,
                height = 3,
                squareCount = 8,
                title = "Tiny Test",
                flags = 0,
                description = null,
                author = "Jane Doe",
                copyright = null,
                comment = "A note",
                date = 1788220800000,
                hash = "177abe3a56edc1ebd1d714a51c753215e7ba7e42")
        val layout: Array<Array<String?>> = arrayOf(
                arrayOf("AB", "C", "D"),
                arrayOf("E", null, "F"),
                arrayOf("G", "H", "I"))
        val attrLayout: Array<Array<String?>> = arrayOf(
                arrayOf(".", ".", "."),
                arrayOf(".", "#", "."),
                arrayOf(".", "O", "."))
        val hints = arrayOf(
                "1A.First across",
                "3A.Second across",
                "1D.First down",
                "2D.Second down")
    }
}
