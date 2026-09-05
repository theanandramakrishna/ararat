package org.anandram.xwordapp

import android.os.Bundle
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.perf.metrics.Trace
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The only place the app talks to Firebase. Every call is guarded so the app
 * never crashes, blocks, or throws because of telemetry — in plain-JVM and
 * Robolectric builds (no FirebaseApp) everything is a silent no-op and all
 * Remote Config lookups fall back to the caller's local default.
 *
 * Remote Config is fetched and activated at most once per session; callers
 * always read from the in-memory cache, so a missing/failed fetch falls back
 * to local defaults (including the "source enabled" default, which keeps any
 * failure from silently disabling a source).
 *
 * Privacy: never pass raw share payloads here; gids are truncated to 8 chars.
 */
object FirebaseStats {

    const val EVENT_SUBSCRIPTION_DOWNLOAD = "subscription_download"
    const val EVENT_SCRAPER_FAILURE = "scraper_failure"
    const val EVENT_PUZZLE_COMPLETED = "puzzle_completed"
    const val EVENT_JOIN_GAME_START = "join_game_start"
    const val EVENT_JOIN_GAME_SUCCEEDED = "join_game_succeeded"
    const val EVENT_JOIN_GAME_DUPLICATE = "join_game_duplicate"
    const val EVENT_JOIN_GAME_FAILED = "join_game_failed"
    const val EVENT_JOIN_GAME_SHARE_RECEIVED = "join_game_share_received"

    const val TRACE_SUBSCRIPTION_DOWNLOAD = "subscription_download"

    private const val TAG = "FirebaseStats"

    private const val MAX_PER_SWEEP_PREFIX = "max_per_sweep_"
    private const val DISABLED_PREFIX = "disabled_"
    private const val VERBOSE_SCRAPE_LOGS = "verbose_scrape_logs"

    private val RATE_LIMIT_WINDOW_MS = 24L * 60 * 60 * 1000

    private val remoteFetched = AtomicBoolean(false)
    private val remoteConfigs = ConcurrentHashMap<String, FirebaseRemoteConfigValue>()
    private val rateLimits = ConcurrentHashMap<String, Long>()

    /**
     * True when a default FirebaseApp exists (i.e. Crashlytics/Analytics are
     * actually available in this build). FirebaseApp is initialized by
     * FirebaseInitProvider at app start; Robolectric and plain-JVM builds never
     * initialize one, so they short-circuit to fallbacks.
     */
    private val available by lazy {
        try {
            FirebaseApp.getInstance() != null
        } catch (t: Throwable) {
            Log.d(TAG, "Firebase unavailable; telemetry disabled", t)
            false
        }
    }

    // --- Analytics --------------------------------------------------------

    fun logEvent(name: String, params: Map<String, Any>) {
        if (!available) return
        try {
            val context = FirebaseApp.getInstance().applicationContext
            FirebaseAnalytics.getInstance(context).logEvent(name, toBundle(params))
        } catch (t: Throwable) {
            Log.d(TAG, "logEvent($name) failed", t)
        }
    }

    /** [logEvent] but at most once per [key] per 24 h per session. */
    fun logEventRateLimited(key: String, name: String, params: Map<String, Any>) {
        val now = System.currentTimeMillis()
        if (now - (rateLimits[key] ?: 0L) < RATE_LIMIT_WINDOW_MS) return
        rateLimits[key] = now
        logEvent(name, params)
    }

    private fun toBundle(params: Map<String, Any>): Bundle {
        val bundle = Bundle()
        for ((key, value) in params) {
            when (value) {
                is String -> bundle.putString(key, value)
                is Long -> bundle.putLong(key, value)
                is Int -> bundle.putLong(key, value.toLong())
                is Boolean -> bundle.putBoolean(key, value)
                is Double -> bundle.putDouble(key, value)
                is Float -> bundle.putDouble(key, value.toDouble())
                else -> bundle.putString(key, value.toString())
            }
        }
        return bundle
    }

    // --- Crashlytics ------------------------------------------------------

    /** Appends a breadcrumb to the current Crashlytics session report. */
    fun log(message: String) {
        if (!available) return
        try {
            FirebaseCrashlytics.getInstance().log(message)
        } catch (t: Throwable) {
            Log.d(TAG, "log() failed", t)
        }
    }

