package com.xuan.gridironmanager.domain.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DatabaseRepositoryTest {

    @Test
    fun testTeamRepositoryParsesJson() {
        val jsonString = """
            {
              "conferences": [
                {
                  "id": "AFC",
                  "name": "American Football Conference",
                  "divisions": [
                    {
                      "id": "AFC_NORTH",
                      "name": "AFC North",
                      "teams": [
                        {
                          "id": "BAL",
                          "city": "Baltimore",
                          "nickname": "Ravens",
                          "abbreviation": "BAL",
                          "primaryColorHex": "#241773",
                          "secondaryColorHex": "#000000"
                        },
                        {
                          "id": "CLE",
                          "city": "Cleveland",
                          "nickname": "Browns",
                          "abbreviation": "CLE",
                          "primaryColorHex": "#311D00",
                          "secondaryColorHex": "#FF3C00"
                        }
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val repository = TeamRepository { jsonString }
        val teams = repository.getAllTeams()

        assertEquals(2, teams.size)
        assertTrue(teams.any { it.id == "BAL" && it.nickname == "Ravens" })
        assertTrue(teams.any { it.id == "CLE" && it.nickname == "Browns" })
    }

    @Test
    fun testPlayerRepositoryUpdatesState() {
        val initialJson = """
            {
              "formatVersion": 1,
              "players": [
                {
                  "id": "p1",
                  "teamId": "BAL",
                  "firstName": "Lamar",
                  "lastName": "Jackson",
                  "position": "QB",
                  "age": 27,
                  "yearsPro": 6,
                  "physicalProfile": { "heightInches": 74, "weightLbs": 215 },
                  "attributes": { "speed": 96, "acceleration": 96, "strength": 65, "verticalJump": 35, "awareness": 90, "playRecognition": 85 }
                },
                {
                  "id": "p2",
                  "teamId": "KC",
                  "firstName": "Patrick",
                  "lastName": "Mahomes",
                  "position": "QB",
                  "age": 28,
                  "yearsPro": 7,
                  "physicalProfile": { "heightInches": 75, "weightLbs": 225 },
                  "attributes": { "speed": 84, "acceleration": 87, "strength": 70, "verticalJump": 30, "awareness": 98, "playRecognition": 95 }
                }
              ]
            }
        """.trimIndent()

        val repository = PlayerRepository { initialJson }
        
        val balPlayers = repository.getPlayersByTeam("BAL")
        assertEquals(1, balPlayers.size)
        assertEquals("Lamar", balPlayers[0].firstName)

        val customJson = """
            {
              "formatVersion": 1,
              "players": [
                {
                  "id": "p3",
                  "teamId": "BAL",
                  "firstName": "Mark",
                  "lastName": "Andrews",
                  "position": "TE",
                  "age": 28,
                  "yearsPro": 6,
                  "physicalProfile": { "heightInches": 77, "weightLbs": 250 },
                  "attributes": { "speed": 86, "acceleration": 88, "strength": 82, "verticalJump": 32, "awareness": 92, "playRecognition": 90 }
                }
              ]
            }
        """.trimIndent()

        repository.loadCustomRoster(customJson)
        
        val newBalPlayers = repository.getPlayersByTeam("BAL")
        assertEquals(1, newBalPlayers.size)
        assertEquals("Mark", newBalPlayers[0].firstName)
        assertEquals("Andrews", newBalPlayers[0].lastName)
    }
}
