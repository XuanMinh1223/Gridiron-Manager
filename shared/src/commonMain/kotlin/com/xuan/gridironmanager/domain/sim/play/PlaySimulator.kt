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

    private val blocking = BlockingModel(offense, defense, playType, random)

    private var ballTrajectory: BallTrajectory? = null
    private var throwTimeSec = 0f
    private var targetReceiver: RunningPlayer? = null
    private var fieldGoalResult: FieldGoalResult? = null
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
            ballTrajectory =
                BallTrajectory(
                    startPos = kicker.currentPos,
                    targetPos = kicker.currentPos.copy(y = kicker.currentPos.y + kickDistance * direction, z = 0f),
                    totalFlightTimeSec = AttributeTranslator.calculateHangtimeSec(kicker.attributes.kickPower),
                    apexHeightYards = KICK_APEX_YDS,
                )
        }
    }

    fun tick(tickDeltaSec: Float): PlayOutcome? {
        elapsedSec += tickDeltaSec

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
                PlayType.KICKOFF, PlayType.PUNT -> tickKick()
                PlayType.FIELD_GOAL -> tickFieldGoal()
                PlayType.RUN -> tickRun()
                PlayType.PASS -> tickPass(tickDeltaSec)
            }

        return outcome ?: if (elapsedSec >= MAX_PLAY_DURATION_SEC) whistleDead() else null
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

    private fun tickKick(): PlayOutcome? {
        val trajectory = ballTrajectory ?: return null
        val ball = trajectory.getPositionAt(elapsedSec)
        ballPosition = ball
        if (elapsedSec < trajectory.totalFlightTimeSec) return null

        // Yard line from the kicking team's perspective (0-100)
        val kickingYardLine = if (snap.isAttackingUp) ball.y else Rules.FIELD_LENGTH_YDS - ball.y
        // Yard line for the receiving team (distance from their own goal)
        val receivingYardLine = (Rules.FIELD_LENGTH_YDS - kickingYardLine).toInt()

        val result =
            when {
                kickingYardLine >= Rules.FIELD_LENGTH_YDS -> {
                    KickResult(endYardLine = 0, description = "Touchback.", isTouchback = true, isOutOfBounds = false)
                }

                ball.x < 0 || ball.x > FieldGeometry.WIDTH_YDS -> {
                    KickResult(
                        endYardLine = if (playType == PlayType.KICKOFF) Rules.KICKOFF_OUT_OF_BOUNDS_YARD_LINE else receivingYardLine,
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

    private fun tickFieldGoal(): PlayOutcome? {
        val kicker = kicker ?: return whistleDead()
        if (ballTrajectory == null) {
            ballPosition = kicker.currentPos
            if (elapsedSec < FIELD_GOAL_HOLD_SEC) return null
            kickFieldGoal(kicker)
        }

        val trajectory = ballTrajectory ?: return null
        val sinceKickSec = elapsedSec - throwTimeSec
        ballPosition = trajectory.getPositionAt(sinceKickSec)
        if (sinceKickSec < trajectory.totalFlightTimeSec) return null
        return fieldGoalResult?.let { PlayOutcome.FieldGoal(it) }
    }

    private fun kickFieldGoal(kicker: RunningPlayer) {
        val distance = Rules.fieldGoalDistance(snap.losYardLine)
        val kickPower = kicker.attributes.kickPower
        val range = AttributeTranslator.calculateFieldGoalRangeYards(kickPower)
        val isBlocked =
            defense.any {
                (it.role == PlayerRole.PASS_RUSHER || it.role == PlayerRole.BLITZER) &&
                    !isBlocked(it) &&
                    it.currentPos.distance2DTo(kicker.currentPos) <= KICK_BLOCK_RADIUS_YDS
            }
        val isGood =
            !isBlocked && random.nextFloat() < AttributeTranslator.calculateFieldGoalMakeChance(distance, kickPower, kicker.attributes.kickAccuracy)
        val postsY = FieldGeometry.goalPostsWorldY(snap.isAttackingUp)
        val overCrossbar = CROSSBAR_HEIGHT_YDS + 2f

        val (target, description) =
            when {
                isBlocked -> {
                    kicker.currentPos.copy(y = kicker.currentPos.y + 3f * direction, z = 0f) to "The $distance-yard kick is BLOCKED!"
                }

                isGood -> {
                    Vector3D(FieldGeometry.CENTER_X, postsY, overCrossbar) to "The $distance-yard kick is GOOD!"
                }

                distance > range -> {
                    Vector3D(FieldGeometry.CENTER_X, kicker.currentPos.y + range * direction, 0f) to "The $distance-yard kick falls short."
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
                startPos = kicker.currentPos,
                targetPos = target,
                totalFlightTimeSec = (kicker.currentPos.distance2DTo(target) / KICK_SPEED_YDS_PER_SEC).coerceAtLeast(MIN_KICK_FLIGHT_SEC),
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
        private const val SIMPLIFIED_RETURN_YDS = 15
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
        private const val FIELD_GOAL_APEX_YDS = 10f
        private const val KICK_SPEED_YDS_PER_SEC = 25f
        private const val MIN_KICK_FLIGHT_SEC = 0.4f
        private const val KICK_BLOCK_RADIUS_YDS = 1.5f
        private const val CROSSBAR_HEIGHT_YDS = 3.33f
        private const val GOAL_POSTS_HALF_WIDTH_YDS = 3.08f
    }
}
