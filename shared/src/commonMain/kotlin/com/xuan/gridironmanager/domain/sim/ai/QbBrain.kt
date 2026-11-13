package com.xuan.gridironmanager.domain.sim.ai

import com.xuan.gridironmanager.domain.sim.AttributeTranslator
import com.xuan.gridironmanager.domain.sim.BallTrajectory
import com.xuan.gridironmanager.domain.sim.match.PlayDebugTrace
import com.xuan.gridironmanager.domain.sim.match.at
import com.xuan.gridironmanager.domain.sim.match.fixed
import com.xuan.gridironmanager.domain.sim.movement.MovementEngine
import com.xuan.gridironmanager.domain.sim.movement.RunningPlayer
import kotlin.math.max

enum class QbState {
    DROPPING_BACK,
    READING_PROGRESSIONS,
    WINDUP,
    THROWING,
    SACKED,
}

data class ThrowCommand(
    val targetId: String,
    val trajectory: BallTrajectory,
    /** The QB saw nothing worth throwing at and threw the ball away. */
    val isThrowAway: Boolean = false,
)

/**
 * Quarterback decision making. After the dropback the QB works through [progressions] in order and throws to the
 * first receiver who will be open when the ball arrives. The longer the QB holds the ball, the tighter a window they
 * accept, until at [forceThrowSec] they take the best option available or throw it away.
 */
