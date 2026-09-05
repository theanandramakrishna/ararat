package org.anandram.xwordapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards FirebaseStats' no-Firebase degradation: in unit tests (Robolectric
 * never initializes a default FirebaseApp) every telemetry call must be a
 * silent no-op, never throw, and every Remote Config lookup must fall back to
 * the caller's default — including the safe "source enabled" default for the
 * inverted kill switch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FirebaseStatsTest {

    @Before
    fun setUp() {
        FirebaseStats.resetForTests()
    }

    @Test
    fun logEventIsNoOpWithoutFirebase() {
        FirebaseStats.logEvent("test_event", mapOf("key" to "value", "n" to 3L))
    }

    @Test
    fun rateLimitedEventIsNoOpWithoutFirebase() {
        FirebaseStats.logEventRateLimited("rate_key", "test_event", mapOf("key" to "value"))
        FirebaseStats.logEventRateLimited("rate_key", "test_event", mapOf("key" to "value"))
    }

    @Test
    fun breadcrumbIsNoOpWithoutFirebase() {
        FirebaseStats.log("test breadcrumb")
    }

    @Test
    fun recordExceptionIsNoOpWithoutFirebase() {
        FirebaseStats.recordException(RuntimeException("boom"), "test_key", gid = "abc12345")
        FirebaseStats.recordException(RuntimeException("boom"))
    }

    @Test
    fun customKeysAreNoOpWithoutFirebase() {
        FirebaseStats.setCustomKey("string_key", "value")
        FirebaseStats.setCustomKey("long_key", 7L)
        FirebaseStats.setCustomKey("bool_key", true)
    }

    @Test
    fun maxPerSweepFallsBackToDefault() {
        assertEquals(30, FirebaseStats.maxPerSweep("newyorker", 30))
        assertEquals(3, FirebaseStats.maxPerSweep("guardian", 3))
    }

    @Test
    fun sourceEnabledByDefault() {
        assertFalse(FirebaseStats.isSourceDisabled("newyorker"))
        assertFalse(FirebaseStats.isSourceDisabled("irishnews"))
    }

    @Test
    fun verboseScrapeLogsDefaultsOff() {
        assertFalse(FirebaseStats.verboseScrapeLogs())
    }

    @Test
    fun tracesDegradeToNull() {
        val trace = FirebaseStats.startTrace(FirebaseStats.TRACE_SUBSCRIPTION_DOWNLOAD)
        assertNull(trace)
        FirebaseStats.stopTrace(trace)
    }
}