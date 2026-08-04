package com.xuan.gridironmanager.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xuan.gridironmanager.presentation.GameSimViewModel
import com.xuan.gridironmanager.ui.match.MatchScreen
import com.xuan.gridironmanager.ui.screens.DashboardScreen

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
        val matchUiState by viewModel.matchUiState.collectAsState()

        when (currentScreen) {
            Screen.DASHBOARD -> {
                DashboardScreen(
                    myTeam = gameState?.homeTeam,
                    roster = gameState?.homeRoster ?: emptyList(),
                    onStartGame = { 
                        gameState?.let { viewModel.syncToMatchPresenter(it) }
                        currentScreen = Screen.LIVE_GAME 
                    }
                )
            }

            Screen.LIVE_GAME -> {
                MatchScreen(
                    uiState = matchUiState,
                    onSnapClicked = { viewModel.startVisualPlay() },
                    onBackClicked = { currentScreen = Screen.DASHBOARD }
                )
            }
        }
    }
}
