package com.xuan.gridironmanager.domain.sim.play

import com.xuan.gridironmanager.domain.sim.match.DriveEngine
import com.xuan.gridironmanager.domain.sim.match.GameState
import com.xuan.gridironmanager.domain.sim.match.PlayOutcome
import com.xuan.gridironmanager.domain.sim.match.PlayResult
import com.xuan.gridironmanager.domain.sim.match.Rules
import com.xuan.gridironmanager.domain.sim.playbook.OffensivePlay
import com.xuan.gridironmanager.domain.sim.playbook.PlayCaller
import com.xuan.gridironmanager.domain.sim.playbook.Playbook
import com.xuan.gridironmanager.domain.sim.playbook.SnapBuilder
import com.xuan.gridironmanager.testMatchup
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PlaySimulatorKneelSpikeTest {
    private val matchup = testMatchup()
    private val tick = 0.05f
    private val homeBall = GameState(quarter = 4, clockSeconds = 30, yardLine = 50, homeScore = 17, awayScore = 14, isHomePossession = true)

    private fun run(
        play: OffensivePlay,
        state: GameState = homeBall,
    ): Pair<PlayResult, Float> {
        val simulator = PlaySimulator(SnapBuilder.build(state, matchup, play, Playbook.COVER_3), Random(1))
        while (true) {
            simulator.tick(tick)?.let { return assertIs<PlayOutcome.Scrimmage>(it).result to simulator.elapsedSec }
        }
    }

    @Test
    fun testKneelLosesAYardAndKeepsTheClockRunning() {
        val (result, elapsed) = run(Playbook.KNEEL)

        assertEquals(-1, result.yardsGained)
        assertEquals("QB takes a knee.", result.description)
        assertTrue(!result.clockStops)
        assertEquals(0.5f, elapsed, tick + 0.001f)
        assertTrue(result.actors.carrierId!!.startsWith("QB_"))
    }

    @Test
    fun testSpikeIsAnIncompletionThatStopsTheClock() {
        val (result, elapsed) = run(Playbook.SPIKE)

        assertEquals(0, result.yardsGained)
        assertTrue(result.clockStops)
        assertEquals(0.3f, elapsed, tick + 0.001f)
        assertTrue(result.actors.passerId!!.startsWith("QB_"))
        assertTrue(!result.isPassComplete)
    }

    @Test
    fun testSpikeSavesTheRunoffAndKneelDoesNot() {
        val engine = DriveEngine()
        val state = homeBall.copy(clockSeconds = 600, quarter = 2, down = 1, distance = 10)

        val spiked = engine.resolve(state, PlayOutcome.Scrimmage(run(Playbook.SPIKE).first), playDurationSec = 1f)
        val kneeled = engine.resolve(state, PlayOutcome.Scrimmage(run(Playbook.KNEEL).first), playDurationSec = 1f)

        assertEquals(599, spiked.clockSeconds, "a spike stops the clock: no between-play runoff")
        assertEquals(599 - Rules.BETWEEN_PLAY_RUNOFF_SEC, kneeled.clockSeconds, "a kneel burns the normal runoff")
        assertEquals(2, spiked.down, "a spike uses up a down")
    }

    @Test
    fun testTheyAreOfferedToTheUserButNeverInTheRandomPool() {
        assertTrue(Playbook.KNEEL in Playbook.offensivePlaysFor(com.xuan.gridironmanager.domain.sim.match.GamePhase.SCRIMMAGE))
        assertTrue(Playbook.SPIKE in Playbook.offensivePlaysFor(com.xuan.gridironmanager.domain.sim.match.GamePhase.SCRIMMAGE))
        assertTrue(Playbook.KNEEL !in Playbook.scrimmagePlays && Playbook.SPIKE !in Playbook.scrimmagePlays)

        val caller = PlayCaller(matchup, Random(2))
        val neutral = GameState(quarter = 2, clockSeconds = 600, down = 1, yardLine = 40)
        val calls = (1..200).map { caller.callOffense(neutral) }

        assertTrue(calls.none { it == Playbook.KNEEL || it == Playbook.SPIKE })
    }

    @Test
    fun testCpuKneelsWhenLeadingLate() {
        val caller = PlayCaller(matchup, Random(3))

        assertEquals(Playbook.KNEEL, caller.callOffense(homeBall))
        assertEquals(Playbook.KNEEL, caller.callOffense(homeBall.copy(down = 3)))
        assertTrue(caller.callOffense(homeBall.copy(down = 4)) != Playbook.KNEEL, "never kneel on 4th down")
        assertTrue(caller.callOffense(homeBall.copy(clockSeconds = 200)) != Playbook.KNEEL, "too much time left")
        assertTrue(caller.callOffense(homeBall.copy(quarter = 1)) != Playbook.KNEEL, "not at the end of a half")
        assertTrue(caller.callOffense(homeBall.copy(yardLine = 1)) != Playbook.KNEEL, "no kneel into your own end zone")
    }

    @Test
    fun testCpuSpikesWhenTrailingLateWithNoTimeouts() {
        val caller = PlayCaller(matchup, Random(4))
        val trailing = homeBall.copy(homeScore = 10, awayScore = 14, clockSeconds = 40, homeTimeouts = 0)

        assertEquals(Playbook.SPIKE, caller.callOffense(trailing))
        assertTrue(caller.callOffense(trailing.copy(homeTimeouts = 1)) != Playbook.SPIKE, "it has a timeout to use instead")
        assertTrue(caller.callOffense(trailing.copy(down = 4)) != Playbook.SPIKE, "never spike on 4th down")
        assertTrue(caller.callOffense(trailing.copy(homeScore = 0)) != Playbook.SPIKE, "down by more than a score")
    }
}
