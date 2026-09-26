package com.xuan.gridironmanager.domain.sim.play

import com.xuan.gridironmanager.domain.model.PlayType
import com.xuan.gridironmanager.domain.model.Position
import com.xuan.gridironmanager.domain.model.Vector3D
import com.xuan.gridironmanager.domain.sim.AttributeTranslator
import com.xuan.gridironmanager.domain.sim.BallTrajectory
import com.xuan.gridironmanager.domain.sim.ai.QbBrain
import com.xuan.gridironmanager.domain.sim.ai.QbState
import com.xuan.gridironmanager.domain.sim.match.GameState
import com.xuan.gridironmanager.domain.sim.match.KickResult
import com.xuan.gridironmanager.domain.sim.match.PlayResult
import com.xuan.gridironmanager.domain.sim.match.Rules
import com.xuan.gridironmanager.domain.sim.movement.MovementEngine
import com.xuan.gridironmanager.domain.sim.movement.RunningPlayer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

sealed interface PlayOutcome {
    val description: String

    data class Scrimmage(
        val result: PlayResult,
    ) : PlayOutcome {
        override val description get() = result.description
    }

    data class Kick(
        val result: KickResult,
    ) : PlayOutcome {
        override val description get() = result.description
    }
}

/**
 * Tick-based simulation of a single play. Holds no timing of its own: the caller advances it with [tick]
 * until it returns a [PlayOutcome].
 *
 * World coordinates: x runs across the field (0 to 53.3), y runs from the home goal line (0) to the away goal line (100).
 */
