package com.xuan.gridironmanager.ui.match

import com.xuan.gridironmanager.domain.model.PlayType
import com.xuan.gridironmanager.domain.sim.match.DriveEngine
import com.xuan.gridironmanager.domain.sim.match.GameState
import com.xuan.gridironmanager.domain.sim.match.Rules
import com.xuan.gridironmanager.domain.sim.movement.RunningPlayer
import com.xuan.gridironmanager.domain.sim.play.PlayOutcome
import com.xuan.gridironmanager.domain.sim.play.PlaySimulator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Owns the live match state and drives [PlaySimulator] in real time for visualisation. */
class MatchPresenter(
    private val driveEngine: DriveEngine,
    private val scope: CoroutineScope,
    private val simDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val random: Random = Random.Default,
) {
    private val _uiState = MutableStateFlow(MatchUiState())
    val uiState: StateFlow<MatchUiState> = _uiState.asStateFlow()

    fun updateGameState(gameState: GameState) {
        _uiState.update { it.copy(gameState = gameState) }
    }

    fun snapBall(
        offense: List<RunningPlayer>,
        defense: List<RunningPlayer>,
        playType: PlayType = PlayType.PASS,
        isAttackingUp: Boolean = true,
    ) {
        val current = _uiState.value
        if (current.isPlayRunning || current.gameState.isGameOver) return

        val gameState = current.gameState
        val losWorldY = if (isAttackingUp) gameState.yardLine.toFloat() else (Rules.FIELD_LENGTH_YDS - gameState.yardLine).toFloat()
        val directionMultiplier = if (isAttackingUp) 1f else -1f
        val isKick = playType == PlayType.KICK || playType == PlayType.PUNT

        _uiState.update {
            it.copy(
                isPlayRunning = true,
                playByPlayText =
                    when {
                        playType == PlayType.RUN -> "Hand-off!"
                        isKick -> "Ready for the kick!"
                        else -> "Ball is snapped!"
                    },
                lineOfScrimmageY = losWorldY,
                firstDownMarkerY = if (isKick) null else losWorldY + (gameState.distance * directionMultiplier),
            )
        }

        scope.launch(simDispatcher) {
            val simulator = PlaySimulator(offense, defense, playType, gameState, isAttackingUp, random)
            val startClockSeconds = gameState.clockSeconds

            var outcome: PlayOutcome?
            do {
                delay(TICK_MILLIS)
                outcome = simulator.tick(TICK_DELTA_SEC)
                _uiState.update { state ->
                    state.copy(
                        gameState =
                            state.gameState.copy(
                                clockSeconds = (startClockSeconds - simulator.elapsedSec.toInt()).coerceAtLeast(0),
                            ),
                        players = simulator.players.map { p -> p.copy() },
                        ballPosition = simulator.ballPosition,
                    )
                }
            } while (outcome == null)

            val liveState = _uiState.value.gameState
            val finalState =
                when (outcome) {
                    is PlayOutcome.Kick -> {
                        if (playType == PlayType.KICK) {
                            driveEngine.resolveKickoff(liveState, outcome.result)
                        } else {
                            driveEngine.resolvePunt(liveState, outcome.result)
                        }
                    }

                    is PlayOutcome.Scrimmage -> {
                        driveEngine.resolvePlay(liveState, outcome.result)
                    }
                }

            _uiState.update {
                it.copy(
                    gameState = finalState,
                    isPlayRunning = false,
                    playByPlayText = if (finalState.isGameOver) "${outcome.description} That's the final whistle!" else outcome.description,
                    ballPosition = null,
                )
            }
        }
    }

    private companion object {
        const val TICK_MILLIS = 50L
        const val TICK_DELTA_SEC = TICK_MILLIS / 1000f // Real time: one sim second per wall-clock second
    }
}