class QbBrain(
    val qb: RunningPlayer,
    /** Eligible receivers, in read order. The last is the checkdown. */
    val progressions: List<RunningPlayer>,
    private val dropbackSec: Float = DEFAULT_DROPBACK_SEC,
    private val forceThrowSec: Float = DEFAULT_FORCE_THROW_SEC,
    var state: QbState = QbState.DROPPING_BACK,
    /**
     * Read time lost to a coverage disguise the QB failed to see through. The first read lands this much later and
     * every read after it stays this far behind; [forceThrowSec] and the shrinking window do not move.
     */
    private val readDelaySec: Float = 0f,
    /** When a fooled QB re-identifies the true coverage (seconds after the snap). Only affects [isFooled]. */
    private val recoverAtSec: Float = 0f,
    /** Diagnostic trace of this play's decisions, or null (the default) when debugging is off. Never read back. */
    private val trace: PlayDebugTrace? = null,
) {
    private var elapsedSec = 0f

    // The pending throw during a windup: the decision is made, the ball has not left yet.
    private var pendingThrow: ThrowCommand? = null
    private var windupRemainingSec = 0f
    private var pendingThrowLog: String? = null

    // Diagnostic bookkeeping only, so each event is traced once. Nothing here influences a decision.
    private var readsBeganTraced = false
    private var forceTimeTraced = false
    private val pressureTraced = mutableSetOf<String>()
    private val readsTraced = mutableSetOf<Int>()

    private fun label(player: RunningPlayer) = player.slot ?: player.id

    private inline fun log(text: () -> String) {
        trace?.at(elapsedSec, text())
    }

    /** True while the QB is still reading the shell rather than the rotated coverage. */
    val isFooled: Boolean get() = readDelaySec > 0f && elapsedSec < recoverAtSec

    /** The progression currently in the QB's visual read, if any. */
    var gazeTargetId: String? = null
        private set

    fun evaluateTick(
        defenders: List<RunningPlayer>,
        tickDeltaSec: Float,
    ): ThrowCommand? {
        if (state == QbState.THROWING || state == QbState.SACKED) return null
        elapsedSec += tickDeltaSec

        if (trace != null) {
            for (defender in defenders) {
                val distance = qb.currentPos.distance2DTo(defender.currentPos)
                if (distance <= PRESSURE_RADIUS_YDS && pressureTraced.add(defender.id)) log { "pressure: ${label(defender)} at ${distance.fixed(1)} yd" }
            }
        }

        // Check Pressure
        if (defenders.any { qb.currentPos.distance2DTo(it.currentPos) <= SACK_RADIUS_YDS }) {
            state = QbState.SACKED
            pendingThrow = null
            pendingThrowLog = null
            defenders.minByOrNull { qb.currentPos.distance2DTo(it.currentPos) }?.let { sacker ->
                log { "SACKED by ${label(sacker)} (${qb.currentPos.distance2DTo(sacker.currentPos).fixed(1)} yd)" }
            }
            return null
        }

        // Committed to the throw: the timer runs out and the stored command is released. A sack above
        // already ended the play, so nothing here can change the read or switch to a throwaway.
        if (state == QbState.WINDUP) {
            windupRemainingSec -= tickDeltaSec
            if (windupRemainingSec <= WINDUP_DONE_EPSILON_SEC) {
                val command = pendingThrow
                pendingThrow = null
                state = QbState.THROWING
                pendingThrowLog?.let { text -> log { text } }
                pendingThrowLog = null
                return command
            }
            return null
        }

        if (elapsedSec < dropbackSec) {
            state = QbState.DROPPING_BACK
            gazeTargetId = null
            return null
        }
        state = QbState.READING_PROGRESSIONS
        if (!readsBeganTraced) {
            readsBeganTraced = true
            log { "reads begin" }
        }
        if (elapsedSec >= forceThrowSec && !forceTimeTraced) {
            forceTimeTraced = true
            log { "force-throw time reached" }
        }

        // A fooled QB's reads start late: until then they are still looking at the play's first read
        val readsSoFar = if (elapsedSec < dropbackSec + readDelaySec) 0 else 1 + ((elapsedSec - dropbackSec - readDelaySec) / READ_TIME_SEC).toInt()
        gazeTargetId = progressions.getOrNull((readsSoFar - 1).coerceIn(0, progressions.lastIndex.coerceAtLeast(0)))?.id

        // Receivers beyond the QB's arm are unreadable. They keep their place in the read order (so the cadence of reads
        // doesn't shift) but are never the open read, and never the best-available read at force-throw.
        // Honest note: generated QBs have 80-99 throwPower (55-60 yd range) and the longest route is 35 yards, so range
        // almost never binds on generated rosters. Velocity is the real effect; range guards edited or custom rosters.
        val maxRangeYds = AttributeTranslator.calculateMaxThrowDistanceYds(qb.attributes.throwPower)
        val reads =
            progressions.map { receiver ->
                val trajectory = planThrow(receiver)
                Read(receiver, trajectory, separationAtCatch(trajectory, defenders), inRange = qb.currentPos.distance2DTo(receiver.currentPos) <= maxRangeYds)
            }
        val isPressured = defenders.any { qb.currentPos.distance2DTo(it.currentPos) <= PRESSURE_RADIUS_YDS }
        val isOutOfTime = elapsedSec >= forceThrowSec || isPressured

        // Each read is traced once, the tick the QB gets to it: what the separation was and whether it met the window
        if (trace != null) {
            for (index in 0 until minOf(readsSoFar, reads.size)) {
                if (!readsTraced.add(index)) continue
                val candidate = reads[index]
                val isOpen = candidate.inRange && candidate.separationYds >= requiredSeparation()
                log { "read ${index + 1}: ${label(candidate.receiver)} sep ${candidate.separationYds.fixed(1)} yd → ${if (isOpen) "OPEN" else "NOT OPEN"}" }
            }
        }

        // One read at a time: the QB gets to the next receiver every READ_TIME_SEC, so the checkdown comes last
        val openRead = reads.take(readsSoFar).firstOrNull { it.inRange && it.separationYds >= requiredSeparation() }
        val read = openRead ?: (if (isOutOfTime) reads.filter { it.inRange }.maxByOrNull { it.separationYds } else null) ?: return throwAwayIfOutOfTime(isOutOfTime, reads)

        val command = ThrowCommand(read.receiver.id, read.trajectory)
        if (isOutOfTime && read.separationYds < MIN_THROWABLE_SEPARATION_YDS) {
            beginWindup(
                command.copy(isThrowAway = true),
                THROWAWAY_WINDUP_SEC,
                "throwaway (${if (isPressured) "forced by pressure" else "force-throw time"})",
                read.receiver,
            )
            return null
        }
        val speed = AttributeTranslator.calculatePassSpeedYdsPerSec(qb.attributes.throwPower)
        beginWindup(
            command,
            WINDUP_SEC,
            "throw → ${label(read.receiver)} sep ${read.separationYds.fixed(1)} yd, speed ${speed.fixed(1)} yd/s",
            read.receiver,
        )
        return null
    }

    /**
     * A throw decision starts the windup: the QB is committed, the ball leaves when the timer runs out, and the
     * `throw →` / `throwaway (...)` line fires when the command is returned, not at the decision.
     */
    private fun beginWindup(
        command: ThrowCommand,
        durationSec: Float,
        logText: String,
        receiver: RunningPlayer,
    ) {
        pendingThrow = command
        windupRemainingSec = durationSec
        pendingThrowLog = logText
        state = QbState.WINDUP
        log { "windup ${durationSec.fixed(2)} s → ${label(receiver)}" }
    }

    /** Out of time with nobody in range: throw it away at the first read, as a QB with nobody open does. */
    private fun throwAwayIfOutOfTime(
        isOutOfTime: Boolean,
        reads: List<Read>,
    ): ThrowCommand? {
        val first = reads.firstOrNull() ?: return null
        if (!isOutOfTime) return null
        beginWindup(
            ThrowCommand(first.receiver.id, first.trajectory, isThrowAway = true),
            THROWAWAY_WINDUP_SEC,
            "throwaway (no receiver in range)",
            first.receiver,
        )
        return null
    }

    private data class Read(
        val receiver: RunningPlayer,
        val trajectory: BallTrajectory,
        val separationYds: Float,
        val inRange: Boolean = true,
    )

    /** The window the QB needs: wide open early in the down, narrowing to a tight window by [forceThrowSec]. */
    private fun requiredSeparation(): Float {
        val progress = ((elapsedSec - dropbackSec) / (forceThrowSec - dropbackSec)).coerceIn(0f, 1f)
        return OPEN_EARLY_SEPARATION_YDS + (OPEN_LATE_SEPARATION_YDS - OPEN_EARLY_SEPARATION_YDS) * progress
    }

    /**
     * How far the nearest defender will still be from the catch point when the ball arrives, assuming they react
     * and sprint straight to it.
     */
    fun separationAtCatch(
        trajectory: BallTrajectory,
        defenders: List<RunningPlayer>,
    ): Float {
        val closingTimeSec = max(0f, trajectory.totalFlightTimeSec - DEFENDER_REACTION_SEC)
        return defenders.minOfOrNull { defender ->
            max(0f, defender.currentPos.distance2DTo(trajectory.targetPos) - defender.speedYdsPerSec * closingTimeSec)
        } ?: Float.MAX_VALUE
    }

    /** Leads [receiver] to where they will be on their route when the ball arrives. */
    fun planThrow(receiver: RunningPlayer): BallTrajectory {
        val passSpeedYdsPerSec = AttributeTranslator.calculatePassSpeedYdsPerSec(qb.attributes.throwPower)
        val flightTimeSec = (qb.currentPos.distance2DTo(receiver.currentPos) / passSpeedYdsPerSec).coerceAtLeast(MIN_FLIGHT_SEC)
        val leadPos = MovementEngine.predictPosition(receiver, flightTimeSec)

        return BallTrajectory(
            startPos = qb.currentPos.copy(z = RELEASE_HEIGHT_YDS),
            targetPos = leadPos.copy(z = CATCH_HEIGHT_YDS),
            totalFlightTimeSec = flightTimeSec,
            apexHeightYards = PASS_APEX_YDS,
        )
    }

    companion object {
        const val DEFAULT_DROPBACK_SEC = 0.6f
        const val DEFAULT_FORCE_THROW_SEC = 2.6f
        const val SACK_RADIUS_YDS = 1.5f
        const val PRESSURE_RADIUS_YDS = 3f
        /** Time between the throw decision and the release for a normal throw (§3). */
        const val WINDUP_SEC = 0.15f
        /** Time between the throw decision and the release for a throwaway (§3). */
        const val THROWAWAY_WINDUP_SEC = 0.10f
        private const val WINDUP_DONE_EPSILON_SEC = 0.001f
        private const val READ_TIME_SEC = 0.4f
        private const val OPEN_EARLY_SEPARATION_YDS = 3f
        private const val OPEN_LATE_SEPARATION_YDS = 1.5f
        private const val MIN_THROWABLE_SEPARATION_YDS = 0.3f
        private const val DEFENDER_REACTION_SEC = 0.3f
        private const val MIN_FLIGHT_SEC = 0.1f
        private const val RELEASE_HEIGHT_YDS = 2.0f
        private const val CATCH_HEIGHT_YDS = 1.2f
        private const val PASS_APEX_YDS = 3f
    }
}
