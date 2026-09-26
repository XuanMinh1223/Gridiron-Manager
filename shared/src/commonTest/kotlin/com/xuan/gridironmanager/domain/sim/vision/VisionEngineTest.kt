package com.xuan.gridironmanager.domain.sim.vision

import com.xuan.gridironmanager.domain.model.Vector3D
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VisionEngineTest {
    private val origin = Vector3D(0f, 0f, 0f)
    private val forward = Vector3D(0f, 1f, 0f)

    @Test
    fun targetInsideAndOutsideFieldOfView() {
        assertTrue(VisionEngine.inspect(origin, forward, Vector3D(0f, 5f, 0f), 120f).visible)
        assertFalse(VisionEngine.inspect(origin, forward, Vector3D(5f, -5f, 0f), 120f).visible)
    }

    @Test
    fun fieldOfViewBoundaryIsInclusive() {
        val result = VisionEngine.inspect(origin, forward, Vector3D(5f, 8.6603f, 0f), 60.01f)
        assertTrue(result.visible)
        assertEquals(30f, result.angleDegrees, 0.1f)
    }

    @Test
    fun lineOfSightReportsBlocker() {
        val result =
            VisionEngine.inspect(
                observer = origin,
                facing = forward,
                target = Vector3D(0f, 10f, 0f),
                fieldOfViewDegrees = 120f,
                blockers = listOf("blocker" to Vector3D(0f, 5f, 0f)),
                targetId = "target",
            )
        assertFalse(result.visible)
        assertEquals("blocker", result.blockerId)
    }

    @Test
    fun coincidentTargetIsVisibleAndZeroFacingIsNot() {
        assertTrue(VisionEngine.inspect(origin, forward, origin, 120f).visible)
        assertFalse(VisionEngine.inspect(origin, Vector3D(0f, 0f, 0f), Vector3D(0f, 1f, 0f), 120f).visible)
    }
}
