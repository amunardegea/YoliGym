package com.yoligym.app.timer

import kotlin.math.ceil

data class RestTimerSnapshot(
    val timerId: Long,
    val endTimeMillis: Long,
    val totalSeconds: Int,
    val notifyInBackground: Boolean,
    val vibrateOnFinish: Boolean
)

data class AdjustedTimerResult(
    val remainingSeconds: Int,
    val totalSeconds: Int,
    val endTimeMillis: Long,
    val shouldStop: Boolean
)

object RestTimerLogic {
    const val MAX_REST_SECONDS = 600

    fun computeRemainingSeconds(endTimeMillis: Long, nowMillis: Long): Int {
        if (endTimeMillis <= nowMillis) return 0
        return ceil((endTimeMillis - nowMillis) / 1000.0).toInt()
    }

    fun computeAdjustedTimer(
        currentEndMillis: Long,
        currentTotalSeconds: Int,
        deltaSeconds: Int,
        nowMillis: Long,
        maxSeconds: Int = MAX_REST_SECONDS
    ): AdjustedTimerResult {
        val currentRemaining = computeRemainingSeconds(currentEndMillis, nowMillis)
        val updated = (currentRemaining + deltaSeconds).coerceIn(0, maxSeconds)
        val newTotal = maxOf(currentTotalSeconds, updated, 15)
        val newEnd = if (updated > 0) nowMillis + updated * 1000L else 0L
        return AdjustedTimerResult(
            remainingSeconds = updated,
            totalSeconds = newTotal,
            endTimeMillis = newEnd,
            shouldStop = updated <= 0
        )
    }

    fun shouldShowBackgroundAlert(notifyEnabled: Boolean, isAppInForeground: Boolean): Boolean {
        return notifyEnabled && !isAppInForeground
    }
}
