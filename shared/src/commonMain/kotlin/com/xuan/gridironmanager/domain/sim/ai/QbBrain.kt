package com.xuan.gridironmanager.domain.sim.ai

import com.xuan.gridironmanager.domain.model.VerticalReach
import com.xuan.gridironmanager.domain.sim.BallTrajectory
import com.xuan.gridironmanager.domain.sim.PassEvaluator
import com.xuan.gridironmanager.domain.sim.PassOutcome
import com.xuan.gridironmanager.domain.sim.movement.RunningPlayer

enum class QbState {
    DROPPING_BACK, READING_PROGRESSIONS, THROWING, SACKED, SCRAMBLING
}

data class ThrowCommand(val targetId: String)

class QbBrain(
    val qb: RunningPlayer,
    val progressions: List<RunningPlayer>,
    var state: QbState = QbState.DROPPING_BACK
) {
    fun evaluateTick(
        defenders: List<RunningPlayer>,
        evaluator: PassEvaluator
    ): ThrowCommand? {
        // Check Pressure
        for (defender in defenders) {
            val dist = qb.currentPos.distance2DTo(defender.currentPos)
            if (dist <= 1.5f) {
                state = QbState.SACKED
                return null
            }
        }

        if (state == QbState.READING_PROGRESSIONS || state == QbState.DROPPING_BACK) {
            if (state == QbState.DROPPING_BACK) state = QbState.READING_PROGRESSIONS
            
            for (receiver in progressions) {
                val dist = qb.currentPos.distance2DTo(receiver.currentPos)
                val flightTime = dist / 20f
                
                val trajectory = BallTrajectory(
                    startPos = qb.currentPos,
                    targetPos = receiver.currentPos,
                    totalFlightTimeSec = if (flightTime > 0) flightTime else 0.1f,
                    apexHeightYards = 3f
                )
                
                val defenderMap = defenders.associate { 
                    it.id to (it.currentPos to VerticalReach()) 
                }
                
                val result = evaluator.checkPassInterception(trajectory, defenderMap)
                
                if (result.outcome == PassOutcome.CLEAN_PASS) {
                    state = QbState.THROWING
                    return ThrowCommand(receiver.id)
                }
            }
        }

        return null
    }
}
