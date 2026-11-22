package com.yoligym.app.timer

object RestTimerForegroundTracker {
    @Volatile
    var isAppInForeground: Boolean = false
}
