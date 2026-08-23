package org.anandram.xwordapp

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionTest {

    private val gson = Gson()

    @Test
    fun gson_legacyEntryWithoutNeedCredsDefaultsToFalse() {
        val json = "{\"name\":\"X\",\"url\":\"https://x.example/\",\"enabled\":true," +
                "\"fetchFrequency\":\"Daily\",\"lastDownloadDate\":\"2026-08-22\"," +
                "\"puzzleFormat\":\"puz\"}"
        val subscription = gson.fromJson(json, Subscription::class.java)

        assertFalse(subscription.needCreds)
        assertEquals("X", subscription.name)
        assertEquals("puz", subscription.puzzleFormat)
    }

    @Test
    fun gson_needCredsRoundTrips() {
        val subscription = Subscription(name = "X", needCreds = true)
        val roundTripped = gson.fromJson(gson.toJson(subscription), Subscription::class.java)

        assertTrue(roundTripped.needCreds)
        assertEquals(subscription, roundTripped)
    }

    @Test
    fun gson_needCredsFalseSerializesExplicitlyAndReadsBackAsFalse() {
        val json = gson.toJson(Subscription(name = "X"))

        assertTrue(json.contains("\"needCreds\":false"))
        assertFalse(gson.fromJson(json, Subscription::class.java).needCreds)
    }

    @Test
    fun enableable_requiresCredentialsUntilStored() {
        val gated = Subscription(name = "X", needCreds = true)

        assertFalse(gated.isEnableable(hasCredentials = false))
        assertTrue(gated.isEnableable(hasCredentials = true))
    }

    @Test
    fun enableable_unconditionalWithoutNeedCreds() {
        val plain = Subscription(name = "X")

        assertTrue(plain.isEnableable(hasCredentials = false))
        assertTrue(plain.isEnableable(hasCredentials = true))
    }

    @Test
    fun downloadable_requiresCredentialsUntilStored() {
        val gated = Subscription(name = "X", needCreds = true, enabled = true)

        assertFalse(gated.isDownloadable(hasCredentials = false))
        assertTrue(gated.isDownloadable(hasCredentials = true))
    }

    @Test
    fun downloadable_unconditionalWithoutNeedCreds() {
        val plain = Subscription(name = "X", enabled = true)

        assertTrue(plain.isDownloadable(hasCredentials = false))
    }
}
