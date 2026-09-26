package com.xuan.gridironmanager.domain.sim.ai

import com.xuan.gridironmanager.domain.sim.BallTrajectory
import com.xuan.gridironmanager.domain.sim.movement.MovementEngine
import com.xuan.gridironmanager.domain.sim.movement.RunningPlayer
import kotlin.math.max

enum class QbState {
    DROPPING_BACK,
    READING_PROGRESSIONS,
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
) {
    private var elapsedSec = 0f

    /** The progression currently in the QB's visual read, if any. */
    var gazeTargetId: String? = null
        private set

    fun evaluateTick(
        defenders: List<RunningPlayer>,
        tickDeltaSec: Float,
    ): ThrowCommand? {
        if (state == QbState.THROWING || state == QbState.SACKED) return null
        elapsedSec += tickDeltaSec

        // Check Pressure
        if (defenders.any { qb.currentPos.distance2DTo(it.currentPos) <= SACK_RADIUS_YDS }) {
            state = QbState.SACKED
            return null
        }

        if (elapsedSec < dropbackSec) {
            state = QbState.DROPPING_BACK
            gazeTargetId = null
            return null
        }
        state = QbState.READING_PROGRESSIONS

        val readsSoFar = 1 + ((elapsedSec - dropbackSec) / READ_TIME_SEC).toInt()
        gazeTargetId = progressions.getOrNull((readsSoFar - 1).coerceAtMost(progressions.lastIndex))?.id

        val reads = progressions.map { receiver -> planThrow(receiver).let { Read(receiver, it, separationAtCatch(it, defenders)) } }
        val isPressured = defenders.any { qb.currentPos.distance2DTo(it.currentPos) <= PRESSURE_RADIUS_YDS }
        val isOutOfTime = elapsedSec >= forceThrowSec || isPressured

        // One read at a time: the QB gets to the next receiver every READ_TIME_SEC, so the checkdown comes last
        val openRead = reads.take(readsSoFar).firstOrNull { it.separationYds >= requiredSeparation() }
        val read = openRead ?: (if (isOutOfTime) reads.maxByOrNull { it.separationYds } else null) ?: return null

        state = QbState.THROWING
        val command = ThrowCommand(read.receiver.id, read.trajectory)
        return if (isOutOfTime && read.separationYds < MIN_THROWABLE_SEPARATION_YDS) command.copy(isThrowAway = true) else command
    }

    private data class Read(
        val receiver: RunningPlayer,
        val trajectory: BallTrajectory,
        val separationYds: Float,
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
        val flightTimeSec = (qb.currentPos.distance2DTo(receiver.currentPos) / PASS_SPEED_YDS_PER_SEC).coerceAtLeast(MIN_FLIGHT_SEC)
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
        private const val SACK_RADIUS_YDS = 1.5f
        private const val PRESSURE_RADIUS_YDS = 3f
        private const val READ_TIME_SEC = 0.4f
        private const val OPEN_EARLY_SEPARATION_YDS = 3f
        private const val OPEN_LATE_SEPARATION_YDS = 1.5f
        private const val MIN_THROWABLE_SEPARATION_YDS = 0.3f
        private const val DEFENDER_REACTION_SEC = 0.3f
        private const val PASS_SPEED_YDS_PER_SEC = 20f
        private const val MIN_FLIGHT_SEC = 0.1f
        private const val RELEASE_HEIGHT_YDS = 2.0f
        private const val CATCH_HEIGHT_YDS = 1.2f
        private const val PASS_APEX_YDS = 3f
    }
}
