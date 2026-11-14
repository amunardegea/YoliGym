package com.yoligym.app.data

enum class ExerciseCategory(val dbValue: String, val labelEs: String, val shortEs: String) {
    FREE_WEIGHT("FREE_WEIGHT", "Peso Libre", "Peso Libre"),
    MACHINE("MACHINE", "Máquina / Polea", "Máquina"),
    MOBILITY("MOBILITY", "Movilidad y Equilibrio", "Movilidad");

    companion object {
        fun fromDb(value: String): ExerciseCategory =
            entries.firstOrNull { it.dbValue == value } ?: FREE_WEIGHT
    }
}

enum class MuscleGroup(val dbValue: String, val labelEs: String) {
    CHEST("CHEST", "Pecho"),
    BACK("BACK", "Espalda"),
    LEGS("LEGS", "Piernas"),
    SHOULDERS("SHOULDERS", "Hombros"),
    ARMS("ARMS", "Brazos"),
    CORE("CORE", "Core / Abdomen"),
    FULL_BODY("FULL_BODY", "Cuerpo Completo");

    companion object {
        fun fromDb(value: String): MuscleGroup =
            entries.firstOrNull { it.dbValue == value } ?: FULL_BODY
    }
}

enum class MeasurementType(val dbValue: String, val labelEs: String, val unitHint: String) {
    WEIGHT_REPS("WEIGHT_REPS", "Peso y Repeticiones", "kg × reps"),
    REPS_ONLY("REPS_ONLY", "Solo Repeticiones / Pasos", "reps"),
    TIME_SECONDS("TIME_SECONDS", "Tiempo (Segundos)", "seg");

    companion object {
        fun fromDb(value: String): MeasurementType =
            entries.firstOrNull { it.dbValue == value } ?: WEIGHT_REPS
    }
}

data class Exercise(
    val id: String,
    val name: String,
    val category: ExerciseCategory,
    val muscleGroup: MuscleGroup,
    val measurementType: MeasurementType,
    val notes: String?,
    val videoUrl: String?,
    val isCustom: Boolean = false
)

data class Routine(
    val id: String,
    val name: String,
    val description: String?,
    val createdAt: String
)

data class RoutineExercise(
    val id: String,
    val routineId: String,
    val exerciseId: String,
    val orderIndex: Int,
    val targetSets: Int = 4,
    val targetReps: String = "12-15",
    val targetDurationSeconds: Int = 0,
    val targetRestSeconds: Int = 90,
    val exercise: Exercise
)

data class RoutineWithExercises(
    val routine: Routine,
    val items: List<RoutineExercise>
)

data class WorkoutSession(
    val id: String,
    val routineId: String?,
    val name: String,
    val startedAt: String,
    val finishedAt: String?,
    val durationSeconds: Int?,
    val notes: String?
)

data class WorkoutSet(
    val id: String,
    val sessionId: String,
    val exerciseId: String,
    val setNumber: Int,
    val weightKg: Double = 0.0,
    val reps: Int = 0,
    val durationSeconds: Int = 0,
    val rpe: Double? = null,
    val isWarmup: Boolean = false,
    val isCompleted: Boolean = false,
    val completedAt: String? = null
)

data class ExerciseSessionSets(
    val exercise: Exercise,
    val sets: List<WorkoutSet>,
    val targetRepsHint: String = "12-15",
    val targetDurationSeconds: Int = 20,
    val targetRestSeconds: Int = 90,
    val previousBestText: String? = null
)

data class SessionDetail(
    val session: WorkoutSession,
    val exercises: List<ExerciseSessionSets>
) {
    val totalSetsCount: Int
        get() = exercises.sumOf { it.sets.size }

    val completedSetsCount: Int
        get() = exercises.sumOf { ex -> ex.sets.count { it.isCompleted } }

    val totalVolumeKg: Double
        get() = exercises.sumOf { ex ->
            ex.sets.filter { it.isCompleted && !it.isWarmup }
                .sumOf { it.weightKg * it.reps.coerceAtLeast(1) }
        }
}

data class ExerciseStats(
    val exerciseId: String,
    val maxWeightKg: Double = 0.0,
    val maxReps: Int = 0,
    val maxDurationSeconds: Int = 0,
    val totalCompletedSets: Int = 0,
    val lastPerformanceText: String? = null,
    val recentSets: List<Pair<String, WorkoutSet>> = emptyList()
)

data class GlobalStats(
    val totalSessions: Int = 0,
    val totalCompletedSets: Int = 0,
    val totalDurationSeconds: Long = 0L,
    val totalVolumeKg: Double = 0.0,
    val totalExercises: Int = 0,
    val customExercises: Int = 0,
    val totalRoutines: Int = 0,
    val foreignKeysEnabled: Boolean = true
)
