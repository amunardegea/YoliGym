package com.yoligym.app.backup

import java.text.Normalizer
import java.time.LocalDate
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

enum class GitHubBackupStatus(val prefValue: String) {
    NEVER("NEVER"),
    SUCCESS("SUCCESS"),
    ERROR("ERROR"),
    AUTH_ERROR("AUTH_ERROR");

    companion object {
        fun fromPref(value: String?): GitHubBackupStatus =
            entries.firstOrNull { it.prefValue == value } ?: NEVER
    }
}

data class GitHubBackupConfigState(
    val isConfigured: Boolean = false,
    val repoOwner: String = "",
    val repoName: String = "",
    val branch: String = "main",
    val userId: String = "",
    val hasToken: Boolean = false,
    val maskedToken: String = "",
    val isPrivateRepo: Boolean? = null,
    val autoBackupEnabled: Boolean = false,
    val wifiOnly: Boolean = true,
    val backupHour: Int = 3,
    val retentionDays: Int = GitHubBackupNaming.DEFAULT_RETENTION_DAYS,
    val lastBackupAt: String? = null,
    val lastBackupFileName: String? = null,
    val lastAttemptAt: String? = null,
    val lastStatus: GitHubBackupStatus = GitHubBackupStatus.NEVER,
    val lastError: String? = null
) {
    val repoFullName: String
        get() = if (repoOwner.isNotBlank() && repoName.isNotBlank()) "$repoOwner/$repoName" else ""
}

data class GitHubBackupFile(
    val name: String,
    val path: String,
    val sha: String,
    val sizeBytes: Long
) {
    val dateLabel: String
        get() = GitHubBackupNaming.extractDateLabel(name) ?: name
}

data class GitHubRepoInfo(
    val owner: String,
    val name: String,
    val defaultBranch: String,
    val isPrivate: Boolean,
    val canPush: Boolean
)

sealed interface GitHubBackupRunResult {
    data class Success(
        val fileName: String,
        val filePath: String,
        val uploadedAt: String,
        val deletedOldCount: Int
    ) : GitHubBackupRunResult

    data class AuthError(val message: String) : GitHubBackupRunResult

    data class Error(val message: String, val isTransient: Boolean) : GitHubBackupRunResult
}

object GitHubBackupNaming {
    const val BACKUPS_ROOT_DIR = "backups"
    const val DEFAULT_RETENTION_DAYS = 30

    private val COMBINING_MARKS_REGEX = Regex("""\p{InCombiningDiacriticalMarks}+""")
    private val NON_ALNUM_RUN_REGEX = Regex("""[^a-z0-9]+""")
    private val VALID_GITHUB_SEGMENT_REGEX = Regex("""^[A-Za-z0-9._-]+$""")
    private val DATE_JSON_SUFFIX_REGEX = Regex("""^\d{4}-\d{2}-\d{2}\.json$""")
    private val DATE_EXTRACT_REGEX = Regex("""^yoligym_backup_[a-z0-9-]+_(\d{4}-\d{2}-\d{2})\.json$""")

    fun sanitizeUserId(raw: String): String {
        val normalized = Normalizer.normalize(raw.trim(), Normalizer.Form.NFD)
        val withoutMarks = COMBINING_MARKS_REGEX.replace(normalized, "")
        val lower = withoutMarks.lowercase(Locale.ROOT)
        val cleaned = NON_ALNUM_RUN_REGEX.replace(lower, "-").trim('-')
        return if (cleaned.length > 32) {
            cleaned.substring(0, 32).trimEnd('-')
        } else {
            cleaned
        }
    }

    fun parseOwnerRepo(raw: String): Pair<String, String>? {
        var text = raw.trim()
        if (text.isEmpty()) return null

        text = text.removePrefix("https://").removePrefix("http://")
        if (text.startsWith("www.github.com/", ignoreCase = true)) {
            text = text.substring("www.github.com/".length)
        } else if (text.startsWith("github.com/", ignoreCase = true)) {
            text = text.substring("github.com/".length)
        }

        text = text.trim('/')
        if (text.endsWith(".git", ignoreCase = true)) {
            text = text.dropLast(4)
        }
        text = text.trim('/')

        val parts = text.split('/')
        if (parts.size != 2) return null

        val owner = parts[0].trim()
        val repo = parts[1].trim()
        if (owner.isEmpty() || repo.isEmpty()) return null
        if (owner == "." || repo == "." || owner.contains("..") || repo.contains("..")) return null
        if (!VALID_GITHUB_SEGMENT_REGEX.matches(owner) || !VALID_GITHUB_SEGMENT_REGEX.matches(repo)) {
            return null
        }
        return owner to repo
    }

    fun buildUserFolderPath(userId: String): String {
        val safeUser = sanitizeUserId(userId)
        require(safeUser.isNotEmpty()) { "El identificador de usuario no puede estar vacío." }
        return "$BACKUPS_ROOT_DIR/$safeUser"
    }

    fun buildDailyFileName(userId: String, date: LocalDate): String {
        val safeUser = sanitizeUserId(userId)
        require(safeUser.isNotEmpty()) { "El identificador de usuario no puede estar vacío." }
        return "yoligym_backup_${safeUser}_${date}.json"
    }

    fun buildDailyFilePath(userId: String, date: LocalDate): String {
        return "${buildUserFolderPath(userId)}/${buildDailyFileName(userId, date)}"
    }

    fun matchesUserDailyFile(fileName: String, userId: String): Boolean {
        val safeUser = sanitizeUserId(userId)
        if (safeUser.isEmpty()) return false
        val prefix = "yoligym_backup_${safeUser}_"
        if (!fileName.startsWith(prefix)) return false
        val suffix = fileName.removePrefix(prefix)
        return DATE_JSON_SUFFIX_REGEX.matches(suffix)
    }

    fun extractDateLabel(fileName: String): String? =
        DATE_EXTRACT_REGEX.matchEntire(fileName)?.groupValues?.getOrNull(1)

    fun selectFilesToDelete(
        files: List<GitHubBackupFile>,
        userId: String,
        maxKeep: Int = DEFAULT_RETENTION_DAYS
    ): List<GitHubBackupFile> {
        val safeMaxKeep = maxKeep.coerceAtLeast(1)
        return files
            .filter { matchesUserDailyFile(it.name, userId) }
            .sortedByDescending { it.name }
            .drop(safeMaxKeep)
    }

    fun computeDelayToNextHourMillis(
        nowMillis: Long,
        targetHour: Int,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long {
        val safeHour = targetHour.coerceIn(0, 23)
        val target = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, safeHour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.timeInMillis - nowMillis < 60_000L) {
            target.add(Calendar.DAY_OF_MONTH, 1)
        }
        return (target.timeInMillis - nowMillis).coerceAtLeast(60_000L)
    }

    fun isBackupDueToday(lastBackupAt: String?, today: LocalDate): Boolean {
        val trimmed = lastBackupAt?.trim()
        if (trimmed.isNullOrEmpty() || trimmed.length < 10) return true
        return trimmed.substring(0, 10) != today.toString()
    }

    fun maskToken(token: String?): String {
        val trimmed = token?.trim().orEmpty()
        if (trimmed.isEmpty()) return ""
        if (trimmed.length <= 8) return "••••"
        return "${trimmed.take(4)}••••${trimmed.takeLast(4)}"
    }
}
