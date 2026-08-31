package org.anandram.xwordapp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards Firebase-on-the-JVM: every FirebaseStats call must degrade to a
 * no-op/default/plain run when the Firebase SDK is not available, so plain
 * unit tests never crash on Firebase plumbing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FirebaseStatsTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun recordExceptionSwallowsWithoutFirebase() {
        FirebaseStats.recordException(
                RuntimeException("boom"), mapOf("source" to "test"))
    }

    @Test
    fun logEventSwallowsWithoutFirebase() {
        FirebaseStats.logEvent(context, "test_event",
                mapOf("format" to "puz", "count" to 3))
    }

    @Test
    fun sweepCapFallsBackToDefaultWithoutFirebase() {
        assertEquals(42L, FirebaseStats.sweepCap("max_per_sweep_test", 42L))
    }

    @Test
    fun sourceEnabledDefaultsToTrueWithoutFirebase() {
        assertEquals(true, FirebaseStats.sourceEnabled("The New Yorker"))
    }

    @Test
    fun breadcrumbsAndKeysSwallowWithoutFirebase() {
        FirebaseStats.log("test breadcrumb")
        FirebaseStats.setCustomKey("num_puzzles", 3L)
        FirebaseStats.setCustomKey("host", "test")
        FirebaseStats.scrapeLog("test fetch")
    }

    @Test
    fun rateLimitedEventSwallowsWithoutFirebase() {
        FirebaseStats.logEventRateLimited(context, "rate_key_test", "scraper_failure",
                mapOf("source" to "The Guardian"))
        FirebaseStats.logEventRateLimited(context, "rate_key_test", "scraper_failure",
                mapOf("source" to "The Guardian"))
    }

    @Test
    fun traceStillRunsBlockWithoutFirebase() {
        var ran = false
        val result = FirebaseStats.trace("test_trace") {
            ran = true
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(true, ran)
    }
}