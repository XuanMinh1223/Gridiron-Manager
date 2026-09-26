package com.xuan.gridironmanager.ui.match

import com.xuan.gridironmanager.domain.sim.match.GamePhase
import com.xuan.gridironmanager.domain.sim.match.GameState
import com.xuan.gridironmanager.domain.sim.playbook.Playbook
import com.xuan.gridironmanager.testMatchup
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MatchSimulationTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val matchup = testMatchup()

    private fun presenter(seed: Int = 1) = MatchPresenter(testScope, testDispatcher, Random(seed))

    private val homeFirstAndTen = GameState(yardLine = 30, isHomePossession = true)

    @Test
    fun testOpeningKickoffCompletes() =
        testScope.runTest {
            val presenter = presenter()
            presenter.startMatch(matchup)

            presenter.snapBall()
            advanceUntilIdle()

            val state = presenter.uiState.value
            assertFalse(state.isPlayRunning, "Kickoff simulation should have completed")
            assertEquals(GamePhase.SCRIMMAGE, state.gameState.phase)
            assertFalse(state.gameState.isHomePossession, "The away team receives the opening kickoff")
        }

    @Test
    fun testGameClockRunsInRealTimeDuringPlay() =
        testScope.runTest {
            val presenter = presenter()
            presenter.startMatch(matchup)

            presenter.snapBall()
            advanceUntilIdle()

            // A kick hangs for at most ~5.2 seconds, and the clock stops on the change of possession
            val clockSeconds = presenter.uiState.value.gameState.clockSeconds
            assertTrue(clockSeconds in 894..899, "Expected ~5s off the clock but it read $clockSeconds")
        }

    @Test
    fun testSnapIsIgnoredAfterGameOver() =
        testScope.runTest {
            val presenter = presenter()
            val finalState = GameState(quarter = 4, clockSeconds = 0, isGameOver = true)
            presenter.startMatch(matchup, finalState)

            presenter.snapBall()
            advanceUntilIdle()

            val state = presenter.uiState.value
            assertFalse(state.isPlayRunning)
            assertEquals(finalState, state.gameState)
            assertTrue(state.players.isEmpty(), "No play should have been simulated")
        }

    @Test
    fun testUserOnOffenseChoosesFromThePlaybookWithCpuSuggestionPreselected() =
        testScope.runTest {
            val presenter = presenter()
            presenter.startMatch(matchup, homeFirstAndTen)

            val playCall = presenter.uiState.value.playCall
            assertTrue(playCall.isUserOnOffense)
            assertTrue(playCall.isUserChoosing)
            assertEquals(Playbook.offensivePlaysFor(GamePhase.SCRIMMAGE), playCall.offenseOptions)
            assertTrue(playCall.selectedOffense in playCall.offenseOptions)
            assertTrue(playCall.defenseOptions.isEmpty())
        }

    @Test
    fun testUserOnDefenseChoosesACoverage() =
        testScope.runTest {
            val presenter = presenter()
            presenter.startMatch(matchup, homeFirstAndTen.copy(isHomePossession = false))

            val playCall = presenter.uiState.value.playCall
            assertFalse(playCall.isUserOnOffense)
            assertEquals(Playbook.defensiveCalls, playCall.defenseOptions)
            assertTrue(playCall.offenseOptions.isEmpty())
        }

    @Test
    fun testSelectedPlayIsTheOneThatRuns() =
        testScope.runTest {
            val presenter = presenter()
            presenter.startMatch(matchup, homeFirstAndTen)

            presenter.selectOffensivePlay(Playbook.PUNT)
            presenter.snapBall()
            advanceUntilIdle()

            val state = presenter.uiState.value
            assertFalse(state.gameState.isHomePossession, "Punting on first down hands the ball over")
            assertTrue(state.playByPlayText.contains("Punt vs"), state.playByPlayText)
        }

    @Test
    fun testAutoCallIgnoresTheUsersSelection() =
        testScope.runTest {
            val presenter = presenter()
            presenter.startMatch(matchup, homeFirstAndTen)

            presenter.setAutoCall(true)
            presenter.selectOffensivePlay(Playbook.PUNT)
            presenter.snapBall()
            advanceUntilIdle()

            val state = presenter.uiState.value
            assertFalse(state.playCall.isUserChoosing)
            assertFalse(state.playByPlayText.contains("Punt vs"), "The CPU never punts on first down")
        }

    @Test
    fun testKickoffsOfferNoChoice() =
        testScope.runTest {
            val presenter = presenter()
            presenter.startMatch(matchup)

            assertFalse(presenter.uiState.value.playCall.isUserChoosing)
        }
}
