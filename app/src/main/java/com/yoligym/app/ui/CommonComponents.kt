package com.yoligym.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yoligym.app.data.Exercise
import com.yoligym.app.data.ExerciseCategory
import com.yoligym.app.data.ExerciseStats
import com.yoligym.app.data.MeasurementType
import com.yoligym.app.data.MuscleGroup
import com.yoligym.app.data.YoliGymDatabase
import com.yoligym.app.data.formatWeight

fun categoryColor(category: ExerciseCategory): Color = when (category) {
    ExerciseCategory.FREE_WEIGHT -> Color(0xFF00E676)
    ExerciseCategory.MACHINE -> Color(0xFF00B0FF)
    ExerciseCategory.MOBILITY -> Color(0xFFFFB300)
}

fun categoryIcon(category: ExerciseCategory): ImageVector = when (category) {
    ExerciseCategory.FREE_WEIGHT -> Icons.Default.FitnessCenter
    ExerciseCategory.MACHINE -> Icons.Default.SettingsInputComponent
    ExerciseCategory.MOBILITY -> Icons.Default.SelfImprovement
}

@Composable
fun CategoryBadge(category: ExerciseCategory, modifier: Modifier = Modifier) {
    val color = categoryColor(category)
    Surface(
        color = color.copy(alpha = 0.16f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = categoryIcon(category),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = category.shortEs,
                color = color,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun MuscleBadge(muscleGroup: MuscleGroup, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Text(
            text = muscleGroup.labelEs,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun MeasurementBadge(measurementType: MeasurementType, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Text(
            text = measurementType.unitHint,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun LargeOneHandStepper(
    valueText: String,
    unitLabel: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    onDirectEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 3.dp)
    ) {
        Surface(
            onClick = onDecrement,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
            shape = RoundedCornerShape(9.dp),
            modifier = Modifier.width(32.dp).height(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Disminuir",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onDirectEdit)
        ) {
            Text(
                text = valueText,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                softWrap = false,
                maxLines = 1
            )
            Text(
                text = unitLabel,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                softWrap = false,
                maxLines = 1
            )
        }

        Surface(
            onClick = onIncrement,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
            shape = RoundedCornerShape(9.dp),
            modifier = Modifier.width(32.dp).height(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Aumentar",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun NumberInputDialog(
    title: String,
    initialValue: String,
    isDecimal: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (isDecimal) KeyboardType.Decimal else KeyboardType.Number
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(text.replace(',', '.')) }) {
                Text("Guardar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseDetailDialog(
    exercise: Exercise,
    stats: ExerciseStats,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val cleanUrl = YoliGymDatabase.sanitizeVideoUrl(exercise.videoUrl)

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
                    Text(
                        text = exercise.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CategoryBadge(exercise.category)
                    MuscleBadge(exercise.muscleGroup)
                    MeasurementBadge(exercise.measurementType)
                    if (exercise.isCustom) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Personalizado",
                                color = MaterialTheme.colorScheme.tertiary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Personal Records & Stats Summary
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = when (exercise.measurementType) {
                                    MeasurementType.WEIGHT_REPS -> "${formatWeight(stats.maxWeightKg)} kg"
                                    MeasurementType.REPS_ONLY -> "${stats.maxReps} reps"
                                    MeasurementType.TIME_SECONDS -> "${stats.maxDurationSeconds}s"
                                },
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Mejor Marca (PR)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${stats.totalCompletedSets}",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Series Totales",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stats.lastPerformanceText ?: "Sin datos",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "Última Serie",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Notas Técnicas y Ejecución",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = exercise.notes?.takeIf { it.isNotBlank() }
                            ?: "Sin notas registradas para este ejercicio.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(14.dp)
                    )
                }

                if (cleanUrl != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl))
                                context.startActivity(intent)
                            } catch (_: Exception) {
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF1744),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(Icons.Default.PlayCircleFilled, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ver Vídeo Demostrativo (YouTube)", fontWeight = FontWeight.Bold)
                    }
                }

                if (stats.recentSets.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Últimas Series Completadas",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    stats.recentSets.take(5).forEach { (dateStr, set) ->
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = dateStr.take(10),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = when (exercise.measurementType) {
                                    MeasurementType.WEIGHT_REPS -> "${formatWeight(set.weightKg)} kg × ${set.reps} reps"
                                    MeasurementType.REPS_ONLY -> "${set.reps} reps"
                                    MeasurementType.TIME_SECONDS -> "${set.durationSeconds} seg"
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onEdit,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Editar")
                    }

                    if (onDelete != null && exercise.isCustom) {
                        OutlinedButton(
                            onClick = onDelete,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Eliminar")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseFormDialog(
    initialExercise: Exercise? = null,
    onDismiss: () -> Unit,
    onSave: (
        id: String?,
        name: String,
        category: ExerciseCategory,
        muscleGroup: MuscleGroup,
        measurementType: MeasurementType,
        notes: String?,
        videoUrl: String?,
        isCustom: Boolean
    ) -> Unit
) {
    var name by remember { mutableStateOf(initialExercise?.name ?: "") }
    var category by remember { mutableStateOf(initialExercise?.category ?: ExerciseCategory.FREE_WEIGHT) }
    var muscleGroup by remember { mutableStateOf(initialExercise?.muscleGroup ?: MuscleGroup.CHEST) }
    var measurementType by remember {
        mutableStateOf(initialExercise?.measurementType ?: MeasurementType.WEIGHT_REPS)
    }
    var notes by remember { mutableStateOf(initialExercise?.notes ?: "") }
    var videoUrl by remember { mutableStateOf(initialExercise?.videoUrl ?: "") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Text(
                    text = if (initialExercise == null) "Nuevo Ejercicio" else "Editar Ejercicio",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del ejercicio *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text("Categoría", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExerciseCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = {
                                category = cat
                                if (cat == ExerciseCategory.MOBILITY && initialExercise == null) {
                                    measurementType = MeasurementType.TIME_SECONDS
                                }
                            },
                            label = { Text(cat.labelEs) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("Grupo Muscular", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MuscleGroup.entries.forEach { mg ->
                        FilterChip(
                            selected = muscleGroup == mg,
                            onClick = { muscleGroup = mg },
                            label = { Text(mg.labelEs) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("Tipo de Medición", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MeasurementType.entries.forEach { mt ->
                        FilterChip(
                            selected = measurementType == mt,
                            onClick = { measurementType = mt },
                            label = { Text(mt.labelEs) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas técnicas / Posición / Consejos") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = videoUrl,
                    onValueChange = { videoUrl = it },
                    label = { Text("URL de vídeo (YouTube opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Text("Cancelar")
                    }
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onSave(
                                    initialExercise?.id,
                                    name,
                                    category,
                                    muscleGroup,
                                    measurementType,
                                    notes.ifBlank { null },
                                    videoUrl.ifBlank { null },
                                    initialExercise?.isCustom ?: true
                                )
                            }
                        },
                        enabled = name.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Text("Guardar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ExercisePickerDialog(
    exercises: List<Exercise>,
    onDismiss: () -> Unit,
    onSelectExercise: (Exercise) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<ExerciseCategory?>(null) }
    var selectedMuscle by remember { mutableStateOf<MuscleGroup?>(null) }

    val filtered = remember(exercises, query, selectedCategory, selectedMuscle) {
        exercises.filter { ex ->
            val matchesQuery = query.isBlank() ||
                ex.name.contains(query, ignoreCase = true) ||
                (ex.notes?.contains(query, ignoreCase = true) == true)
            val matchesCat = selectedCategory == null || ex.category == selectedCategory
            val matchesMuscle = selectedMuscle == null || ex.muscleGroup == selectedMuscle
            matchesQuery && matchesCat && matchesMuscle
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Seleccionar Ejercicio (${filtered.size})",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Buscar ejercicio...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text("Todas") }
                    )
                    ExerciseCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = {
                                selectedCategory = if (selectedCategory == cat) null else cat
                            },
                            label = { Text(cat.shortEs) }
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    FilterChip(
                        selected = selectedMuscle == null,
                        onClick = { selectedMuscle = null },
                        label = { Text("Todos los músculos") }
                    )
                    MuscleGroup.entries.forEach { mg ->
                        FilterChip(
                            selected = selectedMuscle == mg,
                            onClick = {
                                selectedMuscle = if (selectedMuscle == mg) null else mg
                            },
                            label = { Text(mg.labelEs) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(filtered, key = { it.id }) { ex ->
                        Surface(
                            onClick = { onSelectExercise(ex) },
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = ex.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        CategoryBadge(ex.category)
                                        MuscleBadge(ex.muscleGroup)
                                        MeasurementBadge(ex.measurementType)
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Añadir",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
