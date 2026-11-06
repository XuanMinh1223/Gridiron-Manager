package com.xuan.gridironmanager.domain.sim.match

import com.xuan.gridironmanager.domain.model.PlayType
import com.xuan.gridironmanager.domain.sim.MatchSimulator
import com.xuan.gridironmanager.testMatchup
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GameStatsTest {
    private class Tally {
        var completions = 0
        var sacks = 0
        var interceptions = 0
        var rushYards = 0
        var returnYards = 0
        var plays = 0
    }

    /** Plays a seeded CPU game through [MatchSimulator.runPlay], tallying the outcomes independently of the tracker. */
    private fun playGame(seed: Int): Pair<MatchSimulator, Tally> {
        val simulator = MatchSimulator(testMatchup(seed), Random(seed))
        val tally = Tally()
        var state = GameState.openingKickoff()
        while (!state.isGameOver) {
            val offense = simulator.playCaller.callOffense(state)
            val record = simulator.runPlay(state, offense, simulator.playCaller.callDefense(state))
            when (val outcome = record.outcome) {
                is PlayOutcome.Scrimmage -> {
                    val result = outcome.result
                    if (result.isPassComplete) tally.completions++
                    if (result.isSack) tally.sacks++
                    if (result.actors.interceptorId != null) tally.interceptions++
                    if (result.isScramble || offense.type == PlayType.RUN || offense.type == PlayType.KNEEL) tally.rushYards += result.yardsGained
                }

                is PlayOutcome.Kick -> {
                    if (outcome.result.returnerId != null) tally.returnYards += outcome.result.returnYards
                }

                is PlayOutcome.FieldGoal -> {}
            }
            tally.plays++
            state = record.nextState
        }
        return simulator to tally
    }

    @Test
    fun testTrackerReconcilesWithTheOutcomesOfTheGame() {
        for (seed in 1..5) {
            val (simulator, tally) = playGame(seed)
            val players = simulator.gameStats.players.values

            assertEquals(tally.completions, players.sumOf { it.completions }, "completions, seed $seed")
            assertEquals(tally.completions, players.sumOf { it.receptions }, "every completion has a receiver, seed $seed")
            assertEquals(tally.sacks, players.sumOf { it.sacks }, "sacks, seed $seed")
            assertEquals(tally.interceptions, players.sumOf { it.interceptions }, "interceptions, seed $seed")
            assertEquals(tally.interceptions, players.sumOf { it.interceptionsThrown }, "thrown vs taken, seed $seed")
            assertEquals(tally.rushYards, players.sumOf { it.rushYards }, "rush yards, seed $seed")
            assertEquals(tally.returnYards, players.sumOf { it.returnYards }, "return yards, seed $seed")
        }
    }

    @Test
    fun testEveryStatBelongsToARealPlayerOnTheRightTeam() {
        val matchup = testMatchup(2)
        val (simulator, _) = playGame(2)
        val roster = (matchup.homeRoster + matchup.awayRoster).associateBy { it.id }

        assertTrue(simulator.gameStats.players.isNotEmpty())
        for ((id, line) in simulator.gameStats.players) {
            val player = assertNotNull(roster[id], "$id is not on either roster")
            assertEquals(player.teamId, line.teamId)
            assertEquals(player.fullName, line.name)
        }
    }

    @Test
    fun testCountingStatsNeverGoNegative() {
        val (simulator, _) = playGame(3)

        // Yardage can legitimately be negative (a loss on a scramble or a run), so only the counts are checked
        for (line in simulator.gameStats.players.values) {
            val counts =
                listOf(
                    line.passAttempts,
                    line.completions,
                    line.passTouchdowns,
                    line.interceptionsThrown,
                    line.rushAttempts,
                    line.rushTouchdowns,
                    line.receptions,
                    line.receivingTouchdowns,
                    line.tackles,
                    line.sacks,
                    line.interceptions,
                    line.fumblesLost,
                    line.fumblesRecovered,
                    line.fieldGoalsMade,
                    line.fieldGoalAttempts,
                    line.extraPointsMade,
                    line.extraPointAttempts,
                    line.returnYards,
                    line.returnTouchdowns,
                )
            assertTrue(counts.all { it >= 0 }, "${line.name}: $line")
            assertTrue(line.completions <= line.passAttempts)
            assertTrue(line.fieldGoalsMade <= line.fieldGoalAttempts)
            assertTrue(line.extraPointsMade <= line.extraPointAttempts)
        }
    }

    @Test
    fun testScramblesAreRushesNotPassAttempts() {
        // Over many seeded games at least one scramble should show up as a QB rushing attempt
        var qbRushes = 0
        for (seed in 1..8) {
            val (simulator, _) = playGame(seed)
            val lines = simulator.gameStats.players.values
            qbRushes += lines.filter { it.position == "QB" }.sumOf { it.rushAttempts }
        }

        assertTrue(qbRushes > 0, "no QB ever scrambled in 8 games")
    }

    @Test
    fun testSackIsCreditedToTheDefenderOnly() {
        val (simulator, _) = playGame(4)

        val lines = simulator.gameStats.players.values
        val sackers = lines.filter { it.sacks > 0 }
        assertTrue(sackers.none { it.position == "QB" || it.position == "WR" }, "sacks go to defenders")
    }

    @Test
    fun testBoxScoreLeadersAndLines() {
        val stats =
            GameStats(
                mapOf(
                    "a" to PlayerGameStats("H", "a", "James Smith", "QB", passAttempts = 27, completions = 18, passYards = 214, passTouchdowns = 2),
                    "b" to PlayerGameStats("H", "b", "Bob Jones", "QB", passAttempts = 3, completions = 1, passYards = 9),
                    "c" to PlayerGameStats("A", "c", "Al Brown", "RB", rushAttempts = 20, rushYards = 88, rushTouchdowns = 1, returnYards = 30),
                    "d" to PlayerGameStats("A", "d", "Cy Young", "WR", receptions = 5, receivingYards = 71),
                ),
            )

        assertEquals("J. Smith — 18/27, 214 yds, 2 TD", stats.topPassers().first().passingLine())
        assertEquals(listOf("a", "b"), stats.topPassers().map { it.playerId })
        assertEquals("A. Brown — 20 car, 88 yds, 1 TD", stats.topRushers().single().rushingLine())
        assertEquals("C. Young — 5 rec, 71 yds, 0 TD", stats.topReceivers().single().receivingLine())
        assertEquals(TeamTotals(passYards = 223, rushYards = 0, returnYards = 0), stats.totalsFor("H"))
        assertEquals(TeamTotals(passYards = 0, rushYards = 88, returnYards = 30), stats.totalsFor("A"))
    }
}
