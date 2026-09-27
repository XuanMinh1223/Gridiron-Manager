package com.xuan.gridironmanager.domain.sim.play

import com.xuan.gridironmanager.domain.model.PlayType
import com.xuan.gridironmanager.domain.model.Vector3D
import com.xuan.gridironmanager.domain.sim.AttributeTranslator
import com.xuan.gridironmanager.domain.sim.BallTrajectory
import com.xuan.gridironmanager.domain.sim.FieldGeometry
import com.xuan.gridironmanager.domain.sim.ai.QbBrain
import com.xuan.gridironmanager.domain.sim.ai.QbState
import com.xuan.gridironmanager.domain.sim.match.FieldGoalResult
import com.xuan.gridironmanager.domain.sim.match.KickResult
import com.xuan.gridironmanager.domain.sim.match.KickOutcomeType
import com.xuan.gridironmanager.domain.sim.match.PlayOutcome
import com.xuan.gridironmanager.domain.sim.match.PlayResult
import com.xuan.gridironmanager.domain.sim.match.Rules
import com.xuan.gridironmanager.domain.sim.movement.MovementEngine
import com.xuan.gridironmanager.domain.sim.movement.PlayerRole
import com.xuan.gridironmanager.domain.sim.movement.RunningPlayer
import com.xuan.gridironmanager.domain.sim.vision.GazeState
import com.xuan.gridironmanager.domain.sim.vision.VisionEngine
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Tick-based simulation of a single play. Holds no timing of its own: the caller advances it with [tick]
 * until it returns a [PlayOutcome]. All randomness comes from [random], so a seeded run is reproducible.
 */
