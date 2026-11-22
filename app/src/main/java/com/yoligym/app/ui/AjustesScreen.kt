package com.yoligym.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yoligym.app.backup.GitHubBackupConfigState
import com.yoligym.app.backup.GitHubBackupFile
import com.yoligym.app.backup.GitHubBackupNaming
import com.yoligym.app.backup.GitHubBackupStatus
import com.yoligym.app.timer.RestTimerNotificationHelper
import java.time.LocalDate
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AjustesScreen(
    uiState: YoliGymUiState,
    viewModel: YoliGymViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showClearHistoryConfirm by remember { mutableStateOf(false) }
    var showRestoreSeedConfirm by remember { mutableStateOf(false) }
    var showImportBackupDialog by remember { mutableStateOf(false) }
    var showImportSeedDialog by remember { mutableStateOf(false) }
    var showGitHubConfigDialog by remember { mutableStateOf(false) }
    var showDisconnectGitHubConfirm by remember { mutableStateOf(false) }
    var selectedGitHubBackupToRestore by remember { mutableStateOf<GitHubBackupFile?>(null) }
    var hasNotificationPermission by remember {
        mutableStateOf(RestTimerNotificationHelper.hasNotificationPermission(context))
    }
    var triedRuntimeNotificationPrompt by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationPermission = RestTimerNotificationHelper.hasNotificationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val saveBackupFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val json = viewModel.getBackupJson()
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                }
                viewModel.showStatus("Archivo de backup guardado correctamente")
            } catch (e: Exception) {
                viewModel.showStatus("No se pudo guardar el archivo: ${e.message}")
            }
        }
    }

    val openBackupFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                if (!text.isNullOrBlank()) {
                    viewModel.importBackupJson(text)
                    showImportBackupDialog = false
                } else {
                    viewModel.showStatus("El archivo seleccionado está vacío")
                }
            } catch (e: Exception) {
                viewModel.showStatus("Error al leer el archivo: ${e.message}")
            }
        }
    }

    val openSeedFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                if (!text.isNullOrBlank()) {
                    viewModel.importSeedExercisesJson(text)
                    showImportSeedDialog = false
                } else {
                    viewModel.showStatus("El archivo seleccionado está vacío")
                }
            } catch (e: Exception) {
                viewModel.showStatus("Error al leer el archivo: ${e.message}")
            }
        }
    }

    val prefs = uiState.preferences
    val stats = uiState.globalStats

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = "Ajustes y Datos",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Personaliza los controles de gimnasio y gestiona tu base de datos SQLite local",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Ergonomics & Workout Preferences
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Preferencias de Entrenamiento",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // Dark Mode Switch (Default ON per SPEC)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            Text("Modo Oscuro (Alto Contraste Gym)", fontWeight = FontWeight.Bold)
                            Text(
                                "Activado por defecto para ahorrar batería y mejorar legibilidad",
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = prefs.darkTheme,
                            onCheckedChange = { enabled ->
                                viewModel.updatePreferences { it.copy(darkTheme = enabled) }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Auto start rest timer
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            Text("Auto-iniciar Temporizador de Descanso", fontWeight = FontWeight.Bold)
                            Text(
                                "Inicia la cuenta atrás automáticamente al marcar una serie como completada (✓)",
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = prefs.autoStartRestTimer,
                            onCheckedChange = { enabled ->
                                viewModel.updatePreferences { it.copy(autoStartRestTimer = enabled) }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Vibration on finish
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            Text("Vibrar al finalizar descanso o crono", fontWeight = FontWeight.Bold)
                            Text(
                                "Aviso háptico al terminar el descanso o una serie por tiempo",
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = prefs.vibrateOnRestFinish,
                            onCheckedChange = { enabled ->
                                viewModel.updatePreferences { it.copy(vibrateOnRestFinish = enabled) }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Background rest finished alert
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            Text("Avisar al terminar descanso en otras apps", fontWeight = FontWeight.Bold)
                            Text(
                                "Muestra una notificación emergente con sonido y vibración si cambias de app o apagas la pantalla",
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = prefs.notifyRestInBackground,
                            onCheckedChange = { enabled ->
                                viewModel.updatePreferences { it.copy(notifyRestInBackground = enabled) }
                                if (enabled &&
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    !RestTimerNotificationHelper.hasNotificationPermission(context)
                                ) {
                                    triedRuntimeNotificationPrompt = true
                                    viewModel.requestNotificationPermissionPrompt()
                                }
                            }
                        )
                    }

                    if (prefs.notifyRestInBackground &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !hasNotificationPermission
                    ) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Text(
                                    text = "Falta el permiso de notificaciones para mostrar el aviso cuando uses otras apps.",
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Button(
                                    onClick = {
                                        if (!triedRuntimeNotificationPrompt) {
                                            triedRuntimeNotificationPrompt = true
                                            viewModel.requestNotificationPermissionPrompt()
                                        } else {
                                            try {
                                                val settingsIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                                }
                                                context.startActivity(settingsIntent)
                                            } catch (_: Exception) {
                                                val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                    data = Uri.fromParts("package", context.packageName, null)
                                                }
                                                context.startActivity(fallback)
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Permitir notificaciones", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Spacer(modifier = Modifier.height(14.dp))

                    Text("Tiempo de descanso por defecto", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(45, 60, 90, 120, 180).forEach { sec ->
                            FilterChip(
                                selected = prefs.defaultRestSeconds == sec,
                                onClick = {
                                    viewModel.updatePreferences { it.copy(defaultRestSeconds = sec) }
                                },
                                label = { Text("${sec}s") }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Salto de peso en botones (- / +)", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1.0, 1.25, 2.5, 5.0).forEach { step ->
                            FilterChip(
                                selected = prefs.weightStepKg == step,
                                onClick = {
                                    viewModel.updatePreferences { it.copy(weightStepKg = step) }
                                },
                                label = { Text("$step kg") }
                            )
                        }
                    }
                }
            }
        }

        // SQLite Database Status Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Estado de Base de Datos SQLite (Offline-First)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    DbStatRow("Motor local", "SQLite Relacional (yoligym.db)")
                    DbStatRow(
                        "PRAGMA foreign_keys",
                        if (stats.foreignKeysEnabled) "ON (Activo)" else "OFF"
                    )
                    DbStatRow(
                        "Catálogo de ejercicios",
                        "${stats.totalExercises} (${stats.customExercises} personalizados)"
                    )
                    DbStatRow("Plantillas de rutinas", "${stats.totalRoutines}")
                    DbStatRow("Sesiones completadas", "${stats.totalSessions}")
                    DbStatRow("Series registradas", "${stats.totalCompletedSets}")
                }
            }
        }

        // GitHub Automatic Daily Backup Card
        item {
            GitHubBackupCard(
                config = uiState.githubBackup,
                isBusy = uiState.isGitHubBusy,
                onOpenConfig = { showGitHubConfigDialog = true },
                onToggleAutoBackup = { viewModel.setGitHubAutoBackupEnabled(it) },
                onToggleWifiOnly = { viewModel.setGitHubWifiOnly(it) },
                onSelectBackupHour = { viewModel.setGitHubBackupHour(it) },
                onBackupNow = { viewModel.backupToGitHubNow() },
                onOpenRestorePicker = { viewModel.openGitHubRestorePicker() },
                onRequestDisconnect = { showDisconnectGitHubConfirm = true }
            )
        }

        // Backup & Maintenance Actions
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Copia de Seguridad y Mantenimiento",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Button(
                        onClick = {
                            val json = viewModel.getBackupJson()
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(ClipData.newPlainText("YoliGym Backup", json))
                            viewModel.showStatus("Copia JSON copiada al portapapeles")
                            try {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(Intent.EXTRA_TEXT, json)
                                    putExtra(Intent.EXTRA_SUBJECT, "YoliGym Backup JSON")
                                }
                                context.startActivity(
                                    Intent.createChooser(sendIntent, "Exportar copia de seguridad YoliGym")
                                )
                            } catch (_: Exception) {
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.Backup, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Exportar / Compartir Backup JSON",
                            fontSize = 13.sp,
                            lineHeight = 17.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }

                    OutlinedButton(
                        onClick = { saveBackupFileLauncher.launch("yoligym_backup.json") },
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.SaveAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Guardar Backup JSON en Archivo",
                            fontSize = 13.sp,
                            lineHeight = 17.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }

                    Button(
                        onClick = { showImportBackupDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Importar Backup JSON",
                            fontSize = 13.sp,
                            lineHeight = 17.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }

                    Button(
                        onClick = { showImportSeedDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary
                        ),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Importar Nuevo seedExercises.json",
                            fontSize = 13.sp,
                            lineHeight = 17.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }

                    OutlinedButton(
                        onClick = { showRestoreSeedConfirm = true },
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Restaurar Ejercicios y Rutinas de Fábrica",
                            fontSize = 13.sp,
                            lineHeight = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    }

                    OutlinedButton(
                        onClick = { showClearHistoryConfirm = true },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Borrar Historial de Entrenamientos",
                            fontSize = 13.sp,
                            lineHeight = 17.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    if (showImportBackupDialog) {
        ImportJsonDialog(
            title = "Importar Backup JSON",
            description = "Selecciona un archivo .json de copia de seguridad o pega el contenido JSON desde el portapapeles para restaurar tus ejercicios, rutinas e historial.",
            fileButtonLabel = "Seleccionar archivo Backup (.json)",
            confirmButtonLabel = "Importar Backup",
            onPickFile = {
                openBackupFileLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
            },
            onConfirmText = { jsonText ->
                viewModel.importBackupJson(jsonText)
                showImportBackupDialog = false
            },
            onDismiss = { showImportBackupDialog = false }
        )
    }

    if (showImportSeedDialog) {
        ImportJsonDialog(
            title = "Importar seedExercises.json",
            description = "Selecciona un archivo seedExercises.json desde tu teléfono o pega el JSON de ejercicios para añadir o actualizar el catálogo.",
            fileButtonLabel = "Seleccionar archivo seedExercises.json",
            confirmButtonLabel = "Importar Ejercicios",
            onPickFile = {
                openSeedFileLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
            },
            onConfirmText = { jsonText ->
                viewModel.importSeedExercisesJson(jsonText)
                showImportSeedDialog = false
            },
            onDismiss = { showImportSeedDialog = false }
        )
    }

    if (showRestoreSeedConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreSeedConfirm = false },
            title = { Text("Restaurar datos semilla", fontWeight = FontWeight.Bold) },
            text = {
                Text("Se volverán a sincronizar los ejercicios de fábrica de seedExercises.json y las rutinas base sin borrar tus sesiones de entrenamiento.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.restoreSeedData()
                        showRestoreSeedConfirm = false
                    }
                ) {
                    Text("Restaurar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreSeedConfirm = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showClearHistoryConfirm) {
        AlertDialog(
            onDismissRequest = { showClearHistoryConfirm = false },
            title = { Text("¿Borrar todo el historial?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Esta acción eliminará todas las sesiones y series registradas en workout_sessions y workout_sets (ON DELETE CASCADE). Tus rutinas y ejercicios se mantendrán.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllWorkoutHistory()
                        showClearHistoryConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Borrar Historial", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryConfirm = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showGitHubConfigDialog) {
        GitHubConfigDialog(
            initialConfig = uiState.githubBackup,
            isBusy = uiState.isGitHubBusy,
            onSaveAndVerify = { repo, userId, token, branch ->
                viewModel.configureGitHubBackup(
                    repoInput = repo,
                    userIdInput = userId,
                    tokenInput = token,
                    branchInput = branch,
                    onSuccessDismiss = { showGitHubConfigDialog = false }
                )
            },
            onDismiss = {
                if (!uiState.isGitHubBusy) {
                    showGitHubConfigDialog = false
                }
            }
        )
    }

    if (showDisconnectGitHubConfirm) {
        AlertDialog(
            onDismissRequest = { showDisconnectGitHubConfirm = false },
            title = { Text("¿Desconectar GitHub?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Se borrará el token guardado en este dispositivo y se desactivará el backup automático diario. Los archivos ya guardados en GitHub NO se borrarán.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.disconnectGitHubBackup()
                        showDisconnectGitHubConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Desconectar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectGitHubConfirm = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    val remoteBackups = uiState.remoteGitHubBackups
    if (remoteBackups != null) {
        GitHubRestoreDialog(
            userId = uiState.githubBackup.userId,
            files = remoteBackups,
            isBusy = uiState.isGitHubBusy,
            onSelectFile = { selectedGitHubBackupToRestore = it },
            onDismiss = { viewModel.dismissGitHubRestorePicker() }
        )
    }

    val restoreCandidate = selectedGitHubBackupToRestore
    if (restoreCandidate != null) {
        AlertDialog(
            onDismissRequest = { selectedGitHubBackupToRestore = null },
            title = { Text("¿Restaurar copia de seguridad?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Se restaurarán los ejercicios, rutinas e historial desde «${restoreCandidate.name}» (${restoreCandidate.dateLabel}).")
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedGitHubBackupToRestore = null
                        viewModel.restoreBackupFromGitHub(restoreCandidate)
                    }
                ) {
                    Text("Restaurar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedGitHubBackupToRestore = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GitHubBackupCard(
    config: GitHubBackupConfigState,
    isBusy: Boolean,
    onOpenConfig: () -> Unit,
    onToggleAutoBackup: (Boolean) -> Unit,
    onToggleWifiOnly: (Boolean) -> Unit,
    onSelectBackupHour: (Int) -> Unit,
    onBackupNow: () -> Unit,
    onOpenRestorePicker: () -> Unit,
    onRequestDisconnect: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Icon(
                        imageVector = when {
                            !config.isConfigured -> Icons.Default.CloudUpload
                            config.lastStatus == GitHubBackupStatus.AUTH_ERROR ||
                                config.lastStatus == GitHubBackupStatus.ERROR -> Icons.Default.CloudOff
                            else -> Icons.Default.CloudDone
                        },
                        contentDescription = null,
                        tint = when {
                            !config.isConfigured -> MaterialTheme.colorScheme.primary
                            config.lastStatus == GitHubBackupStatus.AUTH_ERROR ||
                                config.lastStatus == GitHubBackupStatus.ERROR -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Backup Automático en GitHub",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (config.isConfigured) {
                            Text(
                                text = "${config.repoFullName} · Usuario: ${config.userId}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (config.isConfigured) {
                    val statusLabel = when {
                        config.lastStatus == GitHubBackupStatus.AUTH_ERROR || !config.hasToken -> "Revisar token"
                        config.lastStatus == GitHubBackupStatus.ERROR -> "Error"
                        config.autoBackupEnabled -> "Activo"
                        else -> "Pausado"
                    }
                    val statusContainer = when {
                        config.lastStatus == GitHubBackupStatus.AUTH_ERROR ||
                            config.lastStatus == GitHubBackupStatus.ERROR ||
                            !config.hasToken -> MaterialTheme.colorScheme.errorContainer
                        config.autoBackupEnabled -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    val statusContent = when {
                        config.lastStatus == GitHubBackupStatus.AUTH_ERROR ||
                            config.lastStatus == GitHubBackupStatus.ERROR ||
                            !config.hasToken -> MaterialTheme.colorScheme.onErrorContainer
                        config.autoBackupEnabled -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Surface(
                        color = statusContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = statusLabel,
                            color = statusContent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (!config.isConfigured) {
                Text(
                    text = "Guarda automáticamente una copia JSON diaria en tu repositorio privado de GitHub. Cada usuario/dispositivo usa su propio identificativo (backups/<usuario>/yoligym_backup_<usuario>_YYYY-MM-DD.json) y conserva sus últimos ${config.retentionDays} días.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = onOpenConfig,
                    enabled = !isBusy,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Configurar Backup en GitHub",
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                if (config.lastStatus == GitHubBackupStatus.AUTH_ERROR || !config.hasToken) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = config.lastError
                                    ?: "El token de GitHub ha caducado o no tiene permisos de escritura.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Button(
                                onClick = onOpenConfig,
                                enabled = !isBusy,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Actualizar Token / Configuración", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Column {
                    val repoPrivacyNote = when (config.isPrivateRepo) {
                        true -> " · Privado"
                        false -> " · Público"
                        null -> ""
                    }
                    DbStatRow("Repositorio", "${config.repoFullName} (${config.branch}$repoPrivacyNote)")
                    DbStatRow("Identificador", config.userId)
                    DbStatRow("Carpeta en repo", "${GitHubBackupNaming.buildUserFolderPath(config.userId)}/")
                    DbStatRow("Token", config.maskedToken.ifBlank { "No configurado" })
                    DbStatRow("Retención", "Últimos ${config.retentionDays} días por usuario")
                    DbStatRow("Último backup", config.lastBackupAt ?: "Sin backups aún")
                    if (!config.lastBackupFileName.isNullOrBlank()) {
                        DbStatRow("Último archivo", config.lastBackupFileName)
                    }
                }

                if (config.lastStatus == GitHubBackupStatus.ERROR && !config.lastError.isNullOrBlank()) {
                    Text(
                        text = "Último error (${config.lastAttemptAt ?: "-"}): ${config.lastError}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    ) {
                        Text("Backup automático diario (cada 24h)", fontWeight = FontWeight.Bold)
                        Text(
                            "Programa una copia diaria y sincroniza al abrir la app si toca hoy",
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = config.autoBackupEnabled,
                        onCheckedChange = onToggleAutoBackup
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    ) {
                        Text("Solo con Wi-Fi", fontWeight = FontWeight.Bold)
                        Text(
                            "Evita consumir datos móviles en la copia automática",
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = config.wifiOnly,
                        onCheckedChange = onToggleWifiOnly
                    )
                }

                Text("Hora aproximada del backup", fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3, 9, 14, 22).forEach { hour ->
                        FilterChip(
                            selected = config.backupHour == hour,
                            onClick = { onSelectBackupHour(hour) },
                            label = { Text(String.format(Locale.US, "%02d:00", hour)) }
                        )
                    }
                }

                Button(
                    onClick = onBackupNow,
                    enabled = !isBusy,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isBusy) "Sincronizando con GitHub..." else "Hacer Backup en GitHub Ahora",
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }

                OutlinedButton(
                    onClick = onOpenRestorePicker,
                    enabled = !isBusy,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Restaurar desde GitHub",
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }

                OutlinedButton(
                    onClick = onOpenConfig,
                    enabled = !isBusy,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Editar Configuración",
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }

                OutlinedButton(
                    onClick = onRequestDisconnect,
                    enabled = !isBusy,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.LinkOff, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Desconectar GitHub",
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun GitHubConfigDialog(
    initialConfig: GitHubBackupConfigState,
    isBusy: Boolean,
    onSaveAndVerify: (repo: String, userId: String, token: String?, branch: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var repoInput by remember(initialConfig) { mutableStateOf(initialConfig.repoFullName) }
    var userIdInput by remember(initialConfig) { mutableStateOf(initialConfig.userId) }
    var tokenInput by remember(initialConfig) { mutableStateOf("") }
    var branchInput by remember(initialConfig) {
        mutableStateOf(if (initialConfig.isConfigured) initialConfig.branch else "")
    }
    var showToken by remember { mutableStateOf(false) }

    val sanitizedUser = GitHubBackupNaming.sanitizeUserId(userIdInput)
    val parsedRepo = GitHubBackupNaming.parseOwnerRepo(repoInput)
    val todayLabel = remember { LocalDate.now().toString() }
    val canSubmit = !isBusy &&
        parsedRepo != null &&
        sanitizedUser.isNotEmpty() &&
        (initialConfig.hasToken || tokenInput.isNotBlank())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configurar Backup en GitHub", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = repoInput,
                    onValueChange = { repoInput = it },
                    label = { Text("Repositorio (usuario/repo o URL)") },
                    placeholder = { Text("amunardegea/yoligym-backups") },
                    singleLine = true,
                    isError = repoInput.isNotBlank() && parsedRepo == null,
                    supportingText = if (repoInput.isNotBlank() && parsedRepo == null) {
                        { Text("Usa el formato usuario/repositorio (ej. amunardegea/yoligym-backups)") }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = userIdInput,
                    onValueChange = { userIdInput = it },
                    label = { Text("Identificador de usuario / dispositivo") },
                    placeholder = { Text("ej. yoli, andres") },
                    singleLine = true,
                    supportingText = {
                        if (sanitizedUser.isNotEmpty()) {
                            Text("Archivo: backups/$sanitizedUser/yoligym_backup_${sanitizedUser}_$todayLabel.json")
                        } else {
                            Text("Introduce letras o números para identificar este dispositivo")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    label = {
                        Text(
                            if (initialConfig.hasToken) {
                                "Personal Access Token (PAT) · opcional"
                            } else {
                                "Personal Access Token (PAT)"
                            }
                        )
                    },
                    placeholder = {
                        Text(
                            if (initialConfig.hasToken) {
                                "Mantener actual (${initialConfig.maskedToken})"
                            } else {
                                "github_pat_..."
                            }
                        )
                    },
                    singleLine = true,
                    visualTransformation = if (showToken) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { showToken = !showToken }) {
                            Icon(
                                imageVector = if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showToken) "Ocultar token" else "Mostrar token"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = branchInput,
                    onValueChange = { branchInput = it },
                    label = { Text("Rama (opcional)") },
                    placeholder = { Text("Automática (main)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveAndVerify(
                        repoInput,
                        userIdInput,
                        tokenInput.takeIf { it.isNotBlank() },
                        branchInput.takeIf { it.isNotBlank() }
                    )
                },
                enabled = canSubmit
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = if (isBusy) "Verificando..." else "Verificar y Guardar",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun GitHubRestoreDialog(
    userId: String,
    files: List<GitHubBackupFile>,
    isBusy: Boolean,
    onSelectFile: (GitHubBackupFile) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Backups en GitHub ($userId)", fontWeight = FontWeight.ExtraBold) },
        text = {
            if (files.isEmpty()) {
                Text(
                    text = "No se encontraron copias diarias para «$userId» en backups/$userId/.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                ) {
                    items(files, key = { it.path }) { file ->
                        val sizeLabel = if (file.sizeBytes > 0L) {
                            "${maxOf(1L, file.sizeBytes / 1024L)} KB"
                        } else {
                            "JSON"
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isBusy) { onSelectFile(file) }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = file.dateLabel,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = file.name,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = sizeLabel,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

@Composable
private fun ImportJsonDialog(
    title: String,
    description: String,
    fileButtonLabel: String,
    confirmButtonLabel: String,
    onPickFile: () -> Unit,
    onConfirmText: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var jsonInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = onPickFile,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(fileButtonLabel, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val clipText = cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                        if (!clipText.isNullOrBlank()) {
                            jsonInput = clipText
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pegar desde el portapapeles", fontWeight = FontWeight.SemiBold)
                }

                OutlinedTextField(
                    value = jsonInput,
                    onValueChange = { jsonInput = it },
                    label = { Text("O pega aquí el contenido JSON") },
                    placeholder = { Text("{ ... } o [ ... ]") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmText(jsonInput) },
                enabled = jsonInput.isNotBlank()
            ) {
                Text(confirmButtonLabel, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun DbStatRow(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.widthIn(max = 175.dp)
        )
    }
}
