package com.xuan.gridironmanager.domain.sim.play

import com.xuan.gridironmanager.domain.model.PlayType
import com.xuan.gridironmanager.domain.model.PlayerAttributes
import com.xuan.gridironmanager.domain.model.Position
import com.xuan.gridironmanager.domain.model.Vector3D
import com.xuan.gridironmanager.domain.sim.ai.QbBrain
import com.xuan.gridironmanager.domain.sim.match.PlayDebugTrace
import com.xuan.gridironmanager.domain.sim.match.PlayOutcome
import com.xuan.gridironmanager.domain.sim.movement.PlayerRole
import com.xuan.gridironmanager.domain.sim.movement.RunningPlayer
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Phase 1 (§3) windup tests: every throw takes time, and a rusher who arrives during the windup sacks the QB
 * with the ball exposed.
 */
class QbWindupTest {
    private val tick = 0.05f
    private val qbX = 26f
    private val qbY = 25f

    private fun qb(strength: Int = 50) =
        RunningPlayer(
            id = "QB",
            currentPos = Vector3D(qbX, qbY, 0f),
            speedYdsPerSec = 7f,
            route = null,
            position = Position.QB,
            role = PlayerRole.PASSER,
            slot = "QB",
            attributes = PlayerAttributes.AVERAGE.copy(strength = strength),
        )

    private val receivers =
        listOf(
            RunningPlayer("R1", Vector3D(10f, 45f, 0f), 8f, null, position = Position.WR, role = PlayerRole.RECEIVER, slot = "R1"),
            RunningPlayer("R2", Vector3D(26f, 48f, 0f), 8f, null, position = Position.WR, role = PlayerRole.RECEIVER, slot = "R2"),
            RunningPlayer("R3", Vector3D(42f, 45f, 0f), 8f, null, position = Position.WR, role = PlayerRole.RECEIVER, slot = "R3"),
        )

    private fun snap(
        qb: RunningPlayer,
        defense: List<RunningPlayer>,
    ) = Snap(
        offense = listOf(qb) + receivers,
        defense = defense,
        playType = PlayType.PASS,
        losYardLine = 30,
        isAttackingUp = true,
        progression = receivers.map { it.id },
    )

    /** The play-clock timestamp stamped on a trace line, e.g. `t=0.60 windup ...` -> 0.60. */
    private fun traceTime(line: String): Float = line.substringAfter("t=").substringBefore(" ").toFloat()

    @Test
    fun testThrowIsDelayedExactlyWindupSecAfterTheDecision() {
        val trace = PlayDebugTrace()
        val simulator = PlaySimulator(snap(qb(), emptyList()), Random(1), trace = trace)
        var releasedAtSec: Float? = null
        var maxBallZBeforeRelease = 0f
        var ticks = 0

        while (releasedAtSec == null && ticks < 40) {
            ticks++
            simulator.tick(tick)
            val z = simulator.ballPosition?.z ?: 0f
            if (z > 0f) {
                releasedAtSec = simulator.elapsedSec
            } else {
                maxBallZBeforeRelease = maxOf(maxBallZBeforeRelease, z)
            }
        }

        val windupLine = trace.lines().firstOrNull { "windup " in it }
        assertNotNull(windupLine, "expected a windup line in the trace: ${trace.lines()}")
        assertTrue(windupLine.contains("windup 0.15 s → R1"), windupLine)
        assertEquals(0f, maxBallZBeforeRelease, "the ball must not leave the QB's hands during the windup")
        val released = assertNotNull(releasedAtSec, "the ball was never released")
        assertEquals(QbBrain.WINDUP_SEC, released - traceTime(windupLine), tick, "release must follow the decision by exactly WINDUP_SEC")
    }

    @Test
    fun testRusherArrivingDuringTheWindupSacksTheQbWithTheWindupStripChance() {
        // A rusher 7 yards out at 8 yd/s reaches the 1.5-yard sack radius at ~0.69 s — after the throw decision
        // at 0.6 s, inside the 0.15 s windup that ends at 0.75 s. Tackle 90 vs strength 45 doubles the base
        // strip chance, so the traced effective p is 0.50 and the base it asserts is 0.25.
        val rusher =
            RunningPlayer(
                id = "RUSH",
                currentPos = Vector3D(qbX, qbY + 7f, 0f),
                speedYdsPerSec = 8f,
                route = null,
                isOffense = false,
                position = Position.EDGE,
                role = PlayerRole.PASS_RUSHER,
                slot = "RUSH",
                attributes = PlayerAttributes.AVERAGE.copy(tackle = 90),
            )
        val trace = PlayDebugTrace()
        val simulator = PlaySimulator(snap(qb(strength = 45), listOf(rusher)), Random(1), trace = trace)
        var outcome: PlayOutcome? = null
        var maxBallZ = 0f
        var ticks = 0
        while (outcome == null && ticks < 60) {
            ticks++
            outcome = simulator.tick(tick)
            maxBallZ = maxOf(maxBallZ, simulator.ballPosition?.z ?: 0f)
        }

        val result = assertNotNull(outcome, "the play never ended") as PlayOutcome.Scrimmage
        assertTrue(result.result.isSack, "expected a sack, got: ${result.result.description}")
        assertEquals(0f, maxBallZ, "the ball was never released — the QB was sacked mid-windup")
        val lines = trace.lines()
        assertTrue(lines.any { "SACKED by RUSH" in it }, "expected the brain to log the sack: $lines")
        val fumbleLine = lines.firstOrNull { "fumble roll p=" in it }
        assertNotNull(fumbleLine, "expected a fumble roll line in the trace: $lines")
        val effectiveP = fumbleLine.substringAfter("fumble roll p=").substringBefore(" ").toFloat()
        val baseP = effectiveP * 45f / 90f
        assertEquals(PlaySimulator.STRIP_SACK_CHANCE_WINDUP, baseP, 0.001f, "the windup strip chance base must be 0.25 (traced effective p=$effectiveP)")
    }
}
