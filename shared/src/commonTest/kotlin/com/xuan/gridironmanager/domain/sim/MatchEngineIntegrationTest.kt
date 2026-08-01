package com.xuan.gridironmanager.domain.sim

import com.xuan.gridironmanager.domain.model.*
import com.xuan.gridironmanager.domain.sim.ai.QbBrain
import com.xuan.gridironmanager.domain.sim.ai.QbState
import com.xuan.gridironmanager.domain.sim.ai.ThrowCommand
import com.xuan.gridironmanager.domain.sim.movement.MovementEngine
import com.xuan.gridironmanager.domain.sim.movement.RunningPlayer
import com.xuan.gridironmanager.domain.sim.play.Formation
import com.xuan.gridironmanager.domain.sim.play.FormationNode
import com.xuan.gridironmanager.domain.sim.play.PlaySetup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MatchEngineIntegrationTest {

    @Test
    fun testQbThrowsToOpenReceiver() {
        // Setup 3v3 mini-play: 1 QB, 1 WR, 1 DB far away
        val qb = RunningPlayer("QB", Vector3D(0f, 45f, 0f), 0f, null)
        val wr = RunningPlayer("WR", Vector3D(-15f, 50f, 0f), 8f, Route("Go", listOf(Waypoint(-15f, 100f))))
        val db = RunningPlayer("DB", Vector3D(30f, 60f, 0f), 8f, null)

        val qbBrain = QbBrain(qb, listOf(wr))
        val defenders = listOf(db)
        val players = listOf(qb, wr, db)

        // Run simulation loop
        var throwCommand: ThrowCommand? = null
        for (i in 1..20) {
            MovementEngine.updatePositions(players, 0.05f)
            throwCommand = qbBrain.evaluateTick(defenders, PassEvaluator)
            if (throwCommand != null) break
        }

        assertNotNull(throwCommand, "QB should have thrown the ball")
        assertEquals("WR", throwCommand.targetId)
        assertEquals(QbState.THROWING, qbBrain.state)
    }

    @Test
    fun testQbTakesSackUnderPressure() {
        val qb = RunningPlayer("QB", Vector3D(0f, 47f, 0f), 0f, null)
        val dl = RunningPlayer("DL", Vector3D(0f, 50f, 0f), 8f, Route("Rush", listOf(Waypoint(0f, 47f))))
        
        val qbBrain = QbBrain(qb, emptyList())
        val defenders = listOf(dl)
        val allPlayers = listOf(qb, dl)

        repeat(20) {
            MovementEngine.updatePositions(allPlayers, 0.05f)
            qbBrain.evaluateTick(defenders, PassEvaluator)
            if (qbBrain.state == QbState.SACKED) return@repeat
        }

        assertEquals(QbState.SACKED, qbBrain.state)
    }
}
