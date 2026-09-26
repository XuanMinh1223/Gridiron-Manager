package com.xuan.gridironmanager.domain.sim.match

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DriveEngineTest {
    private val engine = DriveEngine()

    private fun play(
        yards: Int,
        isTouchdown: Boolean = false,
        isTurnover: Boolean = false,
        clockStops: Boolean = false,
    ) = PlayResult(yardsGained = yards, description = "Play", isTouchdown = isTouchdown, isTurnover = isTurnover, clockStops = clockStops)

    private fun touchback() = KickResult(endYardLine = 0, description = "Touchback", isTouchback = true, isOutOfBounds = false)

    @Test
    fun testFirstDownReset() {
        val initialState = GameState(down = 1, distance = 10, yardLine = 25)

        val nextState = engine.resolvePlay(initialState, play(12))

        assertEquals(1, nextState.down)
        assertEquals(10, nextState.distance)
        assertEquals(37, nextState.yardLine)
    }

    @Test
    fun testSecondDown() {
        val initialState = GameState(down = 1, distance = 10, yardLine = 25)

        val nextState = engine.resolvePlay(initialState, play(4))

        assertEquals(2, nextState.down)
        assertEquals(6, nextState.distance)
        assertEquals(29, nextState.yardLine)
    }

    @Test
    fun testFirstAndGoalInsideTheTen() {
        val initialState = GameState(down = 1, distance = 10, yardLine = 85)

        val nextState = engine.resolvePlay(initialState, play(10))

        assertEquals(95, nextState.yardLine)
        assertEquals(5, nextState.distance) // 1st & Goal from the 5
    }

    @Test
    fun testTurnoverOnDowns() {
        val initialState = GameState(down = 4, distance = 5, yardLine = 50)

        val nextState = engine.resolvePlay(initialState, play(2))

        assertEquals(1, nextState.down)
        assertEquals(10, nextState.distance)
        assertEquals(48, nextState.yardLine) // Possession flips: 100 - (50 + 2) = 48
        assertFalse(nextState.isHomePossession)
    }

    @Test
    fun testInterceptionInOwnEndZoneIsTouchback() {
        val initialState = GameState(yardLine = 90, isHomePossession = true)

        // Picked off 5 yards deep in the defense's end zone
        val nextState = engine.resolvePlay(initialState, play(15, isTurnover = true))

        assertFalse(nextState.isHomePossession)
        assertEquals(Rules.INTERCEPTION_TOUCHBACK_YARD_LINE, nextState.yardLine)
    }

    @Test
    fun testTouchdownScoringTeamKicksOff() {
        val initialState = GameState(homeScore = 0, isHomePossession = true)

        val nextState = engine.resolvePlay(initialState, play(50, isTouchdown = true))

        assertEquals(7, nextState.homeScore)
        assertEquals(1, nextState.down)
        assertEquals(Rules.KICKOFF_YARD_LINE, nextState.yardLine)
        assertTrue(nextState.isHomePossession, "The scoring team keeps the ball to kick off")
        assertTrue(nextState.isKickoffPending)
    }

    @Test
    fun testKickoffAfterTouchdownGoesToTheTeamThatWasScoredOn() {
        val afterTouchdown = engine.resolvePlay(GameState(isHomePossession = true), play(80, isTouchdown = true))

        val afterKickoff = engine.resolveKickoff(afterTouchdown, touchback())

        assertFalse(afterKickoff.isHomePossession)
        assertFalse(afterKickoff.isKickoffPending)
        assertEquals(Rules.KICKOFF_TOUCHBACK_YARD_LINE, afterKickoff.yardLine)
    }

    @Test
    fun testClockRunsOffBetweenPlaysWhenItKeepsRunning() {
        val initialState = GameState(clockSeconds = 600)

        val nextState = engine.resolvePlay(initialState, play(3))

        assertEquals(600 - Rules.BETWEEN_PLAY_RUNOFF_SEC, nextState.clockSeconds)
    }

    @Test
    fun testNoRunoffWhenClockStops() {
        val initialState = GameState(clockSeconds = 600)

        val nextState = engine.resolvePlay(initialState, play(0, clockStops = true))

        assertEquals(600, nextState.clockSeconds)
    }

    @Test
    fun testQuarterTransition() {
        val initialState = GameState(clockSeconds = 0, quarter = 1)

        val nextState = engine.resolvePlay(initialState, play(5))

        assertEquals(2, nextState.quarter)
        assertEquals(Rules.QUARTER_LENGTH_SEC, nextState.clockSeconds)
    }

    @Test
    fun testQuarterAdvancesAfterPunt() {
        val initialState = GameState(clockSeconds = 0, quarter = 1)

        val nextState = engine.resolvePunt(initialState, touchback())

        assertEquals(2, nextState.quarter)
    }

    @Test
    fun testHalftimeAwayTeamKicksOff() {
        val initialState = GameState(clockSeconds = 10, quarter = 2, down = 3, yardLine = 60, isHomePossession = true)

        val nextState = engine.resolvePlay(initialState, play(4))

        assertEquals(3, nextState.quarter)
        assertEquals(Rules.QUARTER_LENGTH_SEC, nextState.clockSeconds)
        assertFalse(nextState.isHomePossession)
        assertTrue(nextState.isKickoffPending)
        assertEquals(Rules.KICKOFF_YARD_LINE, nextState.yardLine)
    }

    @Test
    fun testGameEndsWhenFourthQuarterExpires() {
        val initialState = GameState(clockSeconds = 10, quarter = 4, homeScore = 14, awayScore = 7)

        val nextState = engine.resolvePlay(initialState, play(4))

        assertTrue(nextState.isGameOver)
        assertEquals(0, nextState.clockSeconds)
        assertEquals(4, nextState.quarter)
    }

    @Test
    fun testNothingHappensAfterGameOver() {
        val finalState = GameState(clockSeconds = 0, quarter = 4, isGameOver = true)

        assertEquals(finalState, engine.resolvePlay(finalState, play(80, isTouchdown = true)))
        assertEquals(finalState, engine.resolveKickoff(finalState, touchback()))
    }

    @Test
    fun testKickoffTouchback() {
        val initialState = GameState(isHomePossession = true)

        val nextState = engine.resolveKickoff(initialState, touchback())

        assertEquals(25, nextState.yardLine)
        assertEquals(false, nextState.isHomePossession)
        assertEquals(1, nextState.down)
    }

    @Test
    fun testPuntTouchback() {
        val initialState = GameState(isHomePossession = true)

        val nextState = engine.resolvePunt(initialState, touchback())

        assertEquals(20, nextState.yardLine)
        assertEquals(false, nextState.isHomePossession)
        assertEquals(1, nextState.down)
    }
}