class PlaySimulator(
    private val offense: List<RunningPlayer>,
    private val defense: List<RunningPlayer>,
    private val playType: PlayType,
    private val gameState: GameState,
    private val isAttackingUp: Boolean,
    private val random: Random = Random.Default,
) {
    val players: List<RunningPlayer> = offense + defense

    var elapsedSec = 0f
        private set

    var ballPosition: Vector3D? = null
        private set

    private val direction = if (isAttackingUp) 1f else -1f
    private val losWorldY = if (isAttackingUp) gameState.yardLine.toFloat() else (Rules.FIELD_LENGTH_YDS - gameState.yardLine).toFloat()

    private val qb = if (playType == PlayType.PASS) offense.find { it.position == Position.QB } else null
    private val qbBrain = qb?.let { QbBrain(it, offense.filter { player -> player.position in ELIGIBLE_RECEIVERS }) }
    private val ballCarrier = if (playType == PlayType.RUN) offense.find { it.position == Position.RB } else null

    /** Defensive linemen are engaged by blockers until they shed their block, at a time that varies per rep. */
    private val blockShedSecById: Map<String, Float> =
        if (playType == PlayType.RUN || playType == PlayType.PASS) {
            val meanShedSec = if (playType == PlayType.RUN) RUN_BLOCK_SHED_SEC else PASS_BLOCK_SHED_SEC
            defense
                .filter { it.position in DEFENSIVE_LINE }
                .associate { defender ->
                    val shedSec =
                        if (random.nextFloat() < PENETRATION_CHANCE) {
                            PENETRATION_SHED_SEC // Beats the block at the snap
                        } else {
                            meanShedSec * (1f + BLOCK_SHED_VARIANCE * (2f * random.nextFloat() - 1f))
                        }
                    defender.id to shedSec
                }
        } else {
            emptyMap()
        }
    private val blockedDefenderIds = blockShedSecById.keys

    /**
     * Man coverage, keyed by defender id: every eligible receiver is picked up by the nearest free defender,
     * then any spare defenders double the receiver nearest to them.
     */
    private val coverageAssignments: Map<String, RunningPlayer> =
        if (playType == PlayType.PASS) {
            val receivers = offense.filter { it.position in ELIGIBLE_RECEIVERS }
            val unassigned = defense.filter { it.id !in blockedDefenderIds }.toMutableList()
            val assignments = mutableMapOf<String, RunningPlayer>()
            for (receiver in receivers) {
                val defender = unassigned.minByOrNull { it.currentPos.distance2DTo(receiver.currentPos) } ?: break
                assignments[defender.id] = receiver
                unassigned.remove(defender)
            }
            for (defender in unassigned) {
                receivers.minByOrNull { it.currentPos.distance2DTo(defender.currentPos) }?.let { assignments[defender.id] = it }
            }
            assignments
        } else {
            emptyMap()
        }

    private var ballTrajectory: BallTrajectory? = null
    private var throwTimeSec = 0f
    private var targetReceiver: RunningPlayer? = null

    init {
        if (playType == PlayType.KICK || playType == PlayType.PUNT) {
            val kicker = offense.find { it.position == Position.K || it.position == Position.P } ?: offense.first()
            val kickDistance = AttributeTranslator.calculateKickDistanceYards(kicker.kickPower)
            ballTrajectory =
                BallTrajectory(
                    startPos = kicker.currentPos,
                    targetPos = kicker.currentPos.copy(y = kicker.currentPos.y + kickDistance * direction, z = 0f),
                    totalFlightTimeSec = AttributeTranslator.calculateHangtimeSec(kicker.kickPower),
                    apexHeightYards = KICK_APEX_YDS,
                )
        }
    }

    fun tick(tickDeltaSec: Float): PlayOutcome? {
        elapsedSec += tickDeltaSec

        MovementEngine.updatePositions(offense, tickDeltaSec)

        val freeDefenders = defense.filter { !isBlocked(it) }
        when (playType) {
            PlayType.RUN -> {
                // Once they read run, free defenders converge on the ball carrier
                val carrier = ballCarrier
                if (carrier != null && elapsedSec >= RUN_READ_DELAY_SEC) {
                    freeDefenders.forEach { MovementEngine.pursue(it, carrier.currentPos, tickDeltaSec) }
                }
            }

            PlayType.PASS -> {
                // Linemen who shed their blocks rush the QB; everyone else plays man coverage from over the top
                freeDefenders.forEach { defender ->
                    val target =
                        if (defender.id in blockedDefenderIds) {
                            qb?.currentPos
                        } else {
                            coverageAssignments[defender.id]?.currentPos?.let { it.copy(y = it.y + COVERAGE_CUSHION_YDS * direction) }
                        }
                    target?.let { MovementEngine.pursue(defender, it, tickDeltaSec) }
                }
            }

            PlayType.KICK, PlayType.PUNT -> {
                MovementEngine.updatePositions(freeDefenders, tickDeltaSec)
            }
        }

        val outcome =
            when (playType) {
                PlayType.KICK, PlayType.PUNT -> tickKick()
                PlayType.RUN -> tickRun()
                PlayType.PASS -> tickPass(tickDeltaSec)
            }

        return outcome ?: if (elapsedSec >= MAX_PLAY_DURATION_SEC) {
            PlayOutcome.Scrimmage(PlayResult(0, "Play whistled dead.", isTouchdown = false, isTurnover = false, clockStops = true))
        } else {
            null
        }
    }

    private fun tickKick(): PlayOutcome? {
        val trajectory = ballTrajectory ?: return null
        val ball = trajectory.getPositionAt(elapsedSec)
        ballPosition = ball
        if (elapsedSec < trajectory.totalFlightTimeSec) return null

        // Yard line from the kicking team's perspective (0-100)
        val kickingYardLine = if (isAttackingUp) ball.y else Rules.FIELD_LENGTH_YDS - ball.y
        // Yard line for the receiving team (distance from their own goal)
        val receivingYardLine = (Rules.FIELD_LENGTH_YDS - kickingYardLine).toInt()

        val result =
            when {
                kickingYardLine >= Rules.FIELD_LENGTH_YDS -> {
                    KickResult(endYardLine = 0, description = "Touchback.", isTouchback = true, isOutOfBounds = false)
                }

                ball.x < 0 || ball.x > FIELD_WIDTH_YDS -> {
                    KickResult(
                        endYardLine = if (playType == PlayType.KICK) Rules.KICKOFF_OUT_OF_BOUNDS_YARD_LINE else receivingYardLine,
                        description = "Kick out of bounds.",
                        isTouchback = false,
                        isOutOfBounds = true,
                    )
                }

                else -> {
                    // Caught/landed in bounds -> resolve immediately with a flat return for prototype stability
                    val endYardLine = (receivingYardLine + SIMPLIFIED_RETURN_YDS).coerceAtMost(Rules.FIELD_LENGTH_YDS - 1)
                    KickResult(
                        endYardLine = endYardLine,
                        description = "Kick caught and returned to the $endYardLine.",
                        isTouchback = false,
                        isOutOfBounds = false,
                    )
                }
            }
        return PlayOutcome.Kick(result)
    }

    private fun tickRun(): PlayOutcome? {
        val carrier = ballCarrier ?: return whistleDead()
        ballPosition = carrier.currentPos
        val yardsGained = yardsFromLos(carrier.currentPos.y)

        if (gameState.yardLine + yardsGained >= Rules.FIELD_LENGTH_YDS) {
            return touchdown(yardsGained, "TOUCHDOWN! Run for ${Rules.FIELD_LENGTH_YDS - gameState.yardLine} yards.")
        }

        if (defense.any { !isBlocked(it) && carrier.currentPos.distance2DTo(it.currentPos) < TACKLE_RADIUS_YDS }) {
            return PlayOutcome.Scrimmage(PlayResult(yardsGained, "Run for $yardsGained yards.", isTouchdown = false, isTurnover = false))
        }
        return null
    }

    private fun tickPass(tickDeltaSec: Float): PlayOutcome? {
        val qb = qb ?: return whistleDead()
        val brain = qbBrain ?: return whistleDead()

        if (ballTrajectory == null) {
            ballPosition = qb.currentPos
            brain.evaluateTick(defense, tickDeltaSec)?.let { throwCommand ->
                if (throwCommand.isThrowAway) return incomplete("Nobody open, the pass is thrown away.")
                ballTrajectory = withThrowError(throwCommand.trajectory, qb.throwAccuracy)
                throwTimeSec = elapsedSec
                targetReceiver = offense.find { it.id == throwCommand.targetId }
            }

            if (brain.state == QbState.SACKED) {
                val yardsLost = (-yardsFromLos(qb.currentPos.y)).coerceAtLeast(0)
                return PlayOutcome.Scrimmage(
                    PlayResult(-yardsLost, "QB is SACKED for a loss of $yardsLost yards!", isTouchdown = false, isTurnover = false),
                )
            }
        }

        val trajectory = ballTrajectory ?: return null
        val sinceThrowSec = elapsedSec - throwTimeSec
        val ball = trajectory.getPositionAt(sinceThrowSec)
        ballPosition = ball

        // Defenders who get a hand on the ball in flight break it up or pick it off
        for (defender in defense) {
            if (ball.distance2DTo(defender.currentPos) > DEFENDER_REACH_RADIUS_YDS) continue
            // A ball arriving at chest height can be caught; most contested balls are still only knocked away
            if (ball.z <= defender.verticalReach.heightYards && random.nextFloat() < INTERCEPTION_CHANCE) {
                val returnSpot = yardsFromLos(ball.y)
                return PlayOutcome.Scrimmage(
                    PlayResult(returnSpot, "INTERCEPTED!", isTouchdown = false, isTurnover = true, clockStops = true),
                )
            }
            if (ball.z <= defender.verticalReach.maxCatchHeightYards) {
                return incomplete("Pass broken up!")
            }
        }

        if (sinceThrowSec < trajectory.totalFlightTimeSec) return null

        val receiver = targetReceiver ?: return incomplete("Incomplete pass.")
        val yardsGained = yardsFromLos(ball.y)
        val catchYardLine = gameState.yardLine + yardsGained
        val isCatchable =
            ball.distance2DTo(receiver.currentPos) < CATCH_RADIUS_YDS &&
                ball.x in 0f..FIELD_WIDTH_YDS &&
                catchYardLine <= Rules.FIELD_LENGTH_YDS + Rules.END_ZONE_DEPTH_YDS

        return when {
            !isCatchable -> incomplete("Incomplete pass.")
            catchYardLine >= Rules.FIELD_LENGTH_YDS -> touchdown(yardsGained, "TOUCHDOWN! Pass complete in the end zone!")
            else -> PlayOutcome.Scrimmage(PlayResult(yardsGained, "Pass complete for $yardsGained yards!", isTouchdown = false, isTurnover = false))
        }
    }

    /** Scatters the intended catch point within the QB's accuracy radius for that distance. */
    private fun withThrowError(
        intended: BallTrajectory,
        throwAccuracy: Int,
    ): BallTrajectory {
        val maxError =
            AttributeTranslator.calculatePassAccuracyRadius(
                throwAccuracy,
                intended.startPos.distance2DTo(intended.targetPos),
            )
        val angle = random.nextFloat() * 2f * PI.toFloat()
        val error = maxError * sqrt(random.nextFloat()) // Uniform over the error circle
        return BallTrajectory(
            startPos = intended.startPos,
            targetPos = intended.targetPos.copy(x = intended.targetPos.x + error * cos(angle), y = intended.targetPos.y + error * sin(angle)),
            totalFlightTimeSec = intended.totalFlightTimeSec,
            apexHeightYards = intended.apexHeightYards,
        )
    }

    private fun isBlocked(defender: RunningPlayer) = elapsedSec < (blockShedSecById[defender.id] ?: 0f)

    private fun yardsFromLos(worldY: Float): Int = ((worldY - losWorldY) * direction).toInt()

    private fun touchdown(
        yardsGained: Int,
        description: String,
    ) = PlayOutcome.Scrimmage(PlayResult(yardsGained, description, isTouchdown = true, isTurnover = false, clockStops = true))

    private fun incomplete(description: String) = PlayOutcome.Scrimmage(PlayResult(0, description, isTouchdown = false, isTurnover = false, clockStops = true))

    private fun whistleDead() = PlayOutcome.Scrimmage(PlayResult(0, "Play whistled dead.", isTouchdown = false, isTurnover = false, clockStops = true))

    companion object {
        const val MAX_PLAY_DURATION_SEC = 15f // Safety timeout
        private val ELIGIBLE_RECEIVERS = setOf(Position.WR, Position.TE, Position.RB)
        private const val FIELD_WIDTH_YDS = 53.3f
        private const val KICK_APEX_YDS = 30f
        private const val SIMPLIFIED_RETURN_YDS = 15
        private const val TACKLE_RADIUS_YDS = 1.2f
        private const val CATCH_RADIUS_YDS = 2.5f
        private const val DEFENDER_REACH_RADIUS_YDS = 0.7f
        private val DEFENSIVE_LINE = setOf(Position.DT, Position.DL, Position.EDGE)
        private const val RUN_BLOCK_SHED_SEC = 2.0f
        private const val PASS_BLOCK_SHED_SEC = 2.2f // Average time in the pocket before the rush gets home
        private const val BLOCK_SHED_VARIANCE = 0.3f // Shed time varies by up to ±30%
        private const val INTERCEPTION_CHANCE = 0.25f
        private const val PENETRATION_CHANCE = 0.04f
        private const val PENETRATION_SHED_SEC = 0.2f
        private const val RUN_READ_DELAY_SEC = 1.0f
        private const val COVERAGE_CUSHION_YDS = 2f
    }
}
