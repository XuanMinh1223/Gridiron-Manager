package com.xuan.gridironmanager.ui.match

import com.xuan.gridironmanager.domain.model.Vector3D
import com.xuan.gridironmanager.domain.sim.BallTrajectory
import com.xuan.gridironmanager.domain.sim.PassEvaluator
import com.xuan.gridironmanager.domain.sim.PassOutcome
import com.xuan.gridironmanager.domain.sim.ai.QbBrain
import com.xuan.gridironmanager.domain.sim.ai.QbState
import com.xuan.gridironmanager.domain.sim.match.DriveEngine
import com.xuan.gridironmanager.domain.sim.match.GameState
import com.xuan.gridironmanager.domain.sim.match.PlayResult
import com.xuan.gridironmanager.domain.sim.movement.MovementEngine
import com.xuan.gridironmanager.domain.sim.movement.RunningPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MatchPresenter(
    private val driveEngine: DriveEngine,
    private val scope: CoroutineScope
) {
    private val _uiState = MutableStateFlow(MatchUiState())
    val uiState: StateFlow<MatchUiState> = _uiState.asStateFlow()

    private var activeTrajectory: BallTrajectory? = null
    private var elapsedPlayTimeSec = 0f
    private var passStartTimeSec = 0f
    private var targetReceiverId: String? = null

    fun snapBall(offense: List<RunningPlayer>, defense: List<RunningPlayer>) {
        if (_uiState.value.isPlayRunning) return

        _uiState.update { it.copy(isPlayRunning = true, playByPlayText = "Ball is snapped!") }

        val qb = offense.find { it.id == "QB" } ?: return
        val qbBrain = QbBrain(qb, offense.filter { it.id != "QB" })
        elapsedPlayTimeSec = 0f
        passStartTimeSec = 0f
        activeTrajectory = null

        scope.launch(Dispatchers.Default) {
            val players = offense + defense
            var playResult: PlayResult? = null
            val tickDelta = 0.05f // 20Hz

            while (playResult == null) {
                delay(50)
                elapsedPlayTimeSec += tickDelta

                // 1. Update positions
                MovementEngine.updatePositions(players, tickDelta)

                // 2. QB Logic
                if (qbBrain.state != QbState.THROWING && activeTrajectory == null) {
                    val throwCmd = qbBrain.evaluateTick(defense, PassEvaluator)
                    if (throwCmd != null) {
                        val receiver = offense.find { it.id == throwCmd.targetId }
                        if (receiver != null) {
                            activeTrajectory = BallTrajectory(
                                startPos = qb.currentPos,
                                targetPos = receiver.currentPos,
                                totalFlightTimeSec = qb.currentPos.distance2DTo(receiver.currentPos) / 20f,
                                apexHeightYards = 3f
                            )
                            passStartTimeSec = elapsedPlayTimeSec
                            targetReceiverId = receiver.id
                        }
                    }
                }

                // 3. Ball Trajectory
                var currentBallPos: Vector3D? = null
                activeTrajectory?.let { trajectory ->
                    val elapsedSincePass = elapsedPlayTimeSec - passStartTimeSec
                    val ballPos = trajectory.getPositionAt(elapsedSincePass)
                    currentBallPos = ballPos
                    
                    if (elapsedSincePass >= trajectory.totalFlightTimeSec) {
                        // Pass reached target
                        val receiver = offense.find { it.id == targetReceiverId }
                        val dist = ballPos.distance2DTo(receiver?.currentPos ?: ballPos)
                        
                        if (dist < 1.0f) {
                            playResult = PlayResult(
                                yardsGained = 15, // Fixed for prototype
                                description = "Pass complete to ${targetReceiverId} for 15 yards!",
                                isTouchdown = false,
                                isTurnover = false
                            )
                        } else {
                            playResult = PlayResult(
                                yardsGained = 0,
                                description = "Incomplete pass.",
                                isTouchdown = false,
                                isTurnover = false
                            )
                        }
                        activeTrajectory = null
                    }
                }

                // 4. Sack Check
                if (qbBrain.state == QbState.SACKED) {
                    playResult = PlayResult(
                        yardsGained = -7,
                        description = "QB is SACKED for a loss of 7 yards!",
                        isTouchdown = false,
                        isTurnover = false
                    )
                }

                // Update UI
                _uiState.update { 
                    it.copy(
                        players = players.map { p -> p.copy() }, // Deep copy for UI stability
                        ballPosition = currentBallPos
                    )
                }
            }

            // Resolve Play
            val finalState = driveEngine.resolvePlay(_uiState.value.gameState, playResult!!)
            _uiState.update { 
                it.copy(
                    gameState = finalState,
                    isPlayRunning = false,
                    playByPlayText = playResult!!.description,
                    ballPosition = null
                )
            }
        }
    }
}
