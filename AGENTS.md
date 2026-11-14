# AGENTS.md — Guía de Contexto y Continuidad para Agentes IA

Este documento proporciona todo el contexto técnico, arquitectónico y operativo necesario para continuar con el desarrollo, mantenimiento o ampliación de **YoliGym** en futuras sesiones sin perder contexto.

---

## 1. Visión General del Proyecto

- **Nombre:** YoliGym (`com.yoligym.app`)
- **Propósito:** Aplicación nativa Android (100% *offline-first*) para el registro de entrenamientos de gimnasio: fuerza (peso libre y máquinas), plantillas de rutinas y bloques de movilidad/equilibrio (ej. ejercicios de Romberg y propiocepción).
- **Especificación Original:** Consulta [`SPEC.md`](./SPEC.md) para ver los requisitos iniciales y el esquema relacional SQL obligatorio.
- **Idioma de la Interfaz y Código de Dominio:** Español en toda la interfaz de usuario (`Entrenar`, `Ejercicios`, `Historial`, `Ajustes`); identificadores técnicos en inglés/español según la estructura actual.

---

## 2. Stack Tecnológico y Entorno de Compilación

- **Lenguaje:** Kotlin `2.4.21` (`JavaVersion.VERSION_11`)
- **UI:** Jetpack Compose (`androidx.compose:compose-bom:2026.09.00`) + Material Design 3 + `material-icons-extended`
- **Gradle & AGP:** Gradle `9.8.1` (`gradle/wrapper/gradle-wrapper.properties`), Android Gradle Plugin `9.4.1`
- **SDK Levels:** `minSdk = 26`, `compileSdk = 37`, `targetSdk = 37`
- **Persistencia:** SQLite nativo de Android (`android.database.sqlite.SQLiteOpenHelper`) sin ORM intermedio para garantizar control total sobre el esquema SQL y `PRAGMA foreign_keys = ON`. Preferencias ligeras mediante `SharedPreferences` (`yoligym_prefs`).

---

## 3. Arquitectura y Mapa de Archivos

La aplicación sigue una arquitectura **MVVM reactiva y desacoplada** dentro del paquete `com.yoligym.app`:

### Capa de Datos (`app/src/main/java/com/yoligym/app/data/`)
- **`Models.kt`**:
  - Enums con mapeo bidireccional a los valores `CHECK` de SQLite:
    - `ExerciseCategory`: `FREE_WEIGHT`, `MACHINE`, `MOBILITY`
    - `MuscleGroup`: `CHEST`, `BACK`, `LEGS`, `SHOULDERS`, `ARMS`, `CORE`, `FULL_BODY`
    - `MeasurementType`: `WEIGHT_REPS`, `REPS_ONLY`, `TIME_SECONDS`
  - Modelos inmutables: `Exercise`, `Routine`, `RoutineExercise`, `RoutineWithExercises`, `WorkoutSession`, `WorkoutSet`, `ExerciseSessionSets`, `SessionDetail`, `ExerciseStats`, `GlobalStats`.
- **`YoliGymDatabase.kt`**:
  - Gestiona `yoligym.db` (versión actual: `DATABASE_VERSION = 1`).
  - Activa obligatoriamente `PRAGMA foreign_keys = ON;` en `onConfigure`, `onOpen` y `onCreate`.
  - **Seeding inicial:**
    - `seedExercisesFromAssets()`: Lee `app/src/main/assets/seedExercises.json` (sincronizado con `./seedExercises.json` en la raíz) e inserta el catálogo inicial. Incluye `sanitizeVideoUrl()` para limpiar enlaces en formato Markdown (`[url](url)`) si provienen del JSON semilla.
    - `seedDefaultRoutines()`: Crea 4 rutinas base (`Día 1 · Fuerza y Equilibrio`, `Día 2 · Máquinas y Control Motor`, `Movilidad y Equilibrio (Romberg)`, `Fuerza Full Body · Peso Libre`).
  - **Lógica de sesiones:**
    - Una sesión activa es aquella fila en `workout_sessions` donde `finished_at IS NULL`.
    - Al iniciar una sesión desde una rutina (`startWorkoutFromRoutine`) o añadir un ejercicio (`addExerciseToSession`), las series se pre-rellenan automáticamente con el último peso/repeticiones/duración completados históricamente por el usuario para ese ejercicio (`getLastCompletedNumbers`).

### Capa de Estado y Lógica de Presentación (`app/src/main/java/com/yoligym/app/ui/`)
- **`YoliGymViewModel.kt`**:
  - Expone un único `StateFlow<YoliGymUiState>` observado por `MainActivity`.
  - Ejecuta todas las operaciones de base de datos en ` viewModelScope.launch(Dispatchers.IO)`.
  - Gestiona un `tickerJob` (corutina cada 1 segundo) que actualiza:
    1. `workoutElapsedSeconds`: Tiempo transcurrido de la sesión activa (calculado desde `started_at` para resistir recreaciones de Activity).
    2. `restRemainingSeconds`: Cuenta atrás del temporizador de descanso entre series, disparando vibración (`triggerVibration`) al llegar a `0`.
    3. `activeTimedSetId` / `activeTimedSetRemaining`: Temporizador *inline* para series medidas en segundos (`TIME_SECONDS`), que marca automáticamente la serie como completada al finalizar.
