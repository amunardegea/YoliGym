package com.yoligym.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yoligym.app.data.Exercise
import com.yoligym.app.data.ExerciseSessionSets
import com.yoligym.app.data.MeasurementType
import com.yoligym.app.data.RoutineWithExercises
import com.yoligym.app.data.SessionDetail
import com.yoligym.app.data.WorkoutSet
import com.yoligym.app.data.YoliGymDatabase
import com.yoligym.app.data.formatDurationSeconds
import com.yoligym.app.data.formatWeight

@Composable
fun EntrenarScreen(
    uiState: YoliGymUiState,
    viewModel: YoliGymViewModel
) {
    var inspectedExercise by remember { mutableStateOf<Exercise?>(null) }
    var editingExercise by remember { mutableStateOf<Exercise?>(null) }
    var editingRoutine by remember { mutableStateOf<RoutineWithExercises?>(null) }
    var showNewRoutineDialog by remember { mutableStateOf(false) }
    var routineToDelete by remember { mutableStateOf<RoutineWithExercises?>(null) }

    val activeSession = uiState.activeSession

    if (activeSession != null) {
        ActiveWorkoutView(
            sessionDetail = activeSession,
            uiState = uiState,
            viewModel = viewModel,
            onInspectExercise = { inspectedExercise = it }
        )
    } else {
        RoutineDashboardView(
            uiState = uiState,
            onStartRoutine = { viewModel.startRoutineWorkout(it) },
            onStartFreeWorkout = { viewModel.startFreeWorkout() },
            onCreateRoutine = { showNewRoutineDialog = true },
            onEditRoutine = { editingRoutine = it },
            onDeleteRoutine = { routineToDelete = it },
            onInspectExercise = { inspectedExercise = it }
        )
    }

    inspectedExercise?.let { ex ->
        val stats = remember(ex.id, uiState.completedSessions) {
            viewModel.getExerciseStats(ex.id)
        }
        ExerciseDetailDialog(
            exercise = ex,
            stats = stats,
            onDismiss = { inspectedExercise = null },
            onEdit = {
                val target = ex
                inspectedExercise = null
                editingExercise = target
            },
            onDelete = if (ex.isCustom) {
                {
                    viewModel.deleteExercise(ex.id)
                    inspectedExercise = null
                }
            } else null
        )
    }

    editingExercise?.let { ex ->
        ExerciseFormDialog(
            initialExercise = ex,
            onDismiss = { editingExercise = null },
            onSave = { id, name, cat, mg, mt, notes, video, isCustom ->
                viewModel.saveExercise(id, name, cat, mg, mt, notes, video, isCustom)
                editingExercise = null
            }
        )
    }

    if (showNewRoutineDialog || editingRoutine != null) {
        RoutineEditorDialog(
            initialRoutine = editingRoutine,
            allExercises = uiState.exercises,
            onDismiss = {
                showNewRoutineDialog = false
                editingRoutine = null
            },
            onSave = { id, name, desc, items ->
                viewModel.saveRoutine(id, name, desc, items)
                showNewRoutineDialog = false
                editingRoutine = null
            }
        )
    }

    routineToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { routineToDelete = null },
            title = { Text("Eliminar rutina", fontWeight = FontWeight.Bold) },
            text = {
                Text("¿Seguro que deseas eliminar la plantilla \"${target.routine.name}\"? Tu historial de entrenamientos se conservará intacto.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteRoutine(target.routine.id)
                        routineToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { routineToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoutineDashboardView(
    uiState: YoliGymUiState,
    onStartRoutine: (RoutineWithExercises) -> Unit,
    onStartFreeWorkout: () -> Unit,
    onCreateRoutine: () -> Unit,
    onEditRoutine: (RoutineWithExercises) -> Unit,
    onDeleteRoutine: (RoutineWithExercises) -> Unit,
    onInspectExercise: (Exercise) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = "YoliGym",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Entrenamiento de fuerza, máquinas y equilibrio",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.Bolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${uiState.globalStats.totalSessions} ${if (uiState.globalStats.totalSessions == 1) "sesión" else "sesiones"}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        // Routines Section Header
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Mis Rutinas (${uiState.routines.size})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onCreateRoutine,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Nueva Rutina", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        items(uiState.routines, key = { it.routine.id }) { routineWithEx ->
            val totalSets = routineWithEx.items.sumOf { it.targetSets }
            val distinctMuscles = routineWithEx.items.map { it.exercise.muscleGroup }.distinct()

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = routineWithEx.routine.name,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (!routineWithEx.routine.description.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = routineWithEx.routine.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row {
                            IconButton(onClick = { onEditRoutine(routineWithEx) }) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Editar rutina",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onDeleteRoutine(routineWithEx) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Eliminar rutina",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "${routineWithEx.items.size} ejercicios · $totalSets series",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        distinctMuscles.forEach { mg ->
                            MuscleBadge(mg)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Spacer(modifier = Modifier.height(10.dp))

                    routineWithEx.items.forEachIndexed { idx, item ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onInspectExercise(item.exercise) }
                                .padding(vertical = 5.dp, horizontal = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "${idx + 1}.",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 13.sp,
                                    lineHeight = 17.sp,
                                    modifier = Modifier.width(22.dp)
                                )
                                Text(
                                    text = item.exercise.name,
                                    fontSize = 13.sp,
                                    lineHeight = 17.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (item.exercise.measurementType) {
                                    MeasurementType.TIME_SECONDS -> "${item.targetSets} × ${item.targetDurationSeconds.takeIf { it > 0 } ?: 20}s"
                                    else -> "${item.targetSets} × ${item.targetReps}"
                                },
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { onStartRoutine(routineWithEx) },
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "EMPEZAR RUTINA",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Quick Start Free Workout Card (at the bottom)
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(20.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Sesión Rápida",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Empieza un entrenamiento en blanco y añade ejercicios sobre la marcha.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = onStartFreeWorkout,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "INICIAR ENTRENAMIENTO LIBRE",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveWorkoutView(
    sessionDetail: SessionDetail,
    uiState: YoliGymUiState,
    viewModel: YoliGymViewModel,
    onInspectExercise: (Exercise) -> Unit
) {
    var showAddExercisePicker by remember { mutableStateOf(false) }
    var showFinishDialog by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Sticky Active Session Header
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = sessionDetail.session.name,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "⏱ ${formatDurationSeconds(uiState.workoutElapsedSeconds)}",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "✓ ${sessionDetail.completedSetsCount}/${sessionDetail.totalSetsCount} series",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (sessionDetail.totalVolumeKg > 0) {
                                Text(
                                    text = "${formatWeight(sessionDetail.totalVolumeKg)} kg",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilledTonalIconButton(
                            onClick = { viewModel.startRestTimer() }
                        ) {
                            Icon(
                                Icons.Default.Timer,
                                contentDescription = "Iniciar descanso",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Button(
                            onClick = { showFinishDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "FINALIZAR",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                // Rest Timer Banner when active
                AnimatedVisibility(visible = uiState.isRestTimerRunning || uiState.restRemainingSeconds > 0) {
                    Column(
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Descanso: ${formatDurationSeconds(uiState.restRemainingSeconds)}",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "Prepárate para la siguiente serie",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                onClick = { viewModel.adjustRestTimer(-15) },
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "-15s",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                            Surface(
                                onClick = { viewModel.adjustRestTimer(+15) },
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "+15s",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                            Surface(
                                onClick = { viewModel.stopRestTimer() },
                                color = MaterialTheme.colorScheme.error,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "Saltar",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        val progress = if (uiState.restTotalSeconds > 0) {
                            uiState.restRemainingSeconds.toFloat() / uiState.restTotalSeconds.toFloat()
                        } else 0f
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }
                }
            }
        }

        // Scrollable Exercise Cards in Active Workout
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f)
        ) {
            if (sessionDetail.exercises.isEmpty()) {
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
                                .padding(28.dp)
                        ) {
                            Icon(
                                Icons.Default.FitnessCenter,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Sin ejercicios todavía",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Toca el botón de abajo para añadir tu primer ejercicio a esta sesión.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            items(sessionDetail.exercises, key = { it.exercise.id }) { exGroup ->
                ActiveExerciseCard(
                    exGroup = exGroup,
                    weightStepKg = uiState.preferences.weightStepKg,
                    activeTimedSetId = uiState.activeTimedSetId,
                    activeTimedSetRemaining = uiState.activeTimedSetRemaining,
                    onInspectExercise = { onInspectExercise(exGroup.exercise) },
                    onUpdateSet = { set, weight, reps, duration, isWarmup, isCompleted ->
                        viewModel.updateSet(
                            set = set,
                            weightKg = weight,
                            reps = reps,
                            durationSeconds = duration,
                            isWarmup = isWarmup,
                            isCompleted = isCompleted,
                            restSecondsHint = exGroup.targetRestSeconds
                        )
                    },
                    onToggleSetTimer = { set ->
                        viewModel.startOrStopInlineSetTimer(set)
                    },
                    onAddSet = { viewModel.addSetToExercise(exGroup.exercise.id) },
                    onRemoveLastSet = {
                        exGroup.sets.lastOrNull()?.let { viewModel.removeSet(it) }
                    }
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { showAddExercisePicker = true },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AÑADIR EJERCICIO A LA SESIÓN",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center
                        )
                    }

                    OutlinedButton(
                        onClick = { showDiscardDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 46.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Descartar Entrenamiento", fontSize = 13.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }

    if (showAddExercisePicker) {
        ExercisePickerDialog(
            exercises = uiState.exercises,
            onDismiss = { showAddExercisePicker = false },
            onSelectExercise = { ex ->
                viewModel.addExerciseToActiveSession(ex)
                showAddExercisePicker = false
            }
        )
    }

    if (showFinishDialog) {
        var sessionNotes by remember { mutableStateOf(sessionDetail.session.notes ?: "") }
        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            title = { Text("Finalizar Entrenamiento", fontWeight = FontWeight.ExtraBold) },
            text = {
                Column {
                    Text(
                        text = "Duración: ${formatDurationSeconds(uiState.workoutElapsedSeconds)}\n" +
                            "Series completadas: ${sessionDetail.completedSetsCount} de ${sessionDetail.totalSetsCount}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = sessionNotes,
                        onValueChange = { sessionNotes = it },
                        label = { Text("Notas de la sesión (opcional)") },
                        placeholder = { Text("Ej: Buenas sensaciones en prensa, subí 2.5kg...") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.finishActiveWorkout(sessionNotes)
                        showFinishDialog = false
                    }
                ) {
                    Text("Guardar Sesión", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFinishDialog = false }) {
                    Text("Seguir entrenando")
                }
            }
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("¿Descartar sesión?", fontWeight = FontWeight.Bold) },
            text = { Text("Se borrarán las series registradas en la sesión actual en curso.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.discardActiveWorkout()
                        showDiscardDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Descartar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun FilledTonalIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.size(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveExerciseCard(
    exGroup: ExerciseSessionSets,
    weightStepKg: Double,
    activeTimedSetId: String?,
    activeTimedSetRemaining: Int,
    onInspectExercise: () -> Unit,
    onUpdateSet: (WorkoutSet, Double, Int, Int, Boolean, Boolean) -> Unit,
    onToggleSetTimer: (WorkoutSet) -> Unit,
    onAddSet: () -> Unit,
    onRemoveLastSet: () -> Unit
) {
    val ex = exGroup.exercise
    var editingWeightForSet by remember { mutableStateOf<WorkoutSet?>(null) }
    var editingRepsForSet by remember { mutableStateOf<WorkoutSet?>(null) }
    var editingDurationForSet by remember { mutableStateOf<WorkoutSet?>(null) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onInspectExercise)
                ) {
                    Text(
                        text = ex.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CategoryBadge(ex.category)
                        MuscleBadge(ex.muscleGroup)
                        if (exGroup.previousBestText != null) {
                            Text(
                                text = "Ant: ${exGroup.previousBestText}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                        }
                    }
                }

                IconButton(onClick = onInspectExercise) {
                    Icon(
                        imageVector = if (ex.videoUrl != null) Icons.Default.PlayCircleFilled else Icons.Default.Info,
                        contentDescription = "Ver técnica y vídeo",
                        tint = if (ex.videoUrl != null) Color(0xFFFF5252) else MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (!ex.notes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = ex.notes,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Sets Rows
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                exGroup.sets.forEach { set ->
                    val isTimedRunning = activeTimedSetId == set.id
                    val rowBg = if (set.isCompleted) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.background
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(rowBg)
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        // Set Number / Warmup Badge (tap to toggle warmup)
                        Surface(
                            onClick = {
                                onUpdateSet(
                                    set,
                                    set.weightKg,
                                    set.reps,
                                    set.durationSeconds,
                                    !set.isWarmup,
                                    set.isCompleted
                                )
                            },
                            color = if (set.isWarmup) {
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (set.isWarmup) "C" else "${set.setNumber}",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                    color = if (set.isWarmup) {
                                        MaterialTheme.colorScheme.tertiary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }

                        // Measurement controls
                        when (ex.measurementType) {
                            MeasurementType.WEIGHT_REPS -> {
                                LargeOneHandStepper(
                                    valueText = formatWeight(set.weightKg),
                                    unitLabel = "KG",
                                    onDecrement = {
                                        onUpdateSet(
                                            set,
                                            (set.weightKg - weightStepKg).coerceAtLeast(0.0),
                                            set.reps,
                                            set.durationSeconds,
                                            set.isWarmup,
                                            set.isCompleted
                                        )
                                    },
                                    onIncrement = {
                                        onUpdateSet(
                                            set,
                                            set.weightKg + weightStepKg,
                                            set.reps,
                                            set.durationSeconds,
                                            set.isWarmup,
                                            set.isCompleted
                                        )
                                    },
                                    onDirectEdit = { editingWeightForSet = set },
                                    modifier = Modifier.weight(1.1f)
                                )

                                LargeOneHandStepper(
                                    valueText = "${set.reps}",
                                    unitLabel = "REPS",
                                    onDecrement = {
                                        onUpdateSet(
                                            set,
                                            set.weightKg,
                                            (set.reps - 1).coerceAtLeast(0),
                                            set.durationSeconds,
                                            set.isWarmup,
                                            set.isCompleted
                                        )
                                    },
                                    onIncrement = {
                                        onUpdateSet(
                                            set,
                                            set.weightKg,
                                            set.reps + 1,
                                            set.durationSeconds,
                                            set.isWarmup,
                                            set.isCompleted
                                        )
                                    },
                                    onDirectEdit = { editingRepsForSet = set },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            MeasurementType.REPS_ONLY -> {
                                LargeOneHandStepper(
                                    valueText = "${set.reps}",
                                    unitLabel = "REPS (Obj: ${exGroup.targetRepsHint})",
                                    onDecrement = {
                                        onUpdateSet(
                                            set,
                                            0.0,
                                            (set.reps - 1).coerceAtLeast(0),
                                            0,
                                            set.isWarmup,
                                            set.isCompleted
                                        )
                                    },
                                    onIncrement = {
                                        onUpdateSet(
                                            set,
                                            0.0,
                                            set.reps + 1,
                                            0,
                                            set.isWarmup,
                                            set.isCompleted
                                        )
                                    },
                                    onDirectEdit = { editingRepsForSet = set },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            MeasurementType.TIME_SECONDS -> {
                                LargeOneHandStepper(
                                    valueText = "${set.durationSeconds}s",
                                    unitLabel = "SEGUNDOS",
                                    onDecrement = {
                                        onUpdateSet(
                                            set,
                                            0.0,
                                            0,
                                            (set.durationSeconds - 5).coerceAtLeast(5),
                                            set.isWarmup,
                                            set.isCompleted
                                        )
                                    },
                                    onIncrement = {
                                        onUpdateSet(
                                            set,
                                            0.0,
                                            0,
                                            set.durationSeconds + 5,
                                            set.isWarmup,
                                            set.isCompleted
                                        )
                                    },
                                    onDirectEdit = { editingDurationForSet = set },
                                    modifier = Modifier.weight(1f)
                                )

                                // Live countdown button for balance/mobility exercises
                                Surface(
                                    onClick = { onToggleSetTimer(set) },
                                    color = if (isTimedRunning) {
                                        MaterialTheme.colorScheme.tertiary
                                    } else {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.heightIn(min = 46.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 10.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isTimedRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                            contentDescription = "Cronometrar serie",
                                            tint = if (isTimedRunning) Color.Black else MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = if (isTimedRunning) "${activeTimedSetRemaining}s" else "Crono",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            color = if (isTimedRunning) Color.Black else MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }
                        }

                        // Large Check Button (One-hand friendly)
                        Surface(
                            onClick = {
                                onUpdateSet(
                                    set,
                                    set.weightKg,
                                    set.reps,
                                    set.durationSeconds,
                                    set.isWarmup,
                                    !set.isCompleted
                                )
                            },
                            color = if (set.isCompleted) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Completar serie",
                                    tint = if (set.isCompleted) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Add / Remove Set Footer
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onAddSet,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 42.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Añadir Serie", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                if (exGroup.sets.size > 1) {
                    OutlinedButton(
                        onClick = onRemoveLastSet,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.heightIn(min = 42.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Quitar última serie", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }

    editingWeightForSet?.let { target ->
        NumberInputDialog(
            title = "Peso (kg) · Serie ${target.setNumber}",
            initialValue = formatWeight(target.weightKg),
            isDecimal = true,
            onDismiss = { editingWeightForSet = null },
            onConfirm = { str ->
                val v = str.toDoubleOrNull() ?: target.weightKg
                onUpdateSet(target, v, target.reps, target.durationSeconds, target.isWarmup, target.isCompleted)
                editingWeightForSet = null
            }
        )
    }

    editingRepsForSet?.let { target ->
        NumberInputDialog(
            title = "Repeticiones · Serie ${target.setNumber}",
            initialValue = "${target.reps}",
            isDecimal = false,
            onDismiss = { editingRepsForSet = null },
            onConfirm = { str ->
                val v = str.toIntOrNull() ?: target.reps
                onUpdateSet(target, target.weightKg, v, target.durationSeconds, target.isWarmup, target.isCompleted)
                editingRepsForSet = null
            }
        )
    }

    editingDurationForSet?.let { target ->
        NumberInputDialog(
            title = "Duración (segundos) · Serie ${target.setNumber}",
            initialValue = "${target.durationSeconds}",
            isDecimal = false,
            onDismiss = { editingDurationForSet = null },
            onConfirm = { str ->
                val v = str.toIntOrNull() ?: target.durationSeconds
                onUpdateSet(target, target.weightKg, target.reps, v, target.isWarmup, target.isCompleted)
                editingDurationForSet = null
            }
        )
    }
}

@Composable
fun RoutineEditorDialog(
    initialRoutine: RoutineWithExercises?,
    allExercises: List<Exercise>,
    onDismiss: () -> Unit,
    onSave: (
        routineId: String?,
        name: String,
        description: String?,
        items: List<YoliGymDatabase.RoutineExerciseDraft>
    ) -> Unit
) {
    var name by remember { mutableStateOf(initialRoutine?.routine?.name ?: "") }
    var description by remember { mutableStateOf(initialRoutine?.routine?.description ?: "") }
    var showPicker by remember { mutableStateOf(false) }

    val draftItems = remember {
        mutableStateListOf<YoliGymDatabase.RoutineExerciseDraft>().apply {
            initialRoutine?.items?.forEach { item ->
                add(
                    YoliGymDatabase.RoutineExerciseDraft(
                        exerciseId = item.exerciseId,
                        targetSets = item.targetSets,
                        targetReps = item.targetReps,
                        targetDurationSeconds = item.targetDurationSeconds.takeIf { it > 0 } ?: 20,
                        targetRestSeconds = item.targetRestSeconds
                    )
                )
            }
        }
    }

    val exercisesMap = remember(allExercises) { allExercises.associateBy { it.id } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (initialRoutine == null) "Crear Nueva Rutina" else "Editar Rutina",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre de la rutina *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Ejercicios (${draftItems.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Button(
                        onClick = { showPicker = true },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Añadir Ejercicio", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    itemsIndexed(draftItems) { index, item ->
                        val ex = exercisesMap[item.exerciseId] ?: return@itemsIndexed
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "${index + 1}. ${ex.name}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Row {
                                        if (index > 0) {
                                            IconButton(
                                                onClick = {
                                                    val tmp = draftItems[index - 1]
                                                    draftItems[index - 1] = draftItems[index]
                                                    draftItems[index] = tmp
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.ArrowUpward, contentDescription = "Subir", modifier = Modifier.size(18.dp))
                                            }
                                        }
                                        if (index < draftItems.lastIndex) {
                                            IconButton(
                                                onClick = {
                                                    val tmp = draftItems[index + 1]
                                                    draftItems[index + 1] = draftItems[index]
                                                    draftItems[index] = tmp
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.ArrowDownward, contentDescription = "Bajar", modifier = Modifier.size(18.dp))
                                            }
                                        }
                                        IconButton(
                                            onClick = { draftItems.removeAt(index) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Quitar",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Series stepper
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surface)
                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                draftItems[index] = item.copy(
                                                    targetSets = (item.targetSets - 1).coerceAtLeast(1)
                                                )
                                            },
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(16.dp))
                                        }
                                        Text(
                                            text = "${item.targetSets} series",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp)
                                        )
                                        IconButton(
                                            onClick = {
                                                draftItems[index] = item.copy(
                                                    targetSets = (item.targetSets + 1).coerceAtMost(15)
                                                )
                                            },
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        }
                                    }

                                    if (ex.measurementType == MeasurementType.TIME_SECONDS) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surface)
                                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    draftItems[index] = item.copy(
                                                        targetDurationSeconds = (item.targetDurationSeconds - 5).coerceAtLeast(5)
                                                    )
                                                },
                                                modifier = Modifier.size(26.dp)
                                            ) {
                                                Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(16.dp))
                                            }
                                            Text(
                                                text = "${item.targetDurationSeconds}s",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp)
                                            )
                                            IconButton(
                                                onClick = {
                                                    draftItems[index] = item.copy(
                                                        targetDurationSeconds = item.targetDurationSeconds + 5
                                                    )
                                                },
                                                modifier = Modifier.size(26.dp)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    } else {
                                        OutlinedTextField(
                                            value = item.targetReps,
                                            onValueChange = { newReps ->
                                                draftItems[index] = item.copy(targetReps = newReps)
                                            },
                                            label = { Text("Reps obj.") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text("Cancelar", fontSize = 13.sp)
                    }
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onSave(
                                    initialRoutine?.routine?.id,
                                    name,
                                    description.ifBlank { null },
                                    draftItems.toList()
                                )
                            }
                        },
                        enabled = name.isNotBlank() && draftItems.isNotEmpty(),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text(
                            text = "Guardar Rutina",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    if (showPicker) {
        ExercisePickerDialog(
            exercises = allExercises,
            onDismiss = { showPicker = false },
            onSelectExercise = { ex ->
                draftItems.add(
                    YoliGymDatabase.RoutineExerciseDraft(
                        exerciseId = ex.id,
                        targetSets = if (ex.measurementType == MeasurementType.TIME_SECONDS) 3 else 4,
                        targetReps = if (ex.measurementType == MeasurementType.REPS_ONLY) "15" else "12-15",
                        targetDurationSeconds = if (ex.measurementType == MeasurementType.TIME_SECONDS) 20 else 0,
                        targetRestSeconds = if (ex.measurementType == MeasurementType.TIME_SECONDS) 45 else 90
                    )
                )
                showPicker = false
            }
        )
    }
}
