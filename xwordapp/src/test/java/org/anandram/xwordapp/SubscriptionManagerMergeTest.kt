package org.anandram.xwordapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionManagerMergeTest {

    private val firstDefault = listOf(
            Subscription(name = "Custom", url = "https://custom.example/", enabled = false),
            Subscription(name = "Old", url = "https://old.example/",
                    fetchFrequency = "Daily", lastDownloadDate = "2026-08-21"))

    @Test
    fun mergeIntoEmptyYieldsAllDefaults() {
        val merged = SubscriptionManager.mergeDefaults(emptyList())

        assertTrue(merged.isNotEmpty())
        assertTrue(merged.any { it.name == "Aries Cryptics" })
        assertTrue(merged.any { it.name == "The Guardian" })
        assertTrue(merged.any { it.name == "MyCrossword.co.uk Cryptic" })
    }

    @Test
    fun mergeAppendsOnlyMissingDefaults() {
        val allDefaults = SubscriptionManager.mergeDefaults(emptyList())
        val merged = SubscriptionManager.mergeDefaults(firstDefault)

        assertEquals(allDefaults.size + firstDefault.size, merged.size)
        // Custom entries preserved verbatim, at the front.
        assertSame(firstDefault[0], merged[0])
        assertSame(firstDefault[1], merged[1])
        assertEquals("Custom", merged[0].name)
        assertFalse(merged[0].enabled)
        assertEquals("2026-08-21", merged[1].lastDownloadDate)
    }

    @Test
    fun mergeDoesNotDuplicateExistingDefaultsByName() {
        val existing = firstDefault + listOf(Subscription(name = "The New Yorker",
                url = "https://user-edited.example/", enabled = true))

        val merged = SubscriptionManager.mergeDefaults(existing)

        // The user's row wins; no second copy of the default is appended.
        assertEquals(1, merged.count { it.name == "The New Yorker" })
        assertEquals("https://user-edited.example/",
                merged.first { it.name == "The New Yorker" }.url)
    }

    @Test
    fun mergeOfCompleteSetIsUnchanged() {
        val complete = SubscriptionManager.mergeDefaults(emptyList())
        assertSame(complete, SubscriptionManager.mergeDefaults(complete))
    }
}
