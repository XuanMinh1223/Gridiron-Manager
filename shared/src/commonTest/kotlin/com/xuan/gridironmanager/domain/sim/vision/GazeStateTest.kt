package com.xuan.gridironmanager.domain.sim.vision

import com.xuan.gridironmanager.domain.model.Vector3D
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GazeStateTest {
    @Test
    fun turnsAtTheConfiguredMaximumRate() {
        val gaze = GazeState(Vector3D(1f, 0f, 0f))

        gaze.turnToward(Vector3D(0f, 1f, 0f), maxTurnDegrees = 90f, tickDeltaSec = 0.5f)

        assertEquals(45f, kotlin.math.atan2(gaze.facing.y, gaze.facing.x) * 180f / kotlin.math.PI.toFloat(), 0.1f)
    }

    @Test
    fun snapsWhenTargetIsWithinTurnLimit() {
        val gaze = GazeState(Vector3D(1f, 0f, 0f))

        gaze.turnToward(Vector3D(0f, 1f, 0f), maxTurnDegrees = 180f, tickDeltaSec = 1f)

        assertTrue(gaze.facing.x in -0.001f..0.001f)
        assertTrue(gaze.facing.y > 0.999f)
    }
}
