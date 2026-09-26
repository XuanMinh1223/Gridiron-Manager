package com.xuan.gridironmanager.domain.sim

import com.xuan.gridironmanager.domain.model.Vector3D
import com.xuan.gridironmanager.domain.model.VerticalReach
import com.xuan.gridironmanager.domain.sim.movement.RunningPlayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PassTrajectoryTest {
    private fun linebacker(speedYdsPerSec: Float) =
        RunningPlayer(
            id = "LB",
            currentPos = Vector3D(10f, 0f, 0f), // Directly in the passing lane
            speedYdsPerSec = speedYdsPerSec,
            route = null,
            verticalReach = VerticalReach(heightYards = 2.0f, verticalLeapYards = 0.8f),
        )

    @Test
    fun testBulletPassInterceptedByLinebacker() {
        // QB at (0, 0), WR at (20, 0). At t=0.5 the ball is over the LB at z = 2 + 4 * 0.5 * 0.5 * 0.5 = 2.5,
        // which is above standing reach (2.0) but within jumping reach (2.8).
        val bulletTrajectory =
            BallTrajectory(
                startPos = Vector3D(0f, 0f, 2f),
                targetPos = Vector3D(20f, 0f, 2f),
                totalFlightTimeSec = 1.0f,
                apexHeightYards = 0.5f, // Very low bullet
            )

        val result = PassEvaluator.checkPassInterception(bulletTrajectory, listOf(linebacker(speedYdsPerSec = 8.0f)))
        assertTrue(result.outcome == PassOutcome.INTERCEPTED || result.outcome == PassOutcome.TIPPED)
        assertEquals("LB", result.defenderId)
    }

    @Test
    fun testLobPassSailsOverLinebacker() {
        val trajectory =
            BallTrajectory(
                startPos = Vector3D(0f, 0f, 2f),
                targetPos = Vector3D(20f, 0f, 2f),
                totalFlightTimeSec = 2.0f,
                apexHeightYards = 6.0f, // High apex lob pass
            )

        val result = PassEvaluator.checkPassInterception(trajectory, listOf(linebacker(speedYdsPerSec = 3.0f)))

        // At t=1.0 (midway) the ball is at z = 8, far above the LB's 2.8 reach.
        // At t=2.0 the ball is 10 yards away: 10 / 3 = 3.33s > 2.0s, so the LB cannot reach the catch point.
        assertEquals(PassOutcome.CLEAN_PASS, result.outcome)
    }

    @Test
    fun testFasterDefenderClosesOnSameLob() {
        val trajectory =
            BallTrajectory(
                startPos = Vector3D(0f, 0f, 2f),
                targetPos = Vector3D(20f, 0f, 2f),
                totalFlightTimeSec = 2.0f,
                apexHeightYards = 6.0f,
            )

        // Same lob, but a defender quick enough to reach the catch point (10 / 8 = 1.25s < 2.0s)
        val result = PassEvaluator.checkPassInterception(trajectory, listOf(linebacker(speedYdsPerSec = 8.0f)))

        assertTrue(result.outcome != PassOutcome.CLEAN_PASS)
    }
}
