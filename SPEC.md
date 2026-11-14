# ROL, PERMISOS Y ENTORNO DE EJECUCIÓN
Actúa como un Ingeniero Senior de Android / Mobile autónomo. Tu objetivo es desarrollar, compilar y probar desde cero una aplicación para Android de registro de entrenamientos de gimnasio (pesos, rutinas, series y movilidad/equilibrio).

## Directivas de Autonomía y Permisos (CRÍTICO)
1. **Autonomía Total en el Directorio:** Tienes permiso explícito y total para crear, modificar, mover o eliminar cualquier archivo o subdirectorio dentro de la carpeta actual del proyecto, así como instalar paquetes (`npm`, `npx`, `gradle`, etc.) y ejecutar scripts sin detenerte a pedir confirmación.
2. **Entorno Android SDK Local Disponible:** El sistema cuenta con el Android SDK configurado. Utiliza libremente las herramientas de línea de comandos (`adb`, `emulator`, `avdmanager`, `gradlew`) para:
   - Listar los AVDs disponibles (`emulator -list-avds`).
   - Levantar un emulador en segundo plano si no hay ningún dispositivo conectado (`adb devices`).
   - Compilar la aplicación, instalar el APK de debug y verificar por `adb logcat` que la app inicia sin excepciones ni *crashes*.

---

# STACK TECNOLÓGICO
- **Framework:** React Native con Expo (Prebuild / Bare workflow compatible con compilación local Android) + TypeScript, o Kotlin + Jetpack Compose nativo (elige la opción que te permita compilar y testear de forma más directa con el Android SDK local; si usas Expo/React Native, usa `expo-sqlite` y ejecuta `npx expo run:android`).
- **Base de Datos Local:** SQLite relacional, 100% *offline-first* con claves foráneas (`PRAGMA foreign_keys = ON;`).
- **Interfaz (UI/UX):** Material Design 3, **Modo Oscuro por defecto**, controles táctiles grandes pensados para usarse con una mano en el gimnasio y navegación inferior de 4 pestañas (`Entrenar`, `Ejercicios`, `Historial`, `Ajustes`).

---

# 1. ESQUEMA SQL COMPLETO (SQLite)
Implementa exactamente este modelo relacional en la capa de inicialización de la base de datos:

```sql
PRAGMA foreign_keys = ON;

-- 1. Catálogo de Ejercicios
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

-- 2. Plantillas de Rutinas
CREATE TABLE IF NOT EXISTS routines (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT,
    created_at TEXT DEFAULT CURRENT_TIMESTAMP
);

-- 3. Ejercicios asociados a cada Rutina
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

-- 4. Sesiones de Entrenamiento Reales (Historial)
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

-- 5. Series registradas dentro de una Sesión
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

CREATE INDEX IF NOT EXISTS idx_sets_exercise ON workout_sets(exercise_id, is_completed);
CREATE INDEX IF NOT EXISTS idx_sets_session ON workout_sets(session_id);
CREATE INDEX IF NOT EXISTS idx_exercises_filter ON exercises(category, muscle_group);