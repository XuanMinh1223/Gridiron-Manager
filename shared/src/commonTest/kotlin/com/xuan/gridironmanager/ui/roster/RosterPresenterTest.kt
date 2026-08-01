package com.xuan.gridironmanager.ui.roster

import com.xuan.gridironmanager.domain.repository.PlayerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RosterPresenterTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testRosterFilteringAndSorting() = runTest {
        val json = """
            {
              "formatVersion": 1,
              "players": [
                {
                  "id": "p1", "teamId": "T1", "firstName": "Low", "lastName": "OVR", "position": "QB",
                  "age": 25, "yearsPro": 3, "physicalProfile": { "heightInches": 75, "weightLbs": 220 },
                  "attributes": { "speed": 10, "acceleration": 10, "strength": 10, "verticalJump": 10, "awareness": 10, "playRecognition": 10 }
                },
                {
                  "id": "p2", "teamId": "T1", "firstName": "High", "lastName": "OVR", "position": "QB",
                  "age": 30, "yearsPro": 8, "physicalProfile": { "heightInches": 75, "weightLbs": 220 },
                  "attributes": { "speed": 90, "acceleration": 90, "strength": 90, "verticalJump": 90, "awareness": 90, "playRecognition": 90 }
                },
                {
                  "id": "p3", "teamId": "T1", "firstName": "Def", "lastName": "Player", "position": "CB",
                  "age": 24, "yearsPro": 2, "physicalProfile": { "heightInches": 72, "weightLbs": 195 },
                  "attributes": { "speed": 85, "acceleration": 85, "strength": 60, "verticalJump": 80, "awareness": 75, "playRecognition": 70 }
                },
                {
                   "id": "p4", "teamId": "T2", "firstName": "Other", "lastName": "Team", "position": "WR",
                   "age": 28, "yearsPro": 6, "physicalProfile": { "heightInches": 74, "weightLbs": 210 },
                   "attributes": { "speed": 90, "acceleration": 90, "strength": 70, "verticalJump": 85, "awareness": 85, "playRecognition": 80 }
                }
              ]
            }
        """.trimIndent()

        val repository = PlayerRepository { json }
        val presenter = RosterPresenter(repository, "T1")

        // In runTest, stateIn(WhileSubscribed) needs an active collector
        val job = launch { presenter.uiState.collect {} }
        
        // Wait for state to update
        testDispatcher.scheduler.advanceUntilIdle()

        val state = presenter.uiState.value
        assertTrue(state is RosterUiState.Success, "Expected Success but was ${state::class.simpleName}")
        val players = state.players

        // Should only have 3 players from T1
        assertEquals(3, players.size)

        // Sorting: Offense (QB) before Defense (CB)
        assertEquals("High OVR", players[0].fullName) // Higher OVR QB first
        assertEquals("Low OVR", players[1].fullName)  // Lower OVR QB second
        assertEquals("Def Player", players[2].fullName) // Defense last
        
        job.cancel()
    }
}
