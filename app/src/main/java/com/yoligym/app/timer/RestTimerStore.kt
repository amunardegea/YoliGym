package com.yoligym.app.timer

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences

object RestTimerStore {
    private const val PREFS_NAME = "yoligym_prefs"
    private const val KEY_TIMER_ID = "rest_timer_id"
    private const val KEY_END_TIME_MS = "rest_timer_end_ms"
    private const val KEY_TOTAL_SEC = "rest_timer_total_sec"
    private const val KEY_NOTIFY_BG = "rest_timer_notify_bg"
    private const val KEY_VIBRATE = "rest_timer_vibrate"
    private const val KEY_COMPLETED_ID = "rest_timer_completed_id"

    private val lock = Any()

    private fun prefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @SuppressLint("ApplySharedPref")
    fun saveActiveTimer(
        context: Context,
        endTimeMillis: Long,
        totalSeconds: Int,
        notifyInBackground: Boolean,
        vibrateOnFinish: Boolean
    ): RestTimerSnapshot {
        synchronized(lock) {
            val p = prefs(context)
            val lastId = p.getLong(KEY_TIMER_ID, 0L)
            val timerId = maxOf(System.currentTimeMillis(), lastId + 1L)
            p.edit()
                .putLong(KEY_TIMER_ID, timerId)
                .putLong(KEY_END_TIME_MS, endTimeMillis)
                .putInt(KEY_TOTAL_SEC, totalSeconds)
                .putBoolean(KEY_NOTIFY_BG, notifyInBackground)
                .putBoolean(KEY_VIBRATE, vibrateOnFinish)
                .commit()
            return RestTimerSnapshot(
                timerId = timerId,
                endTimeMillis = endTimeMillis,
                totalSeconds = totalSeconds,
                notifyInBackground = notifyInBackground,
                vibrateOnFinish = vibrateOnFinish
            )
        }
    }

    fun getActiveTimer(context: Context): RestTimerSnapshot? {
        synchronized(lock) {
            val p = prefs(context)
            val endTimeMillis = p.getLong(KEY_END_TIME_MS, 0L)
            val timerId = p.getLong(KEY_TIMER_ID, 0L)
            val completedId = p.getLong(KEY_COMPLETED_ID, -1L)
            if (endTimeMillis <= 0L || timerId <= 0L || completedId == timerId) {
                return null
            }
            return RestTimerSnapshot(
                timerId = timerId,
                endTimeMillis = endTimeMillis,
                totalSeconds = p.getInt(KEY_TOTAL_SEC, 90),
                notifyInBackground = p.getBoolean(KEY_NOTIFY_BG, true),
                vibrateOnFinish = p.getBoolean(KEY_VIBRATE, true)
            )
        }
    }

    @SuppressLint("ApplySharedPref")
    fun claimCompletion(context: Context, timerId: Long): RestTimerSnapshot? {
        synchronized(lock) {
            if (timerId <= 0L) return null
            val p = prefs(context)
            val activeId = p.getLong(KEY_TIMER_ID, 0L)
            val endTimeMillis = p.getLong(KEY_END_TIME_MS, 0L)
            val completedId = p.getLong(KEY_COMPLETED_ID, -1L)
            if (activeId != timerId || endTimeMillis <= 0L || completedId == timerId) {
                return null
            }
            val snapshot = RestTimerSnapshot(
                timerId = activeId,
                endTimeMillis = endTimeMillis,
                totalSeconds = p.getInt(KEY_TOTAL_SEC, 90),
                notifyInBackground = p.getBoolean(KEY_NOTIFY_BG, true),
                vibrateOnFinish = p.getBoolean(KEY_VIBRATE, true)
            )
            p.edit()
                .putLong(KEY_COMPLETED_ID, timerId)
                .putLong(KEY_END_TIME_MS, 0L)
                .putInt(KEY_TOTAL_SEC, 0)
                .commit()
            return snapshot
        }
    }

    @SuppressLint("ApplySharedPref")
    fun clear(context: Context) {
        synchronized(lock) {
            prefs(context).edit()
                .putLong(KEY_END_TIME_MS, 0L)
                .putInt(KEY_TOTAL_SEC, 0)
                .commit()
        }
    }
}
