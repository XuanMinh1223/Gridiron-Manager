package com.xuan.gridironmanager.domain.sim.playbook

import com.xuan.gridironmanager.domain.sim.match.GamePhase
import com.xuan.gridironmanager.domain.sim.match.GameState
import com.xuan.gridironmanager.testMatchup
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayCallerDisguiseTest {
    private val matchup = testMatchup()
    private val scrimmage = GameState(down = 2, distance = 6, yardLine = 40)

    @Test
    fun testCpuDisguisesAboutThirtyPercentOfEligibleScrimmageCalls() {
        val caller = PlayCaller(matchup, Random(3))
        val calls = (1..600).map { caller.callDefense(scrimmage) }

        // Only Cover 2 / Cover 3 picks have a disguised counterpart (Nickel, man and blitz picks never do)
        val disguisedIds =
            Playbook.disguisedCounterparts.values
                .flatten()
                .map { it.id }
        val eligibleIds = setOf(Playbook.COVER_2.id, Playbook.COVER_3.id) + disguisedIds
        val eligible = calls.filter { it.id in eligibleIds }
        val rate = eligible.count { it.disguise != null } / eligible.size.toFloat()

        assertTrue(eligible.size > 100, "only ${eligible.size} eligible picks")
        assertTrue(rate in 0.15f..0.45f, "disguise rate was $rate")
    }

    @Test
    fun testDisguisedCallsKeepTheTrueCoverageOfThePickedCall() {
        val caller = PlayCaller(matchup, Random(5))
        val disguised = (1..300).map { caller.callDefense(scrimmage) }.filter { it.disguise != null }

        assertTrue(disguised.isNotEmpty())
        assertTrue(disguised.all { it in Playbook.defenseOptions })
        assertTrue(disguised.all { it.formation == Formations.BASE_4_3 && it.blitzers.isEmpty() })
    }

    @Test
    fun testTwoPointTriesCanBeDisguised() {
        val caller = PlayCaller(matchup, Random(9))
        val calls = (1..300).map { caller.callDefense(GameState(phase = GamePhase.EXTRA_POINT, yardLine = 98)) }

        assertTrue(calls.any { it.disguise != null })
    }

    @Test
    fun testNeverDisguisesOnKickoff() {
        val caller = PlayCaller(matchup, Random(11))
        val calls = (1..300).map { caller.callDefense(GameState(phase = GamePhase.KICKOFF, yardLine = 35)) }

        assertEquals(0, calls.count { it.disguise != null })
    }

    @Test
    fun testNeverReturnsASpecialTeamsUnit() {
        val caller = PlayCaller(matchup, Random(13))
        val calls = (1..300).map { caller.callDefense(scrimmage) }
        val specialTeams = setOf(Playbook.KICK_RETURN, Playbook.PUNT_RETURN, Playbook.FIELD_GOAL_BLOCK)

        assertTrue(calls.none { it in specialTeams })
        assertEquals(0, calls.count { it.coverage == null })
    }
}
