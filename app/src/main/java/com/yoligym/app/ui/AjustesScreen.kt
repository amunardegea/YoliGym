package com.yoligym.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AjustesScreen(
    uiState: YoliGymUiState,
    viewModel: YoliGymViewModel
) {
    val context = LocalContext.current
    var showClearHistoryConfirm by remember { mutableStateOf(false) }
    var showRestoreSeedConfirm by remember { mutableStateOf(false) }
    var showImportBackupDialog by remember { mutableStateOf(false) }
    var showImportSeedDialog by remember { mutableStateOf(false) }

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
                Column(modifier = Modifier.padding(18.dp)) {
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Modo Oscuro (Alto Contraste Gym)", fontWeight = FontWeight.Bold)
                            Text(
                                "Activado por defecto para ahorrar batería y mejorar legibilidad",
                                fontSize = 12.sp,
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-iniciar Temporizador de Descanso", fontWeight = FontWeight.Bold)
                            Text(
                                "Inicia la cuenta atrás automáticamente al marcar una serie como completada (✓)",
                                fontSize = 12.sp,
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Vibrar al finalizar descanso o crono", fontWeight = FontWeight.Bold)
                            Text(
                                "Aviso háptico al terminar el descanso o una serie por tiempo",
                                fontSize = 12.sp,
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
                Column(modifier = Modifier.padding(18.dp)) {
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

        // Backup & Maintenance Actions
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(18.dp)
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(Icons.Default.Backup, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Exportar / Compartir Backup JSON", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { saveBackupFileLauncher.launch("yoligym_backup.json") },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(Icons.Default.SaveAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Guardar Backup JSON en Archivo", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { showImportBackupDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Importar Backup JSON", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { showImportSeedDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Importar Nuevo seedExercises.json", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { showRestoreSeedConfirm = true },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Restaurar Ejercicios y Rutinas de Fábrica", fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = { showClearHistoryConfirm = true },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Borrar Historial de Entrenamientos", fontWeight = FontWeight.Bold)
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
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
