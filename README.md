# YoliGym 🏋️‍♀️

**YoliGym** es una aplicación nativa para Android desarrollada en **Kotlin** y **Jetpack Compose** (Material Design 3) diseñada para el registro de entrenamientos de gimnasio: fuerza (peso libre y máquinas), rutinas personalizadas, series y bloques específicos de movilidad, equilibrio y propiocepción (Romberg).

Funciona **100% offline-first** sobre una base de datos relacional **SQLite** local con integridad referencial activa (`PRAGMA foreign_keys = ON`).

---

## ✨ Características Principales

- **Entrenamiento en Vivo (`Entrenar`):**
  - Inicio rápido desde plantillas de rutinas predefinidas o sesiones libres.
  - Creación, edición y eliminación de plantillas de rutinas con objetivos de series, repeticiones, duración y descanso.
  - Registro ágil pensado para usarse con una sola mano en el gimnasio (botones táctiles `+/-` configurables, marcado de series de calentamiento `W`, RPE y autocompletado con el mejor/último registro previo).
  - **Cronómetros integrados:**
    - Cronómetro global de duración de la sesión activa.
    - Temporizador automático de descanso entre series con ajuste rápido (`-15s`, `+15s`, `+30s`), aviso por vibración y **notificación emergente con sonido/vibración en segundo plano** (además de cuenta atrás en vivo con `+15s` y `Saltar`) si cambias a otra app o apagas la pantalla.
    - Temporizador *inline* por serie para ejercicios isométricos o de equilibrio medidos por tiempo (`TIME_SECONDS`), que marca la serie como completada automáticamente al terminar.
- **Catálogo de Ejercicios (`Ejercicios`):**
  - Precargado automáticamente desde `seedExercises.json` con ejercicios clasificados por categoría (`Peso Libre`, `Máquina / Polea`, `Movilidad y Equilibrio`), grupo muscular y tipo de medición (`Peso y Repeticiones`, `Solo Repeticiones / Pasos`, `Tiempo (Segundos)`).
  - Búsqueda en tiempo real y filtros combinables por categoría y grupo muscular.
  - Creación y edición de ejercicios personalizados (`is_custom = 1`), notas técnicas, enlaces directos a vídeo demostrativo (YouTube) y vista de récords/estadísticas por ejercicio.
- **Historial y Estadísticas (`Historial`):**
  - Resumen global de sesiones completadas, series totales, volumen acumulado (kg) y tiempo entrenado.
  - Detalle completo de cada sesión pasada, edición de título/notas y eliminación de sesiones.
- **Ajustes y Copias de Seguridad (`Ajustes`):**
  - Modo Oscuro por defecto (Material 3) con opción de cambio a Modo Claro.
  - Configuración de incremento de peso (`1.0 kg`, `1.25 kg`, `2.5 kg`, `5.0 kg`), tiempo de descanso por defecto, auto-inicio de descanso y vibración.
  - **Backup automático diario en GitHub (multiusuario)** mediante WorkManager y GitHub REST API v3 (`backups/<usuario>/yoligym_backup_<usuario>_YYYY-MM-DD.json`, conservando los últimos 30 días por usuario, con opción de solo Wi-Fi, hora configurable, backup inmediato y restauración desde GitHub). Consulta [`GITHUB_BACKUP_SETUP.md`](./GITHUB_BACKUP_SETUP.md).
  - Exportación/importación manual de copias de seguridad JSON (portapapeles o archivo), restauración de datos semilla y limpieza de historial.

---

## 🛠️ Stack Tecnológico

| Componente | Tecnología |
| :--- | :--- |
| **Lenguaje** | Kotlin (`2.4.21`) |
| **UI Framework** | Jetpack Compose (Compose BOM `2026.09.00`) + Material Design 3 |
| **Arquitectura** | MVVM (`AndroidViewModel` + `StateFlow` + `SQLiteOpenHelper`) |
| **Base de Datos** | Android SQLite nativo (`yoligym.db`, `PRAGMA foreign_keys = ON`) |
| **Backup en la Nube** | WorkManager (`2.10.2`) + OkHttp (`4.12.0`) + GitHub REST API v3 |
| **Android SDK** | `minSdk = 26` (Android 8.0), `compileSdk = 37`, `targetSdk = 37` |
| **Build System** | Gradle `9.8.1` + Android Gradle Plugin (AGP) `9.4.1` |

---

## 📂 Estructura del Proyecto

