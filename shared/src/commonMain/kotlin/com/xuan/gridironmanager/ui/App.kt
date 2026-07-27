package com.xuan.gridironmanager.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xuan.gridironmanager.presentation.GameSimViewModel
import com.xuan.gridironmanager.ui.screens.DashboardScreen
import com.xuan.gridironmanager.ui.screens.LiveGameScreen

enum class Screen {
    DASHBOARD,
    LIVE_GAME,
}

@Composable
fun App() {
    MaterialTheme {
        var currentScreen by remember { mutableStateOf(Screen.DASHBOARD) }
        val viewModel: GameSimViewModel = viewModel { GameSimViewModel() }
        val gameState by viewModel.gameState.collectAsState()

        when (currentScreen) {
            Screen.DASHBOARD -> {
                DashboardScreen(
                    myTeam = gameState?.homeTeam,
                    onStartGame = { currentScreen = Screen.LIVE_GAME },
                )
            }

            Screen.LIVE_GAME -> {
                LiveGameScreen(
                    gameState = gameState,
                    onSimPlay = { viewModel.simulatePlay(it) },
                    onQuickSim = { viewModel.quickSimDrive() },
                    onBack = { currentScreen = Screen.DASHBOARD },
                )
            }
        }
    }
}
