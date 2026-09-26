package com.xuan.gridironmanager.domain.sim.match

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DriveEngineTest {
    private val engine = DriveEngine()

    private fun play(
        yards: Int,
        isTouchdown: Boolean = false,
        isTurnover: Boolean = false,
        clockStops: Boolean = false,
    ) = PlayOutcome.Scrimmage(PlayResult(yards, "Play", isTouchdown = isTouchdown, isTurnover = isTurnover, clockStops = clockStops))

    private fun touchback() = PlayOutcome.Kick(KickResult(endYardLine = 0, description = "Touchback", isTouchback = true, isOutOfBounds = false))

    private fun fieldGoal(isGood: Boolean) = PlayOutcome.FieldGoal(FieldGoalResult(isGood, distanceYds = 40, description = "Kick"))

    private fun extraPointTry(
        homeScore: Int = 6,
        awayScore: Int = 0,
    ) = GameState(homeScore = homeScore, awayScore = awayScore, yardLine = Rules.TWO_POINT_YARD_LINE, phase = GamePhase.EXTRA_POINT)

    // Down and distance

    @Test
    fun testFirstDownReset() {
        val nextState = engine.resolve(GameState(down = 1, distance = 10, yardLine = 25), play(12))

        assertEquals(1, nextState.down)
        assertEquals(10, nextState.distance)
        assertEquals(37, nextState.yardLine)
    }

    @Test
    fun testSecondDown() {
        val nextState = engine.resolve(GameState(down = 1, distance = 10, yardLine = 25), play(4))

        assertEquals(2, nextState.down)
        assertEquals(6, nextState.distance)
        assertEquals(29, nextState.yardLine)
    }

    @Test
    fun testFirstAndGoalInsideTheTen() {
        val nextState = engine.resolve(GameState(down = 1, distance = 10, yardLine = 85), play(10))

        assertEquals(95, nextState.yardLine)
        assertEquals(5, nextState.distance) // 1st & Goal from the 5
    }

    @Test
    fun testTurnoverOnDowns() {
        val nextState = engine.resolve(GameState(down = 4, distance = 5, yardLine = 50), play(2))

        assertEquals(1, nextState.down)
        assertEquals(10, nextState.distance)
        assertEquals(48, nextState.yardLine) // Possession flips: 100 - (50 + 2) = 48
        assertFalse(nextState.isHomePossession)
    }

    // Turnovers

    @Test
    fun testInterceptionInOwnEndZoneIsTouchback() {
        // Picked off 5 yards deep in the defense's end zone
        val nextState = engine.resolve(GameState(yardLine = 90, isHomePossession = true), play(15, isTurnover = true))

        assertFalse(nextState.isHomePossession)
        assertEquals(Rules.INTERCEPTION_TOUCHBACK_YARD_LINE, nextState.yardLine)
    }

    @Test
    fun testFumbleRecoveredByDefenseInOffensesEndZoneIsDefensiveTouchdown() {
        val nextState = engine.resolve(GameState(yardLine = 3, isHomePossession = true), play(-5, isTurnover = true))

        assertEquals(0, nextState.homeScore)
        assertEquals(Rules.TOUCHDOWN_POINTS, nextState.awayScore)
        assertFalse(nextState.isHomePossession, "The scoring defense attempts the try")
        assertEquals(GamePhase.EXTRA_POINT, nextState.phase)
    }

    // Touchdowns and tries

    @Test
    fun testTouchdownIsSixPointsFollowedByATry() {
        val nextState = engine.resolve(GameState(homeScore = 0, isHomePossession = true), play(50, isTouchdown = true))

        assertEquals(6, nextState.homeScore)
        assertEquals(GamePhase.EXTRA_POINT, nextState.phase)
        assertEquals(Rules.TWO_POINT_YARD_LINE, nextState.yardLine)
        assertTrue(nextState.isHomePossession, "The scoring team attempts the try")
    }

    @Test
    fun testGoodExtraPointAddsOneAndScorerKicksOff() {
        val nextState = engine.resolve(extraPointTry(), fieldGoal(isGood = true))

        assertEquals(7, nextState.homeScore)
        assertEquals(GamePhase.KICKOFF, nextState.phase)
        assertEquals(Rules.KICKOFF_YARD_LINE, nextState.yardLine)
        assertTrue(nextState.isHomePossession)
    }

    @Test
    fun testMissedExtraPointAddsNothing() {
        val nextState = engine.resolve(extraPointTry(), fieldGoal(isGood = false))

        assertEquals(6, nextState.homeScore)
        assertEquals(GamePhase.KICKOFF, nextState.phase)
    }

    @Test
    fun testSuccessfulTwoPointConversion() {
        val nextState = engine.resolve(extraPointTry(), play(2, isTouchdown = true))

        assertEquals(8, nextState.homeScore)
        assertEquals(GamePhase.KICKOFF, nextState.phase)
    }

    @Test
    fun testFailedTwoPointConversionIncludingTurnover() {
        assertEquals(6, engine.resolve(extraPointTry(), play(1)).homeScore)

        val afterTurnover = engine.resolve(extraPointTry(), play(0, isTurnover = true))
        assertEquals(6, afterTurnover.homeScore)
        assertEquals(0, afterTurnover.awayScore)
        assertTrue(afterTurnover.isHomePossession, "The team that scored still kicks off")
    }

    @Test
    fun testTryIsUntimedAndHappensEvenAfterTimeExpires() {
        val touchdownAtTheGun = GameState(quarter = 4, clockSeconds = 3, homeScore = 10, awayScore = 14)

        val afterTouchdown = engine.resolve(touchdownAtTheGun, play(40, isTouchdown = true), playDurationSec = 6f)
        assertFalse(afterTouchdown.isGameOver, "The try is still to come")
        assertEquals(0, afterTouchdown.clockSeconds)

        val afterTry = engine.resolve(afterTouchdown, fieldGoal(isGood = true), playDurationSec = 2f)
        assertEquals(17, afterTry.homeScore)
        assertTrue(afterTry.isGameOver)
    }

    @Test
    fun testKickoffAfterTouchdownGoesToTheTeamThatWasScoredOn() {
        val afterTry = engine.resolve(extraPointTry(), fieldGoal(isGood = true))

        val afterKickoff = engine.resolve(afterTry, touchback())

        assertFalse(afterKickoff.isHomePossession)
        assertEquals(GamePhase.SCRIMMAGE, afterKickoff.phase)
        assertEquals(Rules.KICKOFF_TOUCHBACK_YARD_LINE, afterKickoff.yardLine)
    }

    // Field goals

    @Test
    fun testGoodFieldGoalIsThreePointsAndScorerKicksOff() {
        val nextState = engine.resolve(GameState(yardLine = 75, down = 4, isHomePossession = true), fieldGoal(isGood = true))

        assertEquals(3, nextState.homeScore)
        assertEquals(GamePhase.KICKOFF, nextState.phase)
        assertTrue(nextState.isHomePossession)
    }

    @Test
    fun testMissedFieldGoalGivesDefenseTheBallAtTheSpotOfTheKick() {
        // Snapped from the opponent's 40: the kick is from the 47, so the defense takes over at its own 47
        val nextState = engine.resolve(GameState(yardLine = 60, down = 4), fieldGoal(isGood = false))

        assertFalse(nextState.isHomePossession)
        assertEquals(47, nextState.yardLine)
    }

    @Test
    fun testMissedFieldGoalInsideTheTwentyGivesDefenseTheBallAtTheTwenty() {
        val nextState = engine.resolve(GameState(yardLine = 90, down = 4), fieldGoal(isGood = false))

        assertEquals(Rules.MISSED_FIELD_GOAL_MIN_YARD_LINE, nextState.yardLine)
    }

    // Safeties

    @Test
    fun testTackledInOwnEndZoneIsSafety() {
        val nextState = engine.resolve(GameState(yardLine = 2, isHomePossession = true, homeScore = 7, awayScore = 3), play(-4))

        assertEquals(7, nextState.homeScore)
        assertEquals(3 + Rules.SAFETY_POINTS, nextState.awayScore)
        assertEquals(GamePhase.KICKOFF, nextState.phase)
        assertTrue(nextState.isHomePossession, "The team that gave up the safety free-kicks")
        assertEquals(Rules.SAFETY_KICK_YARD_LINE, nextState.yardLine)
    }

    // Clock

    @Test
    fun testPlayDurationAndBetweenPlayRunoffComeOffTheClock() {
        val nextState = engine.resolve(GameState(clockSeconds = 600), play(3), playDurationSec = 4.4f)

        assertEquals(600 - 4 - Rules.BETWEEN_PLAY_RUNOFF_SEC, nextState.clockSeconds)
    }

    @Test
    fun testNoRunoffWhenClockStops() {
        val nextState = engine.resolve(GameState(clockSeconds = 600), play(0, clockStops = true), playDurationSec = 2f)

        assertEquals(598, nextState.clockSeconds)
    }

    @Test
    fun testQuarterTransition() {
        val nextState = engine.resolve(GameState(clockSeconds = 0, quarter = 1), play(5))

        assertEquals(2, nextState.quarter)
        assertEquals(Rules.QUARTER_LENGTH_SEC, nextState.clockSeconds)
    }

    @Test
    fun testQuarterAdvancesAfterPunt() {
        val nextState = engine.resolve(GameState(clockSeconds = 3, quarter = 1), touchback(), playDurationSec = 5f)

        assertEquals(2, nextState.quarter)
    }

    @Test
    fun testHalftimeAwayTeamKicksOff() {
        val nextState = engine.resolve(GameState(clockSeconds = 10, quarter = 2, down = 3, yardLine = 60, isHomePossession = true), play(4))

        assertEquals(3, nextState.quarter)
        assertEquals(Rules.QUARTER_LENGTH_SEC, nextState.clockSeconds)
        assertFalse(nextState.isHomePossession)
        assertEquals(GamePhase.KICKOFF, nextState.phase)
        assertEquals(Rules.KICKOFF_YARD_LINE, nextState.yardLine)
    }

    @Test
    fun testGameEndsWhenFourthQuarterExpires() {
        val nextState = engine.resolve(GameState(clockSeconds = 10, quarter = 4, homeScore = 14, awayScore = 7), play(4))

        assertTrue(nextState.isGameOver)
        assertEquals(0, nextState.clockSeconds)
        assertEquals(4, nextState.quarter)
    }

    @Test
    fun testNothingHappensAfterGameOver() {
        val finalState = GameState(clockSeconds = 0, quarter = 4, isGameOver = true)

        assertEquals(finalState, engine.resolve(finalState, play(80, isTouchdown = true)))
        assertEquals(finalState, engine.resolve(finalState, touchback()))
    }

    // Kicks

    @Test
    fun testKickoffTouchback() {
        val nextState = engine.resolve(GameState.openingKickoff(), touchback())

        assertEquals(25, nextState.yardLine)
        assertFalse(nextState.isHomePossession)
        assertEquals(1, nextState.down)
    }

    @Test
    fun testPuntTouchback() {
        val nextState = engine.resolve(GameState(isHomePossession = true, down = 4), touchback())

        assertEquals(20, nextState.yardLine)
        assertFalse(nextState.isHomePossession)
        assertEquals(1, nextState.down)
    }

    @Test
    fun testOutcomeThatCannotHappenInPhaseIsRejected() {
        assertFailsWith<IllegalArgumentException> { engine.resolve(GameState.openingKickoff(), play(5)) }
        assertFailsWith<IllegalArgumentException> { engine.resolve(extraPointTry(), touchback()) }
    }
}
