package com.xuan.gridironmanager.domain.sim

import com.xuan.gridironmanager.domain.model.Route
import com.xuan.gridironmanager.domain.model.Vector3D
import com.xuan.gridironmanager.domain.model.Waypoint
import com.xuan.gridironmanager.domain.sim.ai.QbBrain
import com.xuan.gridironmanager.domain.sim.ai.QbState
import com.xuan.gridironmanager.domain.sim.ai.ThrowCommand
import com.xuan.gridironmanager.domain.sim.movement.MovementEngine
import com.xuan.gridironmanager.domain.sim.movement.RunningPlayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MatchEngineIntegrationTest {
    private val tick = 0.05f

    @Test
    fun testQbThrowsToOpenReceiver() {
        // Setup mini-play: 1 QB, 1 WR, 1 DB far away
        val qb = RunningPlayer("QB", Vector3D(0f, 45f, 0f), 0f, null)
        val wr = RunningPlayer("WR", Vector3D(-15f, 50f, 0f), 8f, Route("Go", listOf(Waypoint(-15f, 100f))))
        val db = RunningPlayer("DB", Vector3D(30f, 60f, 0f), 8f, null)

        val qbBrain = QbBrain(qb, listOf(wr))
        val defenders = listOf(db)
        val players = listOf(qb, wr, db)

        // Run simulation loop
        var throwCommand: ThrowCommand? = null
        for (i in 1..40) {
            MovementEngine.updatePositions(players, tick)
            throwCommand = qbBrain.evaluateTick(defenders, tick)
            if (throwCommand != null) break
        }

        assertNotNull(throwCommand, "QB should have thrown the ball")
        assertEquals("WR", throwCommand.targetId)
        assertEquals(QbState.THROWING, qbBrain.state)
    }

    @Test
    fun testQbHoldsBallDuringDropback() {
        val qb = RunningPlayer("QB", Vector3D(0f, 45f, 0f), 0f, null)
        val wr = RunningPlayer("WR", Vector3D(-15f, 50f, 0f), 8f, null)
        val qbBrain = QbBrain(qb, listOf(wr), dropbackSec = 0.5f)

        // 0.45s into the play the QB is still dropping back, even with a wide open receiver
        repeat(9) { assertNull(qbBrain.evaluateTick(emptyList(), tick)) }
        assertEquals(QbState.DROPPING_BACK, qbBrain.state)

        assertNotNull(qbBrain.evaluateTick(emptyList(), tick))
    }

    @Test
    fun testQbLeadsReceiverAlongRoute() {
        val qb = RunningPlayer("QB", Vector3D(0f, 45f, 0f), 0f, null)
        val wr = RunningPlayer("WR", Vector3D(-15f, 50f, 0f), 8f, Route("Go", listOf(Waypoint(-15f, 100f))))

        val trajectory = QbBrain(qb, listOf(wr)).planThrow(wr)

        // Ball is thrown ahead of the receiver, down their route
        assertTrue(trajectory.targetPos.y > wr.currentPos.y)
        assertEquals(wr.currentPos.x, trajectory.targetPos.x)
    }

    @Test
    fun testQbTakesSackUnderPressure() {
        val qb = RunningPlayer("QB", Vector3D(0f, 47f, 0f), 0f, null)
        val dl = RunningPlayer("DL", Vector3D(0f, 50f, 0f), 8f, Route("Rush", listOf(Waypoint(0f, 47f))))

        val qbBrain = QbBrain(qb, emptyList())
        val defenders = listOf(dl)
        val allPlayers = listOf(qb, dl)

        repeat(20) {
            MovementEngine.updatePositions(allPlayers, tick)
            qbBrain.evaluateTick(defenders, tick)
            if (qbBrain.state == QbState.SACKED) return@repeat
        }

        assertEquals(QbState.SACKED, qbBrain.state)
    }
}
