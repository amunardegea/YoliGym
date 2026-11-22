package com.yoligym.app.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object RestTimerCoordinator {
    const val ACTION_REST_FINISHED = "com.yoligym.app.action.REST_FINISHED"
    const val ACTION_REST_ADD_15S = "com.yoligym.app.action.REST_ADD_15S"
    const val ACTION_REST_SKIP = "com.yoligym.app.action.REST_SKIP"
    const val EXTRA_TIMER_ID = "extra_timer_id"

    private const val ALARM_REQUEST_CODE = 2100

    fun startTimer(
        context: Context,
        durationSeconds: Int,
        notifyInBackground: Boolean,
        vibrateOnFinish: Boolean
    ): RestTimerSnapshot {
        val appCtx = context.applicationContext
        RestTimerNotificationHelper.cancelFinishedNotification(appCtx)
        cancelAlarm(appCtx)

        val safeSeconds = durationSeconds.coerceIn(5, RestTimerLogic.MAX_REST_SECONDS)
        val endTimeMillis = System.currentTimeMillis() + safeSeconds * 1000L
        val snapshot = RestTimerStore.saveActiveTimer(
            context = appCtx,
            endTimeMillis = endTimeMillis,
            totalSeconds = safeSeconds,
            notifyInBackground = notifyInBackground,
            vibrateOnFinish = vibrateOnFinish
        )

        scheduleAlarm(appCtx, snapshot)
        if (notifyInBackground) {
            RestTimerNotificationHelper.showOngoingCountdownNotification(appCtx, snapshot)
        } else {
            RestTimerNotificationHelper.cancelOngoingNotification(appCtx)
        }
        return snapshot
    }

    fun adjustRunningTimer(context: Context, deltaSeconds: Int): RestTimerSnapshot? {
        val appCtx = context.applicationContext
        val active = RestTimerStore.getActiveTimer(appCtx) ?: return null
        val adjusted = RestTimerLogic.computeAdjustedTimer(
            currentEndMillis = active.endTimeMillis,
            currentTotalSeconds = active.totalSeconds,
            deltaSeconds = deltaSeconds,
            nowMillis = System.currentTimeMillis()
        )
        if (adjusted.shouldStop) {
            stopTimer(appCtx)
            return null
        }

        cancelAlarm(appCtx)
        val updated = RestTimerStore.saveActiveTimer(
            context = appCtx,
            endTimeMillis = adjusted.endTimeMillis,
            totalSeconds = adjusted.totalSeconds,
            notifyInBackground = active.notifyInBackground,
            vibrateOnFinish = active.vibrateOnFinish
        )
        scheduleAlarm(appCtx, updated)
        if (updated.notifyInBackground) {
            RestTimerNotificationHelper.showOngoingCountdownNotification(appCtx, updated)
        }
        return updated
    }

    fun stopTimer(context: Context) {
        val appCtx = context.applicationContext
        cancelAlarm(appCtx)
        RestTimerNotificationHelper.cancelOngoingNotification(appCtx)
        RestTimerNotificationHelper.cancelFinishedNotification(appCtx)
        RestTimerStore.clear(appCtx)
    }

    fun refreshOngoingNotification(context: Context) {
        val appCtx = context.applicationContext
        val active = RestTimerStore.getActiveTimer(appCtx) ?: return
        val remaining = RestTimerLogic.computeRemainingSeconds(
            active.endTimeMillis,
            System.currentTimeMillis()
        )
        if (remaining > 0 && active.notifyInBackground) {
            RestTimerNotificationHelper.showOngoingCountdownNotification(appCtx, active)
        }
    }

    fun onTimerFinished(context: Context, timerId: Long) {
        val appCtx = context.applicationContext
        val claimed = RestTimerStore.claimCompletion(appCtx, timerId) ?: return
        cancelAlarm(appCtx)
        RestTimerNotificationHelper.cancelOngoingNotification(appCtx)

        val showBgAlert = RestTimerLogic.shouldShowBackgroundAlert(
            notifyEnabled = claimed.notifyInBackground,
            isAppInForeground = RestTimerForegroundTracker.isAppInForeground
        )

        if (showBgAlert) {
            if (RestTimerNotificationHelper.hasNotificationPermission(appCtx)) {
                RestTimerNotificationHelper.showRestFinishedNotification(
                    context = appCtx,
                    vibrate = claimed.vibrateOnFinish
                )
            } else if (claimed.vibrateOnFinish) {
                RestTimerNotificationHelper.triggerVibration(appCtx, 500L)
            }
        } else if (claimed.vibrateOnFinish) {
            RestTimerNotificationHelper.triggerVibration(appCtx, 400L)
        }
    }

    private fun scheduleAlarm(context: Context, snapshot: RestTimerSnapshot) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val alarmIntent = buildFinishedPendingIntent(context, snapshot.timerId)
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
        if (canExact) {
            try {
                val showIntent = RestTimerNotificationHelper.openAppPendingIntent(context)
                val info = AlarmManager.AlarmClockInfo(snapshot.endTimeMillis, showIntent)
                alarmManager.setAlarmClock(info, alarmIntent)
                return
            } catch (_: SecurityException) {
            }
        }
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            snapshot.endTimeMillis,
            alarmIntent
        )
    }

    private fun cancelAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context.applicationContext, RestTimerReceiver::class.java).apply {
            action = ACTION_REST_FINISHED
        }
        val existing = PendingIntent.getBroadcast(
            context.applicationContext,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (existing != null) {
            alarmManager.cancel(existing)
            existing.cancel()
        }
    }

    private fun buildFinishedPendingIntent(context: Context, timerId: Long): PendingIntent {
        val intent = Intent(context.applicationContext, RestTimerReceiver::class.java).apply {
            action = ACTION_REST_FINISHED
            putExtra(EXTRA_TIMER_ID, timerId)
        }
        return PendingIntent.getBroadcast(
            context.applicationContext,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
