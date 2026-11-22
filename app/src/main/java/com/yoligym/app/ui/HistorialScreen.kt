package com.yoligym.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yoligym.app.data.MeasurementType
import com.yoligym.app.data.SessionDetail
import com.yoligym.app.data.formatDurationSeconds
import com.yoligym.app.data.formatWeight

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistorialScreen(
    uiState: YoliGymUiState,
    viewModel: YoliGymViewModel
) {
    var selectedSession by remember { mutableStateOf<SessionDetail?>(null) }
    var sessionToDelete by remember { mutableStateOf<SessionDetail?>(null) }
    var editingSession by remember { mutableStateOf<SessionDetail?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = "Historial y Progreso",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Registro cronológico de tus entrenamientos completados",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Global Metrics Summary
        item {
            val stats = uiState.globalStats
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        StatMetricBox(
                            value = "${stats.totalSessions}",
                            label = "Sesiones",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        StatMetricBox(
                            value = "${stats.totalCompletedSets}",
                            label = "Series",
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )
                        StatMetricBox(
                            value = "${formatWeight(stats.totalVolumeKg / 1000.0)} t",
                            label = "Volumen",
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f)
                        )
                        StatMetricBox(
                            value = "${(stats.totalDurationSeconds / 60)} min",
                            label = "Tiempo",
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        if (uiState.completedSessions.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp)
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(52.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Sin sesiones registradas",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Cuando finalices una rutina o entrenamiento libre en la pestaña Entrenar, aparecerá aquí con todas sus series y pesos.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(uiState.completedSessions, key = { it.session.id }) { detail ->
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedSession = detail }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = detail.session.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.CalendarToday,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = detail.session.startedAt.take(16),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Timer,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = formatDurationSeconds(detail.session.durationSeconds ?: 0),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            Row {
                                IconButton(onClick = { editingSession = detail }) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Editar notas",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { sessionToDelete = detail }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Eliminar sesión",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                                    )
                                }
                            }
                        }

                        if (!detail.session.notes.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "“${detail.session.notes}”",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        Spacer(modifier = Modifier.height(10.dp))

                        detail.exercises.forEach { exGroup ->
                            val completed = exGroup.sets.filter { it.isCompleted }
                            val bestSummary = when (exGroup.exercise.measurementType) {
                                MeasurementType.WEIGHT_REPS -> {
                                    val best = completed.maxByOrNull { it.weightKg * 100 + it.reps }
                                    if (best != null) "Mejor: ${formatWeight(best.weightKg)} kg × ${best.reps}" else "-"
                                }
                                MeasurementType.REPS_ONLY -> {
                                    val best = completed.maxByOrNull { it.reps }
                                    if (best != null) "Mejor: ${best.reps} reps" else "-"
                                }
                                MeasurementType.TIME_SECONDS -> {
                                    val best = completed.maxByOrNull { it.durationSeconds }
                                    if (best != null) "Mejor: ${best.durationSeconds}s" else "-"
                                }
                            }
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                            ) {
                                Text(
                                    text = "${completed.size}× ${exGroup.exercise.name}",
                                    fontSize = 13.sp,
                                    lineHeight = 17.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = bestSummary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    softWrap = false,
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    selectedSession?.let { detail ->
        SessionDetailModal(
            detail = detail,
            onDismiss = { selectedSession = null },
            onDelete = {
                selectedSession = null
                sessionToDelete = detail
            }
        )
    }

    editingSession?.let { detail ->
        var name by remember { mutableStateOf(detail.session.name) }
        var notes by remember { mutableStateOf(detail.session.notes ?: "") }
        AlertDialog(
            onDismissRequest = { editingSession = null },
            title = { Text("Editar Sesión", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nombre de la sesión") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notas") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateHistorySession(detail.session.id, name, notes)
                        editingSession = null
                    }
                ) {
                    Text("Guardar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingSession = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    sessionToDelete?.let { detail ->
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = { Text("Eliminar sesión", fontWeight = FontWeight.Bold) },
            text = {
                Text("¿Deseas eliminar la sesión \"${detail.session.name}\" del ${detail.session.startedAt.take(10)}? Se borrarán sus series asociadas.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteHistorySession(detail.session.id)
                        sessionToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun StatMetricBox(
    value: String,
    label: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = value,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp,
            maxLines = 1,
            softWrap = false,
            color = color
        )
        Text(
            text = label,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SessionDetailModal(
    detail: SessionDetail,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = detail.session.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Inicio: ${detail.session.startedAt} · Duración: ${formatDurationSeconds(detail.session.durationSeconds ?: 0)}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }

                if (!detail.session.notes.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = detail.session.notes,
                            modifier = Modifier.padding(12.dp),
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                detail.exercises.forEach { exGroup ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.background)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = exGroup.exercise.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        exGroup.sets.forEach { set ->
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                            ) {
                                Text(
                                    text = if (set.isWarmup) "Serie ${set.setNumber} (Calentamiento)" else "Serie ${set.setNumber}",
                                    fontSize = 13.sp,
                                    color = if (set.isWarmup) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = when (exGroup.exercise.measurementType) {
                                        MeasurementType.WEIGHT_REPS -> "${formatWeight(set.weightKg)} kg × ${set.reps} reps"
                                        MeasurementType.REPS_ONLY -> "${set.reps} reps"
                                        MeasurementType.TIME_SECONDS -> "${set.durationSeconds} seg"
                                    } + if (set.isCompleted) " ✓" else " (No compl.)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (set.isCompleted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Eliminar")
                    }
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cerrar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
