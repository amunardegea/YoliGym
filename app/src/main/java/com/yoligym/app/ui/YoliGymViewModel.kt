package com.yoligym.app.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yoligym.app.backup.GitHubBackupConfigState
import com.yoligym.app.backup.GitHubBackupFile
import com.yoligym.app.backup.GitHubBackupManager
import com.yoligym.app.backup.GitHubBackupPreferences
import com.yoligym.app.backup.GitHubBackupRunResult
import com.yoligym.app.backup.GitHubBackupScheduler
import com.yoligym.app.data.Exercise
import com.yoligym.app.data.ExerciseCategory
import com.yoligym.app.data.ExerciseStats
import com.yoligym.app.data.GlobalStats
import com.yoligym.app.data.MeasurementType
import com.yoligym.app.data.MuscleGroup
import com.yoligym.app.data.RoutineWithExercises
import com.yoligym.app.data.SessionDetail
import com.yoligym.app.data.WorkoutSet
import com.yoligym.app.data.YoliGymDatabase
import com.yoligym.app.timer.RestTimerCoordinator
import com.yoligym.app.timer.RestTimerLogic
import com.yoligym.app.timer.RestTimerNotificationHelper
import com.yoligym.app.timer.RestTimerStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

data class AppPreferences(
    val darkTheme: Boolean = true,
    val defaultRestSeconds: Int = 90,
    val weightStepKg: Double = 2.5,
    val autoStartRestTimer: Boolean = true,
    val vibrateOnRestFinish: Boolean = true,
    val notifyRestInBackground: Boolean = true
)

data class YoliGymUiState(
    val isLoading: Boolean = true,
    val exercises: List<Exercise> = emptyList(),
    val routines: List<RoutineWithExercises> = emptyList(),
    val activeSession: SessionDetail? = null,
    val completedSessions: List<SessionDetail> = emptyList(),
    val globalStats: GlobalStats = GlobalStats(),
    val preferences: AppPreferences = AppPreferences(),
    val workoutElapsedSeconds: Int = 0,
    val restRemainingSeconds: Int = 0,
    val restTotalSeconds: Int = 90,
    val isRestTimerRunning: Boolean = false,
    val activeTimedSetId: String? = null,
    val activeTimedSetRemaining: Int = 0,
    val statusMessage: String? = null,
    val shouldRequestNotificationPermission: Boolean = false,
    val githubBackup: GitHubBackupConfigState = GitHubBackupConfigState(),
    val isGitHubBusy: Boolean = false,
    val remoteGitHubBackups: List<GitHubBackupFile>? = null
)

class YoliGymViewModel(application: Application) : AndroidViewModel(application) {

    private val db = YoliGymDatabase.getInstance(application.applicationContext)
    private val prefs = application.getSharedPreferences("yoligym_prefs", Context.MODE_PRIVATE)
    private val githubPrefs = GitHubBackupPreferences(application.applicationContext)
    private val githubManager = GitHubBackupManager(application.applicationContext)

    private val _uiState = MutableStateFlow(YoliGymUiState())
    val uiState: StateFlow<YoliGymUiState> = _uiState.asStateFlow()

