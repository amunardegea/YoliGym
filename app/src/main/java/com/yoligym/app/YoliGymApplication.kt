package com.yoligym.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.yoligym.app.timer.RestTimerForegroundTracker
import com.yoligym.app.timer.RestTimerNotificationHelper

class YoliGymApplication : Application() {

    private var resumedActivitiesCount = 0

    override fun onCreate() {
        super.onCreate()
        RestTimerNotificationHelper.ensureChannels(this)

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                resumedActivitiesCount++
                RestTimerForegroundTracker.isAppInForeground = resumedActivitiesCount > 0
            }

            override fun onActivityPaused(activity: Activity) {
                resumedActivitiesCount = (resumedActivitiesCount - 1).coerceAtLeast(0)
                RestTimerForegroundTracker.isAppInForeground = resumedActivitiesCount > 0
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
