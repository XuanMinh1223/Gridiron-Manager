package com.xuan.gridironmanager.domain.sim.play

import com.xuan.gridironmanager.domain.model.PlayType
import com.xuan.gridironmanager.domain.model.PlayerAttributes
import com.xuan.gridironmanager.domain.model.Position
import com.xuan.gridironmanager.domain.model.Vector3D
import com.xuan.gridironmanager.domain.sim.match.PlayOutcome
import com.xuan.gridironmanager.domain.sim.movement.PlayerRole
import com.xuan.gridironmanager.domain.sim.movement.RunningPlayer
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PlaySimulatorScrambleTest {
    private val tick = 0.05f
    private val qbX = 26f
    private val qbY = 25f

    private fun qb(speed: Int) =
        RunningPlayer(
            id = "QB",
            currentPos = Vector3D(qbX, qbY, 0f),
            speedYdsPerSec = 7f,
            route = null,
            position = Position.QB,
            role = PlayerRole.PASSER,
            slot = "QB",
            attributes = PlayerAttributes.AVERAGE.copy(speed = speed),
        )

    private val receivers =
        listOf(
            RunningPlayer("R1", Vector3D(10f, 45f, 0f), 8f, null, position = Position.WR, role = PlayerRole.RECEIVER, slot = "R1"),
            RunningPlayer("R2", Vector3D(26f, 48f, 0f), 8f, null, position = Position.WR, role = PlayerRole.RECEIVER, slot = "R2"),
            RunningPlayer("R3", Vector3D(42f, 45f, 0f), 8f, null, position = Position.WR, role = PlayerRole.RECEIVER, slot = "R3"),
        )

    /** Defenders standing on every receiver: nobody is ever open, so the QB holds the ball until pressure arrives. */
    private val blanketCoverage =
        receivers.map {
            RunningPlayer("D_${it.id}", it.currentPos, 8f, null, isOffense = false, position = Position.CB, role = PlayerRole.ROUTE_ONLY, slot = "D_${it.id}")
        }

    private fun rusher(
        distanceFromQb: Float,
        speed: Float,
    ) = RunningPlayer(
        id = "RUSH",
        currentPos = Vector3D(qbX, qbY + distanceFromQb, 0f),
        speedYdsPerSec = speed,
        route = null,
        isOffense = false,
        position = Position.EDGE,
        role = PlayerRole.PASS_RUSHER,
        slot = "RUSH",
    )

    private fun play(
        qbSpeed: Int,
        rusher: RunningPlayer?,
        seed: Int,
    ): PlayOutcome {
        val snap =
            Snap(
                offense = listOf(qb(qbSpeed)) + receivers,
                defense = blanketCoverage + listOfNotNull(rusher),
                playType = PlayType.PASS,
                losYardLine = 30,
                isAttackingUp = true,
                progression = receivers.map { it.id },
            )
        val simulator = PlaySimulator(snap, Random(seed))
        while (true) {
            simulator.tick(tick)?.let { return it }
        }
    }

    private fun PlayOutcome.description() = assertIs<PlayOutcome.Scrimmage>(this).result.description

    private fun PlayOutcome.isRun() = description().startsWith("Run for") || description().contains("-yard run")

    @Test
    fun testPressuredFastQbSometimesScramblesAndItIsARun() {
        // A slow rusher closes from 6 yards, reaching the 3-yard pressure band at ~1.0s: after the QB has held the ball
        val outcomes = (1..60).map { play(qbSpeed = 99, rusher = rusher(6f, 3f), seed = it) }

        val scrambles = outcomes.count { it.isRun() }
        assertTrue(scrambles > 0, "no scrambles in 60 pressured plays")
        assertTrue(scrambles < 60, "every pressured play scrambled: the roll should fail sometimes")
        assertTrue(outcomes.filter { it.isRun() }.none { it.description().contains("Pass") })
    }

    @Test
    fun testFastQbsScrambleMoreThanSlowOnes() {
        fun scrambles(speed: Int) = (1..150).count { play(speed, rusher(6f, 3f), it).isRun() }

        assertTrue(scrambles(99) > scrambles(10), "speed should drive the scramble roll")
    }

    @Test
    fun testUnpressuredQbNeverScrambles() {
        val outcomes = (1..40).map { play(qbSpeed = 99, rusher = null, seed = it) }

        assertTrue(outcomes.none { it.isRun() })
    }

    @Test
    fun testSackedQbNeverScrambles() {
        // The rusher starts inside the sack radius, so the QB is sacked on the first tick
        val outcomes = (1..40).map { play(qbSpeed = 99, rusher = rusher(1.0f, 3f), seed = it) }

        // A sack can also come with the (rare) strip-sack fumble
        assertTrue(
            outcomes.all { it.description().contains("SACKED") || it.description().contains("umble", ignoreCase = true) },
            outcomes.map { it.description() }.toSet().toString(),
        )
        assertEquals(0, outcomes.count { it.isRun() })
    }
}