class PlaySimulator(
    private val snap: Snap,
    private val random: Random = Random.Default,
) {
    private val offense = snap.offense
    private val defense = snap.defense
    private val playType = snap.playType

    val players: List<RunningPlayer> = offense + defense

    var elapsedSec = 0f
        private set

    var ballPosition: Vector3D? = null
        private set

    private val direction = if (snap.isAttackingUp) 1f else -1f
    private val losWorldY = snap.losWorldY
    private val playersById = players.associateBy { it.id }
    private val gazeById = players.associate { it.id to initialGaze(it) }.toMutableMap()

    private val passer = if (playType == PlayType.PASS) offense.find { it.role == PlayerRole.PASSER } else null
    private val qbBrain = passer?.let { QbBrain(it, snap.progression.mapNotNull { id -> playersById[id] }) }

    /** The runner on run plays, or the receiver after a catch. */
    private var ballCarrier = if (playType == PlayType.RUN) offense.find { it.role == PlayerRole.BALL_CARRIER } else null
    private val kicker = offense.find { it.role == PlayerRole.KICKER }
    private val holder = if (playType == PlayType.FIELD_GOAL) offense.find { it.slot == "H" } else null
    private var intendedKickTarget: Vector3D? = null

    private val blocking = BlockingModel(offense, defense, playType, random)

    private var ballTrajectory: BallTrajectory? = null
    private var throwTimeSec = 0f
    private var targetReceiver: RunningPlayer? = null
    private var fieldGoalResult: FieldGoalResult? = null
    private var kickPhase = KickPhase.OPERATION
    private var kickContactSec = 0f
    private var kickLandingYardLine = 0
    private var kickReturner: RunningPlayer? = null
    private var carrierVelocity = Vector3D(0f, 0f, 0f)
    private val defenderTracking = mutableMapOf<String, DefenderTrackingState>()

    // Elusiveness: tacklers who missed and are still recovering, and the moves that beat them
    private val recoveringUntilSec = mutableMapOf<String, Float>()
    private var brokenTackles = 0
    private var firstEvasiveMove: String? = null

    init {
        if (playType == PlayType.KICKOFF || playType == PlayType.PUNT) {
            val kicker = kicker ?: offense.first()
            val kickDistance = AttributeTranslator.calculateKickDistanceYards(kicker.attributes.kickPower)
            intendedKickTarget = kicker.currentPos.copy(y = kicker.currentPos.y + kickDistance * direction, z = 0f)
            val placementError = AttributeTranslator.calculateKickPlacementError(kicker.attributes.kickAccuracy, kickDistance)
            val errorAngle = random.nextFloat() * 2f * PI.toFloat()
            val errorMagnitude = placementError * sqrt(random.nextFloat())
            val actualTarget =
                intendedKickTarget!!.copy(
                    x = intendedKickTarget!!.x + cos(errorAngle) * errorMagnitude,
                    y = intendedKickTarget!!.y + sin(errorAngle) * errorMagnitude,
                )
            ballTrajectory =
                BallTrajectory(
                    startPos = kicker.currentPos,
                    targetPos = actualTarget,
                    totalFlightTimeSec = AttributeTranslator.calculateHangtimeSec(kicker.attributes.kickPower),
                    apexHeightYards = KICK_APEX_YDS,
                )
        }
    }

    fun tick(tickDeltaSec: Float): PlayOutcome? {
        elapsedSec += tickDeltaSec

        if (playType == PlayType.KICKOFF || playType == PlayType.PUNT) {
            return tickSpecialTeams(tickDeltaSec)
        }

        val carrierStart = ballCarrier?.currentPos
        moveOffense(tickDeltaSec)
        blocking.tick(tickDeltaSec) { (ballCarrier ?: passer ?: kicker)?.currentPos }
        ballCarrier?.let { carrier ->
            carrierStart?.let { start ->
                carrierVelocity = Vector3D((carrier.currentPos.x - start.x) / tickDeltaSec, (carrier.currentPos.y - start.y) / tickDeltaSec, 0f)
            }
        }
        moveDefense(tickDeltaSec)
        updateGazes(tickDeltaSec)

        val outcome =
            when (playType) {
                PlayType.FIELD_GOAL -> tickFieldGoal(tickDeltaSec)
                PlayType.RUN -> tickRun()
                PlayType.PASS -> tickPass(tickDeltaSec)
            }

        return outcome ?: if (elapsedSec >= MAX_PLAY_DURATION_SEC) whistleDead() else null
    }

    private fun tickSpecialTeams(tickDeltaSec: Float): PlayOutcome? {
        val kicker = kicker ?: return whistleDead()
        when (kickPhase) {
            KickPhase.OPERATION -> {
                // The operation is intentionally short but non-zero: the ball is not in flight
                // until the kicker completes the approach and contacts it.
                if (elapsedSec < KICK_OPERATION_SEC) {
                    moveKickerToContact(kicker, tickDeltaSec)
                    if (playType == PlayType.PUNT) movePuntRush(tickDeltaSec)
                    return null
                }
                if (playType == PlayType.PUNT && defense.any { it.role == PlayerRole.PASS_RUSHER && it.currentPos.distance2DTo(kicker.currentPos) <= PUNT_BLOCK_RADIUS_YDS }) {
                    val spot = receivingYardLine(kicker.currentPos)
                val recoverer = (defense + offense).filter { it !== kicker }.minByOrNull { it.currentPos.distance2DTo(kicker.currentPos) }
                    return kickOutcome(spot, "Punt BLOCKED and recovered!", false, false, spot, spot, KickOutcomeType.BLOCKED_RECOVERED, recoverer?.isOffense == true)
                }
                kickContactSec = elapsedSec
                kickPhase = KickPhase.FLIGHT
            }

            KickPhase.FLIGHT -> {
                moveCoverage(kicker, tickDeltaSec)
                val trajectory = ballTrajectory ?: return null
                if (elapsedSec - kickContactSec < trajectory.totalFlightTimeSec) {
                    ballPosition = trajectory.getPositionAt(elapsedSec - kickContactSec)
                    return null
                }
                return fieldKick() ?: run {
                    kickPhase = KickPhase.RETURN
                    null
                }
            }

            KickPhase.RETURN -> {
                return tickKickReturn(tickDeltaSec)
            }
        }
        return null
    }

    private fun moveKickerToContact(kicker: RunningPlayer, tickDeltaSec: Float) {
        val target = kicker.currentPos.copy(y = kicker.currentPos.y + direction * KICK_APPROACH_YDS)
        MovementEngine.pursue(kicker, target, tickDeltaSec)
    }

    private fun movePuntRush(tickDeltaSec: Float) {
        val punter = kicker ?: return
        defense.filter { it.role == PlayerRole.PASS_RUSHER }.forEach { MovementEngine.pursue(it, punter.currentPos, tickDeltaSec) }
    }

    private fun moveCoverage(kicker: RunningPlayer, tickDeltaSec: Float) {
        val direction = if (snap.isAttackingUp) 1f else -1f
        val players = offense.filter { it !== kicker }
        players.forEach { player ->
            val target = player.currentPos.copy(y = player.currentPos.y + KICK_COVERAGE_SPRINT_YDS * direction)
            MovementEngine.pursue(player, target, tickDeltaSec)
        }
    }

    private fun moveOffense(tickDeltaSec: Float) {
        val runAfterCatch = ballCarrier?.takeIf { playType == PlayType.PASS }
        if (runAfterCatch == null) {
            MovementEngine.updatePositions(offense, tickDeltaSec)
            return
        }
        MovementEngine.updatePositions(offense.filter { it !== runAfterCatch }, tickDeltaSec)
        // Turn upfield and head for the end zone
        MovementEngine.pursue(runAfterCatch, runAfterCatch.currentPos.copy(y = runAfterCatch.currentPos.y + DOWNFIELD_TARGET_YDS * direction), tickDeltaSec)
    }

    private fun moveDefense(tickDeltaSec: Float) {
        val carrier = ballCarrier
        for (defender in defense) {
            if (isBlocked(defender)) continue
            if (carrier != null && chasesBallCarrier(defender)) {
                MovementEngine.intercept(defender, carrier.currentPos, carrierVelocity, tickDeltaSec)
                continue
            }
            val target =
                when (defender.role) {
                    PlayerRole.PASS_RUSHER, PlayerRole.BLITZER -> (passer ?: kicker)?.currentPos
                    PlayerRole.MAN_COVERAGE, PlayerRole.ZONE_COVERAGE -> coverageTarget(defender)
                    else -> null
                }
            if (target != null) {
                MovementEngine.pursue(defender, target, tickDeltaSec)
            } else {
                MovementEngine.updatePositions(listOf(defender), tickDeltaSec)
            }
        }
    }

    /** Rushers chase the ball carrier straight away; coverage players once they read run, or as soon as a pass is caught. */
    private fun chasesBallCarrier(defender: RunningPlayer): Boolean =
        when (defender.role) {
            PlayerRole.PASS_RUSHER, PlayerRole.BLITZER -> true
            PlayerRole.MAN_COVERAGE, PlayerRole.ZONE_COVERAGE -> playType == PlayType.PASS || elapsedSec >= RUN_READ_DELAY_SEC
            else -> false
        }

    private fun coverageTarget(defender: RunningPlayer): Vector3D? {
        // After the throw, nearby defenders break on the ball
        val throwTarget = ballTrajectory?.takeIf { playType == PlayType.PASS }?.targetPos
        if (throwTarget != null &&
            canBreakOnBall(defender) &&
            defender.currentPos.distance2DTo(throwTarget) <= BALL_BREAK_RADIUS_YDS
        ) {
            return throwTarget
        }

        return when (defender.role) {
            PlayerRole.MAN_COVERAGE -> {
                // Mirror the receiver from a step underneath
                defender.coverageTargetId
                    ?.let { playersById[it] }
                    ?.currentPos
                    ?.let { it.copy(y = it.y - MAN_TRAIL_YDS * direction) }
            }

            else -> {
                zoneTarget(defender)
            }
        }
    }

    /**
     * Drop to the landmark, then shade towards the nearest receiver who enters the zone. Deep defenders also carry
     * vertical routes, staying deeper than any receiver threatening their part of the field.
     */
    private fun zoneTarget(defender: RunningPlayer): Vector3D? {
        val landmark = defender.zoneLandmark ?: return null
        val landmarkDepth = (landmark.y - losWorldY) * direction
        if (landmarkDepth >= DEEP_ZONE_MIN_DEPTH_YDS) {
            val deepestThreat =
                offense
                    .filter { it.role == PlayerRole.RECEIVER && abs(it.currentPos.x - landmark.x) <= DEEP_ZONE_HALF_WIDTH_YDS }
                    .maxByOrNull { (it.currentPos.y - losWorldY) * direction }
            val threatDepth = deepestThreat?.let { (it.currentPos.y - losWorldY) * direction }
            if (deepestThreat != null && threatDepth != null && threatDepth + DEEP_CUSHION_YDS > landmarkDepth) {
                // Stay over the top, shading across towards the threat
                return Vector3D(
                    x = landmark.x + (deepestThreat.currentPos.x - landmark.x) * DEEP_ZONE_SHADE,
                    y = FieldGeometry.clampInsideEndLines(losWorldY + (threatDepth + DEEP_CUSHION_YDS) * direction),
                    z = 0f,
                )
            }
        }
        val threat =
            offense
                .filter { it.role == PlayerRole.RECEIVER && it.currentPos.distance2DTo(landmark) <= ZONE_RADIUS_YDS }
                .minByOrNull { it.currentPos.distance2DTo(landmark) }
        return threat?.currentPos?.let { it.copy(y = it.y + COVERAGE_CUSHION_YDS * direction) } ?: landmark
    }

    private fun fieldKick(): PlayOutcome? {
        val trajectory = ballTrajectory ?: return null
        val ball = trajectory.targetPos
        ballPosition = ball

        val kickingYardLine = if (snap.isAttackingUp) ball.y else Rules.FIELD_LENGTH_YDS - ball.y
        kickLandingYardLine = (Rules.FIELD_LENGTH_YDS - kickingYardLine).toInt().coerceIn(0, Rules.FIELD_LENGTH_YDS)
        val intendedLandingYardLine = receivingYardLine(intendedKickTarget ?: trajectory.targetPos)

        if (playType == PlayType.KICKOFF && kickLandingYardLine > Rules.KICKOFF_LANDING_ZONE_FRONT_YARD_LINE) {
            return kickOutcome(kickLandingYardLine, "Kick short of the landing zone.", false, false, intendedLandingYardLine, kickLandingYardLine, KickOutcomeType.SHORT_KICK)
        }
        if (kickingYardLine >= Rules.FIELD_LENGTH_YDS) {
            return kickOutcome(0, "Touchback.", true, false, intendedLandingYardLine, 0, KickOutcomeType.TOUCHBACK)
        }
        if (ball.x !in 0f..FieldGeometry.WIDTH_YDS) {
            return kickOutcome(kickLandingYardLine, "Kick out of bounds.", false, true, intendedLandingYardLine, kickLandingYardLine, KickOutcomeType.KICK_OUT_OF_BOUNDS)
        }

        val returner = defense.find { it.slot == if (playType == PlayType.PUNT) "PR" else "KR" } ?: defense.minByOrNull { it.currentPos.distance2DTo(ball) }
        val canField = returner != null && returner.currentPos.distance2DTo(ball) <= KICK_FIELDING_RADIUS_YDS
        if (!canField) {
            if (playType == PlayType.PUNT) {
                val bounce = random.nextInt(-PUNT_BOUNCE_YDS, PUNT_BOUNCE_YDS + 1)
                val spot = kickLandingYardLine + bounce
                if (spot <= 0) return kickOutcome(0, "Punt bounces into the end zone. Touchback.", true, false, intendedLandingYardLine, kickLandingYardLine, KickOutcomeType.TOUCHBACK)
                val downedSpot = spot.coerceAtMost(Rules.FIELD_LENGTH_YDS - 1)
                return kickOutcome(downedSpot, "Punt bounces and is downed at the $downedSpot.", false, false, intendedLandingYardLine, kickLandingYardLine, KickOutcomeType.DOWNED)
            }
            return kickOutcome(kickLandingYardLine, "Kick downed at the $kickLandingYardLine.", false, false, intendedLandingYardLine, kickLandingYardLine, KickOutcomeType.DOWNED)
        }
        if (playType == PlayType.PUNT && random.nextFloat() < PUNT_MUFF_CHANCE) {
            val recoverer = (offense + defense).filter { it.currentPos.distance2DTo(ball) <= LOOSE_BALL_RECOVERY_RADIUS_YDS }.minByOrNull { it.currentPos.distance2DTo(ball) }
                ?: (offense + defense).minByOrNull { it.currentPos.distance2DTo(ball) }
            return kickOutcome(kickLandingYardLine, "Punt muffed and recovered!", false, false, intendedLandingYardLine, kickLandingYardLine, KickOutcomeType.MUFF_RECOVERED, recoverer?.isOffense == true)
        }
        if (playType == PlayType.PUNT && offense.any { it.currentPos.distance2DTo(ball) <= FAIR_CATCH_COVERAGE_RADIUS_YDS } && random.nextFloat() < FAIR_CATCH_CHANCE) {
            return kickOutcome(kickLandingYardLine, "Fair catch at the $kickLandingYardLine.", false, false, intendedLandingYardLine, kickLandingYardLine, KickOutcomeType.FAIR_CATCH)
        }
        returner.currentPos = ball.copy(z = 0f)
        kickReturner = returner
        ballCarrier = returner
        carrierVelocity = Vector3D(0f, 0f, 0f)
        return null
    }

    private fun tickKickReturn(tickDeltaSec: Float): PlayOutcome? {
        val returner = kickReturner ?: return null
        val start = returner.currentPos
        MovementEngine.pursue(returner, returner.currentPos.copy(y = returner.currentPos.y - direction * DOWNFIELD_TARGET_YDS), tickDeltaSec)
        carrierVelocity = Vector3D((returner.currentPos.x - start.x) / tickDeltaSec, (returner.currentPos.y - start.y) / tickDeltaSec, 0f)
        ballPosition = returner.currentPos

        activateReturnBlocking(returner, tickDeltaSec)
        for (coverPlayer in offense) {
            if (coverPlayer === kicker || isReturnBlocked(coverPlayer)) continue
            MovementEngine.intercept(coverPlayer, returner.currentPos, carrierVelocity, tickDeltaSec)
        }

        val yardLine = receivingYardLine(returner.currentPos)
        val returnYards = (yardLine - kickLandingYardLine).coerceAtLeast(0)
        if (yardLine >= Rules.FIELD_LENGTH_YDS) {
            return kickOutcome(Rules.FIELD_LENGTH_YDS, "TOUCHDOWN! $returnYards-yard kick return!", false, false, outcomeType = KickOutcomeType.RETURN_TOUCHDOWN)
        }
        if (returner.currentPos.x !in 0f..FieldGeometry.WIDTH_YDS) {
            return kickOutcome(yardLine, "Kick returned $returnYards yards out of bounds.", false, false, outcomeType = KickOutcomeType.RETURN_OUT_OF_BOUNDS)
        }
        for (coverPlayer in offense) {
            if (isReturnBlocked(coverPlayer) || coverPlayer.currentPos.distance2DTo(returner.currentPos) >= TACKLE_RADIUS_YDS) continue
            if (random.nextFloat() < tackleChance(coverPlayer, returner)) {
                if (random.nextFloat() < RETURN_FUMBLE_CHANCE) {
                    val recoverer = (offense + defense).filter { it.currentPos.distance2DTo(returner.currentPos) <= LOOSE_BALL_RECOVERY_RADIUS_YDS }
                        .minByOrNull { it.currentPos.distance2DTo(returner.currentPos) } ?: coverPlayer
                    return kickOutcome(yardLine, "Return fumble recovered!", false, false, outcomeType = KickOutcomeType.RETURN_FUMBLE_RECOVERED, recoveredByKickingTeam = recoverer.isOffense)
                }
                return kickOutcome(yardLine, "Kick returned $returnYards yards to the $yardLine.", false, false, outcomeType = KickOutcomeType.RETURN_TACKLED)
            }
            evadeTackle(returner, coverPlayer)
        }
        if (elapsedSec >= MAX_PLAY_DURATION_SEC) {
            return kickOutcome(yardLine, "Kick return ends at the $yardLine.", false, false, outcomeType = KickOutcomeType.RETURN_TACKLED)
        }
        return null
    }

    private fun activateReturnBlocking(returner: RunningPlayer, tickDeltaSec: Float) {
        for (blocker in defense) {
            if (blocker === returner) continue
            val coverPlayer = offense.filter { it !== kicker }.minByOrNull { it.currentPos.distance2DTo(blocker.currentPos) } ?: continue
            if (coverPlayer.currentPos.distance2DTo(blocker.currentPos) <= RETURN_BLOCK_CONTACT_YDS) {
                blocker.blockingId = coverPlayer.id
            } else {
                MovementEngine.pursue(blocker, coverPlayer.currentPos, tickDeltaSec)
            }
        }
    }

    private fun isReturnBlocked(player: RunningPlayer): Boolean = defense.any { it.blockingId == player.id }

    private fun receivingYardLine(position: Vector3D): Int {
        val kickingYardLine = if (snap.isAttackingUp) position.y else Rules.FIELD_LENGTH_YDS - position.y
        return (Rules.FIELD_LENGTH_YDS - kickingYardLine).toInt().coerceIn(0, Rules.FIELD_LENGTH_YDS)
    }

    private fun kickOutcome(
        endYardLine: Int,
        description: String,
        isTouchback: Boolean,
        isOutOfBounds: Boolean,
        intendedLandingYardLine: Int = receivingYardLine(intendedKickTarget ?: ballPosition!!),
        landingYardLine: Int = kickLandingYardLine,
        outcomeType: KickOutcomeType,
        recoveredByKickingTeam: Boolean = false,
    ) = PlayOutcome.Kick(
        KickResult(
            endYardLine = endYardLine,
            description = description,
            isTouchback = isTouchback,
            isOutOfBounds = isOutOfBounds,
            intendedLandingYardLine = intendedLandingYardLine,
            landingYardLine = landingYardLine,
            returnYards = (endYardLine - landingYardLine).coerceAtLeast(0),
            outcomeType = outcomeType,
            recoveredByKickingTeam = recoveredByKickingTeam,
        ),
    )

    private fun tickFieldGoal(tickDeltaSec: Float): PlayOutcome? {
        val kicker = kicker ?: return whistleDead()
        if (ballTrajectory == null) {
            val contact = holder?.currentPos ?: kicker.currentPos.copy(y = losWorldY - Rules.FIELD_GOAL_SNAP_DEPTH_YDS * direction)
            ballPosition = contact
            MovementEngine.moveToward(kicker, contact, FIELD_GOAL_APPROACH_SPEED_YDS_PER_SEC, tickDeltaSec)
            if (elapsedSec < FIELD_GOAL_HOLD_SEC) return null
            kickFieldGoal(kicker, contact)
        }

        val trajectory = ballTrajectory ?: return null
        val sinceKickSec = elapsedSec - throwTimeSec
        ballPosition = trajectory.getPositionAt(sinceKickSec)
        if (sinceKickSec < trajectory.totalFlightTimeSec) return null
        return fieldGoalResult?.let { PlayOutcome.FieldGoal(it) }
    }

    private fun kickFieldGoal(
        kicker: RunningPlayer,
        contact: Vector3D,
    ) {
        val distance = Rules.fieldGoalDistance(snap.losYardLine)
        val kickPower = kicker.attributes.kickPower
        val range = AttributeTranslator.calculateFieldGoalRangeYards(kickPower)
        val isBlocked =
            defense.any {
                    (it.role == PlayerRole.PASS_RUSHER || it.role == PlayerRole.BLITZER) &&
                    !isBlocked(it) &&
                    it.currentPos.distance2DTo(contact) <= KICK_BLOCK_RADIUS_YDS
            }
        val isGood =
            !isBlocked && random.nextFloat() < AttributeTranslator.calculateFieldGoalMakeChance(distance, kickPower, kicker.attributes.kickAccuracy)
        val postsY = FieldGeometry.goalPostsWorldY(snap.isAttackingUp)
        val overCrossbar = CROSSBAR_HEIGHT_YDS + 2f

        val (target, description) =
            when {
                isBlocked -> {
                    contact.copy(y = contact.y + 3f * direction, z = 0f) to "The $distance-yard kick is BLOCKED!"
                }

                isGood -> {
                    Vector3D(FieldGeometry.CENTER_X, postsY, overCrossbar) to "The $distance-yard kick is GOOD!"
                }

                distance > range -> {
                    Vector3D(FieldGeometry.CENTER_X, contact.y + range * direction, 0f) to "The $distance-yard kick falls short."
                }

                else -> {
                    val side = if (random.nextBoolean()) 1f else -1f
                    val missX = FieldGeometry.CENTER_X + side * (GOAL_POSTS_HALF_WIDTH_YDS + 1f + random.nextFloat() * 3f)
                    // +x is the offense's right when attacking up
                    val sideName = if (side * direction > 0) "right" else "left"
                    Vector3D(missX, postsY, overCrossbar) to "The $distance-yard kick is wide $sideName."
                }
            }

        fieldGoalResult = FieldGoalResult(isGood, distance, description)
        throwTimeSec = elapsedSec
        ballTrajectory =
            BallTrajectory(
                startPos = contact,
                targetPos = target,
                totalFlightTimeSec = (contact.distance2DTo(target) / KICK_SPEED_YDS_PER_SEC).coerceAtLeast(MIN_KICK_FLIGHT_SEC),
                apexHeightYards = if (isBlocked) 1f else FIELD_GOAL_APEX_YDS,
            )
    }

    private fun tickRun(): PlayOutcome? {
        val carrier = ballCarrier ?: return whistleDead()
        return tickBallCarrier(carrier, isCatch = false)
    }

    /** Follows the ball carrier until they score or are tackled. */
    private fun tickBallCarrier(
        carrier: RunningPlayer,
        isCatch: Boolean,
    ): PlayOutcome? {
        ballPosition = carrier.currentPos
        val yardsGained = yardsFromLos(carrier.currentPos.y)

        if (snap.losYardLine + yardsGained >= Rules.FIELD_LENGTH_YDS) {
            val touchdownYards = Rules.FIELD_LENGTH_YDS - snap.losYardLine
            val description = if (isCatch) "TOUCHDOWN! $touchdownYards-yard catch and run!" else "TOUCHDOWN! $touchdownYards-yard run!"
            return touchdown(yardsGained, withBrokenTackles(description))
        }

        for (defender in defense) {
            // A defender tied up in a block can still grab a runner who comes through their gap, but less reliably
            val isEngaged = isBlocked(defender)
            val reach = if (isEngaged) ENGAGED_TACKLE_RADIUS_YDS else TACKLE_RADIUS_YDS
            if (carrier.currentPos.distance2DTo(defender.currentPos) >= reach) continue
            if ((recoveringUntilSec[defender.id] ?: 0f) > elapsedSec) continue

            val chance = tackleChance(defender, carrier) * (if (isEngaged) ENGAGED_TACKLE_FACTOR else 1f)
            if (random.nextFloat() < chance) {
                val description = withBrokenTackles(if (isCatch) "Pass complete for $yardsGained yards!" else "Run for $yardsGained yards.")
                return fumbleOrNull(carrier, defender, yardsGained, FUMBLE_CHANCE)
                    ?: PlayOutcome.Scrimmage(PlayResult(yardsGained, description, isTouchdown = false, isTurnover = false))
            }
            evadeTackle(carrier, defender)
        }
        return null
    }

    /** Better tacklers bring the carrier down more often; shifty or powerful carriers slip more tackles. */
    private fun tackleChance(
        tackler: RunningPlayer,
        carrier: RunningPlayer,
    ): Float {
        val evasion = maxOf(agility(carrier), carrier.attributes.strength)
        return (BASE_TACKLE_CHANCE + (tackler.attributes.tackle - evasion) / TACKLE_RATING_SCALE).coerceIn(MIN_TACKLE_CHANCE, MAX_TACKLE_CHANCE)
    }

    /** The carrier makes the tackler miss: a cut away from them, while they take a moment to recover. */
    private fun evadeTackle(
        carrier: RunningPlayer,
        tackler: RunningPlayer,
    ) {
        recoveringUntilSec[tackler.id] = elapsedSec + MISSED_TACKLE_RECOVERY_SEC
        brokenTackles++
        if (firstEvasiveMove == null) {
            firstEvasiveMove =
                when {
                    carrier.attributes.strength > agility(carrier) -> "stiff arm"
                    random.nextBoolean() -> "juke"
                    else -> "spin move"
                }
        }
        val awayFromTackler = if (carrier.currentPos.x >= tackler.currentPos.x) 1f else -1f
        carrier.currentPos =
            carrier.currentPos.copy(
                x = (carrier.currentPos.x + awayFromTackler * EVASION_CUT_YDS).coerceIn(0f, FieldGeometry.WIDTH_YDS),
            )
    }

    private fun agility(player: RunningPlayer) = (player.attributes.speed + player.attributes.acceleration) / 2

    private fun withBrokenTackles(description: String): String =
        when (brokenTackles) {
            0 -> description
            1 -> "${description.dropLast(1)} after a $firstEvasiveMove!"
            else -> "${description.dropLast(1)}, breaking $brokenTackles tackles!"
        }

    private fun tickPass(tickDeltaSec: Float): PlayOutcome? {
        ballCarrier?.let { return tickBallCarrier(it, isCatch = true) }

        val qb = passer ?: return whistleDead()
        val brain = qbBrain ?: return whistleDead()

        if (ballTrajectory == null) {
            ballPosition = qb.currentPos
            brain.evaluateTick(defense.filterNot(::isBlocked), tickDeltaSec)?.let { throwCommand ->
                if (throwCommand.isThrowAway) return incomplete("Nobody open, the pass is thrown away.")
                ballTrajectory = withThrowError(throwCommand.trajectory, qb.attributes.throwAccuracy)
                throwTimeSec = elapsedSec
                targetReceiver = playersById[throwCommand.targetId]
                initializeDefenderTracking(qb, targetReceiver)
            }

            if (brain.state == QbState.SACKED) {
                val yardsLost = (-yardsFromLos(qb.currentPos.y)).coerceAtLeast(0)
                val sacker = defense.minByOrNull { it.currentPos.distance2DTo(qb.currentPos) } ?: return whistleDead()
                return fumbleOrNull(qb, sacker, -yardsLost, STRIP_SACK_CHANCE)
                    ?: PlayOutcome.Scrimmage(
                        PlayResult(-yardsLost, "QB is SACKED for a loss of $yardsLost yards!", isTouchdown = false, isTurnover = false),
                    )
            }
        }

        val trajectory = ballTrajectory ?: return null
        val sinceThrowSec = elapsedSec - throwTimeSec
        val ball = trajectory.getPositionAt(sinceThrowSec)
        ballPosition = ball

        // A low throw through a defender's lane on the way can be picked off
        if (sinceThrowSec < trajectory.totalFlightTimeSec * LANE_PORTION_OF_FLIGHT) {
            val undercut =
                defense.firstOrNull {
                    ball.distance2DTo(
                        it.currentPos,
                    ) <= DEFENDER_REACH_RADIUS_YDS &&
                        ball.z <= it.verticalReach.maxCatchHeightYards
                }
            if (undercut != null) {
                return if (random.nextBoolean()) interception(ball) else incomplete("Pass batted down!")
            }
        }

        if (sinceThrowSec < trajectory.totalFlightTimeSec) return null

        val receiver = targetReceiver ?: return incomplete("Incomplete pass.")
        val yardsGained = yardsFromLos(ball.y)
        val catchYardLine = snap.losYardLine + yardsGained
        val isCatchable =
            ball.distance2DTo(receiver.currentPos) < CATCH_RADIUS_YDS &&
                ball.x in 0f..FieldGeometry.WIDTH_YDS &&
                catchYardLine <= Rules.FIELD_LENGTH_YDS + Rules.END_ZONE_DEPTH_YDS
        if (!isCatchable) return incomplete("Incomplete pass.")

        // At the catch point the closest defender contests: the tighter the coverage, the likelier a pass breakup or pick
        val nearestDefenderYds = defense.minOfOrNull { it.currentPos.distance2DTo(ball) } ?: Float.MAX_VALUE
        val contest = (1f - nearestDefenderYds / CONTEST_RADIUS_YDS).coerceIn(0f, 1f)
        val roll = random.nextFloat()
        when {
            roll < contest * CONTESTED_INTERCEPTION_CHANCE -> return interception(ball)
            roll < contest * (CONTESTED_INTERCEPTION_CHANCE + CONTESTED_BREAKUP_CHANCE) -> return incomplete("Pass broken up!")
        }
        val catchChance = BASE_CATCH_CHANCE + (receiver.attributes.catching - 75) / 300f
        if (random.nextFloat() >= catchChance) return incomplete("Dropped pass!")

        if (catchYardLine >= Rules.FIELD_LENGTH_YDS) return touchdown(yardsGained, "TOUCHDOWN! Pass complete in the end zone!")

        // Caught: the receiver turns upfield and everyone chases
        receiver.currentPos = ball.copy(z = 0f)
        ballCarrier = receiver
        return null
    }

    private fun updateGazes(tickDeltaSec: Float) {
        val qb = passer
        if (qb != null) {
            val qbGaze = gazeById[qb.id]
            val target = qbBrain?.gazeTargetId?.let(playersById::get)
            qbGaze?.targetId = target?.id
            target?.let { qbGaze?.turnToward(relativeVector(qb.currentPos, it.currentPos), QB_TURN_RATE_DEG_PER_SEC, tickDeltaSec) }
        }
        for (defender in defense) {
            val gaze = gazeById[defender.id] ?: continue
            val target =
                when (defender.role) {
                    PlayerRole.MAN_COVERAGE -> defender.coverageTargetId?.let(playersById::get)
                    PlayerRole.ZONE_COVERAGE -> passer
                    else -> null
                }
            gaze.targetId = target?.id
            target?.let { gaze.turnToward(relativeVector(defender.currentPos, it.currentPos), DB_TURN_RATE_DEG_PER_SEC, tickDeltaSec) }
        }
    }

    private fun initializeDefenderTracking(
        qb: RunningPlayer,
        receiver: RunningPlayer?,
    ) {
        if (receiver == null) return
        val blockers = players.map { it.id to it.currentPos }
        for (defender in defense.filter { it.role == PlayerRole.MAN_COVERAGE || it.role == PlayerRole.ZONE_COVERAGE }) {
            val gaze = gazeById[defender.id] ?: continue
            val receiverVisible =
                VisionEngine
                    .inspect(
                        defender.currentPos,
                        gaze.facing,
                        receiver.currentPos,
                        DB_FOV_DEGREES,
                        blockers,
                        defender.id,
                        receiver.id,
                    ).visible
            val qbVisible =
                VisionEngine
                    .inspect(
                        defender.currentPos,
                        gaze.facing,
                        qb.currentPos,
                        DB_FOV_DEGREES,
                        blockers,
                        defender.id,
                        qb.id,
                    ).visible
            val ballVisible =
                ballTrajectory?.let {
                    VisionEngine
                        .inspect(
                            defender.currentPos,
                            gaze.facing,
                            it.startPos,
                            DB_FOV_DEGREES,
                            blockers,
                            defender.id,
                        ).visible
                } == true
            val awareness = defender.attributes.awareness.coerceIn(0, 100)
            val receiverInference = receiverVisible && random.nextFloat() < awarenessProbability(awareness)
            val qbInference = qbVisible && random.nextFloat() < awarenessProbability(awareness)
            if (receiverInference || qbInference || ballVisible) {
                val recognitionDelay = if (ballVisible) 0f else recognitionDelaySec(awareness)
                defenderTracking[defender.id] =
                    DefenderTrackingState(
                        recognitionTimeSec = elapsedSec + recognitionDelay,
                        ballReactionTimeSec = elapsedSec + recognitionDelay + ballTurnDelaySec(awareness),
                        hasLocatedBall = ballVisible,
                    )
            }
        }
    }

    private fun canBreakOnBall(defender: RunningPlayer): Boolean {
        var tracking = defenderTracking[defender.id]
        if (tracking == null && ballPosition != null) {
            val gaze = gazeById[defender.id]
            val seesBall =
                gaze?.let {
                    VisionEngine
                        .inspect(
                            defender.currentPos,
                            it.facing,
                            ballPosition!!,
                            DB_FOV_DEGREES,
                            players.map { player -> player.id to player.currentPos },
                            defender.id,
                        ).visible
                } == true
            if (seesBall) {
                val awareness = defender.attributes.awareness.coerceIn(0, 100)
                tracking = DefenderTrackingState(elapsedSec, elapsedSec + ballTurnDelaySec(awareness), true)
                defenderTracking[defender.id] = tracking
            }
        }
        tracking ?: return false
        if (!tracking.hasLocatedBall && elapsedSec >= tracking.ballReactionTimeSec) tracking.hasLocatedBall = true
        return tracking.hasLocatedBall && elapsedSec >= tracking.ballReactionTimeSec
    }

    private fun initialGaze(player: RunningPlayer): GazeState {
        val target =
            when (player.role) {
                PlayerRole.MAN_COVERAGE -> player.coverageTargetId?.let(playersById::get)?.currentPos
                PlayerRole.ZONE_COVERAGE -> player.zoneLandmark
                else -> null
            }
        val facing = target?.let { relativeVector(player.currentPos, it) } ?: Vector3D(0f, direction, 0f)
        return GazeState(facing, target?.let { playersById.entries.firstOrNull { entry -> entry.value.currentPos == it }?.key })
    }

    private fun relativeVector(
        from: Vector3D,
        to: Vector3D,
    ) = Vector3D(to.x - from.x, to.y - from.y, 0f)

    private fun awarenessProbability(awareness: Int) = (0.25f + awareness / 150f).coerceIn(0.25f, 0.92f)

    private fun recognitionDelaySec(awareness: Int) = 0.35f - awareness / 400f

    private fun ballTurnDelaySec(awareness: Int) = 0.35f - awareness / 500f

    private data class DefenderTrackingState(
        val recognitionTimeSec: Float,
        val ballReactionTimeSec: Float,
        var hasLocatedBall: Boolean,
    )

    private fun interception(ball: Vector3D) =
        PlayOutcome.Scrimmage(PlayResult(yardsFromLos(ball.y), "INTERCEPTED!", isTouchdown = false, isTurnover = true, clockStops = true))

    /**
     * The ball comes loose with a chance scaled by the hitter's tackling against the ball carrier's strength.
     * Returns null if the carrier holds on.
     */
    private fun fumbleOrNull(
        carrier: RunningPlayer,
        hitter: RunningPlayer,
        yardsGained: Int,
        baseChance: Float,
    ): PlayOutcome? {
        val chance = baseChance * hitter.attributes.tackle / carrier.attributes.strength.coerceAtLeast(1)
        if (random.nextFloat() >= chance) return null

        return if (random.nextBoolean()) {
            PlayOutcome.Scrimmage(PlayResult(yardsGained, "FUMBLE! Recovered by the defense!", isTouchdown = false, isTurnover = true, clockStops = true))
        } else {
            PlayOutcome.Scrimmage(PlayResult(yardsGained, "Fumble, but the offense falls on it.", isTouchdown = false, isTurnover = false))
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

    private fun isBlocked(defender: RunningPlayer) = blocking.isBlocked(defender)

    private fun yardsFromLos(worldY: Float): Int = ((worldY - losWorldY) * direction).toInt()

    private fun touchdown(
        yardsGained: Int,
        description: String,
    ) = PlayOutcome.Scrimmage(PlayResult(yardsGained, description, isTouchdown = true, isTurnover = false, clockStops = true))

    private fun incomplete(description: String) = PlayOutcome.Scrimmage(PlayResult(0, description, isTouchdown = false, isTurnover = false, clockStops = true))

    private fun whistleDead() = PlayOutcome.Scrimmage(PlayResult(0, "Play whistled dead.", isTouchdown = false, isTurnover = false, clockStops = true))

    companion object {
        const val MAX_PLAY_DURATION_SEC = 15f // Safety timeout
        private const val KICK_APEX_YDS = 30f
        private const val PUNT_BLOCK_RADIUS_YDS = 1.5f
        private const val KICK_FIELDING_RADIUS_YDS = 12f
        private const val PUNT_BOUNCE_YDS = 9
        private const val LOOSE_BALL_RECOVERY_RADIUS_YDS = 5f
        private const val FAIR_CATCH_COVERAGE_RADIUS_YDS = 12f
        private const val FAIR_CATCH_CHANCE = 0.75f
        private const val PUNT_MUFF_CHANCE = 0.04f
        private const val RETURN_FUMBLE_CHANCE = 0.015f
        private const val KICK_OPERATION_SEC = 0.65f
        private const val KICK_APPROACH_YDS = 4f
        private const val KICK_COVERAGE_SPRINT_YDS = 80f
        private const val RETURN_BLOCK_CONTACT_YDS = 1.2f
        private const val TACKLE_RADIUS_YDS = 1.6f
        private const val BASE_TACKLE_CHANCE = 0.88f
        private const val ENGAGED_TACKLE_RADIUS_YDS = 1.2f
        private const val ENGAGED_TACKLE_FACTOR = 0.25f
        private const val TACKLE_RATING_SCALE = 100f
        private const val MIN_TACKLE_CHANCE = 0.5f
        private const val MAX_TACKLE_CHANCE = 0.97f
        private const val MISSED_TACKLE_RECOVERY_SEC = 0.7f
        private const val EVASION_CUT_YDS = 0.8f
        private const val CATCH_RADIUS_YDS = 2.5f
        private const val DEFENDER_REACH_RADIUS_YDS = 0.7f
        private const val RUN_READ_DELAY_SEC = 0.8f
        private const val COVERAGE_CUSHION_YDS = 2f
        private const val MAN_TRAIL_YDS = 0.5f
        private const val DOWNFIELD_TARGET_YDS = 200f
        private const val BASE_CATCH_CHANCE = 0.93f
        private const val LANE_PORTION_OF_FLIGHT = 0.7f
        private const val CONTEST_RADIUS_YDS = 2f
        private const val CONTESTED_INTERCEPTION_CHANCE = 0.08f
        private const val CONTESTED_BREAKUP_CHANCE = 0.45f
        private const val ZONE_RADIUS_YDS = 7f
        private const val DEEP_ZONE_MIN_DEPTH_YDS = 12f
        private const val DEEP_ZONE_HALF_WIDTH_YDS = 12f
        private const val DEEP_ZONE_SHADE = 0.6f
        private const val DEEP_CUSHION_YDS = 3f
        private const val DB_FOV_DEGREES = 180f
        private const val QB_TURN_RATE_DEG_PER_SEC = 360f
        private const val DB_TURN_RATE_DEG_PER_SEC = 540f
        private const val BALL_BREAK_RADIUS_YDS = 15f
        private const val FUMBLE_CHANCE = 0.012f
        private const val STRIP_SACK_CHANCE = 0.1f
        private const val FIELD_GOAL_HOLD_SEC = 1.3f
        private const val FIELD_GOAL_APPROACH_SPEED_YDS_PER_SEC = 5f
        private const val FIELD_GOAL_APEX_YDS = 10f
        private const val KICK_SPEED_YDS_PER_SEC = 25f
        private const val MIN_KICK_FLIGHT_SEC = 0.4f
        private const val KICK_BLOCK_RADIUS_YDS = 1.5f
        private const val CROSSBAR_HEIGHT_YDS = 3.33f
        private const val GOAL_POSTS_HALF_WIDTH_YDS = 3.08f
    }

    private enum class KickPhase {
        OPERATION,
        FLIGHT,
        RETURN,
    }
}
