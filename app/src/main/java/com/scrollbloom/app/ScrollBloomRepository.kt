package com.scrollbloom.app

import android.content.Context
import kotlin.math.exp
import kotlin.math.ln

/**
 * Single source of truth for the MVP.
 *
 * Real-time state stays in memory in the service/UI, while compact derived
 * values are persisted. This avoids writing on every accessibility event.
 * Room can replace this persistence layer later without changing callers.
 */
class ScrollBloomRepository private constructor(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS,
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun recordShortVideo(
        packageName: String,
        confidence: Float,
        now: Long = System.currentTimeMillis()
    ): BehaviorSnapshot {
        rollDayIfNeeded(now)

        val previousEvent = prefs.getLong(KEY_LAST_EVENT, 0L)
        val previousLoad = currentLoad(now)
        val gap = if (previousEvent == 0L) Long.MAX_VALUE else now - previousEvent
        val sessionWasActive = previousEvent != 0L && gap <= SESSION_IDLE_MS

        var sessionCount = prefs.getInt(KEY_SESSION_COUNT, 0)
        var reentries = prefs.getInt(KEY_REENTRIES, 0)
        var longestBreak = prefs.getLong(KEY_LONGEST_BREAK, 0L)
        var sessionStart = prefs.getLong(KEY_SESSION_START, 0L)

        if (!sessionWasActive) {
            if (previousEvent != 0L && gap > 0L) {
                longestBreak = maxOf(longestBreak, gap)
                if (gap <= REENTRY_WINDOW_MS) {
                    reentries += 1
                }
            }
            sessionCount += 1
            sessionStart = now
        }

        // Confidence slightly affects load, not the permanent count.
        // Only callers that have already crossed the confirmation threshold arrive here.
        val confidenceFactor = confidence.coerceIn(0.75f, 1f)
        val rapidFactor = if (gap in 1L..RAPID_SCROLL_WINDOW_MS) 1.25f else 1f
        val eventLoad = BASE_EVENT_LOAD * confidenceFactor * rapidFactor
        val newLoad = (previousLoad + eventLoad).coerceIn(0f, 100f)

        prefs.edit()
            .putInt(KEY_COUNT, prefs.getInt(KEY_COUNT, 0) + 1)
            .putFloat(KEY_LOAD, newLoad)
            .putLong(KEY_LAST_EVENT, now)
            .putLong(KEY_SESSION_START, sessionStart)
            .putInt(KEY_SESSION_COUNT, sessionCount)
            .putInt(KEY_REENTRIES, reentries)
            .putLong(KEY_LONGEST_BREAK, longestBreak)
            .putString(KEY_DAY, dayKey(now))
            .apply()

        return snapshot(now)
    }

    @Synchronized
    fun snapshot(now: Long = System.currentTimeMillis()): BehaviorSnapshot {
        rollDayIfNeeded(now)

        val lastEvent = prefs.getLong(KEY_LAST_EVENT, 0L)
        val gap = if (lastEvent == 0L) Long.MAX_VALUE else now - lastEvent
        val active = lastEvent != 0L && gap <= SESSION_IDLE_MS
        val load = currentLoad(now)
        val health = (100f - load).coerceIn(0f, 100f).toInt()
        val recovery = lastEvent != 0L && gap >= RECOVERY_START_MS

        return BehaviorSnapshot(
            dailyCount = prefs.getInt(KEY_COUNT, 0),
            recentLoad = load,
            health = health,
            sessionActive = active,
            sessionDurationMs = if (active) now - prefs.getLong(KEY_SESSION_START, now) else 0L,
            sessionCount = prefs.getInt(KEY_SESSION_COUNT, 0),
            reentryCount = prefs.getInt(KEY_REENTRIES, 0),
            longestBreakMs = prefs.getLong(KEY_LONGEST_BREAK, 0L),
            recoveryActive = recovery,
            recoveryDurationMs = if (recovery) gap else 0L,
            lastEventAt = lastEvent
        )
    }

    @Synchronized
    fun resetForDebug() {
        prefs.edit().clear().apply()
    }

    private fun currentLoad(now: Long): Float {
        val storedLoad = prefs.getFloat(KEY_LOAD, 0f)
        val lastEvent = prefs.getLong(KEY_LAST_EVENT, 0L)
        if (storedLoad <= 0f || lastEvent <= 0L || now <= lastEvent) return storedLoad

        val elapsed = (now - lastEvent).coerceAtLeast(0L).toDouble()
        val lambda = ln(2.0) / RECOVERY_HALF_LIFE_MS
        return (storedLoad * exp(-lambda * elapsed)).toFloat().coerceIn(0f, 100f)
    }

    private fun rollDayIfNeeded(now: Long) {
        val today = dayKey(now)
        val storedDay = prefs.getString(KEY_DAY, null)
        if (storedDay == today) return

        prefs.edit()
            .putString(KEY_DAY, today)
            .putInt(KEY_COUNT, 0)
            .putFloat(KEY_LOAD, 0f)
            .putLong(KEY_LAST_EVENT, 0L)
            .putLong(KEY_SESSION_START, 0L)
            .putInt(KEY_SESSION_COUNT, 0)
            .putInt(KEY_REENTRIES, 0)
            .putLong(KEY_LONGEST_BREAK, 0L)
            .apply()
    }

    private fun dayKey(now: Long): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(now))

    companion object {
        private const val PREFS = "scrollbloom_prefs"
        private const val KEY_DAY = "day_key"
        private const val KEY_COUNT = "reel_count"
        private const val KEY_LOAD = "recent_load"
        private const val KEY_LAST_EVENT = "last_short_video_at"
        private const val KEY_SESSION_START = "session_start_at"
        private const val KEY_SESSION_COUNT = "session_count"
        private const val KEY_REENTRIES = "reentry_count"
        private const val KEY_LONGEST_BREAK = "longest_break_ms"

        private const val BASE_EVENT_LOAD = 4.0f
        private const val RAPID_SCROLL_WINDOW_MS = 3_500L
        private const val SESSION_IDLE_MS = 2 * 60 * 1000L
        private const val REENTRY_WINDOW_MS = 15 * 60 * 1000L
        private const val RECOVERY_START_MS = 2 * 60 * 1000L
        private const val RECOVERY_HALF_LIFE_MS = 15 * 60 * 1000.0

        @Volatile
        private var instance: ScrollBloomRepository? = null

        fun get(context: Context): ScrollBloomRepository =
            instance ?: synchronized(this) {
                instance ?: ScrollBloomRepository(context).also { instance = it }
            }
    }
}
