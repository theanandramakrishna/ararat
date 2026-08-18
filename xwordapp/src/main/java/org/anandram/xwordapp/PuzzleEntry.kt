package org.anandram.xwordapp

data class PuzzleEntry(
        val id: String = "",
        val title: String = "",
        val author: String? = null,
        val fileName: String = "",
        val modified: Long = 0)