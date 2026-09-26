package com.xuan.gridironmanager.domain.sim

import com.xuan.gridironmanager.domain.sim.movement.RunningPlayer

enum class PassOutcome {
    CLEAN_PASS,
    TIPPED,
    INTERCEPTED,
}

data class InterceptionResult(
    val outcome: PassOutcome,
    val timeOfImpactSec: Float? = null,
    val defenderId: String? = null,
)

object PassEvaluator {
    private const val STEP_SEC = 0.05f // 20Hz

    /**
     * Predicts whether any defender can get to the ball during its flight, using each defender's
     * current position, speed and vertical reach.
     */
    fun checkPassInterception(
        trajectory: BallTrajectory,
        defenders: List<RunningPlayer>,
    ): InterceptionResult {
        var t = 0.0f

        while (t <= trajectory.totalFlightTimeSec) {
            val ballPos = trajectory.getPositionAt(t)

            for (defender in defenders) {
                val distance2D = ballPos.distance2DTo(defender.currentPos)

                // If defender can reach the 2D position by time t
                if ((distance2D / defender.speedYdsPerSec) <= t) {
                    if (ballPos.z <= defender.verticalReach.heightYards) {
                        return InterceptionResult(PassOutcome.INTERCEPTED, t, defender.id)
                    } else if (ballPos.z <= defender.verticalReach.maxCatchHeightYards) {
                        return InterceptionResult(PassOutcome.TIPPED, t, defender.id)
                    }
                }
            }

            t += STEP_SEC
        }

        return InterceptionResult(PassOutcome.CLEAN_PASS)
    }
}
