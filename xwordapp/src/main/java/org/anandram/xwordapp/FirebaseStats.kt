package org.anandram.xwordapp

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Thin, never-crashing gateway to the Firebase SDKs. Every call is guarded so
 * that reporting never interferes with the app: environments without a
 * default FirebaseApp (plain JVM/Robolectric unit tests, builds without
 * google-services) silently fall back to defaults instead of throwing.
 *
 * Free-tier conscious: Analytics events are rate-limited by [rateKey] where
 * noted, Remote Config is fetched at most once per session (well under the
 * 5 fetches/hour/device cap) and custom keys stay far below Crashlytics'
 * 64-key ceiling.
 */
object FirebaseStats {

    /** Remote Config keys with their (safe) local defaults. */
    private const val KEY_VERBOSE_SCRAPE = "verbose_scrape_logs"
    private const val DEFAULT_VERBOSE_SCRAPE = false
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    @Volatile
    private var app: Context? = null

    @Volatile
    private var remoteFetched = false

    private val rateLimitedEvents = ConcurrentHashMap<String, Long>()

    fun attach(context: Context) {
        if (app != null) return
        app = context.applicationContext
    }

    private inline fun <T> guarded(block: () -> T): T? = try {
        block()
    } catch (_: Throwable) {
        null
    }

    /** Appends a line to the Crashlytics breadcrumb log shipped with the next report. */
    fun log(message: String) {
        guarded { FirebaseCrashlytics.getInstance().log(message) }
    }

    /** Session-scoped custom key that accompanies every crash report until changed. */
    fun setCustomKey(key: String, value: String) {
        guarded { FirebaseCrashlytics.getInstance().setCustomKey(key, value) }
    }

    fun setCustomKey(key: String, value: Long) {
        guarded { FirebaseCrashlytics.getInstance().setCustomKey(key, value) }
    }

    fun setCustomKey(key: String, value: Boolean) {
        guarded { FirebaseCrashlytics.getInstance().setCustomKey(key, value) }
    }

    /**
     * Logs an analytics event. Parameter values must be of a primitive type
     * (String/Int/Long/Double/Boolean); anything else is skipped.
     */
    fun logEvent(context: Context, name: String, params: Map<String, Any?> = emptyMap()) {
        val ctx = context.applicationContext
        guarded {
            val bundle = Bundle()
            for ((key, value) in params) {
                when (value) {
                    is String -> bundle.putString(key, value)
                    is Int -> bundle.putInt(key, value)
                    is Long -> bundle.putLong(key, value)
                    is Double -> bundle.putDouble(key, value)
                    is Boolean -> bundle.putBoolean(key, value)
                    else -> {}
                }
            }
            FirebaseAnalytics.getInstance(ctx).logEvent(name, bundle)
        }
    }

    /**
     * Logs [name] at most once per [minIntervalMillis] per [rateKey] (tracked
     * in memory). Keeps high-signal diagnostics out of Analytics' per-user
     * event budget.
     */
    fun logEventRateLimited(context: Context, rateKey: String, name: String,
                            params: Map<String, Any?> = emptyMap(),
                            minIntervalMillis: Long = DAY_MS) {
        val now = System.currentTimeMillis()
        val last = rateLimitedEvents[rateKey] ?: 0L
        if (now - last < minIntervalMillis) return
        rateLimitedEvents[rateKey] = now
        logEvent(context, name, params)
    }

    /** Reports a non-fatal exception, attaching a few custom keys for grouping. */
    fun recordException(t: Throwable, keys: Map<String, String> = emptyMap()) {
        guarded {
            val crashlytics = FirebaseCrashlytics.getInstance()
            for ((key, value) in keys) crashlytics.setCustomKey(key, value)
            crashlytics.recordException(t)
        }
    }

    /**
     * Runs [block] under a manual Firebase Performance trace. When the perf
     * SDK is unavailable the block still runs, untraced.
     */
    fun <T> trace(name: String, block: () -> T): T {
        val trace = guarded { FirebasePerformance.getInstance().newTrace(name) }
        if (trace != null) {
            val started = guarded { trace.start(); true } == true
            if (started) {
                try {
                    return block()
                } finally {
                    guarded { trace.stop() }
                }
            }
        }
        return block()
    }

    /**
     * Per-source kill switch. Publish `disabled_<source>` = true in the
     * Firebase console to stop a broken scraper without shipping an update.
     * The flag is inverted on purpose: an absent/never-published value always
     * reads as "enabled", so an in-flight Remote Config miss can never
     * silently disable a source.
     */
    fun sourceEnabled(sourceName: String): Boolean {
        if (app == null) return true
        remoteConfigIfNeeded()
        val key = "disabled_" + sourceKey(sourceName)
        val disabled = guarded { FirebaseRemoteConfig.getInstance().getBoolean(key) } ?: false
        return !disabled
    }

    /**
     * Reads a Remote Config long with a local fallback. Applies the default
     * immediately; the background fetch+activate runs at most once per
     * session, so the first read uses [default] and later reads pick up
     * server values. Values below 1 (including "not set yet") resolve to
     * [default].
     */
    fun sweepCap(key: String, default: Long): Long {
        if (app == null) return default
        remoteConfigIfNeeded()
        val value = guarded { FirebaseRemoteConfig.getInstance().getLong(key) }
                ?: return default
        return if (value >= 1) value else default
    }

    /**
     * Debug aid: with `verbose_scrape_logs` switched on in the console (and
     * the user retrying), each fetch becomes a Crashlytics breadcrumb so the
     * next report carries exactly which URLs were hit and which failed.
     */
    fun scrapeLog(message: String) {
        if (!verboseScrapeLogs()) return
        log(message)
    }

    private fun verboseScrapeLogs(): Boolean {
        if (app == null) return DEFAULT_VERBOSE_SCRAPE
        remoteConfigIfNeeded()
        return guarded { FirebaseRemoteConfig.getInstance()
                .getBoolean(KEY_VERBOSE_SCRAPE) } ?: DEFAULT_VERBOSE_SCRAPE
    }

    private fun remoteConfigIfNeeded() {
        if (remoteFetched) return
        remoteFetched = true
        guarded { FirebaseRemoteConfig.getInstance().fetchAndActivate() }
    }

    private fun sourceKey(sourceName: String): String =
            sourceName.lowercase(Locale.US).replace(Regex("[^a-z0-9_]"), "_")
}