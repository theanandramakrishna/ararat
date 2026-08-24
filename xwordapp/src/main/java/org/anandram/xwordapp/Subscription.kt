package org.anandram.xwordapp

data class Subscription(
        val name: String = "",
        val url: String = "",
        val enabled: Boolean = false,
        val fetchFrequency: String = "One-Time",
        val lastDownloadDate: String = "",
        val puzzleFormat: String = "puz",
        val needCreds: Boolean = false)

/**
 * Whether the subscription may be toggled in the UI given whether its
 * credentials are stored. Subscriptions requiring credentials are locked
 * until they have them.
 */
fun Subscription.isEnableable(hasCredentials: Boolean): Boolean =
        !needCreds || hasCredentials

/**
 * Whether the download sweep may proceed for this subscription given whether
 * its credentials are stored.
 */
fun Subscription.isDownloadable(hasCredentials: Boolean): Boolean =
        !needCreds || hasCredentials

/** Whether an already-recorded last download date suppresses another run today. */
fun Subscription.isSkipped(today: String): Boolean {
    if (lastDownloadDate.isEmpty()) return false

    return when (fetchFrequency) {
        "Weekly" -> lastDownloadDate >= startOfWeek(today)
        "Daily" -> lastDownloadDate == today
        "Weekdays" -> !isWeekday(today) || lastDownloadDate == today
        else -> true
    }
}

private fun startOfWeek(today: String): String {
    val parts = today.split("-")
    val cal = java.util.Calendar.getInstance()
    cal.clear()
    cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
    // Step back to the most recent Sunday deterministically; resolving the
    // week via Calendar.set(DAY_OF_WEEK, ...) is ambiguous and can yield the
    // same day rather than the week's start.
    val daysSinceSunday =
            (cal.get(java.util.Calendar.DAY_OF_WEEK) + 7 - java.util.Calendar.SUNDAY) % 7
    cal.add(java.util.Calendar.DAY_OF_MONTH, -daysSinceSunday)
    return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cal.time)
}

private fun isWeekday(today: String): Boolean {
    val parts = today.split("-")
    val cal = java.util.Calendar.getInstance()
    cal.clear()
    cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
    val dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK)
    return dayOfWeek != java.util.Calendar.SATURDAY && dayOfWeek != java.util.Calendar.SUNDAY
}