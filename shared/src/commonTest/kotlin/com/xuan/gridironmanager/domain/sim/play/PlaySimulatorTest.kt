package com.xuan.gridironmanager.domain.sim.play

import com.xuan.gridironmanager.domain.engine.LeagueGenerator
import com.xuan.gridironmanager.domain.model.PlayType
import com.xuan.gridironmanager.domain.model.Position
import com.xuan.gridironmanager.domain.model.Vector3D
import com.xuan.gridironmanager.domain.sim.match.GameState
import com.xuan.gridironmanager.domain.sim.movement.RunningPlayer
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PlaySimulatorTest {
    private val tick = 0.05f
    private val fieldCenterX = 26.65f

    private fun run(simulator: PlaySimulator): PlayOutcome {
        while (true) {
            simulator.tick(tick)?.let { return it }
        }
    }

    /** Simulates a scrimmage play between two seeded, generated rosters. */
    private fun simulateScrimmage(
        seed: Int,
        playType: PlayType,
    ): PlayOutcome {
        val random = Random(seed)
        val offenseRoster = LeagueGenerator.generatePlayersForTeam("OFF", random)
        val defenseRoster = LeagueGenerator.generatePlayersForTeam("DEF", random)
        val isAttackingUp = seed % 2 == 0
        val gameState = GameState(yardLine = 20 + seed % 50)
        val losWorldY = if (isAttackingUp) gameState.yardLine.toFloat() else 100f - gameState.yardLine

        val offense = PlaySetupHelper.createRunningPlayers(offenseRoster, PlaySetupHelper.getShotgunFormation(), losWorldY, true, isAttackingUp)
        val defense = PlaySetupHelper.createRunningPlayers(defenseRoster, PlaySetupHelper.getBaseDefense(), losWorldY, false, isAttackingUp)
        return run(PlaySimulator(offense, defense, playType, gameState, isAttackingUp, random))
    }

    @Test
    fun testUntouchedRunnerScoresInsteadOfTimingOut() {
        val gameState = GameState(yardLine = 95)
        val offense = PlaySetupHelper.createRunningPlayers(generatedRoster(), PlaySetupHelper.getShotgunFormation(), 95f, true, true)
        val simulator = PlaySimulator(offense, emptyList(), PlayType.RUN, gameState, isAttackingUp = true)

        val outcome = assertIs<PlayOutcome.Scrimmage>(run(simulator))

        assertTrue(outcome.result.isTouchdown)
        assertTrue(simulator.elapsedSec < PlaySimulator.MAX_PLAY_DURATION_SEC)
    }

    @Test
    fun testKickDistanceIsMeasuredFromTheKicker() {
        // Kick power 0 = 30 yards. From the kicking team's 35 the ball lands at the receiving 35, returned 15 yards.
        for (isAttackingUp in listOf(true, false)) {
            val kickerY = if (isAttackingUp) 35f else 65f
            val kicker = RunningPlayer("K", Vector3D(fieldCenterX, kickerY, 0f), 0f, null, position = Position.K, kickPower = 0)
            // Game state yard line deliberately differs from the kicker's spot
            val simulator = PlaySimulator(listOf(kicker), emptyList(), PlayType.KICK, GameState(yardLine = 25), isAttackingUp)

            val outcome = assertIs<PlayOutcome.Kick>(run(simulator))

            assertFalse(outcome.result.isTouchback)
            assertEquals(50, outcome.result.endYardLine)
        }
    }

    @Test
    fun testQbNeverTargetsLinemen() {
        // The only other offensive player is a wide open center: the QB has nobody eligible to throw to
        val qb = RunningPlayer("QB", Vector3D(fieldCenterX, 45f, 0f), 0f, null, position = Position.QB)
        val center = RunningPlayer("C", Vector3D(fieldCenterX, 50f, 0f), 0f, null, position = Position.C)
        val simulator = PlaySimulator(listOf(qb, center), emptyList(), PlayType.PASS, GameState(yardLine = 50), isAttackingUp = true)

        val outcome = run(simulator)

        assertEquals("Play whistled dead.", outcome.description)
    }

    @Test
    fun testPuntReturnerLinesUpDownfield() {
        val returnTeam = PlaySetupHelper.createRunningPlayers(generatedRoster(), PlaySetupHelper.getPuntReturnFormation(), 40f, false, true)

        val returner = returnTeam.first { it.position == Position.RB }

        assertEquals(85f, returner.currentPos.y)
    }

    @Test
    fun testSeededPassPlaysProduceAMixOfOutcomes() {
        val outcomes = (0 until 200).map { simulateScrimmage(it, PlayType.PASS) }

        fun share(predicate: (String) -> Boolean) = outcomes.count { predicate(it.description) } / outcomes.size.toFloat()

        assertEquals(0f, share { it == "Play whistled dead." }, "No pass play should hit the safety timeout")
        assertTrue(share { it.startsWith("Pass complete") || it.contains("TOUCHDOWN") } in 0.4f..0.9f)
        assertTrue(share { it.contains("SACKED") } in 0.01f..0.2f)
        assertTrue(share { it == "INTERCEPTED!" } in 0.001f..0.1f)
        assertTrue(share { it == "Pass broken up!" } > 0f)
    }

    @Test
    fun testSeededRunPlaysGainRealisticYardage() {
        val results = (0 until 200).map { assertIs<PlayOutcome.Scrimmage>(simulateScrimmage(it, PlayType.RUN)).result }

        val averageYards = results.map { it.yardsGained }.average()
        assertTrue(averageYards in 2.0..8.0, "Average run was $averageYards yards")
        assertTrue(results.any { it.yardsGained <= 0 }, "Some runs should be stuffed")
    }

    private fun generatedRoster() = LeagueGenerator.generatePlayersForTeam("T", Random(1))
}
