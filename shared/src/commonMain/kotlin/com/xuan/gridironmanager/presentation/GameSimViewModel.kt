package com.xuan.gridironmanager.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xuan.gridironmanager.domain.engine.LeagueGenerator
import com.xuan.gridironmanager.domain.engine.MatchEngine
import com.xuan.gridironmanager.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GameSimViewModel : ViewModel() {
    private val _gameState = MutableStateFlow<GameState?>(null)
    val gameState: StateFlow<GameState?> = _gameState.asStateFlow()

    init {
        val teams = LeagueGenerator.generateLeague()
        if (teams.size >= 2) {
            _gameState.value = GameState(
                homeTeam = teams[0],
                awayTeam = teams[1],
                possession = TeamId.HOME
            )
        }
    }

    fun simulatePlay(playCall: PlayCall) {
        viewModelScope.launch {
            _gameState.update { currentState ->
                currentState?.let {
                    MatchEngine.resolvePlay(it, playCall)
                }
            }
        }
    }

    fun quickSimDrive() {
        viewModelScope.launch {
            repeat(5) {
                _gameState.update { currentState ->
                    currentState?.let {
                        val playCall = if (kotlin.random.Random.nextBoolean()) {
                            PlayCall(PlayType.RUN, Formation.SINGLEBACK)
                        } else {
                            PlayCall(PlayType.PASS, Formation.SHOTGUN)
                        }
                        MatchEngine.resolvePlay(it, playCall)
                    }
                }
            }
        }
    }
}
