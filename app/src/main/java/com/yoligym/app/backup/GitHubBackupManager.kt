package com.yoligym.app.backup

import android.content.Context
import com.yoligym.app.data.YoliGymDatabase
import java.io.IOException
import java.time.LocalDate

class GitHubBackupManager(context: Context) {

    private val appContext = context.applicationContext
    private val db = YoliGymDatabase.getInstance(appContext)
    private val prefs = GitHubBackupPreferences(appContext)
    private val restClient = GitHubRestClient()

    fun configureAndVerify(
        repoInput: String,
        userIdInput: String,
        tokenInput: String?,
        branchInput: String?
    ): Result<GitHubBackupConfigState> {
        val parsedRepo = GitHubBackupNaming.parseOwnerRepo(repoInput)
            ?: return Result.failure(
                IllegalArgumentException("Formato de repositorio inválido. Usa «usuario/repositorio» o la URL de GitHub.")
            )

        val sanitizedUserId = GitHubBackupNaming.sanitizeUserId(userIdInput)
        if (sanitizedUserId.isEmpty()) {
            return Result.failure(
                IllegalArgumentException("Introduce un identificador de usuario válido (letras o números, ej. «yoli»).")
            )
        }

        val resolvedToken = tokenInput?.trim()?.takeIf { it.isNotEmpty() }
            ?: prefs.getToken()
            ?: return Result.failure(
                IllegalArgumentException("Introduce un Personal Access Token (PAT) de GitHub.")
            )

        return try {
            val (owner, repo) = parsedRepo
            val repoInfo = restClient.verifyRepository(resolvedToken, owner, repo)
            if (!repoInfo.canPush) {
                return Result.failure(
                    IllegalStateException("La cuenta/token no tiene permisos de escritura en $owner/$repo. Activa «Contents: Read and write».")
                )
            }

            val finalOwner = repoInfo.owner.ifBlank { owner }
            val finalRepo = repoInfo.name.ifBlank { repo }
            val finalBranch = branchInput?.trim()?.takeIf { it.isNotEmpty() }
                ?: repoInfo.defaultBranch

            prefs.saveConfiguration(
                owner = finalOwner,
                repo = finalRepo,
                branch = finalBranch,
                userId = sanitizedUserId,
                token = resolvedToken,
                isPrivate = repoInfo.isPrivate
            )

            val savedState = prefs.loadState()
            if (savedState.autoBackupEnabled) {
                GitHubBackupScheduler.scheduleDailyBackup(
                    context = appContext,
                    backupHour = savedState.backupHour,
                    wifiOnly = savedState.wifiOnly,
                    replaceExisting = true
                )
            }
            Result.success(savedState)
        } catch (e: GitHubApiException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(
                IOException("Error de red al verificar el repositorio en GitHub: ${e.message ?: "sin conexión"}", e)
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun performBackup(): GitHubBackupRunResult {
        val state = prefs.loadState()
        val token = prefs.getToken()
        val timestamp = YoliGymDatabase.nowIsoString()

        if (!state.isConfigured || token.isNullOrBlank() || state.userId.isBlank() || state.repoFullName.isBlank()) {
            val msg = "Configura primero el repositorio, usuario y token de GitHub."
            prefs.recordError(msg, timestamp)
            return GitHubBackupRunResult.Error(msg, isTransient = false)
        }

        return try {
            val today = LocalDate.now()
            val dateLabel = today.toString()
            val fileName = GitHubBackupNaming.buildDailyFileName(state.userId, today)
            val filePath = GitHubBackupNaming.buildDailyFilePath(state.userId, today)
            val jsonContent = db.exportBackupJson()

            restClient.uploadOrUpdateDailyBackup(
                token = token,
                owner = state.repoOwner,
                repo = state.repoName,
                branch = state.branch,
                path = filePath,
                userId = state.userId,
                dateLabel = dateLabel,
                jsonContent = jsonContent
            )

            val userFiles = restClient.listUserBackups(
                token = token,
                owner = state.repoOwner,
                repo = state.repoName,
                branch = state.branch,
                userId = state.userId
            )
            val toDelete = GitHubBackupNaming.selectFilesToDelete(
                files = userFiles,
                userId = state.userId,
                maxKeep = state.retentionDays
            )
            var deletedCount = 0
            for (oldFile in toDelete) {
                runCatching {
                    restClient.deleteFile(
                        token = token,
                        owner = state.repoOwner,
                        repo = state.repoName,
                        branch = state.branch,
                        file = oldFile,
                        userId = state.userId
                    )
                    deletedCount++
                }
            }

            prefs.recordSuccess(fileName = fileName, timestamp = timestamp)
            GitHubBackupRunResult.Success(
                fileName = fileName,
                filePath = filePath,
                uploadedAt = timestamp,
                deletedOldCount = deletedCount
            )
        } catch (e: GitHubApiException) {
            val msg = e.message ?: "Error de GitHub (HTTP ${e.httpCode})"
            if (e.isAuthError) {
                prefs.recordAuthError(msg, timestamp)
                GitHubBackupRunResult.AuthError(msg)
            } else {
                prefs.recordError(msg, timestamp)
                GitHubBackupRunResult.Error(msg, isTransient = e.isTransient)
            }
        } catch (e: IOException) {
            val msg = "Error de red al conectar con GitHub: ${e.message ?: "sin conexión"}"
            prefs.recordError(msg, timestamp)
            GitHubBackupRunResult.Error(msg, isTransient = true)
        } catch (e: Exception) {
            val msg = "Error inesperado al subir backup a GitHub: ${e.message ?: "desconocido"}"
            prefs.recordError(msg, timestamp)
            GitHubBackupRunResult.Error(msg, isTransient = false)
        }
    }

    fun listRemoteBackups(): Result<List<GitHubBackupFile>> {
        val state = prefs.loadState()
        val token = prefs.getToken()
        if (!state.isConfigured || token.isNullOrBlank() || state.userId.isBlank()) {
            return Result.failure(IllegalStateException("El backup en GitHub no está configurado."))
        }
        return try {
            val files = restClient.listUserBackups(
                token = token,
                owner = state.repoOwner,
                repo = state.repoName,
                branch = state.branch,
                userId = state.userId
            )
            Result.success(files)
        } catch (e: GitHubApiException) {
            if (e.isAuthError) {
                prefs.recordAuthError(e.message ?: "Error de autenticación", YoliGymDatabase.nowIsoString())
            }
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(IOException("Error de red al consultar backups en GitHub: ${e.message ?: "sin conexión"}", e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun downloadRemoteBackup(file: GitHubBackupFile): Result<String> {
        val state = prefs.loadState()
        val token = prefs.getToken()
        if (!state.isConfigured || token.isNullOrBlank()) {
            return Result.failure(IllegalStateException("El backup en GitHub no está configurado."))
        }
        return try {
            val content = restClient.downloadBackupContent(
                token = token,
                owner = state.repoOwner,
                repo = state.repoName,
                branch = state.branch,
                filePath = file.path
            )
            Result.success(content)
        } catch (e: GitHubApiException) {
            if (e.isAuthError) {
                prefs.recordAuthError(e.message ?: "Error de autenticación", YoliGymDatabase.nowIsoString())
            }
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(IOException("Error de red al descargar backup de GitHub: ${e.message ?: "sin conexión"}", e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun disconnect() {
        GitHubBackupScheduler.cancelAll(appContext)
        prefs.clearConfiguration()
    }
}
