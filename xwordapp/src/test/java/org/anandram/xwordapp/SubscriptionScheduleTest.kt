package org.anandram.xwordapp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2026-08-22 is a Saturday; 2026-08-23 a Sunday; 2026-08-26 a Wednesday
 * whose Sunday-first week starts on 2026-08-23.
 */
class SubscriptionScheduleTest {

    private fun subscription(
            fetchFrequency: String,
            lastDownloadDate: String): Subscription =
            Subscription(name = "X",
                    fetchFrequency = fetchFrequency,
                    lastDownloadDate = lastDownloadDate)

    @Test
    fun neverDownloadedIsNeverSkipped() {
        assertFalse(subscription("One-Time", "").isSkipped("2026-08-26"))
        assertFalse(subscription("Weekly", "").isSkipped("2026-08-26"))
        assertFalse(subscription("Daily", "").isSkipped("2026-08-26"))
        assertFalse(subscription("Weekdays", "").isSkipped("2026-08-26"))
    }

    @Test
    fun oneTimeSkipsAfterAnyDownload() {
        val sub = subscription("One-Time", "2020-01-01")
        assertTrue(sub.isSkipped("2026-08-26"))
        assertTrue(sub.isSkipped("2020-01-01"))
    }

    @Test
    fun dailySkipsSameDayOnly() {
        val sub = subscription("Daily", "2026-08-25")
        assertTrue(sub.isSkipped("2026-08-25"))
        assertFalse(sub.isSkipped("2026-08-26"))
    }

    @Test
    fun weeklySkipsForRestOfWeekStartingSunday() {
        // Wednesday 2026-08-26 belongs to the week starting Sunday 2026-08-23.
        val sub23 = subscription("Weekly", "2026-08-23")
        assertTrue("last=23 today=26", sub23.isSkipped("2026-08-26"))
        val sub24 = subscription("Weekly", "2026-08-24")
        assertTrue("last=24 today=26", sub24.isSkipped("2026-08-26"))
        val sub22 = subscription("Weekly", "2026-08-22")
        assertFalse("last=22 today=26", sub22.isSkipped("2026-08-26"))

        // A download made later that same week is skipped through Saturday.
        assertTrue(subscription("Weekly", "2026-08-24").isSkipped("2026-08-28"))
        // The next Sunday starts a new week and re-downloads.
        assertFalse(subscription("Weekly", "2026-08-23").isSkipped("2026-08-30"))
        assertFalse(subscription("Weekly", "2026-08-24").isSkipped("2026-08-30"))
    }

    @Test
    fun weekdaysSkipWeekendsEntirelyAfterFirstRun() {
        val sub = subscription("Weekdays", "2026-08-28") // Friday

        assertTrue(sub.isSkipped("2026-08-29")) // Saturday
        assertTrue(sub.isSkipped("2026-08-30")) // Sunday
        assertFalse(sub.isSkipped("2026-08-31")) // Monday
    }

    @Test
    fun weekdaysOnWeekdaySkipsSameDayOnly() {
        val sub = subscription("Weekdays", "2026-08-27") // Thursday

        assertTrue(sub.isSkipped("2026-08-27"))
        assertFalse(sub.isSkipped("2026-08-28"))
    }

    @Test
    fun unknownFrequencyAlwaysSkipsOnceDownloaded() {
        val sub = subscription("Whenever", "2020-01-01")
        assertTrue(sub.isSkipped("2026-08-26"))
    }
}
