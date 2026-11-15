package com.yoligym.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class YoliGymDatabase(private val appContext: Context) :
    SQLiteOpenHelper(appContext, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "yoligym.db"
        const val DATABASE_VERSION = 1

        fun sanitizeVideoUrl(raw: String?): String? {
            if (raw.isNullOrBlank() || raw == "null") return null
            val trimmed = raw.trim()
            val markdownRegex = Regex("""^\[([^\]]+)]\(([^)]+)\)$""")
            val match = markdownRegex.find(trimmed)
            return if (match != null) {
                match.groupValues[2].trim()
            } else {
                trimmed
            }
        }

        fun nowIsoString(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            return sdf.format(Date())
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
        db.execSQL("PRAGMA foreign_keys = ON;")
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        db.execSQL("PRAGMA foreign_keys = ON;")
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys = ON;")

        // 1. Catálogo de Ejercicios
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS exercises (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                category TEXT NOT NULL CHECK(category IN ('FREE_WEIGHT', 'MACHINE', 'MOBILITY')),
                muscle_group TEXT NOT NULL CHECK(muscle_group IN ('CHEST', 'BACK', 'LEGS', 'SHOULDERS', 'ARMS', 'CORE', 'FULL_BODY')),
                measurement_type TEXT NOT NULL CHECK(measurement_type IN ('WEIGHT_REPS', 'REPS_ONLY', 'TIME_SECONDS')),
                notes TEXT,
                video_url TEXT,
                is_custom INTEGER DEFAULT 0
            );
            """.trimIndent()
        )

        // 2. Plantillas de Rutinas
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS routines (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                description TEXT,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP
            );
            """.trimIndent()
        )

        // 3. Ejercicios asociados a cada Rutina
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS routine_exercises (
                id TEXT PRIMARY KEY,
                routine_id TEXT NOT NULL,
                exercise_id TEXT NOT NULL,
                order_index INTEGER NOT NULL,
                target_sets INTEGER DEFAULT 4,
                target_reps TEXT DEFAULT '12-15',
                target_duration_seconds INTEGER DEFAULT 0,
                target_rest_seconds INTEGER DEFAULT 90,
                FOREIGN KEY (routine_id) REFERENCES routines(id) ON DELETE CASCADE,
                FOREIGN KEY (exercise_id) REFERENCES exercises(id) ON DELETE RESTRICT
            );
            """.trimIndent()
        )

        // 4. Sesiones de Entrenamiento Reales (Historial)
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS workout_sessions (
                id TEXT PRIMARY KEY,
                routine_id TEXT,
                name TEXT NOT NULL,
                started_at TEXT NOT NULL,
                finished_at TEXT,
                duration_seconds INTEGER,
                notes TEXT,
                FOREIGN KEY (routine_id) REFERENCES routines(id) ON DELETE SET NULL
            );
            """.trimIndent()
        )

        // 5. Series registradas dentro de una Sesión
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS workout_sets (
                id TEXT PRIMARY KEY,
                session_id TEXT NOT NULL,
                exercise_id TEXT NOT NULL,
                set_number INTEGER NOT NULL,
                weight_kg REAL DEFAULT 0,
                reps INTEGER DEFAULT 0,
                duration_seconds INTEGER DEFAULT 0,
                rpe REAL,
                is_warmup INTEGER DEFAULT 0,
                is_completed INTEGER DEFAULT 0,
                completed_at TEXT,
                FOREIGN KEY (session_id) REFERENCES workout_sessions(id) ON DELETE CASCADE,
                FOREIGN KEY (exercise_id) REFERENCES exercises(id) ON DELETE RESTRICT
            );
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sets_exercise ON workout_sets(exercise_id, is_completed);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sets_session ON workout_sets(session_id);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_exercises_filter ON exercises(category, muscle_group);")

        seedExercisesFromAssets(db)
        seedDefaultRoutines(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onCreate(db)
    }

    fun ensureSeeded() {
        val db = writableDatabase
        val count = db.rawQuery("SELECT COUNT(*) FROM exercises", null).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
        if (count == 0) {
            seedExercisesFromAssets(db)
        }
        val routineCount = db.rawQuery("SELECT COUNT(*) FROM routines", null).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
        if (routineCount == 0) {
            seedDefaultRoutines(db)
        }
    }

    private fun upsertExerciseRow(db: SQLiteDatabase, values: ContentValues, id: String): Boolean {
        val updated = db.update("exercises", values, "id = ?", arrayOf(id))
        if (updated > 0) return true
        val rowId = db.insertWithOnConflict("exercises", null, values, SQLiteDatabase.CONFLICT_IGNORE)
        return rowId != -1L
    }

    private fun upsertRoutineRow(db: SQLiteDatabase, values: ContentValues, id: String) {
        val updated = db.update("routines", values, "id = ?", arrayOf(id))
        if (updated == 0) {
            db.insertWithOnConflict("routines", null, values, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    fun seedExercisesFromAssets(db: SQLiteDatabase = writableDatabase): Int {
        return try {
            val jsonText = appContext.assets.open("seedExercises.json")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            importSeedExercisesJson(jsonText, db).getOrDefault(0)
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    fun importSeedExercisesJson(
        jsonText: String,
        db: SQLiteDatabase = writableDatabase
    ): Result<Int> {
        return try {
            val trimmed = jsonText.trim()
            if (trimmed.isEmpty()) {
                return Result.failure(IllegalArgumentException("El contenido JSON está vacío."))
            }
            val jsonArray = when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                trimmed.startsWith("{") -> {
                    val root = JSONObject(trimmed)
                    root.optJSONArray("exercises")
                        ?: return Result.failure(
                            IllegalArgumentException("El JSON no contiene una lista de ejercicios válida.")
                        )
                }
                else -> return Result.failure(
                    IllegalArgumentException("Formato JSON no válido para seedExercises.json.")
                )
            }

            var processedCount = 0
            db.beginTransaction()
            try {
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.optJSONObject(i) ?: continue
                    val id = obj.optString("id").trim()
                    val name = obj.optString("name").trim()
                    if (id.isEmpty() || name.isEmpty()) continue

                    val category = ExerciseCategory.fromDb(obj.optString("category")).dbValue
                    val muscleGroup = MuscleGroup.fromDb(obj.optString("muscle_group")).dbValue
                    val measurementType = MeasurementType.fromDb(obj.optString("measurement_type")).dbValue
                    val notes = if (obj.isNull("notes")) null else obj.optString("notes").takeIf { it.isNotBlank() && it != "null" }
                    val rawVideo = if (obj.isNull("video_url")) null else obj.optString("video_url")
                    val cleanVideo = sanitizeVideoUrl(rawVideo)
                    val isCustom = if (obj.has("is_custom")) obj.optInt("is_custom", 0) else 0

                    val values = ContentValues().apply {
                        put("id", id)
                        put("name", name)
                        put("category", category)
                        put("muscle_group", muscleGroup)
                        put("measurement_type", measurementType)
                        put("notes", notes)
                        put("video_url", cleanVideo)
                        put("is_custom", isCustom)
                    }
                    if (upsertExerciseRow(db, values, id)) {
                        processedCount++
                    }
                }
                if (processedCount == 0) {
                    throw IllegalArgumentException("No se encontró ningún ejercicio válido en el archivo JSON.")
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            Result.success(processedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun seedDefaultRoutines(db: SQLiteDatabase = writableDatabase) {
        db.beginTransaction()
        try {
            val now = nowIsoString()

            // Rutina 1: Día 1 - Máquinas, Peso Libre y Equilibrio
            insertRoutineInternal(
                db = db,
                id = "routine_rgym_d1",
                name = "Día 1 · Fuerza y Equilibrio",
                description = "Tracción vertical, empuje horizontal, brazos con mancuerna, pierna en máquina y bloque de equilibrio.",
                createdAt = now,
                items = listOf(
                    SeedRoutineItem("rgym_d1_01", 1, 4, "12-15", 0, 90),
                    SeedRoutineItem("rgym_d1_02", 2, 4, "12-15", 0, 90),
                    SeedRoutineItem("rgym_d1_03", 3, 3, "12-15", 0, 75),
                    SeedRoutineItem("rgym_d1_04", 4, 3, "12-15", 0, 75),
                    SeedRoutineItem("rgym_d1_05", 5, 4, "12-15", 0, 90),
                    SeedRoutineItem("rgym_d1_06", 6, 4, "12-15", 0, 90),
                    SeedRoutineItem("rgym_mob_01", 7, 3, "1", 20, 45),
                    SeedRoutineItem("rgym_mob_03", 8, 2, "20", 0, 45)
                )
            )

            // Rutina 2: Día 2 - Máquinas, Cadera y Equilibrio
            insertRoutineInternal(
                db = db,
                id = "routine_rgym_d2",
                name = "Día 2 · Máquinas y Control Motor",
                description = "Remo sentado, contractor pectoral, bíceps/tríceps en máquina, aducción/abducción de cadera y propiocepción.",
                createdAt = now,
                items = listOf(
                    SeedRoutineItem("rgym_d2_01", 1, 4, "12-15", 0, 90),
                    SeedRoutineItem("rgym_d2_02", 2, 4, "12-15", 0, 90),
                    SeedRoutineItem("rgym_d2_03", 3, 3, "12-15", 0, 75),
                    SeedRoutineItem("rgym_d2_04", 4, 3, "12-15", 0, 75),
                    SeedRoutineItem("rgym_d2_05", 5, 4, "12-15", 0, 75),
                    SeedRoutineItem("rgym_d2_06", 6, 4, "12-15", 0, 75),
                    SeedRoutineItem("rgym_mob_02", 7, 3, "1", 20, 45),
                    SeedRoutineItem("rgym_mob_03", 8, 2, "20", 0, 45)
                )
            )

            // Rutina 3: Movilidad, Equilibrio y Propiocepción
            insertRoutineInternal(
                db = db,
                id = "routine_rgym_mob",
                name = "Movilidad y Equilibrio (Romberg)",
                description = "Sesión específica de equilibrio monopodal, superficie inestable con toalla, marcha Romberg y movilidad articular.",
                createdAt = now,
                items = listOf(
                    SeedRoutineItem("rgym_mob_01", 1, 3, "1", 20, 30),
                    SeedRoutineItem("rgym_mob_02", 2, 3, "1", 20, 30),
                    SeedRoutineItem("rgym_mob_03", 3, 2, "20", 0, 45),
                    SeedRoutineItem("mb_03", 4, 2, "12", 0, 30),
                    SeedRoutineItem("mb_06", 5, 2, "10", 0, 30),
                    SeedRoutineItem("mb_07", 6, 2, "8", 0, 45)
                )
            )

            // Rutina 4: Fuerza Básica Peso Libre
            insertRoutineInternal(
                db = db,
                id = "routine_fw_full",
                name = "Fuerza Full Body · Peso Libre",
                description = "Básicos multiarticulares con barra y mancuernas para fuerza general.",
                createdAt = now,
                items = listOf(
                    SeedRoutineItem("fw_09", 1, 4, "6-8", 0, 120),
                    SeedRoutineItem("fw_01", 2, 4, "8-10", 0, 120),
                    SeedRoutineItem("fw_06", 3, 4, "8-10", 0, 90),
                    SeedRoutineItem("fw_10", 4, 3, "10-12", 0, 90),
                    SeedRoutineItem("fw_15", 5, 3, "10-12", 0, 90),
                    SeedRoutineItem("fw_21", 6, 3, "12", 0, 60)
                )
            )

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private data class SeedRoutineItem(
        val exerciseId: String,
        val orderIndex: Int,
        val targetSets: Int,
        val targetReps: String,
        val targetDurationSeconds: Int,
        val targetRestSeconds: Int
    )

    private fun insertRoutineInternal(
        db: SQLiteDatabase,
        id: String,
        name: String,
        description: String,
        createdAt: String,
        items: List<SeedRoutineItem>
    ) {
        val rValues = ContentValues().apply {
            put("id", id)
            put("name", name)
            put("description", description)
            put("created_at", createdAt)
        }
        db.insertWithOnConflict("routines", null, rValues, SQLiteDatabase.CONFLICT_REPLACE)
        db.delete("routine_exercises", "routine_id = ?", arrayOf(id))

        items.forEach { item ->
            // Verify exercise exists before linking
            val exists = db.rawQuery(
                "SELECT 1 FROM exercises WHERE id = ?",
                arrayOf(item.exerciseId)
            ).use { it.moveToFirst() }
            if (exists) {
                val reValues = ContentValues().apply {
                    put("id", "${id}_${item.orderIndex}")
                    put("routine_id", id)
                    put("exercise_id", item.exerciseId)
                    put("order_index", item.orderIndex)
                    put("target_sets", item.targetSets)
                    put("target_reps", item.targetReps)
                    put("target_duration_seconds", item.targetDurationSeconds)
                    put("target_rest_seconds", item.targetRestSeconds)
                }
                db.insertWithOnConflict(
                    "routine_exercises",
                    null,
                    reValues,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
        }
    }

    // =========================================================================
    // EXERCISES CRUD & QUERIES
    // =========================================================================

    private fun Cursor.toExercise(): Exercise {
        val rawUrl = if (isNull(getColumnIndexOrThrow("video_url"))) null
        else getString(getColumnIndexOrThrow("video_url"))
        return Exercise(
            id = getString(getColumnIndexOrThrow("id")),
            name = getString(getColumnIndexOrThrow("name")),
            category = ExerciseCategory.fromDb(getString(getColumnIndexOrThrow("category"))),
            muscleGroup = MuscleGroup.fromDb(getString(getColumnIndexOrThrow("muscle_group"))),
            measurementType = MeasurementType.fromDb(getString(getColumnIndexOrThrow("measurement_type"))),
            notes = if (isNull(getColumnIndexOrThrow("notes"))) null else getString(getColumnIndexOrThrow("notes")),
            videoUrl = sanitizeVideoUrl(rawUrl),
            isCustom = getInt(getColumnIndexOrThrow("is_custom")) == 1
        )
    }

    fun getAllExercises(): List<Exercise> {
        val list = mutableListOf<Exercise>()
        readableDatabase.rawQuery(
            "SELECT * FROM exercises ORDER BY is_custom DESC, name COLLATE NOCASE ASC",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursor.toExercise())
            }
        }
        return list
    }

    fun getExerciseById(id: String): Exercise? {
        return readableDatabase.rawQuery(
            "SELECT * FROM exercises WHERE id = ?",
            arrayOf(id)
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toExercise() else null
        }
    }

    fun saveExercise(
        id: String?,
        name: String,
        category: ExerciseCategory,
        muscleGroup: MuscleGroup,
        measurementType: MeasurementType,
        notes: String?,
        videoUrl: String?,
        isCustom: Boolean = true
    ): Exercise {
        val finalId = id?.takeIf { it.isNotBlank() } ?: "custom_${UUID.randomUUID().toString().take(8)}"
        val cleanVideo = sanitizeVideoUrl(videoUrl)
        val values = ContentValues().apply {
            put("id", finalId)
            put("name", name.trim())
            put("category", category.dbValue)
            put("muscle_group", muscleGroup.dbValue)
            put("measurement_type", measurementType.dbValue)
            put("notes", notes?.trim()?.takeIf { it.isNotEmpty() })
            put("video_url", cleanVideo)
            put("is_custom", if (isCustom) 1 else 0)
        }
        writableDatabase.insertWithOnConflict(
            "exercises",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
        return Exercise(
            id = finalId,
            name = name.trim(),
            category = category,
            muscleGroup = muscleGroup,
            measurementType = measurementType,
            notes = notes?.trim()?.takeIf { it.isNotEmpty() },
            videoUrl = cleanVideo,
            isCustom = isCustom
        )
    }

    fun deleteExercise(exerciseId: String): Result<Unit> {
        return try {
            val db = writableDatabase
            val rows = db.delete("exercises", "id = ?", arrayOf(exerciseId))
            if (rows > 0) Result.success(Unit)
            else Result.failure(IllegalStateException("No se encontró el ejercicio."))
        } catch (e: Exception) {
            Result.failure(
                IllegalStateException(
                    "No se puede eliminar este ejercicio porque está asociado a una rutina o historial (Integridad Referencial ON DELETE RESTRICT)."
                )
            )
        }
    }

    // =========================================================================
    // ROUTINES CRUD & QUERIES
    // =========================================================================

    fun getAllRoutinesWithExercises(): List<RoutineWithExercises> {
        val db = readableDatabase
        val routines = mutableListOf<Routine>()
        db.rawQuery("SELECT * FROM routines ORDER BY created_at ASC, name ASC", null).use { c ->
            while (c.moveToNext()) {
                routines.add(
                    Routine(
                        id = c.getString(c.getColumnIndexOrThrow("id")),
                        name = c.getString(c.getColumnIndexOrThrow("name")),
                        description = if (c.isNull(c.getColumnIndexOrThrow("description"))) null
                        else c.getString(c.getColumnIndexOrThrow("description")),
                        createdAt = c.getString(c.getColumnIndexOrThrow("created_at")) ?: ""
                    )
                )
            }
        }

        val exercisesMap = getAllExercises().associateBy { it.id }

        return routines.map { routine ->
            val items = mutableListOf<RoutineExercise>()
            db.rawQuery(
                "SELECT * FROM routine_exercises WHERE routine_id = ? ORDER BY order_index ASC",
                arrayOf(routine.id)
            ).use { c ->
                while (c.moveToNext()) {
                    val exId = c.getString(c.getColumnIndexOrThrow("exercise_id"))
                    val exercise = exercisesMap[exId] ?: continue
                    items.add(
                        RoutineExercise(
                            id = c.getString(c.getColumnIndexOrThrow("id")),
                            routineId = routine.id,
                            exerciseId = exId,
                            orderIndex = c.getInt(c.getColumnIndexOrThrow("order_index")),
                            targetSets = c.getInt(c.getColumnIndexOrThrow("target_sets")),
                            targetReps = c.getString(c.getColumnIndexOrThrow("target_reps")) ?: "12-15",
                            targetDurationSeconds = c.getInt(c.getColumnIndexOrThrow("target_duration_seconds")),
                            targetRestSeconds = c.getInt(c.getColumnIndexOrThrow("target_rest_seconds")),
                            exercise = exercise
                        )
                    )
                }
            }
            RoutineWithExercises(routine = routine, items = items)
        }
    }

    data class RoutineExerciseDraft(
        val exerciseId: String,
        val targetSets: Int = 4,
        val targetReps: String = "12-15",
        val targetDurationSeconds: Int = 20,
        val targetRestSeconds: Int = 90
    )

    fun saveRoutine(
        routineId: String?,
        name: String,
        description: String?,
        draftItems: List<RoutineExerciseDraft>
    ): String {
        val db = writableDatabase
        val finalId = routineId?.takeIf { it.isNotBlank() } ?: "routine_${UUID.randomUUID().toString().take(8)}"
        db.beginTransaction()
        try {
            val existingCreatedAt = if (routineId != null) {
                db.rawQuery("SELECT created_at FROM routines WHERE id = ?", arrayOf(routineId)).use {
                    if (it.moveToFirst()) it.getString(0) else null
                }
            } else null

            val rValues = ContentValues().apply {
                put("id", finalId)
                put("name", name.trim())
                put("description", description?.trim()?.takeIf { it.isNotEmpty() })
                put("created_at", existingCreatedAt ?: nowIsoString())
            }
            db.insertWithOnConflict("routines", null, rValues, SQLiteDatabase.CONFLICT_REPLACE)

            db.delete("routine_exercises", "routine_id = ?", arrayOf(finalId))
            draftItems.forEachIndexed { index, item ->
                val reValues = ContentValues().apply {
                    put("id", "${finalId}_${index + 1}_${UUID.randomUUID().toString().take(4)}")
                    put("routine_id", finalId)
                    put("exercise_id", item.exerciseId)
                    put("order_index", index + 1)
                    put("target_sets", item.targetSets.coerceIn(1, 20))
                    put("target_reps", item.targetReps.ifBlank { "12-15" })
                    put("target_duration_seconds", item.targetDurationSeconds.coerceAtLeast(0))
                    put("target_rest_seconds", item.targetRestSeconds.coerceIn(10, 600))
                }
                db.insert("routine_exercises", null, reValues)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return finalId
    }

    fun deleteRoutine(routineId: String) {
        writableDatabase.delete("routines", "id = ?", arrayOf(routineId))
    }

    // =========================================================================
    // WORKOUT SESSIONS & SETS (ACTIVE SESSION + HISTORY)
    // =========================================================================

    fun getActiveSessionDetail(): SessionDetail? {
        val db = readableDatabase
        val session = db.rawQuery(
            "SELECT * FROM workout_sessions WHERE finished_at IS NULL ORDER BY started_at DESC LIMIT 1",
            null
        ).use { c ->
            if (c.moveToFirst()) c.toWorkoutSession() else null
        } ?: return null

        return buildSessionDetail(session)
    }

    fun getCompletedSessions(): List<SessionDetail> {
        val db = readableDatabase
        val sessions = mutableListOf<WorkoutSession>()
        db.rawQuery(
            "SELECT * FROM workout_sessions WHERE finished_at IS NOT NULL ORDER BY started_at DESC",
            null
        ).use { c ->
            while (c.moveToNext()) {
                sessions.add(c.toWorkoutSession())
            }
        }
        return sessions.map { buildSessionDetail(it) }
    }

    private fun Cursor.toWorkoutSession(): WorkoutSession {
        return WorkoutSession(
            id = getString(getColumnIndexOrThrow("id")),
            routineId = if (isNull(getColumnIndexOrThrow("routine_id"))) null
            else getString(getColumnIndexOrThrow("routine_id")),
            name = getString(getColumnIndexOrThrow("name")),
            startedAt = getString(getColumnIndexOrThrow("started_at")),
            finishedAt = if (isNull(getColumnIndexOrThrow("finished_at"))) null
            else getString(getColumnIndexOrThrow("finished_at")),
            durationSeconds = if (isNull(getColumnIndexOrThrow("duration_seconds"))) null
            else getInt(getColumnIndexOrThrow("duration_seconds")),
            notes = if (isNull(getColumnIndexOrThrow("notes"))) null
            else getString(getColumnIndexOrThrow("notes"))
        )
    }

    private fun Cursor.toWorkoutSet(): WorkoutSet {
        return WorkoutSet(
            id = getString(getColumnIndexOrThrow("id")),
            sessionId = getString(getColumnIndexOrThrow("session_id")),
            exerciseId = getString(getColumnIndexOrThrow("exercise_id")),
            setNumber = getInt(getColumnIndexOrThrow("set_number")),
            weightKg = getDouble(getColumnIndexOrThrow("weight_kg")),
            reps = getInt(getColumnIndexOrThrow("reps")),
            durationSeconds = getInt(getColumnIndexOrThrow("duration_seconds")),
            rpe = if (isNull(getColumnIndexOrThrow("rpe"))) null else getDouble(getColumnIndexOrThrow("rpe")),
            isWarmup = getInt(getColumnIndexOrThrow("is_warmup")) == 1,
            isCompleted = getInt(getColumnIndexOrThrow("is_completed")) == 1,
            completedAt = if (isNull(getColumnIndexOrThrow("completed_at"))) null
            else getString(getColumnIndexOrThrow("completed_at"))
        )
    }

    private fun buildSessionDetail(session: WorkoutSession): SessionDetail {
        val db = readableDatabase
        val allExercises = getAllExercises().associateBy { it.id }

        // Metadata from routine_exercises if session belongs to a routine
        data class RoutineMeta(
            val orderIndex: Int,
            val targetReps: String,
            val targetDuration: Int,
            val targetRest: Int
        )
        val routineMetaMap = mutableMapOf<String, RoutineMeta>()
        if (session.routineId != null) {
            db.rawQuery(
                "SELECT * FROM routine_exercises WHERE routine_id = ? ORDER BY order_index ASC",
                arrayOf(session.routineId)
            ).use { c ->
                while (c.moveToNext()) {
                    val exId = c.getString(c.getColumnIndexOrThrow("exercise_id"))
                    routineMetaMap[exId] = RoutineMeta(
                        orderIndex = c.getInt(c.getColumnIndexOrThrow("order_index")),
                        targetReps = c.getString(c.getColumnIndexOrThrow("target_reps")) ?: "12-15",
                        targetDuration = c.getInt(c.getColumnIndexOrThrow("target_duration_seconds")),
                        targetRest = c.getInt(c.getColumnIndexOrThrow("target_rest_seconds"))
                    )
                }
            }
        }

        val setsList = mutableListOf<WorkoutSet>()
        db.rawQuery(
            "SELECT * FROM workout_sets WHERE session_id = ? ORDER BY rowid ASC, set_number ASC",
            arrayOf(session.id)
        ).use { c ->
            while (c.moveToNext()) {
                setsList.add(c.toWorkoutSet())
            }
        }

        val grouped = linkedMapOf<String, MutableList<WorkoutSet>>()
        for (set in setsList) {
            grouped.getOrPut(set.exerciseId) { mutableListOf() }.add(set)
        }

        val exerciseSessionSets = grouped.entries.mapNotNull { (exId, sets) ->
            val exercise = allExercises[exId] ?: return@mapNotNull null
            val meta = routineMetaMap[exId]
            val prevText = getPreviousPerformanceSummary(exId, excludeSessionId = session.id)
            ExerciseSessionSets(
                exercise = exercise,
                sets = sets.sortedBy { it.setNumber },
                targetRepsHint = meta?.targetReps ?: "12-15",
                targetDurationSeconds = meta?.targetDuration?.takeIf { it > 0 } ?: 20,
                targetRestSeconds = meta?.targetRest ?: 90,
                previousBestText = prevText
            )
        }

        return SessionDetail(
            session = session,
            exercises = exerciseSessionSets
        )
    }

    fun getPreviousPerformanceSummary(exerciseId: String, excludeSessionId: String? = null): String? {
        val db = readableDatabase
        val exercise = getExerciseById(exerciseId) ?: return null
        val query = if (excludeSessionId != null) {
            """
            SELECT weight_kg, reps, duration_seconds
            FROM workout_sets
            WHERE exercise_id = ? AND is_completed = 1 AND session_id != ?
            ORDER BY completed_at DESC, rowid DESC
            LIMIT 1
            """.trimIndent()
        } else {
            """
            SELECT weight_kg, reps, duration_seconds
            FROM workout_sets
            WHERE exercise_id = ? AND is_completed = 1
            ORDER BY completed_at DESC, rowid DESC
            LIMIT 1
            """.trimIndent()
        }
        val args = if (excludeSessionId != null) arrayOf(exerciseId, excludeSessionId) else arrayOf(exerciseId)

        return db.rawQuery(query, args).use { c ->
            if (c.moveToFirst()) {
                val w = c.getDouble(0)
                val r = c.getInt(1)
                val d = c.getInt(2)
                when (exercise.measurementType) {
                    MeasurementType.WEIGHT_REPS -> "${formatWeight(w)} kg × $r reps"
                    MeasurementType.REPS_ONLY -> "$r reps"
                    MeasurementType.TIME_SECONDS -> "${d}s"
                }
            } else null
        }
    }

    private fun getLastCompletedNumbers(
        exerciseId: String,
        excludeSessionId: String? = null
    ): Triple<Double, Int, Int>? {
        val query = if (excludeSessionId != null) {
            """
            SELECT weight_kg, reps, duration_seconds
            FROM workout_sets
            WHERE exercise_id = ? AND is_completed = 1 AND session_id != ?
            ORDER BY completed_at DESC, rowid DESC
            LIMIT 1
            """.trimIndent()
        } else {
            """
            SELECT weight_kg, reps, duration_seconds
            FROM workout_sets
            WHERE exercise_id = ? AND is_completed = 1
            ORDER BY completed_at DESC, rowid DESC
            LIMIT 1
            """.trimIndent()
        }
        val args = if (excludeSessionId != null) {
            arrayOf(exerciseId, excludeSessionId)
        } else {
            arrayOf(exerciseId)
        }
        return readableDatabase.rawQuery(query, args).use { c ->
            if (c.moveToFirst()) {
                Triple(c.getDouble(0), c.getInt(1), c.getInt(2))
            } else null
        }
    }

    private fun getPreviousNumbersForExactSet(
        exerciseId: String,
        setNumber: Int,
        excludeSessionId: String? = null
    ): Triple<Double, Int, Int>? {
        val query = if (excludeSessionId != null) {
            """
            SELECT s.weight_kg, s.reps, s.duration_seconds
            FROM workout_sets s
            JOIN workout_sessions ws ON ws.id = s.session_id
            WHERE s.exercise_id = ? AND s.set_number = ? AND s.is_completed = 1 AND s.session_id != ?
            ORDER BY COALESCE(s.completed_at, ws.started_at) DESC, s.rowid DESC
            LIMIT 1
            """.trimIndent()
        } else {
            """
            SELECT s.weight_kg, s.reps, s.duration_seconds
            FROM workout_sets s
            JOIN workout_sessions ws ON ws.id = s.session_id
            WHERE s.exercise_id = ? AND s.set_number = ? AND s.is_completed = 1
            ORDER BY COALESCE(s.completed_at, ws.started_at) DESC, s.rowid DESC
            LIMIT 1
            """.trimIndent()
        }
        val args = if (excludeSessionId != null) {
            arrayOf(exerciseId, setNumber.toString(), excludeSessionId)
        } else {
            arrayOf(exerciseId, setNumber.toString())
        }
        return readableDatabase.rawQuery(query, args).use { c ->
            if (c.moveToFirst()) {
                Triple(c.getDouble(0), c.getInt(1), c.getInt(2))
            } else null
        }
    }

    private fun getLastCompletedNumbersForSet(
        exerciseId: String,
        setNumber: Int,
        excludeSessionId: String? = null
    ): Triple<Double, Int, Int>? {
        return getPreviousNumbersForExactSet(exerciseId, setNumber, excludeSessionId)
            ?: getLastCompletedNumbers(exerciseId, excludeSessionId)
    }

    private fun parseDefaultReps(targetReps: String): Int {
        val firstNum = Regex("""\d+""").find(targetReps)?.value?.toIntOrNull()
        return firstNum ?: 12
    }

    fun startWorkoutFromRoutine(routineWithExercises: RoutineWithExercises): SessionDetail {
        // If there is already an active session, return it
        getActiveSessionDetail()?.let { return it }

        val db = writableDatabase
        val sessionId = "sess_${UUID.randomUUID().toString().take(8)}"
        val startedAt = nowIsoString()

        db.beginTransaction()
        try {
            val sValues = ContentValues().apply {
                put("id", sessionId)
                put("routine_id", routineWithExercises.routine.id)
                put("name", routineWithExercises.routine.name)
                put("started_at", startedAt)
            }
            db.insert("workout_sessions", null, sValues)

            for (item in routineWithExercises.items) {
                val fallbackWeight = when (item.exercise.category) {
                    ExerciseCategory.MACHINE -> 25.0
                    ExerciseCategory.FREE_WEIGHT -> if (item.exercise.measurementType == MeasurementType.WEIGHT_REPS) 10.0 else 0.0
                    ExerciseCategory.MOBILITY -> 0.0
                }
                val fallbackReps = if (item.exercise.measurementType == MeasurementType.TIME_SECONDS) {
                    1
                } else {
                    parseDefaultReps(item.targetReps)
                }
                val fallbackDuration = if (item.targetDurationSeconds > 0) item.targetDurationSeconds else 20

                val count = item.targetSets.coerceIn(1, 15)
                for (setNum in 1..count) {
                    val lastNumbers = getLastCompletedNumbersForSet(
                        exerciseId = item.exerciseId,
                        setNumber = setNum,
                        excludeSessionId = sessionId
                    )
                    val defaultWeight = lastNumbers?.first ?: fallbackWeight
                    val defaultReps = lastNumbers?.second?.takeIf { it > 0 } ?: fallbackReps
                    val defaultDuration = lastNumbers?.third?.takeIf { it > 0 } ?: fallbackDuration

                    val setValues = ContentValues().apply {
                        put("id", "set_${UUID.randomUUID().toString().take(8)}")
                        put("session_id", sessionId)
                        put("exercise_id", item.exerciseId)
                        put("set_number", setNum)
                        put("weight_kg", if (item.exercise.measurementType == MeasurementType.WEIGHT_REPS) defaultWeight else 0.0)
                        put("reps", if (item.exercise.measurementType == MeasurementType.TIME_SECONDS) 0 else defaultReps)
                        put("duration_seconds", if (item.exercise.measurementType == MeasurementType.TIME_SECONDS) defaultDuration else 0)
                        put("is_warmup", 0)
                        put("is_completed", 0)
                    }
                    db.insert("workout_sets", null, setValues)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        return getActiveSessionDetail()!!
    }

    fun startFreeWorkout(customName: String = "Entrenamiento Libre"): SessionDetail {
        getActiveSessionDetail()?.let { return it }

        val db = writableDatabase
        val sessionId = "sess_${UUID.randomUUID().toString().take(8)}"
        val startedAt = nowIsoString()
        val sValues = ContentValues().apply {
            put("id", sessionId)
            putNull("routine_id")
            put("name", customName.ifBlank { "Entrenamiento Libre" })
            put("started_at", startedAt)
        }
        db.insert("workout_sessions", null, sValues)
        return getActiveSessionDetail()!!
    }

    fun addExerciseToSession(sessionId: String, exercise: Exercise, setsCount: Int = 3) {
        val db = writableDatabase
        val existingMaxSet = db.rawQuery(
            "SELECT MAX(set_number) FROM workout_sets WHERE session_id = ? AND exercise_id = ?",
            arrayOf(sessionId, exercise.id)
        ).use {
            if (it.moveToFirst() && !it.isNull(0)) it.getInt(0) else 0
        }

        val fallbackWeight = when (exercise.category) {
            ExerciseCategory.MACHINE -> 25.0
            ExerciseCategory.FREE_WEIGHT -> if (exercise.measurementType == MeasurementType.WEIGHT_REPS) 10.0 else 0.0
            ExerciseCategory.MOBILITY -> 0.0
        }
        val fallbackReps = 12
        val fallbackDuration = 20

        db.beginTransaction()
        try {
            val setsToCreate = if (existingMaxSet == 0) setsCount else 1
            for (i in 1..setsToCreate) {
                val setNum = existingMaxSet + i
                val lastNumbers = getLastCompletedNumbersForSet(
                    exerciseId = exercise.id,
                    setNumber = setNum,
                    excludeSessionId = sessionId
                )
                val defaultWeight = lastNumbers?.first ?: fallbackWeight
                val defaultReps = lastNumbers?.second?.takeIf { it > 0 } ?: fallbackReps
                val defaultDuration = lastNumbers?.third?.takeIf { it > 0 } ?: fallbackDuration

                val values = ContentValues().apply {
                    put("id", "set_${UUID.randomUUID().toString().take(8)}")
                    put("session_id", sessionId)
                    put("exercise_id", exercise.id)
                    put("set_number", setNum)
                    put("weight_kg", if (exercise.measurementType == MeasurementType.WEIGHT_REPS) defaultWeight else 0.0)
                    put("reps", if (exercise.measurementType == MeasurementType.TIME_SECONDS) 0 else defaultReps)
                    put("duration_seconds", if (exercise.measurementType == MeasurementType.TIME_SECONDS) defaultDuration else 0)
                    put("is_warmup", 0)
                    put("is_completed", 0)
                }
                db.insert("workout_sets", null, values)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun addSetToExerciseInSession(sessionId: String, exerciseId: String) {
        val db = writableDatabase
        val lastSet = db.rawQuery(
            "SELECT * FROM workout_sets WHERE session_id = ? AND exercise_id = ? ORDER BY set_number DESC LIMIT 1",
            arrayOf(sessionId, exerciseId)
        ).use {
            if (it.moveToFirst()) it.toWorkoutSet() else null
        }

        val nextSetNumber = (lastSet?.setNumber ?: 0) + 1
        val prevExactSet = getPreviousNumbersForExactSet(
            exerciseId = exerciseId,
            setNumber = nextSetNumber,
            excludeSessionId = sessionId
        )
        val values = ContentValues().apply {
            put("id", "set_${UUID.randomUUID().toString().take(8)}")
            put("session_id", sessionId)
            put("exercise_id", exerciseId)
            put("set_number", nextSetNumber)
            put("weight_kg", prevExactSet?.first ?: lastSet?.weightKg ?: 0.0)
            put("reps", prevExactSet?.second?.takeIf { it > 0 } ?: lastSet?.reps ?: 12)
            put("duration_seconds", prevExactSet?.third?.takeIf { it > 0 } ?: lastSet?.durationSeconds ?: 20)
            put("rpe", lastSet?.rpe)
            put("is_warmup", 0)
            put("is_completed", 0)
        }
        db.insert("workout_sets", null, values)
    }

    fun removeSetFromSession(setId: String, sessionId: String, exerciseId: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("workout_sets", "id = ?", arrayOf(setId))
            // Renumber remaining sets for this exercise in this session
            val remainingIds = mutableListOf<String>()
            db.rawQuery(
                "SELECT id FROM workout_sets WHERE session_id = ? AND exercise_id = ? ORDER BY set_number ASC, rowid ASC",
                arrayOf(sessionId, exerciseId)
            ).use { c ->
                while (c.moveToNext()) {
                    remainingIds.add(c.getString(0))
                }
            }
            remainingIds.forEachIndexed { index, id ->
                val cv = ContentValues().apply {
                    put("set_number", index + 1)
                }
                db.update("workout_sets", cv, "id = ?", arrayOf(id))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun updateWorkoutSet(
        setId: String,
        weightKg: Double,
        reps: Int,
        durationSeconds: Int,
        rpe: Double?,
        isWarmup: Boolean,
        isCompleted: Boolean
    ) {
        val values = ContentValues().apply {
            put("weight_kg", weightKg.coerceAtLeast(0.0))
            put("reps", reps.coerceAtLeast(0))
            put("duration_seconds", durationSeconds.coerceAtLeast(0))
            if (rpe != null) put("rpe", rpe) else putNull("rpe")
            put("is_warmup", if (isWarmup) 1 else 0)
            put("is_completed", if (isCompleted) 1 else 0)
            if (isCompleted) {
                put("completed_at", nowIsoString())
            } else {
                putNull("completed_at")
            }
        }
        writableDatabase.update("workout_sets", values, "id = ?", arrayOf(setId))
    }

    fun finishWorkoutSession(sessionId: String, durationSeconds: Int, notes: String?) {
        val values = ContentValues().apply {
            put("finished_at", nowIsoString())
            put("duration_seconds", durationSeconds.coerceAtLeast(1))
            put("notes", notes?.trim()?.takeIf { it.isNotEmpty() })
        }
        writableDatabase.update("workout_sessions", values, "id = ?", arrayOf(sessionId))
    }

    fun updateSessionNotesAndName(sessionId: String, name: String, notes: String?) {
        val values = ContentValues().apply {
            put("name", name.trim().ifBlank { "Entrenamiento" })
            put("notes", notes?.trim()?.takeIf { it.isNotEmpty() })
        }
        writableDatabase.update("workout_sessions", values, "id = ?", arrayOf(sessionId))
    }

    fun deleteWorkoutSession(sessionId: String) {
        writableDatabase.delete("workout_sessions", "id = ?", arrayOf(sessionId))
    }

    // =========================================================================
    // STATS & BACKUP
    // =========================================================================

    fun getExerciseStats(exerciseId: String): ExerciseStats {
        val db = readableDatabase
        var maxWeight = 0.0
        var maxReps = 0
        var maxDuration = 0
        var totalCompleted = 0

        db.rawQuery(
            """
            SELECT MAX(weight_kg), MAX(reps), MAX(duration_seconds), COUNT(*)
            FROM workout_sets
            WHERE exercise_id = ? AND is_completed = 1
            """.trimIndent(),
            arrayOf(exerciseId)
        ).use { c ->
            if (c.moveToFirst()) {
                maxWeight = c.getDouble(0)
                maxReps = c.getInt(1)
                maxDuration = c.getInt(2)
                totalCompleted = c.getInt(3)
            }
        }

        val recent = mutableListOf<Pair<String, WorkoutSet>>()
        db.rawQuery(
            """
            SELECT s.*, ws.started_at
            FROM workout_sets s
            JOIN workout_sessions ws ON ws.id = s.session_id
            WHERE s.exercise_id = ? AND s.is_completed = 1
            ORDER BY COALESCE(s.completed_at, ws.started_at) DESC
            LIMIT 10
            """.trimIndent(),
            arrayOf(exerciseId)
        ).use { c ->
            while (c.moveToNext()) {
                val dateStr = c.getString(c.getColumnIndexOrThrow("started_at"))
                recent.add(dateStr to c.toWorkoutSet())
            }
        }

        return ExerciseStats(
            exerciseId = exerciseId,
            maxWeightKg = maxWeight,
            maxReps = maxReps,
            maxDurationSeconds = maxDuration,
            totalCompletedSets = totalCompleted,
            lastPerformanceText = getPreviousPerformanceSummary(exerciseId),
            recentSets = recent
        )
    }

    fun getGlobalStats(): GlobalStats {
        val db = readableDatabase
        var totalSessions = 0
        var totalDuration = 0L
        db.rawQuery(
            "SELECT COUNT(*), COALESCE(SUM(duration_seconds), 0) FROM workout_sessions WHERE finished_at IS NOT NULL",
            null
        ).use { c ->
            if (c.moveToFirst()) {
                totalSessions = c.getInt(0)
                totalDuration = c.getLong(1)
            }
        }

        var totalSets = 0
        var totalVol = 0.0
        db.rawQuery(
            """
            SELECT COUNT(*), COALESCE(SUM(weight_kg * CASE WHEN reps > 0 THEN reps ELSE 1 END), 0)
            FROM workout_sets ws
            JOIN workout_sessions s ON s.id = ws.session_id
            WHERE ws.is_completed = 1 AND s.finished_at IS NOT NULL
            """.trimIndent(),
            null
        ).use { c ->
            if (c.moveToFirst()) {
                totalSets = c.getInt(0)
                totalVol = c.getDouble(1)
            }
        }

        var totalEx = 0
        var customEx = 0
        db.rawQuery(
            "SELECT COUNT(*), COALESCE(SUM(is_custom), 0) FROM exercises",
            null
        ).use { c ->
            if (c.moveToFirst()) {
                totalEx = c.getInt(0)
                customEx = c.getInt(1)
            }
        }

        val totalRoutines = db.rawQuery("SELECT COUNT(*) FROM routines", null).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }

        val fkEnabled = db.rawQuery("PRAGMA foreign_keys;", null).use { c ->
            if (c.moveToFirst()) c.getInt(0) == 1 else false
        }

        return GlobalStats(
            totalSessions = totalSessions,
            totalCompletedSets = totalSets,
            totalDurationSeconds = totalDuration,
            totalVolumeKg = totalVol,
            totalExercises = totalEx,
            customExercises = customEx,
            totalRoutines = totalRoutines,
            foreignKeysEnabled = fkEnabled
        )
    }

    fun clearAllHistory() {
        val db = writableDatabase
        db.delete("workout_sessions", null, null)
    }

    data class BackupImportSummary(
        val exercisesCount: Int,
        val routinesCount: Int,
        val sessionsCount: Int,
        val setsCount: Int
    )

    fun exportBackupJson(): String {
        val root = JSONObject()
        root.put("exportedAt", nowIsoString())
        root.put("app", "YoliGym")

        val exercisesArr = JSONArray()
        getAllExercises().forEach { ex ->
            exercisesArr.put(
                JSONObject().apply {
                    put("id", ex.id)
                    put("name", ex.name)
                    put("category", ex.category.dbValue)
                    put("muscle_group", ex.muscleGroup.dbValue)
                    put("measurement_type", ex.measurementType.dbValue)
                    put("notes", ex.notes)
                    put("video_url", ex.videoUrl)
                    put("is_custom", if (ex.isCustom) 1 else 0)
                }
            )
        }
        root.put("exercises", exercisesArr)

        val routinesArr = JSONArray()
        getAllRoutinesWithExercises().forEach { rwe ->
            val rObj = JSONObject().apply {
                put("id", rwe.routine.id)
                put("name", rwe.routine.name)
                put("description", rwe.routine.description)
                put("created_at", rwe.routine.createdAt)
            }
            val itemsArr = JSONArray()
            rwe.items.forEach { item ->
                itemsArr.put(
                    JSONObject().apply {
                        put("id", item.id)
                        put("exercise_id", item.exerciseId)
                        put("order_index", item.orderIndex)
                        put("target_sets", item.targetSets)
                        put("target_reps", item.targetReps)
                        put("target_duration_seconds", item.targetDurationSeconds)
                        put("target_rest_seconds", item.targetRestSeconds)
                    }
                )
            }
            rObj.put("items", itemsArr)
            routinesArr.put(rObj)
        }
        root.put("routines", routinesArr)

        val sessionsArr = JSONArray()
        getCompletedSessions().forEach { detail ->
            val sObj = JSONObject().apply {
                put("id", detail.session.id)
                put("routine_id", detail.session.routineId)
                put("name", detail.session.name)
                put("started_at", detail.session.startedAt)
                put("finished_at", detail.session.finishedAt)
                put("duration_seconds", detail.session.durationSeconds)
                put("notes", detail.session.notes)
            }
            val setsArr = JSONArray()
            detail.exercises.flatMap { it.sets }.forEach { set ->
                setsArr.put(
                    JSONObject().apply {
                        put("id", set.id)
                        put("exercise_id", set.exerciseId)
                        put("set_number", set.setNumber)
                        put("weight_kg", set.weightKg)
                        put("reps", set.reps)
                        put("duration_seconds", set.durationSeconds)
                        put("rpe", set.rpe)
                        put("is_warmup", if (set.isWarmup) 1 else 0)
                        put("is_completed", if (set.isCompleted) 1 else 0)
                        put("completed_at", set.completedAt)
                    }
                )
            }
            sObj.put("sets", setsArr)
            sessionsArr.put(sObj)
        }
        root.put("sessions", sessionsArr)
        return root.toString(2)
    }

    fun importBackupJson(jsonText: String): Result<BackupImportSummary> {
        return try {
            val trimmed = jsonText.trim()
            if (trimmed.isEmpty()) {
                return Result.failure(IllegalArgumentException("El contenido del backup está vacío."))
            }
            val root = JSONObject(trimmed)
            val exercisesArr = root.optJSONArray("exercises")
            val routinesArr = root.optJSONArray("routines")
            val sessionsArr = root.optJSONArray("sessions")

            if (exercisesArr == null && routinesArr == null && sessionsArr == null) {
                return Result.failure(
                    IllegalArgumentException("El archivo JSON no tiene el formato de Backup de YoliGym.")
                )
            }

            val db = writableDatabase
            var exCount = 0
            var routCount = 0
            var sessCount = 0
            var setsCount = 0

            db.beginTransaction()
            try {
                // 1. Importar ejercicios
                if (exercisesArr != null) {
                    for (i in 0 until exercisesArr.length()) {
                        val obj = exercisesArr.optJSONObject(i) ?: continue
                        val id = obj.optString("id").trim()
                        val name = obj.optString("name").trim()
                        if (id.isEmpty() || name.isEmpty()) continue

                        val category = ExerciseCategory.fromDb(obj.optString("category")).dbValue
                        val muscleGroup = MuscleGroup.fromDb(obj.optString("muscle_group")).dbValue
                        val measurementType = MeasurementType.fromDb(obj.optString("measurement_type")).dbValue
                        val notes = if (obj.isNull("notes")) null else obj.optString("notes").takeIf { it.isNotBlank() && it != "null" }
                        val rawVideo = if (obj.isNull("video_url")) null else obj.optString("video_url")
                        val cleanVideo = sanitizeVideoUrl(rawVideo)
                        val isCustom = obj.optInt("is_custom", 0)

                        val values = ContentValues().apply {
                            put("id", id)
                            put("name", name)
                            put("category", category)
                            put("muscle_group", muscleGroup)
                            put("measurement_type", measurementType)
                            put("notes", notes)
                            put("video_url", cleanVideo)
                            put("is_custom", isCustom)
                        }
                        if (upsertExerciseRow(db, values, id)) {
                            exCount++
                        }
                    }
                }

                // Conjunto de IDs de ejercicios existentes para asegurar integridad referencial
                val validExerciseIds = mutableSetOf<String>()
                db.rawQuery("SELECT id FROM exercises", null).use { c ->
                    while (c.moveToNext()) validExerciseIds.add(c.getString(0))
                }

                // 2. Importar rutinas (si están incluidas en el backup)
                if (routinesArr != null) {
                    for (i in 0 until routinesArr.length()) {
                        val rObj = routinesArr.optJSONObject(i) ?: continue
                        val rId = rObj.optString("id").trim()
                        val rName = rObj.optString("name").trim()
                        if (rId.isEmpty() || rName.isEmpty()) continue

                        val rDesc = if (rObj.isNull("description")) null else rObj.optString("description").takeIf { it.isNotBlank() && it != "null" }
                        val rCreated = rObj.optString("created_at").ifBlank { nowIsoString() }

                        val rValues = ContentValues().apply {
                            put("id", rId)
                            put("name", rName)
                            put("description", rDesc)
                            put("created_at", rCreated)
                        }
                        upsertRoutineRow(db, rValues, rId)
                        routCount++

                        val itemsArr = rObj.optJSONArray("items")
                        if (itemsArr != null) {
                            db.delete("routine_exercises", "routine_id = ?", arrayOf(rId))
                            for (j in 0 until itemsArr.length()) {
                                val itemObj = itemsArr.optJSONObject(j) ?: continue
                                val exId = itemObj.optString("exercise_id").trim()
                                if (exId !in validExerciseIds) continue

                                val itemId = itemObj.optString("id").ifBlank { "${rId}_${j + 1}" }
                                val reValues = ContentValues().apply {
                                    put("id", itemId)
                                    put("routine_id", rId)
                                    put("exercise_id", exId)
                                    put("order_index", itemObj.optInt("order_index", j + 1))
                                    put("target_sets", itemObj.optInt("target_sets", 4))
                                    put("target_reps", itemObj.optString("target_reps", "12-15"))
                                    put("target_duration_seconds", itemObj.optInt("target_duration_seconds", 0))
                                    put("target_rest_seconds", itemObj.optInt("target_rest_seconds", 90))
                                }
                                db.insertWithOnConflict("routine_exercises", null, reValues, SQLiteDatabase.CONFLICT_REPLACE)
                            }
                        }
                    }
                }

                // Conjunto de IDs de rutinas existentes
                val validRoutineIds = mutableSetOf<String>()
                db.rawQuery("SELECT id FROM routines", null).use { c ->
                    while (c.moveToNext()) validRoutineIds.add(c.getString(0))
                }

                // 3. Importar sesiones e historial de series
                if (sessionsArr != null) {
                    for (i in 0 until sessionsArr.length()) {
                        val sObj = sessionsArr.optJSONObject(i) ?: continue
                        val sId = sObj.optString("id").trim()
                        val sName = sObj.optString("name").trim().ifBlank { "Entrenamiento" }
                        val startedAt = sObj.optString("started_at").trim().ifBlank { nowIsoString() }
                        if (sId.isEmpty()) continue

                        val rawRoutineId = if (sObj.isNull("routine_id")) null else sObj.optString("routine_id").takeIf { it.isNotBlank() && it != "null" }
                        val safeRoutineId = if (rawRoutineId != null && rawRoutineId in validRoutineIds) rawRoutineId else null
                        val finishedAt = if (sObj.isNull("finished_at")) nowIsoString() else sObj.optString("finished_at").takeIf { it.isNotBlank() && it != "null" } ?: nowIsoString()
                        val durationSec = if (sObj.isNull("duration_seconds")) 0 else sObj.optInt("duration_seconds", 0)
                        val sNotes = if (sObj.isNull("notes")) null else sObj.optString("notes").takeIf { it.isNotBlank() && it != "null" }

                        val sValues = ContentValues().apply {
                            put("id", sId)
                            if (safeRoutineId != null) put("routine_id", safeRoutineId) else putNull("routine_id")
                            put("name", sName)
                            put("started_at", startedAt)
                            put("finished_at", finishedAt)
                            put("duration_seconds", durationSec)
                            if (sNotes != null) put("notes", sNotes) else putNull("notes")
                        }
                        val updatedSess = db.update("workout_sessions", sValues, "id = ?", arrayOf(sId))
                        if (updatedSess == 0) {
                            db.insertWithOnConflict("workout_sessions", null, sValues, SQLiteDatabase.CONFLICT_IGNORE)
                        }
                        sessCount++

                        val setsArr = sObj.optJSONArray("sets")
                        if (setsArr != null) {
                            db.delete("workout_sets", "session_id = ?", arrayOf(sId))
                            for (j in 0 until setsArr.length()) {
                                val setObj = setsArr.optJSONObject(j) ?: continue
                                val exId = setObj.optString("exercise_id").trim()
                                if (exId !in validExerciseIds) continue

                                val setId = setObj.optString("id").ifBlank { "set_${UUID.randomUUID().toString().take(8)}" }
                                val setValues = ContentValues().apply {
                                    put("id", setId)
                                    put("session_id", sId)
                                    put("exercise_id", exId)
                                    put("set_number", setObj.optInt("set_number", j + 1))
                                    put("weight_kg", setObj.optDouble("weight_kg", 0.0))
                                    put("reps", setObj.optInt("reps", 0))
                                    put("duration_seconds", setObj.optInt("duration_seconds", 0))
                                    if (!setObj.isNull("rpe")) put("rpe", setObj.optDouble("rpe")) else putNull("rpe")
                                    put("is_warmup", setObj.optInt("is_warmup", 0))
                                    put("is_completed", setObj.optInt("is_completed", 1))
                                    val compAt = if (setObj.isNull("completed_at")) null else setObj.optString("completed_at").takeIf { it.isNotBlank() && it != "null" }
                                    if (compAt != null) put("completed_at", compAt) else putNull("completed_at")
                                }
                                db.insertWithOnConflict("workout_sets", null, setValues, SQLiteDatabase.CONFLICT_REPLACE)
                                setsCount++
                            }
                        }
                    }
                }

                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }

            Result.success(
                BackupImportSummary(
                    exercisesCount = exCount,
                    routinesCount = routCount,
                    sessionsCount = sessCount,
                    setsCount = setsCount
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

fun formatWeight(weight: Double): String {
    return if (weight % 1.0 == 0.0) {
        weight.toInt().toString()
    } else {
        String.format(Locale.US, "%.1f", weight)
    }
}

fun formatDurationSeconds(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val hrs = safe / 3600
    val mins = (safe % 3600) / 60
    val secs = safe % 60
    return if (hrs > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }
}
