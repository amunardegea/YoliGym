package com.yoligym.app.backup

import android.content.Context
import android.content.SharedPreferences

class GitHubBackupPreferences(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val secretPrefs: SharedPreferences =
        appContext.getSharedPreferences(SECRET_PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "yoligym_prefs"
        private const val SECRET_PREFS_NAME = "yoligym_github_secret"

        private const val KEY_CONFIGURED = "gh_configured"
        private const val KEY_REPO_OWNER = "gh_repo_owner"
        private const val KEY_REPO_NAME = "gh_repo_name"
        private const val KEY_BRANCH = "gh_branch"
        private const val KEY_USER_ID = "gh_user_id"
        private const val KEY_IS_PRIVATE = "gh_repo_is_private"
        private const val KEY_HAS_PRIVATE_FLAG = "gh_repo_has_private_flag"
        private const val KEY_AUTO_ENABLED = "gh_auto_enabled"
        private const val KEY_WIFI_ONLY = "gh_wifi_only"
        private const val KEY_BACKUP_HOUR = "gh_backup_hour"
        private const val KEY_LAST_BACKUP_AT = "gh_last_backup_at"
        private const val KEY_LAST_BACKUP_FILE = "gh_last_backup_file"
        private const val KEY_LAST_ATTEMPT_AT = "gh_last_attempt_at"
        private const val KEY_LAST_STATUS = "gh_last_status"
        private const val KEY_LAST_ERROR = "gh_last_error"

        private const val KEY_PAT_TOKEN = "gh_pat_token"
    }

    fun loadState(): GitHubBackupConfigState {
        val token = getToken()
        val hasPrivateFlag = prefs.getBoolean(KEY_HAS_PRIVATE_FLAG, false)
        val isPrivate = if (hasPrivateFlag) prefs.getBoolean(KEY_IS_PRIVATE, true) else null
        return GitHubBackupConfigState(
            isConfigured = prefs.getBoolean(KEY_CONFIGURED, false),
            repoOwner = prefs.getString(KEY_REPO_OWNER, "") ?: "",
            repoName = prefs.getString(KEY_REPO_NAME, "") ?: "",
            branch = prefs.getString(KEY_BRANCH, "main") ?: "main",
            userId = prefs.getString(KEY_USER_ID, "") ?: "",
            hasToken = !token.isNullOrBlank(),
            maskedToken = GitHubBackupNaming.maskToken(token),
            isPrivateRepo = isPrivate,
            autoBackupEnabled = prefs.getBoolean(KEY_AUTO_ENABLED, false),
            wifiOnly = prefs.getBoolean(KEY_WIFI_ONLY, true),
            backupHour = prefs.getInt(KEY_BACKUP_HOUR, 3).coerceIn(0, 23),
            retentionDays = GitHubBackupNaming.DEFAULT_RETENTION_DAYS,
            lastBackupAt = prefs.getString(KEY_LAST_BACKUP_AT, null),
            lastBackupFileName = prefs.getString(KEY_LAST_BACKUP_FILE, null),
            lastAttemptAt = prefs.getString(KEY_LAST_ATTEMPT_AT, null),
            lastStatus = GitHubBackupStatus.fromPref(prefs.getString(KEY_LAST_STATUS, null)),
            lastError = prefs.getString(KEY_LAST_ERROR, null)
        )
    }

    fun getToken(): String? {
        return secretPrefs.getString(KEY_PAT_TOKEN, null)?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun saveConfiguration(
        owner: String,
        repo: String,
        branch: String,
        userId: String,
        token: String,
        isPrivate: Boolean
    ) {
        secretPrefs.edit()
            .putString(KEY_PAT_TOKEN, token.trim())
            .apply()

        val wasConfigured = prefs.getBoolean(KEY_CONFIGURED, false)
        val currentAuto = if (wasConfigured) prefs.getBoolean(KEY_AUTO_ENABLED, true) else true

        prefs.edit()
            .putBoolean(KEY_CONFIGURED, true)
            .putString(KEY_REPO_OWNER, owner.trim())
            .putString(KEY_REPO_NAME, repo.trim())
            .putString(KEY_BRANCH, branch.trim().ifEmpty { "main" })
            .putString(KEY_USER_ID, GitHubBackupNaming.sanitizeUserId(userId))
            .putBoolean(KEY_HAS_PRIVATE_FLAG, true)
            .putBoolean(KEY_IS_PRIVATE, isPrivate)
            .putBoolean(KEY_AUTO_ENABLED, currentAuto)
            .remove(KEY_LAST_ERROR)
            .apply()
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_ENABLED, enabled).apply()
    }

    fun setWifiOnly(wifiOnly: Boolean) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY, wifiOnly).apply()
    }

    fun setBackupHour(hour: Int) {
        prefs.edit().putInt(KEY_BACKUP_HOUR, hour.coerceIn(0, 23)).apply()
    }

    fun recordSuccess(fileName: String, timestamp: String) {
        prefs.edit()
            .putString(KEY_LAST_BACKUP_AT, timestamp)
            .putString(KEY_LAST_BACKUP_FILE, fileName)
            .putString(KEY_LAST_ATTEMPT_AT, timestamp)
            .putString(KEY_LAST_STATUS, GitHubBackupStatus.SUCCESS.prefValue)
            .remove(KEY_LAST_ERROR)
            .apply()
    }

    fun recordError(message: String, timestamp: String) {
        prefs.edit()
            .putString(KEY_LAST_ATTEMPT_AT, timestamp)
            .putString(KEY_LAST_STATUS, GitHubBackupStatus.ERROR.prefValue)
            .putString(KEY_LAST_ERROR, message)
            .apply()
    }

    fun recordAuthError(message: String, timestamp: String) {
        prefs.edit()
            .putString(KEY_LAST_ATTEMPT_AT, timestamp)
            .putString(KEY_LAST_STATUS, GitHubBackupStatus.AUTH_ERROR.prefValue)
            .putString(KEY_LAST_ERROR, message)
            .apply()
    }

    fun clearConfiguration() {
        secretPrefs.edit().remove(KEY_PAT_TOKEN).apply()
        prefs.edit()
            .putBoolean(KEY_CONFIGURED, false)
            .remove(KEY_REPO_OWNER)
            .remove(KEY_REPO_NAME)
            .remove(KEY_BRANCH)
            .remove(KEY_USER_ID)
            .remove(KEY_HAS_PRIVATE_FLAG)
            .remove(KEY_IS_PRIVATE)
            .putBoolean(KEY_AUTO_ENABLED, false)
            .remove(KEY_LAST_BACKUP_AT)
            .remove(KEY_LAST_BACKUP_FILE)
            .remove(KEY_LAST_ATTEMPT_AT)
            .putString(KEY_LAST_STATUS, GitHubBackupStatus.NEVER.prefValue)
            .remove(KEY_LAST_ERROR)
            .apply()
    }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }
}
