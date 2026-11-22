package com.yoligym.app.backup

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import java.time.LocalDate
import java.util.concurrent.TimeUnit

object GitHubBackupScheduler {

    const val PERIODIC_WORK_NAME = "yoligym_daily_github_backup"
    const val CATCHUP_WORK_NAME = "yoligym_daily_github_backup_catchup"

    private const val LEGACY_DRIVE_PERIODIC_WORK = "yoligym_daily_drive_backup"
    private const val LEGACY_DRIVE_CATCHUP_WORK = "yoligym_daily_drive_backup_catchup"

    fun scheduleDailyBackup(
        context: Context,
        backupHour: Int,
        wifiOnly: Boolean,
        replaceExisting: Boolean
    ) {
        val wm = WorkManager.getInstance(context.applicationContext)
        cancelLegacyDriveWork(wm)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val initialDelayMs = GitHubBackupNaming.computeDelayToNextHourMillis(
            nowMillis = System.currentTimeMillis(),
            targetHour = backupHour
        )

        val request = PeriodicWorkRequestBuilder<GitHubBackupWorker>(
            24,
            TimeUnit.HOURS,
            2,
            TimeUnit.HOURS
        )
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        val policy = if (replaceExisting) {
            ExistingPeriodicWorkPolicy.UPDATE
        } else {
            ExistingPeriodicWorkPolicy.KEEP
        }

        wm.enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, policy, request)
    }

    fun enqueueCatchUpIfDue(context: Context, state: GitHubBackupConfigState) {
        if (!state.isConfigured || !state.hasToken || !state.autoBackupEnabled) return
        if (state.lastStatus == GitHubBackupStatus.AUTH_ERROR) return
        if (!GitHubBackupNaming.isBackupDueToday(state.lastBackupAt, LocalDate.now())) return

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (state.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<GitHubBackupWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(CATCHUP_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    fun cancelAll(context: Context) {
        val wm = WorkManager.getInstance(context.applicationContext)
        wm.cancelUniqueWork(PERIODIC_WORK_NAME)
        wm.cancelUniqueWork(CATCHUP_WORK_NAME)
        cancelLegacyDriveWork(wm)
    }

    private fun cancelLegacyDriveWork(wm: WorkManager) {
        wm.cancelUniqueWork(LEGACY_DRIVE_PERIODIC_WORK)
        wm.cancelUniqueWork(LEGACY_DRIVE_CATCHUP_WORK)
    }
}
