package org.anandram.xwordapp

data class Subscription(
        val name: String = "",
        val url: String = "",
        val enabled: Boolean = true,
        val fetchFrequency: String = "One-Time",
        val lastDownloadDate: String = "")