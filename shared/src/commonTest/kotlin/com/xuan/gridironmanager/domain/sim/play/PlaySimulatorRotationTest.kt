package com.xuan.gridironmanager.domain.sim.play

import com.xuan.gridironmanager.domain.model.PlayerAttributes
import com.xuan.gridironmanager.domain.model.Vector3D
import com.xuan.gridironmanager.domain.sim.match.GameState
import com.xuan.gridironmanager.domain.sim.movement.PlayerRole
import com.xuan.gridironmanager.domain.sim.playbook.DefensiveCall
import com.xuan.gridironmanager.domain.sim.playbook.Playbook
import com.xuan.gridironmanager.domain.sim.playbook.SnapBuilder
import com.xuan.gridironmanager.testMatchup
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaySimulatorRotationTest {
    private val tick = 0.05f
    private val matchup = testMatchup()
    private val homeBall = GameState(yardLine = 30, isHomePossession = true)

    private fun snap(call: DefensiveCall) = SnapBuilder.build(homeBall, matchup, Playbook.QUICK_SLANTS, call)

    private fun PlaySimulator.tickTo(seconds: Float) {
        val ticks = (seconds / tick + 0.5f).toInt()
        repeat(ticks) { if (elapsedSec < seconds - tick / 2) tick(tick) }
    }

    private data class Assignment(
        val role: PlayerRole,
        val target: String?,
        val landmark: Vector3D?,
    )

    private fun PlaySimulator.defenseAssignments() =
        players
            .filter { !it.isOffense }
            .associate { it.slot!! to Assignment(it.role, it.coverageTargetId, it.zoneLandmark) }

    private fun RotationPlan.asAssignments() = assignments.mapValues { (_, a) -> Assignment(a.role, a.coverageTargetId, a.zoneLandmark) }

    @Test
    fun testEachDisguiseRotatesFromShellToTrueCoverage() {
        for (call in listOf(Playbook.COVER_3_FROM_2, Playbook.COVER_2_FROM_3, Playbook.COVER_2_FROM_MAN)) {
            val snap = snap(call)
            val simulator = PlaySimulator(snap, Random(1))
            val shellAssignments = simulator.defenseAssignments()

            simulator.tickTo(0.35f)
            assertEquals(shellAssignments, simulator.defenseAssignments(), "${call.name} is still in its shell at 0.35s")

            simulator.tickTo(0.45f)
            val rotated = simulator.defenseAssignments()
            assertEquals(snap.rotation!!.asAssignments(), rotated, "${call.name} has rotated at 0.45s")
            assertTrue(rotated != shellAssignments, "${call.name} actually changed")
        }
    }

    @Test
    fun testRotationHappensExactlyOnce() {
        val simulator = PlaySimulator(snap(Playbook.COVER_3_FROM_2), Random(1))
        simulator.tickTo(0.45f)
        val fs = simulator.players.single { !it.isOffense && it.slot == "FS" }
        val sentinel = Vector3D(1f, 2f, 0f)

        fs.zoneLandmark = sentinel
        simulator.tickTo(2.0f)

        assertEquals(sentinel, fs.zoneLandmark, "a second rotation would have overwritten this")
    }

    @Test
    fun testUndisguisedPlayNeverRotates() {
        val simulator = PlaySimulator(snap(Playbook.COVER_3), Random(1))
        val before = simulator.defenseAssignments()

        simulator.tickTo(2.0f)

        assertEquals(before, simulator.defenseAssignments())
    }

    @Test
    fun testRotationLeavesRushersBlocksAndBallAlone() {
        val snap = snap(Playbook.COVER_2_FROM_MAN)
        val simulator = PlaySimulator(snap, Random(1))
        val rushersBefore = snap.defense.filter { it.role == PlayerRole.PASS_RUSHER || it.role == PlayerRole.BLITZER }.map { it.id }
        assertTrue(rushersBefore.isNotEmpty())

        simulator.tickTo(0.45f)

        val rushersAfter = snap.defense.filter { it.role == PlayerRole.PASS_RUSHER || it.role == PlayerRole.BLITZER }.map { it.id }
        assertEquals(rushersBefore, rushersAfter)
        val passer = snap.offense.single { it.role == PlayerRole.PASSER }
        assertEquals(passer.currentPos, simulator.ballPosition, "the ball is still with the QB")
        val engaged = snap.offense.mapNotNull { it.blockingId }
        assertTrue(engaged.all { it in rushersBefore }, "every block is still on a rusher")
    }

    @Test
    fun testOnlyDisguisedPassPlaysRollForTheQb() {
        val qbSnap = snap(Playbook.COVER_3_FROM_2)
        assertTrue(PlaySimulator(qbSnap, Random(1)).qbWasFooled == PlaySimulator(qbSnap, Random(1)).qbWasFooled, "same seed, same roll")

        assertTrue(!PlaySimulator(snap(Playbook.COVER_3), Random(1)).qbWasFooled)
        val run = SnapBuilder.build(homeBall, matchup, Playbook.INSIDE_ZONE, Playbook.COVER_3_FROM_2)
        assertTrue(!PlaySimulator(run, Random(1)).qbWasFooled, "no QB read penalty on a run")
        assertNotNull(run.rotation, "but the coverage still rotates on a run")
        assertNull(snap(Playbook.COVER_3).rotation)
    }

    @Test
    fun testFooledQbRecoversAfterRotationPlusRecognition() {
        val qbSnap = snap(Playbook.COVER_3_FROM_2)
        val average =
            qbSnap.copy(
                offense = qbSnap.offense.map { if (it.role == PlayerRole.PASSER) it.copy(attributes = PlayerAttributes.AVERAGE) else it },
            )
        val simulator = PlaySimulator(average, Random(1), qbIdentifiedOverride = false)

        assertTrue(simulator.qbWasFooled)
        simulator.tickTo(0.3f)
        assertTrue(simulator.isQbFooled, "still reading the shell before the rotation")
        simulator.tickTo(0.7f) // 0.4s rotation + 0.225s recognition for an average QB = 0.625s
        assertTrue(!simulator.isQbFooled, "re-identified after rotation + recognition delay")
        assertTrue(simulator.qbWasFooled, "but he was still fooled at the snap")
    }
}
