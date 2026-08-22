package org.anandram.xwordapp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SubscriptionManagerStorageTest {

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    @Test
    fun initSeedsDefaultsIntoFreshStorage() {
        SubscriptionManager.initForTests(context())

        val subs = SubscriptionManager.getSubscriptions()
        assertTrue(subs.isNotEmpty())
        assertTrue(subs.any { it.name == "Aries Cryptics" })
        assertTrue(subs.any { it.name == "The Guardian" })
    }

    @Test
    fun markDownloadStartedPersists() {
        SubscriptionManager.initForTests(context())

        SubscriptionManager.markDownloadStarted("The Guardian", "2026-08-22")

        // Re-read from disk (fresh getSubscriptions) to prove persistence.
        assertEquals("2026-08-22",
                SubscriptionManager.getSubscriptions()
                        .first { it.name == "The Guardian" }.lastDownloadDate)
    }

    @Test
    fun saveAndGetRoundTripPreservesNeedCreds() {
        SubscriptionManager.initForTests(context())

        val custom = Subscription(name = "Paywalled", url = "https://x.example/",
                fetchFrequency = "Weekly", needCreds = true)
        SubscriptionManager.saveSubscriptions(listOf(custom))

        val loaded = SubscriptionManager.getSubscriptions()
        assertEquals(1, loaded.size)
        assertTrue(loaded[0].needCreds)
        assertFalse(loaded[0].enabled.not()) // sanity: default preserved through Gson
    }
}
