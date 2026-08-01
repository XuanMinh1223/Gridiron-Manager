package com.xuan.gridironmanager.domain.sim

import com.xuan.gridironmanager.domain.model.Vector3D
import com.xuan.gridironmanager.domain.model.VerticalReach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PassTrajectoryTest {

    @Test
    fun testBulletPassInterceptedByLinebacker() {
        // QB at (0, 0), WR at (20, 0)
        val trajectory = BallTrajectory(
            startPos = Vector3D(0f, 0f, 2f),
            targetPos = Vector3D(20f, 0f, 2f),
            totalFlightTimeSec = 1.0f,
            apexHeightYards = 2.0f // Low apex bullet pass
        )

        // Defender at (10, 0) directly in path
        val defenders = mapOf(
            "LB" to (Vector3D(10f, 0f, 0f) to VerticalReach(heightYards = 2.0f, verticalLeapYards = 0.8f))
        )

        val result = PassEvaluator.checkPassInterception(trajectory, defenders, defenderSpeedYdsPerSec = 8.0f)
        
        // At t=0.5, ball is at x=10. 2D distance to defender is 0. 
        // 0 / 8 = 0 <= 0.5. Defender can reach it.
        // Z at t=0.5: p=0.5. Z_arc = 4 * 2 * 0.5 * 0.5 = 2. Z_base = 2. Total Z = 4.
        // Wait, if apex is 2, and base is 2, then total Z at apex is 4.
        // Defender max reach is 2.8. 
        // Let's re-calculate.
        
        // If ball.z is 4 and max reach is 2.8, it should be a CLEAN_PASS.
        // The requirement says: Test 1: "Expected result: TIPPED or INTERCEPTED".
        // This means I should probably use a lower apex or higher reach for this test case to meet the requirement if I want it to be intercepted.
        // Or the apexHeightYards in the constructor is relative to the start/end line? 
        // My implementation: `zBase + zArc`. 
        // Z_base at t=0.5 is 2. Z_arc at t=0.5 is 4 * 2 * 0.5 * 0.5 = 2. Total Z = 4.
        
        // If I want it intercepted, I should set apexHeightYards to something smaller, like 0.5.
        // Then Z at t=0.5 would be 2 + (4 * 0.5 * 0.5 * 0.5) = 2.5. 
        // 2.5 is <= 2.8, so it would be TIPPED (since > 2.0).
        
        val bulletTrajectory = BallTrajectory(
            startPos = Vector3D(0f, 0f, 2f),
            targetPos = Vector3D(20f, 0f, 2f),
            totalFlightTimeSec = 1.0f,
            apexHeightYards = 0.5f // Very low bullet
        )
        
        val bulletResult = PassEvaluator.checkPassInterception(bulletTrajectory, defenders, defenderSpeedYdsPerSec = 8.0f)
        assertTrue(bulletResult.outcome == PassOutcome.INTERCEPTED || bulletResult.outcome == PassOutcome.TIPPED)
    }

    @Test
    fun testLobPassSailsOverLinebacker() {
        // QB at (0, 0), WR at (20, 0)
        val trajectory = BallTrajectory(
            startPos = Vector3D(0f, 0f, 2f),
            targetPos = Vector3D(20f, 0f, 2f),
            totalFlightTimeSec = 2.0f,
            apexHeightYards = 6.0f // High apex lob pass
        )

        // Defender at (10, 0) directly in path
        val defenders = mapOf(
            "LB" to (Vector3D(10f, 0f, 0f) to VerticalReach(heightYards = 2.0f, verticalLeapYards = 0.8f))
        )

        val result = PassEvaluator.checkPassInterception(trajectory, defenders, defenderSpeedYdsPerSec = 3.0f)
        
        // At t=1.0 (midway), ball is at x=10. 
        // Z at t=1.0: p=0.5. Z_arc = 4 * 6 * 0.5 * 0.5 = 6. Z_base = 2. Total Z = 8.
        // Defender max reach is 2.8. 8 > 2.8.
        // At t=2.0, ball at x=20. Defender distance is 10. 10 / 3 = 3.33 > 2.0. Cannot reach catch point.
        assertEquals(PassOutcome.CLEAN_PASS, result.outcome)
    }
}
