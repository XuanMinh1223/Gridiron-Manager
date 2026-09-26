package com.xuan.gridironmanager.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xuan.gridironmanager.presentation.GameSimViewModel
import com.xuan.gridironmanager.ui.match.MatchScreen
import com.xuan.gridironmanager.ui.roster.RosterPresenter
import com.xuan.gridironmanager.ui.roster.RosterScreen
import com.xuan.gridironmanager.ui.screens.DashboardScreen

enum class Screen {
    DASHBOARD,
    ROSTER,
    LIVE_GAME,
}

@Composable
fun App() {
    MaterialTheme {
        var currentScreen by remember { mutableStateOf(Screen.DASHBOARD) }
        val viewModel: GameSimViewModel = viewModel { GameSimViewModel() }
        val matchup by viewModel.matchup.collectAsState()
        val matchUiState by viewModel.matchUiState.collectAsState()

        when (currentScreen) {
            Screen.DASHBOARD -> {
                DashboardScreen(
                    myTeam = matchup?.homeTeam,
                    opponent = matchup?.awayTeam,
                    onViewRoster = { currentScreen = Screen.ROSTER },
                    onStartGame = { currentScreen = Screen.LIVE_GAME },
                )
            }

            Screen.ROSTER -> {
                matchup?.homeTeam?.let { team ->
                    val rosterPresenter = viewModel(key = "roster_${team.id}") { RosterPresenter(viewModel.playerRepository, team.id) }
                    val rosterUiState by rosterPresenter.uiState.collectAsState()
                    RosterScreen(
                        teamName = team.fullName,
                        uiState = rosterUiState,
                        onBack = { currentScreen = Screen.DASHBOARD },
                    )
                }
            }

            Screen.LIVE_GAME -> {
                MatchScreen(
                    uiState = matchUiState,
                    homeTeamName = matchup?.homeTeam?.abbreviation ?: "HOME",
                    awayTeamName = matchup?.awayTeam?.abbreviation ?: "AWAY",
                    onSnapClicked = { viewModel.startVisualPlay() },
                    onBackClicked = { currentScreen = Screen.DASHBOARD },
                )
            }
        }
    }
}
