package com.xuan.gridironmanager.domain.sim.movement

import com.xuan.gridironmanager.domain.model.Vector3D
import kotlin.test.Test
import kotlin.test.assertTrue

class MovementEngineTest {
    @Test
    fun trailingDefenderClosesOnRunnerNearEitherGoalLine() {
        for (direction in listOf(1f, -1f)) {
            val carrier = Vector3D(26f, if (direction > 0) 95f else 5f, 0f)
            val defender = RunningPlayer("DB", carrier.copy(y = carrier.y - 3f * direction), 8f, null, isOffense = false)
            val before = defender.currentPos.distance2DTo(carrier)

            MovementEngine.intercept(defender, carrier, Vector3D(0f, 9f * direction, 0f), 0.05f)

            assertTrue(defender.currentPos.distance2DTo(carrier) < before)
        }
    }

    @Test
    fun defenderAheadCanStillTakeAnInterceptAngle() {
        val carrier = Vector3D(26f, 60f, 0f)
        val defender = RunningPlayer("DB", Vector3D(30f, 66f, 0f), 8f, null, isOffense = false)
        val before = defender.currentPos.distance2DTo(carrier)

        MovementEngine.intercept(defender, carrier, Vector3D(0f, 8f, 0f), 0.05f)

        assertTrue(defender.currentPos.distance2DTo(carrier) < before)
    }

    @Test
    fun nearbyDefenderDoesNotRunAwayFromCarrierToLeadPoint() {
        val carrier = Vector3D(26f, 55f, 0f)
        val defender = RunningPlayer("S", Vector3D(26f, 60f, 0f), 8f, null, isOffense = false)
        val before = defender.currentPos.distance2DTo(carrier)

        MovementEngine.intercept(defender, carrier, Vector3D(0f, 10f, 0f), 0.05f)

        assertTrue(defender.currentPos.distance2DTo(carrier) < before)
    }
}
