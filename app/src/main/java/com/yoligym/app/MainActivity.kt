package com.yoligym.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yoligym.app.ui.AjustesScreen
import com.yoligym.app.ui.EjerciciosScreen
import com.yoligym.app.ui.EntrenarScreen
import com.yoligym.app.ui.HistorialScreen
import com.yoligym.app.ui.YoliGymTheme
import com.yoligym.app.ui.YoliGymViewModel

enum class BottomNavTab(val label: String, val icon: ImageVector) {
    ENTRENAR("Entrenar", Icons.Default.FitnessCenter),
    EJERCICIOS("Ejercicios", Icons.AutoMirrored.Filled.ListAlt),
    HISTORIAL("Historial", Icons.Default.History),
    AJUSTES("Ajustes", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val gymViewModel: YoliGymViewModel = viewModel()
            val uiState by gymViewModel.uiState.collectAsState()

            YoliGymTheme(darkTheme = uiState.preferences.darkTheme) {
                YoliGymMainScaffold(
                    uiState = uiState,
                    viewModel = gymViewModel
                )
            }
        }
    }
}

@Composable
fun YoliGymMainScaffold(
    uiState: com.yoligym.app.ui.YoliGymUiState,
    viewModel: YoliGymViewModel
) {
    var currentTab by rememberSaveable { mutableStateOf(BottomNavTab.ENTRENAR) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.statusMessage) {
        val msg = uiState.statusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                BottomNavTab.entries.forEach { tab ->
                    val selected = currentTab == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { currentTab = tab },
                        icon = {
                            if (tab == BottomNavTab.ENTRENAR && uiState.activeSession != null) {
                                BadgedBox(
                                    badge = {
                                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                            Text("ACT")
                                        }
                                    }
                                ) {
                                    Icon(imageVector = tab.icon, contentDescription = tab.label)
                                }
                            } else {
                                Icon(imageVector = tab.icon, contentDescription = tab.label)
                            }
                        },
                        label = {
                            Text(
                                text = tab.label,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                BottomNavTab.ENTRENAR -> EntrenarScreen(uiState = uiState, viewModel = viewModel)
                BottomNavTab.EJERCICIOS -> EjerciciosScreen(uiState = uiState, viewModel = viewModel)
                BottomNavTab.HISTORIAL -> HistorialScreen(uiState = uiState, viewModel = viewModel)
                BottomNavTab.AJUSTES -> AjustesScreen(uiState = uiState, viewModel = viewModel)
            }
        }
    }
}
