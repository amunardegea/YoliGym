package com.yoligym.app.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class GitHubBackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = GitHubBackupPreferences(applicationContext)
        val state = prefs.loadState()
        if (!state.isConfigured || !state.hasToken || !state.autoBackupEnabled) {
            return Result.success()
        }

        val manager = GitHubBackupManager(applicationContext)
        return when (val result = manager.performBackup()) {
            is GitHubBackupRunResult.Success -> Result.success()
            is GitHubBackupRunResult.AuthError -> Result.failure()
            is GitHubBackupRunResult.Error -> {
                if (result.isTransient && runAttemptCount < 3) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            }
        }
    }
}