- **Pantallas y Componentes Compose**:
  - **`MainActivity.kt`**: `Scaffold` principal con `SnackbarHost` y `NavigationBar` inferior de 4 pestañas (`ENTRENAR`, `EJERCICIOS`, `HISTORIAL`, `AJUSTES`). Muestra un badge `"ACT"` en la pestaña `Entrenar` cuando hay una sesión en curso.
  - **`EntrenarScreen.kt`**:
    - Si `uiState.activeSession == null`: Muestra el listado de plantillas de rutinas, botón de entrenamiento libre y diálogo de creación/edición de rutinas (`RoutineEditorDialog`).
    - Si `uiState.activeSession != null`: Muestra la vista de entrenamiento activo con cabecera de progreso/cronómetro, barra flotante del temporizador de descanso, tarjetas por ejercicio con su mejor marca previa y controles táctiles grandes (`+/-`) para peso, repeticiones o tiempo.
  - **`EjerciciosScreen.kt`**: Buscador, filtros por categoría y grupo muscular, diálogo de creación/edición (`ExerciseEditorDialog`) y hoja de estadísticas/récords por ejercicio (`ExerciseStatsDialog`).
  - **`HistorialScreen.kt`**: Tarjetas de resumen global y listado de sesiones finalizadas con desglose de series, volumen total, edición de notas y borrado.
  - **`AjustesScreen.kt`**: Configuración de tema oscuro/claro, paso de peso (`weightStepKg`), tiempo de descanso, exportación de copia de seguridad JSON al portapapeles, restauración de datos semilla y limpieza de historial.
  - **`CommonComponents.kt`** y **`Theme.kt`**: Paleta Material 3 (Modo Oscuro por defecto con acentos verde lima/esmeralda de alta visibilidad en gimnasio) y chips/badges reutilizables.

---

## 4. Reglas e Invariantes Críticas (¡No Romper!)

1. **Integridad Referencial SQLite (`PRAGMA foreign_keys = ON`):**
   - `routine_exercises.exercise_id` y `workout_sets.exercise_id` tienen `ON DELETE RESTRICT`. No se puede borrar un ejercicio si está referenciado en una rutina o en series registradas; `YoliGymDatabase.deleteExercise()` captura esta restricción y devuelve un `Result.failure` amigable para mostrar en un `Snackbar`.
   - `routine_exercises.routine_id` y `workout_sets.session_id` tienen `ON DELETE CASCADE`.
   - `workout_sessions.routine_id` tiene `ON DELETE SET NULL`.
2. **Sincronización de `seedExercises.json`:**
   - Existen dos archivos JSON semilla: `./seedExercises.json` (raíz del repositorio, referencia de especificación) y `app/src/main/assets/seedExercises.json` (el que empaqueta el APK y lee `YoliGymDatabase`). **Si modificas o añades ejercicios semilla, actualiza ambos archivos.**
3. **Migraciones de Base de Datos:**
   - Si en el futuro se añaden tablas o columnas a `yoligym.db`, incrementa `DATABASE_VERSION` en `YoliGymDatabase.kt` e implementa una migración no destructiva en `onUpgrade` (`ALTER TABLE ...`) para no perder el historial del usuario.
4. **Ergonomía de Gimnasio (UI/UX):**
   - Mantén botones táctiles amplios y controles de incremento directo (`-` / `+`) para evitar que el usuario dependa exclusivamente del teclado numérico mientras entrena.

---

## 5. Comandos de Compilación, Despliegue y Verificación

En Windows PowerShell (desde la raíz del repositorio):

```powershell
# 1. Compilar APK de Debug y verificar que no hay errores de Kotlin/Compose
.\gradlew.bat assembleDebug

# 2. Verificar dispositivos o emuladores conectados
adb devices
emulator -list-avds

# 3. Instalar el APK en el dispositivo/emulador activo
.\gradlew.bat installDebug

# 4. Lanzar la aplicación y comprobar logs de errores
adb logcat -c
adb shell am start -n com.yoligym.app/.MainActivity
adb logcat -d *:E
```

> **Nota sobre `local.properties`:** El archivo `local.properties` contiene la ruta local de `sdk.dir` de la máquina de desarrollo y está excluido en `.gitignore`. Si clonas el repositorio en un entorno nuevo, asegúrate de que `local.properties` o la variable de entorno `ANDROID_HOME` apunten al Android SDK local.

---

## 6. Posibles Próximos Pasos / Mejoras Futuras

Si el usuario solicita continuar ampliando la aplicación en una nueva sesión, estas son las evoluciones naturales del proyecto:

1. **Importación de Backup JSON:** Actualmente `AjustesScreen` permite exportar la copia de seguridad JSON (`exportBackupJson()`). Añadir la función inversa (`importBackupJson()`) desde el portapapeles o selector de archivos (`ActivityResultContracts.OpenDocument`).
2. **Gráficas de Progresión:** En el diálogo de estadísticas de un ejercicio (`ExerciseStatsDialog`), añadir una gráfica en Canvas de Jetpack Compose con la evolución del peso máximo, 1RM estimado (fórmula de Epley) o volumen por sesión.
3. **Reordenación Drag & Drop en Rutinas y Sesiones:** Permitir cambiar el orden de los ejercicios dentro de una sesión activa o al editar una rutina.
4. **Notificaciones en Segundo Plano para Descanso:** Mostrar una notificación local o servicio ligero si el usuario bloquea la pantalla o sale de la app mientras corre el temporizador de descanso.
5. **Soporte de Superseries / Biseries:** Agrupar dos o más ejercicios en una misma ronda dentro de `routine_exercises` y `EntrenarScreen`.