```text
YoliGym/
├── SPEC.md                          # Especificación funcional y esquema SQL original
├── seedExercises.json               # Copia raíz del catálogo semilla de ejercicios
├── app/
│   ├── build.gradle.kts             # Configuración del módulo Android y dependencias Compose
│   └── src/main/
│       ├── AndroidManifest.xml      # Permisos (VIBRATE, INTERNET, ACCESS_NETWORK_STATE, POST_NOTIFICATIONS, USE_EXACT_ALARM), Application y Receiver
│       ├── assets/
│       │   └── seedExercises.json   # Catálogo JSON embebido cargado en el primer arranque
│       └── java/com/yoligym/app/
│           ├── MainActivity.kt      # Punto de entrada, permiso de notificaciones y Scaffold de 4 pestañas
│           ├── YoliGymApplication.kt # Canales de notificación y tracking de primer plano
│           ├── backup/
│           │   ├── GitHubBackupModels.kt      # Nomenclatura multiusuario, retención (30 días) y estado
│           │   ├── GitHubBackupPreferences.kt # Preferencias y almacenamiento aislado del token PAT
│           │   ├── GitHubRestClient.kt        # Cliente GitHub REST API v3 (OkHttp)
│           │   ├── GitHubBackupScheduler.kt   # Programación diaria WorkManager + catch-up
│           │   ├── GitHubBackupManager.kt     # Verificación de repo, backup, poda y restauración
│           │   └── GitHubBackupWorker.kt      # Worker en segundo plano de WorkManager
│           ├── timer/
│           │   ├── RestTimerLogic.kt              # Cálculos puros de cuenta atrás y ajustes
│           │   ├── RestTimerForegroundTracker.kt  # Estado de primer plano / segundo plano
│           │   ├── RestTimerStore.kt              # Persistencia sincronizada del descanso activo
│           │   ├── RestTimerNotificationHelper.kt # Canales, banner heads-up y cuenta atrás en curso
│           │   ├── RestTimerCoordinator.kt        # AlarmManager exacto y coordinación de fin de descanso
│           │   └── RestTimerReceiver.kt           # BroadcastReceiver de alarma y acciones de notificación
│           ├── data/
│           │   ├── Models.kt        # Modelos de dominio, enums y clases de estadísticas
│           │   └── YoliGymDatabase.kt # Capa SQLite, DDL, seeding, CRUD, stats y backup JSON
│           └── ui/
│               ├── Theme.kt         # Paleta de colores Material 3 (Dark/Light)
│               ├── YoliGymViewModel.kt # Estado reactivo (YoliGymUiState), timers y acciones
│               ├── CommonComponents.kt # Componentes UI reutilizables, badges y diálogos
│               ├── EntrenarScreen.kt   # Pantalla de sesión activa y gestión de rutinas
│               ├── EjerciciosScreen.kt # Catálogo, filtros, estadísticas y CRUD de ejercicios
│               ├── HistorialScreen.kt  # Historial de entrenamientos y métricas globales
│               └── AjustesScreen.kt    # Preferencias de usuario, backup JSON y mantenimiento
```

---

## 🗄️ Modelo de Datos (SQLite)

La base de datos local `yoligym.db` implementa 5 tablas relacionales con restricciones `CHECK` y claves foráneas activas:

1. **`exercises`**: Catálogo de ejercicios (`FREE_WEIGHT`, `MACHINE`, `MOBILITY`), grupo muscular y tipo de medición (`WEIGHT_REPS`, `REPS_ONLY`, `TIME_SECONDS`).
2. **`routines`**: Plantillas de rutinas de entrenamiento.
3. **`routine_exercises`**: Ejercicios asignados a cada rutina (`ON DELETE CASCADE` respecto a `routines`, `ON DELETE RESTRICT` respecto a `exercises`).
4. **`workout_sessions`**: Sesiones de entrenamiento en curso (`finished_at IS NULL`) o finalizadas (`ON DELETE SET NULL` respecto a `routines`).
5. **`workout_sets`**: Series registradas dentro de una sesión (`ON DELETE CASCADE` respecto a `workout_sessions`, `ON DELETE RESTRICT` respecto a `exercises`).

---

## 🚀 Compilación y Ejecución

### Requisitos previos
- **JDK 11** o superior.
- **Android SDK** configurado (ruta definida en `local.properties` o variable de entorno `ANDROID_HOME`).

### Comandos principales (Windows PowerShell)

```powershell
# Compilar el APK de Debug
.\gradlew.bat assembleDebug

# Instalar y lanzar en un emulador o dispositivo conectado por ADB
.\gradlew.bat installDebug
adb shell am start -n com.yoligym.app/.MainActivity
```

El archivo APK generado se encuentra en:
`app/build/outputs/apk/debug/app-debug.apk`
