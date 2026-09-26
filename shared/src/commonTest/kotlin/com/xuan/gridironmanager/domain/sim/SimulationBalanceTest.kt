package com.xuan.gridironmanager.domain.sim

import com.xuan.gridironmanager.domain.model.PlayType
import com.xuan.gridironmanager.domain.sim.match.GamePhase
import com.xuan.gridironmanager.domain.sim.match.GameState
import com.xuan.gridironmanager.domain.sim.match.PlayOutcome
import com.xuan.gridironmanager.testMatchup
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the overall feel of the simulation: plays out seeded CPU-vs-CPU games and checks the box score stays in a
 * believable NFL-like range. If a change to the sim moves a number out of range, re-tune rather than widening the range.
 */
class SimulationBalanceTest {
    private class BoxScore {
        var games = 0
        var points = 0
        var passAttempts = 0
        var completions = 0
        var sacks = 0
        var interceptions = 0
        var rushes = 0
        var rushYards = 0
        var fieldGoalAttempts = 0
        var fieldGoalsMade = 0
        var extraPointAttempts = 0
        var extraPointsMade = 0
        var punts = 0
        var whistledDead = 0

        fun rate(
            part: Int,
            whole: Int,
        ) = part.toFloat() / whole.coerceAtLeast(1)
    }

    private val box: BoxScore by lazy { playGames(GAMES) }

    private fun playGames(count: Int): BoxScore {
        val box = BoxScore()
        repeat(count) { seed ->
            val random = Random(seed)
            val simulator = MatchSimulator(testMatchup(seed), random)
            var state = GameState.openingKickoff()
            while (!state.isGameOver) {
                val offense = simulator.playCaller.callOffense(state)
                val record = simulator.runPlay(state, offense, simulator.playCaller.callDefense(state))
                tally(box, state, offense.type, record.outcome)
                state = record.nextState
            }
            box.games++
            box.points += state.homeScore + state.awayScore
        }
        return box
    }

    private fun tally(
        box: BoxScore,
        state: GameState,
        playType: PlayType,
        outcome: PlayOutcome,
    ) {
        val description = outcome.description
        if (description == "Play whistled dead.") box.whistledDead++
        if (state.phase == GamePhase.EXTRA_POINT) {
            if (outcome is PlayOutcome.FieldGoal) {
                box.extraPointAttempts++
                if (outcome.result.isGood) box.extraPointsMade++
            }
            return
        }
        when (playType) {
            PlayType.PASS -> {
                box.passAttempts++
                when {
                    description.contains("SACKED") -> box.sacks++
                    description == "INTERCEPTED!" -> box.interceptions++
                    description.startsWith("Pass complete") || description.contains("catch and run") -> box.completions++
                }
            }

            PlayType.RUN -> {
                box.rushes++
                box.rushYards += (outcome as PlayOutcome.Scrimmage).result.yardsGained
            }

            PlayType.FIELD_GOAL -> {
                box.fieldGoalAttempts++
                if ((outcome as PlayOutcome.FieldGoal).result.isGood) box.fieldGoalsMade++
            }

            PlayType.PUNT -> {
                box.punts++
            }

            PlayType.KICKOFF -> {}
        }
    }

    private fun assertInRange(
        name: String,
        value: Float,
        range: ClosedFloatingPointRange<Float>,
    ) = assertTrue(value in range, "$name was $value, expected $range")

    @Test
    fun testNoPlayHitsTheSafetyTimeout() = assertEquals(0, box.whistledDead)

    @Test
    fun testScoring() = assertInRange("Points per game", box.points.toFloat() / box.games, 28f..65f)

    @Test
    fun testPassing() {
        assertInRange("Completion rate", box.rate(box.completions, box.passAttempts), 0.52f..0.72f)
        assertInRange("Sack rate", box.rate(box.sacks, box.passAttempts), 0.03f..0.1f)
        assertInRange("Interception rate", box.rate(box.interceptions, box.passAttempts), 0.01f..0.05f)
    }

    @Test
    fun testRushing() = assertInRange("Yards per carry", box.rate(box.rushYards, box.rushes), 3.3f..5.5f)

    @Test
    fun testKicking() {
        assertInRange("Field goal rate", box.rate(box.fieldGoalsMade, box.fieldGoalAttempts), 0.7f..0.95f)
        assertInRange("Extra point rate", box.rate(box.extraPointsMade, box.extraPointAttempts), 0.85f..0.99f)
        assertInRange("Punts per game", box.punts.toFloat() / box.games, 4f..16f)
    }

    private companion object {
        const val GAMES = 30
    }
}