    /**
     * Records [e] as a non-fatal. [key] is written as a custom key (bounded
     * label -> short value) so the report is greppable; when [gid] is given it
     * is stored truncated (8 chars) under `cwf_gid` for the CWF flows.
     */
    fun recordException(e: Throwable, key: String? = null, gid: String? = null) {
        if (!available) return
        try {
            val crashlytics = FirebaseCrashlytics.getInstance()
            if (key != null) {
                crashlytics.setCustomKey(key,
                        (e.message ?: e.javaClass.simpleName).take(64))
            }
            if (gid != null) crashlytics.setCustomKey("cwf_gid", gid.take(8))
            crashlytics.recordException(e)
        } catch (t: Throwable) {
            Log.d(TAG, "recordException() failed", t)
        }
    }

    fun setCustomKey(key: String, value: String) {
        if (!available) return
        try {
            FirebaseCrashlytics.getInstance().setCustomKey(key, value)
        } catch (t: Throwable) {
            Log.d(TAG, "setCustomKey($key) failed", t)
        }
    }

    fun setCustomKey(key: String, value: Long) {
        if (!available) return
        try {
            FirebaseCrashlytics.getInstance().setCustomKey(key, value)
        } catch (t: Throwable) {
            Log.d(TAG, "setCustomKey($key) failed", t)
        }
    }

    fun setCustomKey(key: String, value: Boolean) {
        if (!available) return
        try {
            FirebaseCrashlytics.getInstance().setCustomKey(key, value)
        } catch (t: Throwable) {
            Log.d(TAG, "setCustomKey($key) failed", t)
        }
    }

    // --- Remote Config ----------------------------------------------------

    /**
     * Fetches and activates Remote Config once per session. Values are cached
     * in [remoteConfigs]; with Firebase absent, or when the fetch has not yet
     * completed, lookups return the caller's fallback. Never blocks the
     * calling thread.
     */
    fun ensureRemoteConfigFetched() {
        if (!available) return
        if (!remoteFetched.compareAndSet(false, true)) return
        try {
            val config = FirebaseRemoteConfig.getInstance()
            config.setConfigSettingsAsync(FirebaseRemoteConfigSettings.Builder()
                    .setMinimumFetchIntervalInSeconds(60 * 60)
                    .build())
            config.fetchAndActivate().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    remoteConfigs.clear()
                    remoteConfigs.putAll(config.all)
                } else {
                    Log.d(TAG, "Remote Config fetch failed",
                            task.exception ?: IllegalStateException("fetch failed"))
                }
            }
        } catch (t: Throwable) {
            remoteFetched.set(false)
            Log.d(TAG, "ensureRemoteConfigFetched() failed", t)
        }
    }

    /**
     * Sweep cap for a scraper. Only a value published by the server (REMOTE
     * source) with count >= 1 is honored; anything else (missing, default,
     * stale, zero) falls back to the caller's [fallback].
     */
    fun maxPerSweep(sourceKey: String, fallback: Int): Int {
        ensureRemoteConfigFetched()
        val value = remoteConfigs[MAX_PER_SWEEP_PREFIX + sourceKey] ?: return fallback
        if (value.source != FirebaseRemoteConfig.VALUE_SOURCE_REMOTE) return fallback
        val count = value.asLong()
        return if (count > 0) count.toInt() else fallback
    }

    /**
     * Inverted kill switch: a source is disabled only when the server has
     * explicitly published `disabled_<sourceKey> = true`. Absence (or any
     * fetch mishap) always reads *enabled*, so a config problem can never
     * silently stop downloads.
     */
    fun isSourceDisabled(sourceKey: String): Boolean {
        ensureRemoteConfigFetched()
        val value = remoteConfigs[DISABLED_PREFIX + sourceKey] ?: return false
        return value.source == FirebaseRemoteConfig.VALUE_SOURCE_REMOTE && value.asBoolean()
    }

    /** Opt-in per-URL scrape breadcrumbs for debugging a user's download. */
    fun verboseScrapeLogs(): Boolean {
        ensureRemoteConfigFetched()
        val value = remoteConfigs[VERBOSE_SCRAPE_LOGS] ?: return false
        return value.source == FirebaseRemoteConfig.VALUE_SOURCE_REMOTE && value.asBoolean()
    }

    // --- Performance ------------------------------------------------------

    fun startTrace(name: String): Trace? {
        if (!available) return null
        return try {
            FirebasePerformance.getInstance().newTrace(name).apply { start() }
        } catch (t: Throwable) {
            Log.d(TAG, "startTrace($name) failed", t)
            null
        }
    }

    fun stopTrace(trace: Trace?) {
        if (trace == null) return
        try {
            trace.stop()
        } catch (t: Throwable) {
            Log.d(TAG, "stopTrace() failed", t)
        }
    }

    // --- Tests ------------------------------------------------------------

    /** Clears session-level state so tests are independent and deterministic. */
    internal fun resetForTests() {
        remoteFetched.set(false)
        remoteConfigs.clear()
        rateLimits.clear()
    }
}