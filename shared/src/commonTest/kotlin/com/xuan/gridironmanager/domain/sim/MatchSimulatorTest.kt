package com.xuan.gridironmanager.domain.sim

import com.xuan.gridironmanager.domain.sim.match.GameState
import com.xuan.gridironmanager.testMatchup
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MatchSimulatorTest {
    @Test
    fun testSimulatedGameFinishes() {
        val finalState = MatchSimulator(testMatchup(), Random(11)).simulateRestOfGame(GameState.openingKickoff())

        assertTrue(finalState.isGameOver)
        assertEquals(4, finalState.quarter)
        assertEquals(0, finalState.clockSeconds)
    }

    @Test
    fun testSameSeedPlaysTheSameGame() {
        fun play(seed: Int) = MatchSimulator(testMatchup(), Random(seed)).simulateRestOfGame(GameState.openingKickoff())

        assertEquals(play(21), play(21))
    }

    @Test
    fun testSimulatingFromMidGameKeepsTheScore() {
        val midGame = GameState(quarter = 4, clockSeconds = 120, homeScore = 21, awayScore = 17, yardLine = 40)

        val finalState = MatchSimulator(testMatchup(), Random(2)).simulateRestOfGame(midGame)

        assertTrue(finalState.homeScore >= 21 && finalState.awayScore >= 17)
    }
}
