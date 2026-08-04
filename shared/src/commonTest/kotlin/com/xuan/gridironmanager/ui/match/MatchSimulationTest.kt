package com.xuan.gridironmanager.ui.match

import com.xuan.gridironmanager.domain.model.*
import com.xuan.gridironmanager.domain.sim.match.DriveEngine
import com.xuan.gridironmanager.domain.sim.match.GameState as SimGameState
import com.xuan.gridironmanager.domain.sim.play.PlaySetupHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MatchSimulationTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val driveEngine = DriveEngine()
    private val presenter = MatchPresenter(driveEngine, testScope, testDispatcher)

    @Test
    fun testKickoffSimulationCompletes() = testScope.runTest {
        val roster = createMockRoster()
        val offFormation = PlaySetupHelper.getKickoffFormation()
        val defFormation = PlaySetupHelper.getKickReturnFormation()
        
        val offense = PlaySetupHelper.createRunningPlayers(
            roster = roster,
            formation = offFormation,
            losWorldY = 35f,
            isOffense = true,
            isAttackingUp = true
        )
        val defense = PlaySetupHelper.createRunningPlayers(
            roster = roster,
            formation = defFormation,
            losWorldY = 98f,
            isOffense = false,
            isAttackingUp = true
        )

        presenter.updateGameState(SimGameState(yardLine = 35))
        
        presenter.snapBall(offense, defense, PlayType.KICK, isAttackingUp = true)
        
        // Use a timeout check or advanceUntilIdle
        advanceUntilIdle()
        
        val state = presenter.uiState.value
        assertTrue(!state.isPlayRunning, "Kickoff simulation should have completed")
        assertTrue(state.gameState.yardLine != 35, "Yard line should have moved from 35")
    }

    @Test
    fun testPassSimulationCompletes() = testScope.runTest {
        val roster = createMockRoster()
        val offFormation = PlaySetupHelper.getShotgunFormation()
        val defFormation = PlaySetupHelper.getBaseDefense()
        
        val offense = PlaySetupHelper.createRunningPlayers(
            roster = roster,
            formation = offFormation,
            losWorldY = 25f,
            isOffense = true,
            isAttackingUp = true
        )
        val defense = PlaySetupHelper.createRunningPlayers(
            roster = roster,
            formation = defFormation,
            losWorldY = 25f,
            isOffense = false,
            isAttackingUp = true
        )

        presenter.updateGameState(SimGameState(yardLine = 25))
        
        presenter.snapBall(offense, defense, PlayType.PASS, isAttackingUp = true)
        
        advanceUntilIdle()
        
        val state = presenter.uiState.value
        assertTrue(!state.isPlayRunning, "Pass simulation should have completed")
    }

    private fun createMockRoster(): List<Player> {
        val teamId = "TEAM1"
        return listOf(
            createPlayer(teamId, "QB"),
            createPlayer(teamId, "RB"),
            createPlayer(teamId, "WR"),
            createPlayer(teamId, "WR"),
            createPlayer(teamId, "WR"),
            createPlayer(teamId, "TE"),
            createPlayer(teamId, "C"),
            createPlayer(teamId, "OG"),
            createPlayer(teamId, "OG"),
            createPlayer(teamId, "OT"),
            createPlayer(teamId, "OT"),
            createPlayer(teamId, "K"),
            createPlayer(teamId, "P"),
            createPlayer(teamId, "DT"),
            createPlayer(teamId, "EDGE"),
            createPlayer(teamId, "LB"),
            createPlayer(teamId, "CB"),
            createPlayer(teamId, "S")
        )
    }

    private fun createPlayer(teamId: String, position: String): Player {
        return Player(
            id = "p_$position",
            teamId = teamId,
            firstName = "John",
            lastName = position,
            position = position,
            age = 25,
            yearsPro = 3,
            physicalProfile = PhysicalProfile(74, 220),
            attributes = PlayerAttributes(
                speed = 80, acceleration = 80, strength = 80, verticalJump = 80,
                awareness = 80, playRecognition = 80, throwPower = 90, throwAccuracy = 85,
                catching = 80, kickPower = 90, kickAccuracy = 85
            )
        )
    }
}
