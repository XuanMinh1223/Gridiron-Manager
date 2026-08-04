package com.xuan.gridironmanager.ui.match

import com.xuan.gridironmanager.domain.model.PlayType
import com.xuan.gridironmanager.domain.model.Vector3D
import com.xuan.gridironmanager.domain.sim.AttributeTranslator
import com.xuan.gridironmanager.domain.sim.BallTrajectory
import com.xuan.gridironmanager.domain.sim.PassEvaluator
import com.xuan.gridironmanager.domain.sim.ai.QbBrain
import com.xuan.gridironmanager.domain.sim.ai.QbState
import com.xuan.gridironmanager.domain.sim.match.DriveEngine
import com.xuan.gridironmanager.domain.sim.match.GameState
import com.xuan.gridironmanager.domain.sim.match.KickResult
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
    private val scope: CoroutineScope,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default
) {
    private val _uiState = MutableStateFlow(MatchUiState())
    val uiState: StateFlow<MatchUiState> = _uiState.asStateFlow()

    private var activeTrajectory: BallTrajectory? = null
    private var elapsedPlayTimeSec = 0f
    private var passStartTimeSec = 0f
    private var targetReceiverId: String? = null

    fun updateGameState(gameState: GameState) {
        _uiState.update { it.copy(gameState = gameState) }
    }

    fun snapBall(
        offense: List<RunningPlayer>,
        defense: List<RunningPlayer>,
        playType: PlayType = PlayType.PASS,
        isAttackingUp: Boolean = true,
        onPlayResolved: (GameState, PlayResult) -> Unit = { _, _ -> }
    ) {
        if (_uiState.value.isPlayRunning) return

        val gameState = _uiState.value.gameState
        val initialWorldY = if (isAttackingUp) gameState.yardLine.toFloat() else (100 - gameState.yardLine).toFloat()
        val distance = gameState.distance
        val directionMultiplier = if (isAttackingUp) 1f else -1f
        
        _uiState.update { 
            it.copy(
                isPlayRunning = true, 
                playByPlayText = if (playType == PlayType.RUN) "Hand-off!" else if (playType == PlayType.KICK || playType == PlayType.PUNT) "Ready for the kick!" else "Ball is snapped!",
                lineOfScrimmageY = initialWorldY,
                firstDownMarkerY = initialWorldY + (distance * directionMultiplier)
            )
        }

        elapsedPlayTimeSec = 0f
        passStartTimeSec = 0f
        activeTrajectory = null

        scope.launch(ioDispatcher) {
            val players = offense + defense
            var playResult: PlayResult? = null
            var kickResult: KickResult? = null
            val tickDelta = 0.05f // 20Hz
            val maxPlayDurationSec = 15f // Safety timeout

            // 2. QB Logic Setup (only for PASS)
            val qb = if (playType == PlayType.PASS) offense.find { it.id.startsWith("QB_") } else null
            val qbBrain = if (qb != null) QbBrain(qb, offense.filter { it != qb }) else null

            // Special Teams Phase: FLIGHT -> LANDED
            var isKickFlight = (playType == PlayType.KICK || playType == PlayType.PUNT)

            if (isKickFlight) {
                val kicker = offense.find { it.id.startsWith("K_") || it.id.startsWith("P_") } ?: offense.first()
                val kickPower = 80 // Mock or from player attributes
                val dist = AttributeTranslator.calculateKickDistanceYards(kickPower)
                val hangtime = AttributeTranslator.calculateHangtimeSec(kickPower)
                
                val targetY = if (isAttackingUp) initialWorldY + dist else initialWorldY - dist
                
                activeTrajectory = BallTrajectory(
                    startPos = kicker.currentPos,
                    targetPos = Vector3D(kicker.currentPos.x, targetY, 0f),
                    totalFlightTimeSec = hangtime,
                    apexHeightYards = 30f
                )
                passStartTimeSec = elapsedPlayTimeSec
            }

            while (playResult == null && kickResult == null && elapsedPlayTimeSec < maxPlayDurationSec) {
                delay(50)
                elapsedPlayTimeSec += tickDelta
                
                // Deduct clock in real-time
                _uiState.update { state ->
                    state.copy(
                        gameState = state.gameState.copy(
                            clockSeconds = (state.gameState.clockSeconds - 1).coerceAtLeast(0)
                        )
                    )
                }

                // 1. Update positions
                MovementEngine.updatePositions(players, tickDelta)

                var currentBallPos: Vector3D? = null

                if (isKickFlight) {
                    activeTrajectory?.let { trajectory ->
                        val elapsedSinceKick = elapsedPlayTimeSec - passStartTimeSec
                        currentBallPos = trajectory.getPositionAt(elapsedSinceKick)
                        
                        if (elapsedSinceKick >= trajectory.totalFlightTimeSec) {
                            isKickFlight = false
                            // Check landing spot
                            val ballPosAtLanding = currentBallPos
                            
                            // Yard line from kicker's perspective (0-100)
                            val kickerYardLine = if (isAttackingUp) ballPosAtLanding.y else 100 - ballPosAtLanding.y
                            
                            // Yard line for the receiving team (distance from their own goal)
                            val receiverYardLine = (100 - kickerYardLine).toInt()

                            if (kickerYardLine >= 100 || kickerYardLine <= 0) {
                                // Touchback
                                kickResult = KickResult(
                                    endYardLine = if (playType == PlayType.KICK) 25 else 20,
                                    description = "Touchback.",
                                    isTouchback = true,
                                    isOutOfBounds = false
                                )
                            } else if (ballPosAtLanding.x < 0 || ballPosAtLanding.x > 53.3f) {
                                // Out of bounds
                                kickResult = KickResult(
                                    endYardLine = if (playType == PlayType.KICK) 40 else receiverYardLine,
                                    description = "Kick out of bounds.",
                                    isTouchback = false,
                                    isOutOfBounds = true
                                )
                            } else {
                                // Caught/Landed in bounds -> Resolve immediately for prototype stability
                                val returnYards = 15 // Simplified return
                                val endYardLine = (receiverYardLine + returnYards).coerceAtMost(100)

                                kickResult = KickResult(
                                    endYardLine = endYardLine,
                                    description = "Kick caught and returned to the $endYardLine.",
                                    isTouchback = false,
                                    isOutOfBounds = false
                                )
                            }
                        }
                    }
                } else if (playType == PlayType.RUN) {
                    val rb = offense.find { it.id.startsWith("RB_") }
                    rb?.let { runner ->
                        currentBallPos = runner.currentPos
                        // Simple Run Logic: Advance until tackled
                        for (defender in defense) {
                            if (runner.currentPos.distance2DTo(defender.currentPos) < 1.2f) {
                                val currentY = runner.currentPos.y
                                val yardsGained = if (isAttackingUp) {
                                    (currentY - initialWorldY).toInt()
                                } else {
                                    (initialWorldY - currentY).toInt()
                                }
                                
                                val totalYardLine = gameState.yardLine + yardsGained
                                playResult = PlayResult(
                                    yardsGained = yardsGained,
                                    description = if (totalYardLine >= 100) "TOUCHDOWN!" else "Run for $yardsGained yards.",
                                    isTouchdown = totalYardLine >= 100,
                                    isTurnover = false
                                )
                                break
                            }
                        }
                    }
                } else if (playType == PlayType.PASS) {
                    // 2. QB Logic (Pass)
                    if (qbBrain != null && qbBrain.state != QbState.THROWING && activeTrajectory == null) {
                        val throwCmd = qbBrain.evaluateTick(defense, PassEvaluator)
                        if (throwCmd != null) {
                            val receiver = offense.find { it.id == throwCmd.targetId }
                            if (receiver != null) {
                                // LEAD PASSING: Target where receiver will be in flightTime
                                val currentDist = qb!!.currentPos.distance2DTo(receiver.currentPos)
                                val estimatedFlightTime = currentDist / 20f
                                
                                // Simple lead: assume receiver keeps moving on their route
                                val leadPos = receiver.currentPos.copy(
                                    y = receiver.currentPos.y + (directionMultiplier * receiver.speedYdsPerSec * estimatedFlightTime)
                                )
                                
                                activeTrajectory = BallTrajectory(
                                    startPos = qb.currentPos,
                                    targetPos = leadPos,
                                    totalFlightTimeSec = estimatedFlightTime.coerceAtLeast(0.1f),
                                    apexHeightYards = 3f
                                )
                                passStartTimeSec = elapsedPlayTimeSec
                                targetReceiverId = receiver.id
                            }
                        }
                    }

                    // 3. Ball Trajectory
                    activeTrajectory?.let { trajectory ->
                        val elapsedSincePass = elapsedPlayTimeSec - passStartTimeSec
                        val ballPos = trajectory.getPositionAt(elapsedSincePass)
                        currentBallPos = ballPos
                        
                        if (elapsedSincePass >= trajectory.totalFlightTimeSec) {
                            // Pass reached target
                            val receiver = offense.find { it.id == targetReceiverId }
                            val dist = ballPos.distance2DTo(receiver?.currentPos ?: ballPos)
                            
                            if (dist < 2.5f) { // Catch radius
                                val catchY = ballPos.y
                                val yardsGained = if (isAttackingUp) {
                                    (catchY - initialWorldY).toInt()
                                } else {
                                    (initialWorldY - catchY).toInt()
                                }
                                
                                val totalYardLine = gameState.yardLine + yardsGained
                                
                                playResult = PlayResult(
                                    yardsGained = yardsGained,
                                    description = if (totalYardLine >= 100) "TOUCHDOWN!" else "Pass complete for $yardsGained yards!",
                                    isTouchdown = totalYardLine >= 100,
                                    isTurnover = false,
                                    clockStops = totalYardLine >= 100
                                )
                            } else {
                                playResult = PlayResult(
                                    yardsGained = 0,
                                    description = "Incomplete pass.",
                                    isTouchdown = false,
                                    isTurnover = false,
                                    clockStops = true
                                )
                            }
                            activeTrajectory = null
                        }
                    }

                    // 4. Sack Check
                    if (qbBrain != null && qbBrain.state == QbState.SACKED) {
                        val currentY = qb!!.currentPos.y
                        val yardsLost = if (isAttackingUp) {
                            (initialWorldY - currentY).toInt()
                        } else {
                            (currentY - initialWorldY).toInt()
                        }.coerceAtLeast(0)
                        
                        playResult = PlayResult(
                            yardsGained = -yardsLost,
                            description = "QB is SACKED for a loss of $yardsLost yards!",
                            isTouchdown = false,
                            isTurnover = false
                        )
                    }
                }

                // Update UI state with ball and player positions
                _uiState.update { 
                    it.copy(
                        players = players.map { p -> p.copy() },
                        ballPosition = currentBallPos
                    )
                }
            }

            // Handle safety timeout
            if (playResult == null && kickResult == null) {
                playResult = PlayResult(0, "Play whistled dead.", false, false, clockStops = true)
            }

            // Resolve Play
            val finalSimState = when {
                kickResult != null -> {
                    if (playType == PlayType.KICK) driveEngine.resolveKickoff(_uiState.value.gameState, kickResult!!)
                    else driveEngine.resolvePunt(_uiState.value.gameState, kickResult!!)
                }
                else -> driveEngine.resolvePlay(_uiState.value.gameState, playResult!!)
            }

            _uiState.update { 
                it.copy(
                    gameState = finalSimState,
                    isPlayRunning = false,
                    playByPlayText = kickResult?.description ?: playResult!!.description,
                    ballPosition = null
                )
            }
            
            // Map KickResult back to PlayResult for the callback
            val callbackResult = playResult ?: PlayResult(
                yardsGained = kickResult!!.endYardLine, // For kicks, we'll store the field position reached
                description = kickResult!!.description,
                isTouchdown = kickResult!!.isTouchdown,
                isTurnover = true
            )
            
            onPlayResolved(finalSimState, callbackResult)
        }
    }
}
