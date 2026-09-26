package com.xuan.gridironmanager.domain.sim.ai

import com.xuan.gridironmanager.domain.sim.BallTrajectory
import com.xuan.gridironmanager.domain.sim.InterceptionResult
import com.xuan.gridironmanager.domain.sim.PassEvaluator
import com.xuan.gridironmanager.domain.sim.PassOutcome
import com.xuan.gridironmanager.domain.sim.movement.MovementEngine
import com.xuan.gridironmanager.domain.sim.movement.RunningPlayer

enum class QbState {
    DROPPING_BACK,
    READING_PROGRESSIONS,
    THROWING,
    SACKED,
    SCRAMBLING,
}

data class ThrowCommand(
    val targetId: String,
    val trajectory: BallTrajectory,
    /** The QB saw nothing but interception risk and threw the ball away. */
    val isThrowAway: Boolean = false,
)

class QbBrain(
    val qb: RunningPlayer,
    /** Eligible receivers, in read order. */
    val progressions: List<RunningPlayer>,
    private val dropbackSec: Float = DEFAULT_DROPBACK_SEC,
    private val forceThrowSec: Float = DEFAULT_FORCE_THROW_SEC,
    var state: QbState = QbState.DROPPING_BACK,
) {
    private var elapsedSec = 0f

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
            return null
        }
        state = QbState.READING_PROGRESSIONS

        val reads =
            progressions.map { receiver ->
                val trajectory = planThrow(receiver)
                Read(ThrowCommand(receiver.id, trajectory), PassEvaluator.checkPassInterception(trajectory, defenders))
            }

        val openRead = reads.firstOrNull { it.risk.outcome == PassOutcome.CLEAN_PASS }
        // Out of time: force the ball to whoever looks least covered and let the play decide
        val forcedRead =
            if (elapsedSec >= forceThrowSec) {
                reads.minWithOrNull(compareBy<Read> { it.risk.outcome.ordinal }.thenByDescending { it.risk.timeOfImpactSec ?: 0f })
            } else {
                null
            }

        val read = openRead ?: forcedRead ?: return null
        state = QbState.THROWING
        return if (read.risk.outcome == PassOutcome.INTERCEPTED) read.command.copy(isThrowAway = true) else read.command
    }

    private data class Read(
        val command: ThrowCommand,
        val risk: InterceptionResult,
    )

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
        const val DEFAULT_FORCE_THROW_SEC = 2.1f
        private const val SACK_RADIUS_YDS = 1.5f
        private const val PASS_SPEED_YDS_PER_SEC = 20f
        private const val MIN_FLIGHT_SEC = 0.1f
        private const val RELEASE_HEIGHT_YDS = 2.0f
        private const val CATCH_HEIGHT_YDS = 1.2f
        private const val PASS_APEX_YDS = 3f
    }
}
