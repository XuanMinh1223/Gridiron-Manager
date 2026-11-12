package com.xuan.gridironmanager.domain.sim.ai

import com.xuan.gridironmanager.domain.model.PlayerAttributes
import com.xuan.gridironmanager.domain.model.Position
import com.xuan.gridironmanager.domain.model.Vector3D
import com.xuan.gridironmanager.domain.sim.movement.RunningPlayer
import com.xuan.gridironmanager.domain.sim.playbook.Playbook
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QbReadTest {
    private val tick = 0.05f

    private fun player(
        id: String,
        x: Float,
        y: Float,
    ) = RunningPlayer(id = id, currentPos = Vector3D(x, y, 0f), speedYdsPerSec = 8f, route = null, position = Position.WR)

    private val qb = player("QB", 26f, 20f)
    private val receivers = listOf(player("R1", 10f, 35f), player("R2", 26f, 38f), player("R3", 42f, 35f))

    /** A defender standing on every receiver, so no read is ever open and the QB just keeps reading. */
    private val coverage = receivers.map { player("D_${it.id}", it.currentPos.x, it.currentPos.y) }

    private fun brain(
        readDelaySec: Float = 0f,
        recoverAtSec: Float = 0f,
    ) = QbBrain(qb, receivers, readDelaySec = readDelaySec, recoverAtSec = recoverAtSec)

    private fun QbBrain.gazeAfter(
        seconds: Float,
        defenders: List<RunningPlayer> = coverage,
    ): String? {
        repeat((seconds / tick + 0.5f).toInt()) { evaluateTick(defenders, tick) }
        return gazeTargetId
    }

    /** The time of the first throw when every receiver is open. */
    private fun QbBrain.firstThrowSec(): Float {
        var t = 0f
        while (t < 3f) {
            t += tick
            if (evaluateTick(emptyList(), tick) != null) return t
        }
        error("never threw")
    }

    @Test
    fun testIdentifyProbabilityFormula() {
        assertEquals(0.50f, CoverageRead.identifyProbability(50, 50), 0.001f)
        assertEquals(0.64f, CoverageRead.identifyProbability(70, 70), 0.001f)
        assertEquals(0.78f, CoverageRead.identifyProbability(90, 90), 0.001f)
        assertEquals(0.36f, CoverageRead.identifyProbability(30, 30), 0.001f)
        assertEquals(0.15f, CoverageRead.identifyProbability(0, 0), 0.001f)
        assertEquals(0.843f, CoverageRead.identifyProbability(99, 99), 0.001f)
        assertEquals(0.90f, CoverageRead.identifyProbability(130, 130), 0.001f) // clamped
    }

    @Test
    fun testFooledQbsFirstReadArrivesPointFourSecondsLater() {
        val identified = brain().firstThrowSec()
        val fooled = brain(readDelaySec = 0.4f).firstThrowSec()

        assertEquals(0.4f, fooled - identified, tick + 0.001f)
    }

    @Test
    fun testFooledQbStaysOneReadIntervalBehindAndNeverCatchesUp() {
        val identified = brain()
        val fooled = brain(readDelaySec = 0.4f)

        // 1.1s: the identified QB is on his second read, the fooled QB is still on his first
        assertEquals("R2", identified.gazeAfter(1.1f))
        assertEquals("R1", fooled.gazeAfter(1.1f))
        // 1.5s: the fooled QB has moved on, still a full interval behind where he'd have been (R3 at 1.5s)
        assertEquals("R3", brain().gazeAfter(1.5f))
        assertEquals("R2", brain(readDelaySec = 0.4f).gazeAfter(1.5f))
    }

    @Test
    fun testFooledQbLooksAtTheFirstReadWhileHisReadsAreDelayed() {
        val fooled = brain(readDelaySec = 0.4f)

        assertNull(fooled.gazeAfter(0.3f), "still dropping back")
        assertEquals("R1", fooled.gazeAfter(0.5f), "0.8s: reads have not started but he is looking at the first read")
    }

    @Test
    fun testRecoveryClearsTheFooledFlagWithoutGivingTheReadTimeBack() {
        val fooled = brain(readDelaySec = 0.4f, recoverAtSec = 0.625f)

        fooled.gazeAfter(0.5f)
        assertTrue(fooled.isFooled)
        fooled.gazeAfter(0.2f) // 0.7s
        assertFalse(fooled.isFooled)
        // Recovery did not advance his reads: at 1.1s he is still on the first read
        assertEquals("R1", fooled.gazeAfter(0.4f))
    }

    @Test
    fun testIdentifiedQbIsExactlyTheDefaultBrain() {
        val plain = QbBrain(qb, receivers)
        val identified = QbBrain(qb, receivers, readDelaySec = 0f, recoverAtSec = 0.625f)

        assertEquals(plain.firstThrowSec(), identified.firstThrowSec())
        assertFalse(identified.isFooled)
    }

    @Test
    fun testFooledQbIsStillForcedToThrowAtTheSameTime() {
        // Nobody is open, so both QBs are forced to throw at 2.6s from the snap - the delay does not move it
        for (delay in listOf(0f, 0.4f)) {
            val brain = brain(readDelaySec = delay)
            var t = 0f
            var thrown: Float? = null
            while (t < 3f && thrown == null) {
                t += tick
                if (brain.evaluateTick(coverage, tick) != null) thrown = t
            }
            assertEquals(2.6f, assertNotNull(thrown), tick + 0.001f, "delay=$delay")
        }
    }

    @Test
    fun testFooledHintNeverNamesTheTrueCoverage() {
        for (call in listOf(Playbook.COVER_3_FROM_2, Playbook.COVER_2_FROM_3, Playbook.COVER_2_FROM_MAN)) {
            val fooled = assertNotNull(CoverageRead.hint(call, identified = false), call.name)
            val sure = assertNotNull(CoverageRead.hint(call, identified = true), call.name)

            assertNotEquals(call.coverage!!.label, fooled.coverageLabel, call.name)
            assertEquals(call.disguise!!.shellCoverage!!.label, fooled.coverageLabel)
            assertFalse(fooled.isConfident)
            assertEquals(call.coverage.label, sure.coverageLabel)
            assertTrue(sure.isConfident)
        }
        assertEquals("Read: Cover 3 — Confident", CoverageRead.hint(Playbook.COVER_3_FROM_2, true)?.text)
        assertEquals("Read: Cover 2 — Uncertain", CoverageRead.hint(Playbook.COVER_3_FROM_2, false)?.text)
        assertNull(CoverageRead.hint(Playbook.COVER_3, true))
    }

    @Test
    fun testAverageQbIsFooledAboutHalfTheTime() {
        val random = Random(7)
        val fooled = (1..400).count { !CoverageRead.roll(PlayerAttributes.AVERAGE, random) }

        assertTrue(fooled / 400f in 0.40f..0.60f, "fooled rate was ${fooled / 400f}")
    }
}
