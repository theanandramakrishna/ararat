package org.anandram.xwordapp

data class PuzzleEntry(
        val id: String = "",
        val title: String = "",
        val author: String? = null,
        val fileName: String = "",
        val modified: Long = 0,
        val source: String? = null,
        val downloadUrl: String? = null,
        val format: String = "puz")