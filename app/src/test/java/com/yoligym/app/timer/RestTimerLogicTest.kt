package com.yoligym.app.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestTimerLogicTest {

    @Test
    fun `computeRemainingSeconds handles exact seconds ceiling rounding and expiration`() {
        val now = 1_000_000L
        assertEquals(90, RestTimerLogic.computeRemainingSeconds(now + 90_000L, now))
        assertEquals(90, RestTimerLogic.computeRemainingSeconds(now + 89_001L, now))
        assertEquals(1, RestTimerLogic.computeRemainingSeconds(now + 1L, now))
        assertEquals(0, RestTimerLogic.computeRemainingSeconds(now, now))
        assertEquals(0, RestTimerLogic.computeRemainingSeconds(now - 5_000L, now))
    }

    @Test
    fun `computeAdjustedTimer handles additions subtractions upper clamp and stop`() {
        val now = 1_000_000L

        val plus15 = RestTimerLogic.computeAdjustedTimer(
            currentEndMillis = now + 45_000L,
            currentTotalSeconds = 90,
            deltaSeconds = 15,
            nowMillis = now
        )
        assertFalse(plus15.shouldStop)
        assertEquals(60, plus15.remainingSeconds)
        assertEquals(90, plus15.totalSeconds)
        assertEquals(now + 60_000L, plus15.endTimeMillis)

        val extendedTotal = RestTimerLogic.computeAdjustedTimer(
            currentEndMillis = now + 80_000L,
            currentTotalSeconds = 90,
            deltaSeconds = 30,
            nowMillis = now
        )
        assertFalse(extendedTotal.shouldStop)
        assertEquals(110, extendedTotal.remainingSeconds)
        assertEquals(110, extendedTotal.totalSeconds)

        val clampedMax = RestTimerLogic.computeAdjustedTimer(
            currentEndMillis = now + 590_000L,
            currentTotalSeconds = 590,
            deltaSeconds = 30,
            nowMillis = now
        )
        assertFalse(clampedMax.shouldStop)
        assertEquals(600, clampedMax.remainingSeconds)
        assertEquals(600, clampedMax.totalSeconds)
        assertEquals(now + 600_000L, clampedMax.endTimeMillis)

        val stopped = RestTimerLogic.computeAdjustedTimer(
            currentEndMillis = now + 10_000L,
            currentTotalSeconds = 90,
            deltaSeconds = -15,
            nowMillis = now
        )
        assertTrue(stopped.shouldStop)
        assertEquals(0, stopped.remainingSeconds)
        assertEquals(0L, stopped.endTimeMillis)
    }

    @Test
    fun `shouldShowBackgroundAlert covers all notify and foreground combinations`() {
        assertTrue(RestTimerLogic.shouldShowBackgroundAlert(notifyEnabled = true, isAppInForeground = false))
        assertFalse(RestTimerLogic.shouldShowBackgroundAlert(notifyEnabled = true, isAppInForeground = true))
        assertFalse(RestTimerLogic.shouldShowBackgroundAlert(notifyEnabled = false, isAppInForeground = false))
        assertFalse(RestTimerLogic.shouldShowBackgroundAlert(notifyEnabled = false, isAppInForeground = true))
    }
}
