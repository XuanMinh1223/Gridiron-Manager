package com.xuan.gridironmanager.domain.sim.match

import com.xuan.gridironmanager.domain.sim.MatchSimulator
import com.xuan.gridironmanager.testMatchup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DriveEngineTimeoutTest {
    private val engine = DriveEngine()
    private val gain = PlayOutcome.Scrimmage(PlayResult(5, "play", isTouchdown = false, isTurnover = false))
    private val midGame = GameState(quarter = 2, clockSeconds = 600, yardLine = 30, distance = 10)

    @Test
    fun testTimeoutSkipsTheBetweenPlayRunoff() {
        val withTimeout = engine.resolve(midGame.withTimeoutCalled(), gain, playDurationSec = 6f)
        val without = engine.resolve(midGame, gain, playDurationSec = 6f)

        assertEquals(600 - 6, withTimeout.clockSeconds, "only the play's own duration runs off")
        assertEquals(600 - 6 - Rules.BETWEEN_PLAY_RUNOFF_SEC, without.clockSeconds)
    }

    @Test
    fun testTheFlagIsConsumedByThePlay() {
        val first = engine.resolve(midGame.withTimeoutCalled(), gain, playDurationSec = 6f)
        assertFalse(first.timeoutCalled)

        val second = engine.resolve(first, gain, playDurationSec = 6f)
        assertEquals(first.clockSeconds - 6 - Rules.BETWEEN_PLAY_RUNOFF_SEC, second.clockSeconds, "the next play runs off normally")
    }

    @Test
    fun testTimeoutsResetAtHalftime() {
        val endOfHalf = GameState(quarter = 2, clockSeconds = 5, homeTimeouts = 0, awayTimeouts = 1, yardLine = 40)

        val next = engine.resolve(endOfHalf, gain, playDurationSec = 10f)

        assertEquals(3, next.quarter)
        assertEquals(Rules.TIMEOUTS_PER_HALF, next.homeTimeouts)
        assertEquals(Rules.TIMEOUTS_PER_HALF, next.awayTimeouts)
    }

    @Test
    fun testTimeoutsAreTwoEachInOvertime() {
        val tiedEnd = GameState(quarter = 4, clockSeconds = 5, yardLine = 40, homeTimeouts = 3, awayTimeouts = 0)

        val next = engine.resolve(tiedEnd, gain, playDurationSec = 10f)

        assertEquals(Rules.OVERTIME_TIMEOUTS, next.homeTimeouts)
        assertEquals(Rules.OVERTIME_TIMEOUTS, next.awayTimeouts)
    }

    @Test
    fun testCallingATimeoutSpendsOneForThePossessingTeam() {
        val home = GameState(isHomePossession = true).withTimeoutCalled()
        assertEquals(2, home.homeTimeouts)
        assertEquals(3, home.awayTimeouts)
        assertTrue(home.timeoutCalled)

        val away = GameState(isHomePossession = false).withTimeoutCalled()
        assertEquals(2, away.awayTimeouts)
        assertEquals(3, away.homeTimeouts)
    }

    @Test
    fun testTimeoutWithNoneLeftIsANoOp() {
        val state = GameState(isHomePossession = true, homeTimeouts = 0)

        assertEquals(state, state.withTimeoutCalled())
        assertEquals(0, state.withTimeoutCalled().homeTimeouts)
        assertFalse(state.withTimeoutCalled().timeoutCalled)
    }

    @Test
    fun testCallingTwiceDoesNotSpendTwo() {
        val once = GameState(isHomePossession = true).withTimeoutCalled()

        assertEquals(once, once.withTimeoutCalled())
    }

    @Test
    fun testCpuCallsATimeoutOnlyWhenTrailingLateWithTimeoutsLeft() {
        val simulator = MatchSimulator(testMatchup())
        val trailingLate = GameState(quarter = 4, clockSeconds = 100, homeScore = 10, awayScore = 14, isHomePossession = true, yardLine = 40)

        assertTrue(simulator.maybeCallTimeout(trailingLate).timeoutCalled)
        assertEquals(2, simulator.maybeCallTimeout(trailingLate).homeTimeouts)

        assertFalse(simulator.maybeCallTimeout(trailingLate.copy(homeScore = 21)).timeoutCalled, "leading")
        assertFalse(simulator.maybeCallTimeout(trailingLate.copy(clockSeconds = 500)).timeoutCalled, "too early")
        assertFalse(simulator.maybeCallTimeout(trailingLate.copy(quarter = 3)).timeoutCalled, "not the fourth quarter")
        assertFalse(simulator.maybeCallTimeout(trailingLate.copy(homeTimeouts = 0)).timeoutCalled, "none left")
        assertFalse(simulator.maybeCallTimeout(trailingLate.copy(phase = GamePhase.KICKOFF)).timeoutCalled, "kickoff")
    }

    @Test
    fun testCpuAndUserDoNotDoubleCount() {
        val simulator = MatchSimulator(testMatchup())
        val trailingLate = GameState(quarter = 4, clockSeconds = 100, homeScore = 10, awayScore = 14, isHomePossession = true, yardLine = 40)
        val userCalled = trailingLate.withTimeoutCalled()

        assertEquals(userCalled, simulator.maybeCallTimeout(userCalled))
    }
}
