package com.xuan.gridironmanager.domain.sim

import com.xuan.gridironmanager.domain.model.Vector3D
import com.xuan.gridironmanager.domain.model.VerticalReach

enum class PassOutcome {
    CLEAN_PASS, TIPPED, INTERCEPTED
}

data class InterceptionResult(
    val outcome: PassOutcome,
    val timeOfImpactSec: Float? = null,
    val defenderId: String? = null
)

object PassEvaluator {
    fun checkPassInterception(
        trajectory: BallTrajectory,
        defenders: Map<String, Pair<Vector3D, VerticalReach>>,
        defenderSpeedYdsPerSec: Float = 8.0f // Average speed
    ): InterceptionResult {
        var t = 0.0f
        val step = 0.05f // 20Hz

        while (t <= trajectory.totalFlightTimeSec) {
            val ballPos = trajectory.getPositionAt(t)
            
            for ((id, defenderData) in defenders) {
                val (defenderPos, reach) = defenderData
                val distance2D = ballPos.distance2DTo(defenderPos)
                
                // If defender can reach the 2D position by time t
                if ((distance2D / defenderSpeedYdsPerSec) <= t) {
                    if (ballPos.z <= reach.heightYards) {
                        return InterceptionResult(PassOutcome.INTERCEPTED, t, id)
                    } else if (ballPos.z <= reach.maxCatchHeightYards) {
                        return InterceptionResult(PassOutcome.TIPPED, t, id)
                    }
                }
            }
            
            t += step
        }

        return InterceptionResult(PassOutcome.CLEAN_PASS)
    }
}
