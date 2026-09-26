package com.xuan.gridironmanager.domain.sim.playbook

import com.xuan.gridironmanager.domain.model.PlayType
import com.xuan.gridironmanager.domain.sim.match.GamePhase
import com.xuan.gridironmanager.domain.sim.match.GameState
import com.xuan.gridironmanager.domain.sim.match.Rules
import com.xuan.gridironmanager.testMatchup
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayCallerTest {
    private val playCaller = PlayCaller(testMatchup(), Random(5))

    /** Calls the same situation many times, since play selection is randomised. */
    private fun callsFor(state: GameState) = (1..50).map { playCaller.callOffense(state) }.toSet()

    @Test
    fun testKickoffPhaseAlwaysKicksOff() {
        assertEquals(setOf(Playbook.KICKOFF), callsFor(GameState.openingKickoff()))
    }

    @Test
    fun testKicksTheExtraPointNormally() {
        val tryState = GameState(phase = GamePhase.EXTRA_POINT, quarter = 2, homeScore = 13, awayScore = 3, yardLine = Rules.TWO_POINT_YARD_LINE)

        assertEquals(setOf(Playbook.EXTRA_POINT), callsFor(tryState))
    }

    @Test
    fun testGoesForTwoWhenTrailingByTwoLate() {
        // Just scored to trail 12-14 in the fourth quarter: two points ties it
        val tryState = GameState(phase = GamePhase.EXTRA_POINT, quarter = 4, homeScore = 12, awayScore = 14, yardLine = Rules.TWO_POINT_YARD_LINE)

        assertTrue(callsFor(tryState).all { it.type == PlayType.RUN || it.type == PlayType.PASS })
    }

    @Test
    fun testKicksFieldGoalOnFourthDownInRange() {
        assertEquals(setOf(Playbook.FIELD_GOAL), callsFor(GameState(down = 4, distance = 6, yardLine = 75)))
    }

    @Test
    fun testPuntsOnFourthAndLongInOwnTerritory() {
        assertEquals(setOf(Playbook.PUNT), callsFor(GameState(down = 4, distance = 10, yardLine = 30)))
    }

    @Test
    fun testGoesForItOnFourthAndInchesPastMidfield() {
        assertTrue(callsFor(GameState(down = 4, distance = 1, yardLine = 55)).none { it == Playbook.PUNT || it == Playbook.FIELD_GOAL })
    }

    @Test
    fun testGoesForItWhenTrailingLateInsteadOfPunting() {
        val desperate = GameState(down = 4, distance = 8, yardLine = 30, quarter = 4, clockSeconds = 90, homeScore = 10, awayScore = 21)

        assertTrue(callsFor(desperate).none { it == Playbook.PUNT })
    }

    @Test
    fun testMixesRunsAndPassesOnEarlyDowns() {
        val types = callsFor(GameState(down = 1, distance = 10, yardLine = 40)).map { it.type }.toSet()

        assertEquals(setOf(PlayType.RUN, PlayType.PASS), types)
    }

    @Test
    fun testDefenseAlwaysPicksFromTheScrimmageCalls() {
        val calls = (1..50).map { playCaller.callDefense(GameState(down = 3, distance = 12)) }

        assertTrue(calls.all { it in Playbook.defensiveCalls })
    }
}
