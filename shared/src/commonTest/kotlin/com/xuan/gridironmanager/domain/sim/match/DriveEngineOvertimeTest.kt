package com.xuan.gridironmanager.domain.sim.match

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DriveEngineOvertimeTest {
    private val engine = DriveEngine()

    private fun play(
        yards: Int,
        isTouchdown: Boolean = false,
    ) = PlayOutcome.Scrimmage(PlayResult(yards, "play", isTouchdown = isTouchdown, isTurnover = false))

    private fun lateInFourth(
        homeScore: Int,
        awayScore: Int,
    ) = GameState(quarter = 4, clockSeconds = 5, homeScore = homeScore, awayScore = awayScore, yardLine = 50, isHomePossession = true)

    private val overtime =
        GameState(quarter = 4, isOvertime = true, clockSeconds = 500, yardLine = 90, isHomePossession = true, homeScore = 20, awayScore = 20)

    @Test
    fun testTiedAtTheEndOfRegulationStartsOvertime() {
        val next = engine.resolve(lateInFourth(17, 17), play(3), playDurationSec = 10f)

        assertFalse(next.isGameOver)
        assertTrue(next.isOvertime)
        assertEquals(Rules.OVERTIME_LENGTH_SEC, next.clockSeconds)
        assertEquals(4, next.quarter)
        assertEquals(GamePhase.KICKOFF, next.phase)
        assertFalse(next.isHomePossession, "the home team kicked off to open the game, so the away team kicks off here")
        assertEquals(Rules.OVERTIME_TIMEOUTS, next.homeTimeouts)
        assertEquals(Rules.OVERTIME_TIMEOUTS, next.awayTimeouts)
    }

    @Test
    fun testEndOfRegulationWithALeaderStillEndsTheGame() {
        val next = engine.resolve(lateInFourth(21, 17), play(3), playDurationSec = 10f)

        assertTrue(next.isGameOver)
        assertFalse(next.isOvertime)
    }

    @Test
    fun testTouchdownInOvertimeEndsTheGameWithoutATry() {
        val next = engine.resolve(overtime, play(10, isTouchdown = true), playDurationSec = 5f)

        assertTrue(next.isGameOver)
        assertEquals(26, next.homeScore)
        assertEquals(20, next.awayScore)
        assertTrue(next.phase != GamePhase.EXTRA_POINT, "sudden death: no extra point")
    }

    @Test
    fun testFieldGoalInOvertimeEndsTheGame() {
        val good = PlayOutcome.FieldGoal(FieldGoalResult(isGood = true, distanceYds = 35, description = "good"))

        val next = engine.resolve(overtime, good, playDurationSec = 5f)

        assertTrue(next.isGameOver)
        assertEquals(23, next.homeScore)
    }

    @Test
    fun testSafetyInOvertimeEndsTheGame() {
        val next = engine.resolve(overtime.copy(yardLine = 3), play(-5), playDurationSec = 5f)

        assertTrue(next.isGameOver)
        assertEquals(22, next.awayScore, "two points to the defense")
    }

    @Test
    fun testMissedFieldGoalInOvertimeKeepsPlaying() {
        val missed = PlayOutcome.FieldGoal(FieldGoalResult(isGood = false, distanceYds = 50, description = "wide"))

        val next = engine.resolve(overtime, missed, playDurationSec = 5f)

        assertFalse(next.isGameOver)
    }

    @Test
    fun testStillTiedWhenOvertimeExpiresIsATie() {
        val next = engine.resolve(overtime.copy(clockSeconds = 5, yardLine = 40), play(3), playDurationSec = 10f)

        assertTrue(next.isGameOver)
        assertEquals(next.homeScore, next.awayScore)
    }
}
