package com.yoligym.app.ui

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

data class AppPreferences(
    val darkTheme: Boolean = true,
    val defaultRestSeconds: Int = 90,
    val weightStepKg: Double = 2.5,
    val autoStartRestTimer: Boolean = true,
    val vibrateOnRestFinish: Boolean = true
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
    val statusMessage: String? = null
)

class YoliGymViewModel(application: Application) : AndroidViewModel(application) {

    private val db = YoliGymDatabase(application.applicationContext)
    private val prefs = application.getSharedPreferences("yoligym_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(YoliGymUiState())
    val uiState: StateFlow<YoliGymUiState> = _uiState.asStateFlow()

    private var tickerJob: Job? = null

    init {
        loadPreferences()
        refreshAllData()
        startTicker()
    }

    private fun loadPreferences() {
        val appPrefs = AppPreferences(
            darkTheme = prefs.getBoolean("dark_theme", true),
            defaultRestSeconds = prefs.getInt("default_rest_seconds", 90),
            weightStepKg = prefs.getFloat("weight_step_kg", 2.5f).toDouble(),
            autoStartRestTimer = prefs.getBoolean("auto_start_rest", true),
            vibrateOnRestFinish = prefs.getBoolean("vibrate_on_rest", true)
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
            .apply()
        _uiState.update { it.copy(preferences = updated) }
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

                var nextRest = current.restRemainingSeconds
                var nextRestRunning = current.isRestTimerRunning
                if (current.isRestTimerRunning && current.restRemainingSeconds > 0) {
                    nextRest -= 1
                    if (nextRest <= 0) {
                        nextRest = 0
                        nextRestRunning = false
                        if (current.preferences.vibrateOnRestFinish) {
                            triggerVibration(400L)
                        }
                    }
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
                    nextRestRunning != current.isRestTimerRunning ||
                    nextTimedSetId != current.activeTimedSetId ||
                    nextTimedSetRem != current.activeTimedSetRemaining
                ) {
                    _uiState.update {
                        it.copy(
                            workoutElapsedSeconds = nextElapsed,
                            restRemainingSeconds = nextRest,
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
        try {
            val ctx = getApplication<Application>().applicationContext
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.vibrate(
                VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } catch (_: Exception) {
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
            _uiState.update { state ->
                val restSec = restSecondsHint?.takeIf { it > 0 } ?: state.preferences.defaultRestSeconds
                val shouldStartRest = becameCompleted && state.preferences.autoStartRestTimer
                state.copy(
                    activeSession = updated,
                    restRemainingSeconds = if (shouldStartRest) restSec else state.restRemainingSeconds,
                    restTotalSeconds = if (shouldStartRest) restSec else state.restTotalSeconds,
                    isRestTimerRunning = if (shouldStartRest) true else state.isRestTimerRunning
                )
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
        val safe = seconds.coerceIn(5, 600)
        _uiState.update {
            it.copy(
                restRemainingSeconds = safe,
                restTotalSeconds = safe,
                isRestTimerRunning = true
            )
        }
    }

    fun adjustRestTimer(deltaSeconds: Int) {
        _uiState.update { state ->
            val updated = (state.restRemainingSeconds + deltaSeconds).coerceIn(0, 600)
            val total = maxOf(state.restTotalSeconds, updated, 15)
            state.copy(
                restRemainingSeconds = updated,
                restTotalSeconds = total,
                isRestTimerRunning = updated > 0
            )
        }
    }

    fun stopRestTimer() {
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
}
