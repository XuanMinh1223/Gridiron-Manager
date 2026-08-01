package com.xuan.gridironmanager.domain.sim

import com.xuan.gridironmanager.domain.model.Route
import com.xuan.gridironmanager.domain.model.Vector3D
import com.xuan.gridironmanager.domain.model.Waypoint
import com.xuan.gridironmanager.domain.sim.movement.MovementEngine
import com.xuan.gridironmanager.domain.sim.movement.RunningPlayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RouteRunnerTest {

    @Test
    fun testGoRouteMovement() {
        // Player starts at (0, 0), speed 8 yds/sec
        val route = Route("Go", listOf(Waypoint(0f, 20f)))
        val player = RunningPlayer(
            id = "WR1",
            currentPos = Vector3D(0f, 0f, 0f),
            speedYdsPerSec = 8.0f,
            route = route
        )

        // 1 second at 20Hz (20 ticks of 0.05s)
        repeat(20) {
            MovementEngine.updatePositions(listOf(player), 0.05f)
        }

        // Expected Y = 8.0 yards (8 yds/sec * 1 sec)
        assertEquals(0.0f, player.currentPos.x, 0.01f)
        assertEquals(8.0f, player.currentPos.y, 0.01f)
    }

    @Test
    fun testOutRouteCut() {
        // Cut point at (0, 10), then break to (10, 10)
        val route = Route("Out", listOf(
            Waypoint(0f, 10f),
            Waypoint(10f, 10f)
        ))
        val player = RunningPlayer(
            id = "WR2",
            currentPos = Vector3D(0f, 0f, 0f),
            speedYdsPerSec = 10.0f,
            route = route
        )

        // 1 second should reach the first waypoint (10 yds / 10 yds/sec = 1 sec)
        repeat(20) {
            MovementEngine.updatePositions(listOf(player), 0.05f)
        }

        assertEquals(1, player.currentWaypointIndex)
        assertTrue(player.currentPos.y >= 9.8f) 
        
        // Next tick should move toward (10, 10)
        MovementEngine.updatePositions(listOf(player), 0.05f)
        assertTrue(player.currentPos.x > 0f)
        assertEquals(10.0f, player.currentPos.y, 0.01f)
    }

    private fun assertEquals(expected: Float, actual: Float, delta: Float) {
        assertTrue(kotlin.math.abs(expected - actual) <= delta, "Expected $expected but was $actual")
    }
}