    private var tickerJob: Job? = null
    private var askedNotificationPermissionThisSession = false

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key.startsWith("gh_")) {
            _uiState.update { it.copy(githubBackup = githubPrefs.loadState()) }
        }
        if (key == null || key.startsWith("rest_timer_")) {
            updateRestTimerUiFromStoreWithoutTrigger()
        }
    }

    init {
        loadPreferences()
        loadGitHubStateAndEnsureSchedule()
        syncRestTimerFromStore()
        refreshAllData()
        startTicker()
    }

    override fun onCleared() {
        super.onCleared()
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
    }

    private fun loadGitHubStateAndEnsureSchedule() {
        val ghState = githubPrefs.loadState()
        _uiState.update { it.copy(githubBackup = ghState) }
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        if (ghState.isConfigured && ghState.hasToken && ghState.autoBackupEnabled) {
            val appCtx = getApplication<Application>().applicationContext
            GitHubBackupScheduler.scheduleDailyBackup(
                context = appCtx,
                backupHour = ghState.backupHour,
                wifiOnly = ghState.wifiOnly,
                replaceExisting = false
            )
            GitHubBackupScheduler.enqueueCatchUpIfDue(appCtx, ghState)
        }
    }

    private fun loadPreferences() {
        val appPrefs = AppPreferences(
            darkTheme = prefs.getBoolean("dark_theme", true),
            defaultRestSeconds = prefs.getInt("default_rest_seconds", 90),
            weightStepKg = prefs.getFloat("weight_step_kg", 2.5f).toDouble(),
            autoStartRestTimer = prefs.getBoolean("auto_start_rest", true),
            vibrateOnRestFinish = prefs.getBoolean("vibrate_on_rest", true),
            notifyRestInBackground = prefs.getBoolean("notify_rest_background", true)
        )
        _uiState.update { it.copy(preferences = appPrefs) }
    }

    fun updatePreferences(transform: (AppPreferences) -> AppPreferences) {
        val updated = transform(_uiState.value.preferences)
        prefs.edit()
            .putBoolean("dark_theme", updated.darkTheme)
            .putInt("default_rest_seconds", updated.defaultRestSeconds)
            .putFloat("weight_step_kg", updated.weightStepKg.toFloat())
            .putBoolean("auto_start_rest", updated.autoStartRestTimer)
            .putBoolean("vibrate_on_rest", updated.vibrateOnRestFinish)
            .putBoolean("notify_rest_background", updated.notifyRestInBackground)
            .apply()
        _uiState.update { it.copy(preferences = updated) }
        if (!updated.notifyRestInBackground) {
            RestTimerNotificationHelper.cancelOngoingNotification(
                getApplication<Application>().applicationContext
            )
        }
    }

    fun refreshAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            db.ensureSeeded()
            val exercises = db.getAllExercises()
            val routines = db.getAllRoutinesWithExercises()
            val active = db.getActiveSessionDetail()
            val history = db.getCompletedSessions()
            val stats = db.getGlobalStats()

            val initialElapsed = if (active != null) {
                computeElapsedFromStartedAt(active.session.startedAt)
            } else 0

            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    exercises = exercises,
                    routines = routines,
                    activeSession = active,
                    completedSessions = history,
                    globalStats = stats,
                    workoutElapsedSeconds = if (active != null) {
                        maxOf(state.workoutElapsedSeconds, initialElapsed)
                    } else 0
                )
            }
        }
    }

    private fun computeElapsedFromStartedAt(startedAt: String): Int {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val parsed = sdf.parse(startedAt) ?: return 0
            val diffMs = System.currentTimeMillis() - parsed.time
            (diffMs / 1000L).toInt().coerceAtLeast(0)
        } catch (e: Exception) {
            0
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                val current = _uiState.value
                var nextElapsed = current.workoutElapsedSeconds
                if (current.activeSession != null) {
                    nextElapsed += 1
                }

                val appCtx = getApplication<Application>().applicationContext
                val activeRest = RestTimerStore.getActiveTimer(appCtx)
                var nextRest = current.restRemainingSeconds
                var nextRestTotal = current.restTotalSeconds
                var nextRestRunning = current.isRestTimerRunning
                if (activeRest != null) {
                    val rem = RestTimerLogic.computeRemainingSeconds(
                        activeRest.endTimeMillis,
                        System.currentTimeMillis()
                    )
                    if (rem <= 0) {
                        nextRest = 0
                        nextRestRunning = false
                        RestTimerCoordinator.onTimerFinished(appCtx, activeRest.timerId)
                    } else {
                        nextRest = rem
                        nextRestTotal = activeRest.totalSeconds
                        nextRestRunning = true
                    }
                } else if (current.isRestTimerRunning || current.restRemainingSeconds > 0) {
                    nextRest = 0
                    nextRestRunning = false
                }

                var nextTimedSetId = current.activeTimedSetId
                var nextTimedSetRem = current.activeTimedSetRemaining
                if (nextTimedSetId != null && nextTimedSetRem > 0) {
                    nextTimedSetRem -= 1
                    if (nextTimedSetRem <= 0) {
                        nextTimedSetRem = 0
                        val finishedSetId = nextTimedSetId
                        nextTimedSetId = null
                        if (current.preferences.vibrateOnRestFinish) {
                            triggerVibration(350L)
                        }
                        // Automatically mark the timed set as completed
                        completeSetById(finishedSetId)
                    }
                }

                if (nextElapsed != current.workoutElapsedSeconds ||
                    nextRest != current.restRemainingSeconds ||
                    nextRestTotal != current.restTotalSeconds ||
                    nextRestRunning != current.isRestTimerRunning ||
                    nextTimedSetId != current.activeTimedSetId ||
                    nextTimedSetRem != current.activeTimedSetRemaining
                ) {
                    _uiState.update {
                        it.copy(
                            workoutElapsedSeconds = nextElapsed,
                            restRemainingSeconds = nextRest,
                            restTotalSeconds = nextRestTotal,
                            isRestTimerRunning = nextRestRunning,
                            activeTimedSetId = nextTimedSetId,
                            activeTimedSetRemaining = nextTimedSetRem
                        )
                    }
                }
            }
        }
    }

    private fun completeSetById(setId: String) {
        val session = _uiState.value.activeSession ?: return
        val targetPair = session.exercises.firstNotNullOfOrNull { exGroup ->
            exGroup.sets.firstOrNull { it.id == setId }?.let { exGroup to it }
        } ?: return
        val (exGroup, set) = targetPair
        updateSet(
            set = set,
            weightKg = set.weightKg,
            reps = set.reps,
            durationSeconds = set.durationSeconds,
            rpe = set.rpe,
            isWarmup = set.isWarmup,
            isCompleted = true,
            restSecondsHint = exGroup.targetRestSeconds
        )
    }

    private fun triggerVibration(durationMs: Long) {
        RestTimerNotificationHelper.triggerVibration(
            getApplication<Application>().applicationContext,
            durationMs
        )
    }

    fun requestNotificationPermissionPrompt() {
        _uiState.update { it.copy(shouldRequestNotificationPermission = true) }
    }

    fun consumeNotificationPermissionRequest() {
        _uiState.update { it.copy(shouldRequestNotificationPermission = false) }
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        val appCtx = getApplication<Application>().applicationContext
        if (granted) {
            RestTimerNotificationHelper.ensureChannels(appCtx)
            RestTimerCoordinator.refreshOngoingNotification(appCtx)
            showStatus("Avisos de descanso activados")
        } else {
            showStatus("Sin permiso de notificaciones: podrás activarlo en los ajustes del sistema")
        }
    }

    fun onAppResumed() {
        val appCtx = getApplication<Application>().applicationContext
        RestTimerNotificationHelper.cancelFinishedNotification(appCtx)
        syncRestTimerFromStore()
    }

    private fun updateRestTimerUiFromStoreWithoutTrigger() {
        val appCtx = getApplication<Application>().applicationContext
        val active = RestTimerStore.getActiveTimer(appCtx)
        if (active == null) {
            _uiState.update {
                if (it.isRestTimerRunning || it.restRemainingSeconds > 0) {
                    it.copy(restRemainingSeconds = 0, isRestTimerRunning = false)
                } else it
            }
            return
        }
        val rem = RestTimerLogic.computeRemainingSeconds(
            active.endTimeMillis,
            System.currentTimeMillis()
        )
        if (rem > 0) {
            _uiState.update {
                it.copy(
                    restRemainingSeconds = rem,
                    restTotalSeconds = active.totalSeconds,
                    isRestTimerRunning = true
                )
            }
        }
    }

    private fun syncRestTimerFromStore() {
        val appCtx = getApplication<Application>().applicationContext
        val active = RestTimerStore.getActiveTimer(appCtx)
        if (active == null) {
            _uiState.update {
                it.copy(restRemainingSeconds = 0, isRestTimerRunning = false)
            }
            return
        }
        val rem = RestTimerLogic.computeRemainingSeconds(
            active.endTimeMillis,
            System.currentTimeMillis()
        )
        if (rem <= 0) {
            RestTimerCoordinator.onTimerFinished(appCtx, active.timerId)
            _uiState.update {
                it.copy(restRemainingSeconds = 0, isRestTimerRunning = false)
            }
        } else {
            _uiState.update {
                it.copy(
                    restRemainingSeconds = rem,
                    restTotalSeconds = active.totalSeconds,
                    isRestTimerRunning = true
                )
            }
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun showStatus(msg: String) {
        _uiState.update { it.copy(statusMessage = msg) }
    }

    // =========================================================================
    // WORKOUT SESSION ACTIONS
    // =========================================================================

    fun startRoutineWorkout(routine: RoutineWithExercises) {
        viewModelScope.launch(Dispatchers.IO) {
            val session = db.startWorkoutFromRoutine(routine)
            _uiState.update {
                it.copy(
                    activeSession = session,
                    workoutElapsedSeconds = computeElapsedFromStartedAt(session.session.startedAt),
                    statusMessage = "Sesión iniciada: ${routine.routine.name}"
                )
            }
        }
    }

    fun startFreeWorkout(name: String = "Entrenamiento Libre") {
        viewModelScope.launch(Dispatchers.IO) {
            val session = db.startFreeWorkout(name)
            _uiState.update {
                it.copy(
                    activeSession = session,
                    workoutElapsedSeconds = 0,
                    statusMessage = "Entrenamiento libre iniciado"
                )
            }
        }
    }

    fun addExerciseToActiveSession(exercise: Exercise) {
        val session = _uiState.value.activeSession ?: return
        viewModelScope.launch(Dispatchers.IO) {
            db.addExerciseToSession(session.session.id, exercise, setsCount = 3)
            val updated = db.getActiveSessionDetail()
            _uiState.update {
                it.copy(
                    activeSession = updated,
                    statusMessage = "Añadido: ${exercise.name}"
                )
            }
        }
    }

    fun addSetToExercise(exerciseId: String) {
        val session = _uiState.value.activeSession ?: return
        viewModelScope.launch(Dispatchers.IO) {
            db.addSetToExerciseInSession(session.session.id, exerciseId)
            val updated = db.getActiveSessionDetail()
            _uiState.update { it.copy(activeSession = updated) }
        }
    }

    fun removeSet(set: WorkoutSet) {
        val session = _uiState.value.activeSession ?: return
        viewModelScope.launch(Dispatchers.IO) {
            db.removeSetFromSession(set.id, session.session.id, set.exerciseId)
            val updated = db.getActiveSessionDetail()
            _uiState.update { it.copy(activeSession = updated) }
        }
    }

    fun updateSet(
        set: WorkoutSet,
        weightKg: Double = set.weightKg,
        reps: Int = set.reps,
        durationSeconds: Int = set.durationSeconds,
        rpe: Double? = set.rpe,
        isWarmup: Boolean = set.isWarmup,
        isCompleted: Boolean = set.isCompleted,
        restSecondsHint: Int? = null
    ) {
        val becameCompleted = !set.isCompleted && isCompleted
        viewModelScope.launch(Dispatchers.IO) {
            db.updateWorkoutSet(
                setId = set.id,
                weightKg = weightKg,
                reps = reps,
                durationSeconds = durationSeconds,
                rpe = rpe,
                isWarmup = isWarmup,
                isCompleted = isCompleted
            )
            val updated = db.getActiveSessionDetail()
            val appPrefs = _uiState.value.preferences
            val restSec = restSecondsHint?.takeIf { it > 0 } ?: appPrefs.defaultRestSeconds
            val shouldStartRest = becameCompleted && appPrefs.autoStartRestTimer
            _uiState.update { state ->
                state.copy(activeSession = updated)
            }
            if (shouldStartRest) {
                startRestTimerInternal(restSec)
            }
        }
    }

    fun startOrStopInlineSetTimer(set: WorkoutSet) {
        val current = _uiState.value
        if (current.activeTimedSetId == set.id) {
            // Stop
            _uiState.update {
                it.copy(activeTimedSetId = null, activeTimedSetRemaining = 0)
            }
        } else {
            val seconds = set.durationSeconds.takeIf { it > 0 } ?: 20
            _uiState.update {
                it.copy(
                    activeTimedSetId = set.id,
                    activeTimedSetRemaining = seconds
                )
            }
        }
    }

    fun startRestTimer(seconds: Int = _uiState.value.preferences.defaultRestSeconds) {
        startRestTimerInternal(seconds)
    }

    private fun startRestTimerInternal(seconds: Int) {
        val appCtx = getApplication<Application>().applicationContext
        val appPrefs = _uiState.value.preferences
        val snapshot = RestTimerCoordinator.startTimer(
            context = appCtx,
            durationSeconds = seconds,
            notifyInBackground = appPrefs.notifyRestInBackground,
            vibrateOnFinish = appPrefs.vibrateOnRestFinish
        )
        val askPermission = appPrefs.notifyRestInBackground &&
            !RestTimerNotificationHelper.hasNotificationPermission(appCtx) &&
            !askedNotificationPermissionThisSession
        if (askPermission) {
            askedNotificationPermissionThisSession = true
        }
        _uiState.update {
            it.copy(
                restRemainingSeconds = snapshot.totalSeconds,
                restTotalSeconds = snapshot.totalSeconds,
                isRestTimerRunning = true,
                shouldRequestNotificationPermission = if (askPermission) true else it.shouldRequestNotificationPermission
            )
        }
    }

    fun adjustRestTimer(deltaSeconds: Int) {
        val appCtx = getApplication<Application>().applicationContext
        val updated = RestTimerCoordinator.adjustRunningTimer(appCtx, deltaSeconds)
        if (updated == null) {
            _uiState.update {
                it.copy(restRemainingSeconds = 0, isRestTimerRunning = false)
            }
        } else {
            val rem = RestTimerLogic.computeRemainingSeconds(
                updated.endTimeMillis,
                System.currentTimeMillis()
            )
            _uiState.update {
                it.copy(
                    restRemainingSeconds = rem,
                    restTotalSeconds = updated.totalSeconds,
                    isRestTimerRunning = rem > 0
                )
            }
        }
    }

    fun stopRestTimer() {
        RestTimerCoordinator.stopTimer(getApplication<Application>().applicationContext)
        _uiState.update {
            it.copy(
                restRemainingSeconds = 0,
                isRestTimerRunning = false
            )
        }
    }

    fun finishActiveWorkout(notes: String?) {
        val session = _uiState.value.activeSession ?: return
        val elapsed = _uiState.value.workoutElapsedSeconds.coerceAtLeast(1)
        viewModelScope.launch(Dispatchers.IO) {
            RestTimerCoordinator.stopTimer(getApplication<Application>().applicationContext)
            db.finishWorkoutSession(session.session.id, elapsed, notes)
            val history = db.getCompletedSessions()
            val stats = db.getGlobalStats()
            val exercises = db.getAllExercises()
            _uiState.update {
                it.copy(
                    activeSession = null,
                    completedSessions = history,
                    globalStats = stats,
                    exercises = exercises,
                    workoutElapsedSeconds = 0,
                    restRemainingSeconds = 0,
                    isRestTimerRunning = false,
                    activeTimedSetId = null,
                    activeTimedSetRemaining = 0,
                    statusMessage = "¡Entrenamiento guardado en el historial!"
                )
            }
        }
    }

    fun discardActiveWorkout() {
        val session = _uiState.value.activeSession ?: return
        viewModelScope.launch(Dispatchers.IO) {
            RestTimerCoordinator.stopTimer(getApplication<Application>().applicationContext)
            db.deleteWorkoutSession(session.session.id)
            _uiState.update {
                it.copy(
                    activeSession = null,
                    workoutElapsedSeconds = 0,
                    restRemainingSeconds = 0,
                    isRestTimerRunning = false,
                    activeTimedSetId = null,
                    activeTimedSetRemaining = 0,
                    statusMessage = "Sesión descartada"
                )
            }
        }
    }

    // =========================================================================
    // EXERCISES & ROUTINES MANAGEMENT
    // =========================================================================

    fun saveExercise(
        id: String?,
        name: String,
        category: ExerciseCategory,
        muscleGroup: MuscleGroup,
        measurementType: MeasurementType,
        notes: String?,
        videoUrl: String?,
        isCustom: Boolean = true
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            db.saveExercise(
                id = id,
                name = name,
                category = category,
                muscleGroup = muscleGroup,
                measurementType = measurementType,
                notes = notes,
                videoUrl = videoUrl,
                isCustom = isCustom
            )
            refreshAllData()
            showStatus("Ejercicio guardado: $name")
        }
    }

    fun deleteExercise(exerciseId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val res = db.deleteExercise(exerciseId)
            res.onSuccess {
                refreshAllData()
                showStatus("Ejercicio eliminado")
            }.onFailure { err ->
                showStatus(err.message ?: "No se pudo eliminar el ejercicio")
            }
        }
    }

    fun getExerciseStats(exerciseId: String): ExerciseStats {
        return db.getExerciseStats(exerciseId)
    }

    fun saveRoutine(
        routineId: String?,
        name: String,
        description: String?,
        items: List<YoliGymDatabase.RoutineExerciseDraft>
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            db.saveRoutine(routineId, name, description, items)
            refreshAllData()
            showStatus("Rutina guardada: $name")
        }
    }

    fun deleteRoutine(routineId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.deleteRoutine(routineId)
            refreshAllData()
            showStatus("Rutina eliminada")
        }
    }

    fun deleteHistorySession(sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.deleteWorkoutSession(sessionId)
            refreshAllData()
            showStatus("Sesión eliminada del historial")
        }
    }

    fun updateHistorySession(sessionId: String, name: String, notes: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            db.updateSessionNotesAndName(sessionId, name, notes)
            refreshAllData()
            showStatus("Sesión actualizada")
        }
    }

    fun restoreSeedData() {
        viewModelScope.launch(Dispatchers.IO) {
            db.seedExercisesFromAssets()
            db.seedDefaultRoutines()
            refreshAllData()
            showStatus("Catálogo de ejercicios y rutinas base restaurados")
        }
    }

    fun clearAllWorkoutHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            db.clearAllHistory()
            refreshAllData()
            showStatus("Historial de sesiones borrado")
        }
    }

    fun getBackupJson(): String {
        return db.exportBackupJson()
    }

    fun importBackupJson(jsonText: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = db.importBackupJson(jsonText)
            result.onSuccess { summary ->
                refreshAllData()
                showStatus(
                    "Backup importado: ${summary.exercisesCount} ejercicios, ${summary.routinesCount} rutinas y ${summary.sessionsCount} sesiones (${summary.setsCount} series)"
                )
            }.onFailure { err ->
                showStatus("Error al importar backup: ${err.message ?: "JSON inválido"}")
            }
        }
    }

    fun importSeedExercisesJson(jsonText: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = db.importSeedExercisesJson(jsonText)
            result.onSuccess { count ->
                refreshAllData()
                showStatus("Importados/actualizados $count ejercicios desde seedExercises.json")
            }.onFailure { err ->
                showStatus("Error al importar seedExercises.json: ${err.message ?: "JSON inválido"}")
            }
        }
    }

    // =========================================================================
    // GITHUB AUTOMATIC DAILY BACKUP
    // =========================================================================

    fun configureGitHubBackup(
        repoInput: String,
        userIdInput: String,
        tokenInput: String?,
        branchInput: String?,
        onSuccessDismiss: () -> Unit
    ) {
        if (_uiState.value.isGitHubBusy) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isGitHubBusy = true) }
            val result = githubManager.configureAndVerify(
                repoInput = repoInput,
                userIdInput = userIdInput,
                tokenInput = tokenInput,
                branchInput = branchInput
            )
            result.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isGitHubBusy = false,
                        statusMessage = err.message ?: "No se pudo verificar el repositorio en GitHub"
                    )
                }
            }.onSuccess { savedState ->
                _uiState.update { it.copy(githubBackup = savedState) }
                withContext(Dispatchers.Main) {
                    onSuccessDismiss()
                }
                runGitHubBackupInternal(configuredRepoLabel = "${savedState.repoFullName} (${savedState.userId})")
            }
        }
    }

    fun setGitHubAutoBackupEnabled(enabled: Boolean) {
        githubPrefs.setAutoBackupEnabled(enabled)
        val updated = githubPrefs.loadState()
        _uiState.update { it.copy(githubBackup = updated) }
        val appCtx = getApplication<Application>().applicationContext
        if (enabled && updated.isConfigured && updated.hasToken) {
            GitHubBackupScheduler.scheduleDailyBackup(
                context = appCtx,
                backupHour = updated.backupHour,
                wifiOnly = updated.wifiOnly,
                replaceExisting = true
            )
            GitHubBackupScheduler.enqueueCatchUpIfDue(appCtx, updated)
            showStatus("Backup diario automático en GitHub activado")
        } else {
            GitHubBackupScheduler.cancelAll(appCtx)
            showStatus("Backup diario automático en GitHub desactivado")
        }
    }

    fun setGitHubWifiOnly(wifiOnly: Boolean) {
        githubPrefs.setWifiOnly(wifiOnly)
        val updated = githubPrefs.loadState()
        _uiState.update { it.copy(githubBackup = updated) }
        if (updated.isConfigured && updated.hasToken && updated.autoBackupEnabled) {
            GitHubBackupScheduler.scheduleDailyBackup(
                context = getApplication<Application>().applicationContext,
                backupHour = updated.backupHour,
                wifiOnly = updated.wifiOnly,
                replaceExisting = true
            )
        }
    }

    fun setGitHubBackupHour(hour: Int) {
        githubPrefs.setBackupHour(hour)
        val updated = githubPrefs.loadState()
        _uiState.update { it.copy(githubBackup = updated) }
        if (updated.isConfigured && updated.hasToken && updated.autoBackupEnabled) {
            GitHubBackupScheduler.scheduleDailyBackup(
                context = getApplication<Application>().applicationContext,
                backupHour = updated.backupHour,
                wifiOnly = updated.wifiOnly,
                replaceExisting = true
            )
        }
    }

    fun backupToGitHubNow() {
        if (_uiState.value.isGitHubBusy) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isGitHubBusy = true) }
            runGitHubBackupInternal(configuredRepoLabel = null)
        }
    }

    fun openGitHubRestorePicker() {
        if (_uiState.value.isGitHubBusy) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isGitHubBusy = true) }
            val result = githubManager.listRemoteBackups()
            result.onSuccess { files ->
                _uiState.update {
                    it.copy(
                        isGitHubBusy = false,
                        remoteGitHubBackups = files,
                        githubBackup = githubPrefs.loadState()
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isGitHubBusy = false,
                        githubBackup = githubPrefs.loadState(),
                        statusMessage = err.message ?: "No se pudieron listar los backups en GitHub"
                    )
                }
            }
        }
    }

    fun dismissGitHubRestorePicker() {
        _uiState.update { it.copy(remoteGitHubBackups = null) }
    }

    fun restoreBackupFromGitHub(file: GitHubBackupFile) {
        if (_uiState.value.isGitHubBusy) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isGitHubBusy = true) }
            val downloadResult = githubManager.downloadRemoteBackup(file)
            downloadResult.onSuccess { jsonText ->
                val importRes = db.importBackupJson(jsonText)
                importRes.onSuccess { summary ->
                    refreshAllData()
                    _uiState.update {
                        it.copy(
                            isGitHubBusy = false,
                            remoteGitHubBackups = null,
                            statusMessage = "Backup restaurado (${file.name}): ${summary.exercisesCount} ejercicios, ${summary.routinesCount} rutinas y ${summary.sessionsCount} sesiones"
                        )
                    }
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isGitHubBusy = false,
                            statusMessage = "Error al importar backup de GitHub: ${err.message ?: "JSON inválido"}"
                        )
                    }
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isGitHubBusy = false,
                        githubBackup = githubPrefs.loadState(),
                        statusMessage = err.message ?: "No se pudo descargar el backup desde GitHub"
                    )
                }
            }
        }
    }

    fun disconnectGitHubBackup() {
        if (_uiState.value.isGitHubBusy) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isGitHubBusy = true) }
            githubManager.disconnect()
            _uiState.update {
                it.copy(
                    isGitHubBusy = false,
                    githubBackup = githubPrefs.loadState(),
                    remoteGitHubBackups = null,
                    statusMessage = "Repositorio de GitHub desconectado"
                )
            }
        }
    }

    private fun runGitHubBackupInternal(configuredRepoLabel: String?) {
        when (val result = githubManager.performBackup()) {
            is GitHubBackupRunResult.Success -> {
                val deletedNote = if (result.deletedOldCount > 0) {
                    " (${result.deletedOldCount} antiguos eliminados)"
                } else ""
                val prefix = if (configuredRepoLabel != null) "Conectado a $configuredRepoLabel. " else ""
                _uiState.update {
                    it.copy(
                        isGitHubBusy = false,
                        githubBackup = githubPrefs.loadState(),
                        statusMessage = "${prefix}Backup guardado en GitHub: ${result.fileName}$deletedNote"
                    )
                }
            }
            is GitHubBackupRunResult.AuthError -> {
                val prefix = if (configuredRepoLabel != null) "Configuración guardada, pero error de token: " else ""
                _uiState.update {
                    it.copy(
                        isGitHubBusy = false,
                        githubBackup = githubPrefs.loadState(),
                        statusMessage = "$prefix${result.message}"
                    )
                }
            }
            is GitHubBackupRunResult.Error -> {
                val prefix = if (configuredRepoLabel != null) {
                    "Configuración guardada, pero falló el primer backup: "
                } else {
                    "Error al subir backup a GitHub: "
                }
                _uiState.update {
                    it.copy(
                        isGitHubBusy = false,
                        githubBackup = githubPrefs.loadState(),
                        statusMessage = "$prefix${result.message}"
                    )
                }
            }
        }
    }
}
