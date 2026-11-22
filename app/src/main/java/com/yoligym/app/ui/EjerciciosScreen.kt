package com.yoligym.app.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yoligym.app.data.Exercise
import com.yoligym.app.data.ExerciseCategory
import com.yoligym.app.data.MuscleGroup

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EjerciciosScreen(
    uiState: YoliGymUiState,
    viewModel: YoliGymViewModel
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<ExerciseCategory?>(null) }
    var selectedMuscle by remember { mutableStateOf<MuscleGroup?>(null) }
    var inspectedExercise by remember { mutableStateOf<Exercise?>(null) }
    var editingExercise by remember { mutableStateOf<Exercise?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    val filteredExercises = remember(
        uiState.exercises,
        searchQuery,
        selectedCategory,
        selectedMuscle
    ) {
        uiState.exercises.filter { ex ->
            val matchesQuery = searchQuery.isBlank() ||
                ex.name.contains(searchQuery, ignoreCase = true) ||
                (ex.notes?.contains(searchQuery, ignoreCase = true) == true)
            val matchesCategory = selectedCategory == null || ex.category == selectedCategory
            val matchesMuscle = selectedMuscle == null || ex.muscleGroup == selectedMuscle
            matchesQuery && matchesCategory && matchesMuscle
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header & Filters
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                text = "Catálogo de Ejercicios",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "${filteredExercises.size} de ${uiState.exercises.size} ejercicios disponibles",
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = "Buscar ejercicio...",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Category Filter Row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { selectedCategory = null },
                            label = { Text("Todas las Categorías") }
                        )
                        ExerciseCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = {
                                    selectedCategory = if (selectedCategory == cat) null else cat
                                },
                                label = { Text(cat.labelEs) }
                            )
                        }
                    }

                    // Muscle Group Filter Row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        FilterChip(
                            selected = selectedMuscle == null,
                            onClick = { selectedMuscle = null },
                            label = { Text("Todos los Músculos") }
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
                }
            }

            // Exercises List
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 92.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredExercises, key = { it.id }) { exercise ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { inspectedExercise = exercise }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = exercise.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                if (exercise.videoUrl != null) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = Color(0xFFFF1744).copy(alpha = 0.16f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayCircleFilled,
                                                contentDescription = "Tiene vídeo",
                                                tint = Color(0xFFFF5252),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Vídeo",
                                                color = Color(0xFFFF5252),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
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

                            if (!exercise.notes.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = exercise.notes,
                                    style = MaterialTheme.typography.bodyMedium,
                                    lineHeight = 17.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { showCreateDialog = true },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text("Nuevo Ejercicio", fontWeight = FontWeight.ExtraBold) },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
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

    if (showCreateDialog || editingExercise != null) {
        ExerciseFormDialog(
            initialExercise = editingExercise,
            onDismiss = {
                showCreateDialog = false
                editingExercise = null
            },
            onSave = { id, name, cat, mg, mt, notes, video, isCustom ->
                viewModel.saveExercise(id, name, cat, mg, mt, notes, video, isCustom)
                showCreateDialog = false
                editingExercise = null
            }
        )
    }
}
