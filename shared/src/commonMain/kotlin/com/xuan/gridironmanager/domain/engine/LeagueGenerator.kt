package com.xuan.gridironmanager.domain.engine

import com.xuan.gridironmanager.domain.model.*
import kotlin.random.Random

object LeagueGenerator {
    private val firstNames = listOf("James", "John", "Robert", "Michael", "William", "David", "Richard", "Joseph", "Thomas", "Charles")
    private val lastNames = listOf("Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis", "Rodriguez", "Martinez")
    private val cities = listOf("New York", "Los Angeles", "Chicago", "Houston", "Phoenix", "Philadelphia", "San Antonio", "San Diego", "Dallas", "San Jose")
    private val teamNicknames = listOf("Eagles", "Lions", "Tigers", "Bears", "Warriors", "Titans", "Kings", "Wolves", "Sharks", "Hawks")

    fun generateLeague(): List<Team> {
        return (1..32).map { i ->
            val city = cities[Random.nextInt(cities.size)]
            val nickname = teamNicknames[Random.nextInt(teamNicknames.size)]
            Team(
                id = "team_$i",
                city = city,
                nickname = nickname,
                abbreviation = nickname.take(3).uppercase(),
                primaryColorHex = "#FFFFFF",
                secondaryColorHex = "#000000"
            )
        }
    }

    fun generatePlayersForTeam(teamId: String): List<Player> {
        val roster = mutableListOf<Player>()
        Position.entries.forEach { position ->
            val count = when (position) {
                Position.QB -> 2
                Position.RB -> 3
                Position.WR -> 6
                Position.TE -> 3
                Position.OT, Position.OG, Position.OL -> 4
                Position.C -> 2
                Position.EDGE, Position.DT, Position.DL, Position.LB, Position.CB, Position.S -> 5
                Position.K, Position.P -> 1
            }
            repeat(count) {
                roster.add(generatePlayer(teamId, position.abbreviation))
            }
        }
        return roster
    }

    private fun generatePlayer(teamId: String, position: String): Player {
        val firstName = firstNames[Random.nextInt(firstNames.size)]
        val lastName = lastNames[Random.nextInt(lastNames.size)]
        
        fun rating(min: Int = 60, max: Int = 99): Int {
            return Random.nextInt(min, max + 1)
        }

        val attributes = PlayerAttributes(
            speed = rating(),
            acceleration = rating(),
            strength = rating(),
            verticalJump = rating(),
            awareness = rating(),
            playRecognition = rating(),
            throwPower = if (position == "QB") rating(80, 99) else rating(10, 40),
            throwAccuracy = if (position == "QB") rating(80, 99) else rating(10, 40),
            catching = if (position == "WR" || position == "TE") rating(80, 99) else rating(20, 60),
            routeRunning = if (position == "WR") rating(80, 99) else rating(10, 50),
            blockPass = if (position == "OL") rating(80, 99) else rating(10, 40),
            tackle = if (position == "LB" || position == "DL" || position == "CB" || position == "S") rating(70, 99) else rating(10, 50)
        )

        return Player(
            id = "player_${Random.nextInt(1000000)}",
            teamId = teamId,
            firstName = firstName,
            lastName = lastName,
            position = position,
            age = Random.nextInt(21, 35),
            yearsPro = Random.nextInt(0, 15),
            physicalProfile = PhysicalProfile(Random.nextInt(70, 80), Random.nextInt(190, 320)),
            attributes = attributes
        )
    }
}
