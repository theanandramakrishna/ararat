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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Base64

class TestAmuseLabsDecode {

    @Test
    fun decodeRawcHandlesObfuscatedPayload() {
        val rawc = File("src/test/res/hindu-rawc.txt").readText().trim()

        val decoded = AmuseLabsJsonFormatter.decodeRawc(rawc)

        assertNotNull(decoded)
        val root = org.json.JSONObject(decoded!!)
        assertEquals("14879", root.optString("title"))
    }

    @Test
    fun decodeRawcHandlesPlainBase64() {
        val json = """{"title":"Tiny","w":2,"h":1}"""
        val rawc = Base64.getEncoder().encodeToString(json.toByteArray())

        assertEquals(json, AmuseLabsJsonFormatter.decodeRawc(rawc))
    }

    @Test
    fun parseHandlesWithheldAnswers() {
        // Prize puzzles ship without answer strings; nBoxes gives extents and
        // letters come from the box grid.
        val json = "{\"title\":\"Sunday #70\",\"w\":2,\"h\":1," +
                "\"box\":[[\"A\"],[\"B\"]]," +
                "\"placedWords\":[{\"x\":\"0\",\"y\":\"0\"," +
                "\"acrossNotDown\":\"True\",\"direction\":\"E\",\"clueNum\":\"1\"," +
                "\"nBoxes\":\"2\",\"clue\":{\"clue\":\"Twice (2)\"}}]}"
        val cw = org.akop.ararat.core.buildCrossword {
            AmuseLabsJsonFormatter().read(this, json.byteInputStream())
        }
        org.junit.Assert.assertEquals(2, cw.width)
        org.junit.Assert.assertEquals("AB",
                cw.wordsAcross[0].cells.joinToString("") { it.chars })
    }

    @Test
    fun decodeRawcRejectsGarbage() {
        assertNull(AmuseLabsJsonFormatter.decodeRawc("not-a-rawc-payload!!"))
    }

    @Test
    fun extractRawcFindsEscapedAndBareFields() {
        assertTrue(AmuseLabsJsonFormatter.extractRawc(
                "<script>var p = {\"rawc\": \"abc123\"};</script>") == "abc123")
        assertNull(AmuseLabsJsonFormatter.extractRawc("<html></html>"))
    }
}
