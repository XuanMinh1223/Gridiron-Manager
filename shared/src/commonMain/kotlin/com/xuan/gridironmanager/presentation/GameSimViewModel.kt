package com.xuan.gridironmanager.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xuan.gridironmanager.domain.engine.LeagueGenerator
import com.xuan.gridironmanager.domain.engine.MatchEngine
import com.xuan.gridironmanager.domain.model.*
import com.xuan.gridironmanager.domain.sim.match.DriveEngine
import com.xuan.gridironmanager.domain.sim.match.GameState as SimGameState
import com.xuan.gridironmanager.domain.sim.play.PlaySetupHelper
import com.xuan.gridironmanager.ui.match.MatchPresenter
import com.xuan.gridironmanager.ui.match.MatchUiState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class GameSimViewModel : ViewModel() {
    private val _gameState = MutableStateFlow<GameState?>(null)
    val gameState: StateFlow<GameState?> = _gameState.asStateFlow()

    private val driveEngine = DriveEngine()
    private val matchPresenter = MatchPresenter(driveEngine, viewModelScope)
    val matchUiState: StateFlow<MatchUiState> = matchPresenter.uiState

    init {
        val teams = LeagueGenerator.generateLeague()
        if (teams.size >= 2) {
            val homeTeam = teams[0]
            val awayTeam = teams[1]
            val homeRoster = LeagueGenerator.generatePlayersForTeam(homeTeam.id)
            val awayRoster = LeagueGenerator.generatePlayersForTeam(awayTeam.id)
            
            val initialState = GameState(
                homeTeam = homeTeam,
                awayTeam = awayTeam,
                homeRoster = homeRoster,
                awayRoster = awayRoster,
                possession = TeamId.HOME
            )
            _gameState.value = initialState
            
            // Sync initial state to match presenter
            syncToMatchPresenter(initialState)
        }
    }

    fun syncToMatchPresenter(state: GameState) {
        val simState = SimGameState(
            down = state.down,
            distance = state.distance,
            yardLine = state.yardLine,
            homeScore = state.homeScore,
            awayScore = state.awayScore,
            clockSeconds = state.clockSeconds,
            isHomePossession = state.possession == TeamId.HOME
        )
        matchPresenter.updateGameState(simState)
    }

    fun startVisualPlay() {
        val current = _gameState.value ?: return
        val isHomePossession = current.possession == TeamId.HOME
        
        // Determine Play Type (NFL Logic)
        val playType = when {
            current.playHistory.isEmpty() -> PlayType.KICK // Start of game
            current.playHistory.lastOrNull()?.isTouchdown == true -> PlayType.KICK // After TD
            current.down == 4 && current.distance > 5 -> PlayType.PUNT // 4th and long
            else -> if (kotlin.random.Random.nextBoolean()) PlayType.RUN else PlayType.PASS
        }

        // Use appropriate formations
        val offFormation = when (playType) {
            PlayType.KICK -> PlaySetupHelper.getKickoffFormation()
            PlayType.PUNT -> PlaySetupHelper.getPuntFormation()
            else -> PlaySetupHelper.getShotgunFormation()
        }
        val defFormation = when (playType) {
            PlayType.KICK -> PlaySetupHelper.getKickReturnFormation()
            PlayType.PUNT -> PlaySetupHelper.getPuntReturnFormation()
            else -> PlaySetupHelper.getBaseDefense()
        }
        
        // World coordinates: Home attacks towards 100 (+Y), Away attacks towards 0 (-Y)
        val offLosWorldY = when (playType) {
            PlayType.KICK -> if (isHomePossession) 35f else 65f
            else -> if (isHomePossession) current.yardLine.toFloat() else (100 - current.yardLine).toFloat()
        }

        val defLosWorldY = when (playType) {
            PlayType.KICK -> if (isHomePossession) 98f else 2f
            else -> offLosWorldY
        }
        
        val offense = PlaySetupHelper.createRunningPlayers(
            roster = if (isHomePossession) current.homeRoster else current.awayRoster,
            formation = offFormation,
            losWorldY = offLosWorldY,
            isOffense = true,
            isAttackingUp = isHomePossession
        )
        
        val defense = PlaySetupHelper.createRunningPlayers(
            roster = if (isHomePossession) current.awayRoster else current.homeRoster,
            formation = defFormation,
            losWorldY = defLosWorldY,
            isOffense = false,
            isAttackingUp = isHomePossession
        )
        
        matchPresenter.snapBall(offense, defense, playType, isHomePossession) { finalSimState, playResult ->
            _gameState.update { domainState ->
                domainState?.copy(
                    homeScore = finalSimState.homeScore,
                    awayScore = finalSimState.awayScore,
                    down = finalSimState.down,
                    distance = finalSimState.distance,
                    yardLine = finalSimState.yardLine,
                    clockSeconds = finalSimState.clockSeconds,
                    possession = if (finalSimState.isHomePossession) TeamId.HOME else TeamId.AWAY,
                    playHistory = domainState.playHistory + PlayResult(
                        description = playResult.description,
                        yardage = playResult.yardsGained,
                        type = playType,
                        isTouchdown = playResult.isTouchdown,
                        isTurnover = playResult.isTurnover
                    )
                )
            }
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
