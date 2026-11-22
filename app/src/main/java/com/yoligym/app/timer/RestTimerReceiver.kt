package com.yoligym.app.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class RestTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            RestTimerCoordinator.ACTION_REST_FINISHED -> {
                val timerId = intent.getLongExtra(RestTimerCoordinator.EXTRA_TIMER_ID, -1L)
                RestTimerCoordinator.onTimerFinished(context, timerId)
            }
            RestTimerCoordinator.ACTION_REST_ADD_15S -> {
                RestTimerCoordinator.adjustRunningTimer(context, 15)
            }
            RestTimerCoordinator.ACTION_REST_SKIP -> {
                RestTimerCoordinator.stopTimer(context)
            }
        }
    }
}
