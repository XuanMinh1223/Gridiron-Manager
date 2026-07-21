package com.xuan.gridironmanager.domain.engine

import com.xuan.gridironmanager.domain.model.*
import kotlin.random.Random

object LeagueGenerator {
    private val firstNames = listOf("James", "John", "Robert", "Michael", "William", "David", "Richard", "Joseph", "Thomas", "Charles")
    private val lastNames = listOf("Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis", "Rodriguez", "Martinez")
    private val cities = listOf("New York", "Los Angeles", "Chicago", "Houston", "Phoenix", "Philadelphia", "San Antonio", "San Diego", "Dallas", "San Jose")
    private val teamNames = listOf("Eagles", "Lions", "Tigers", "Bears", "Warriors", "Titans", "Kings", "Wolves", "Sharks", "Hawks")

    fun generateLeague(): List<Team> {
        return (1..32).map { i ->
            val city = cities[Random.nextInt(cities.size)]
            val teamName = teamNames[Random.nextInt(teamNames.size)]
            val roster = generateRoster()
            Team(
                id = "team_$i",
                city = city,
                name = teamName,
                roster = roster,
                salaryCapUsed = roster.sumOf { it.contract.salary }
            )
        }
    }

    private fun generateRoster(): List<Player> {
        val roster = mutableListOf<Player>()
        Position.entries.forEach { position ->
            val count = when (position) {
                Position.QB -> 2
                Position.RB -> 3
                Position.WR -> 6
                Position.TE -> 3
                Position.OT, Position.OG -> 4
                Position.C -> 2
                Position.EDGE, Position.DT, Position.LB, Position.CB, Position.S -> 5
                Position.K, Position.P -> 1
            }
            repeat(count) {
                roster.add(generatePlayer(position))
            }
        }
        return roster
    }

    private fun generatePlayer(position: Position): Player {
        val firstName = firstNames[Random.nextInt(firstNames.size)]
        val lastName = lastNames[Random.nextInt(lastNames.size)]
        
        // Bell curve approximation: sum of 3 dice
        fun bellRating(min: Int = 1, max: Int = 20): Int {
            val raw = (Random.nextInt(1, 7) + Random.nextInt(1, 7) + Random.nextInt(1, 7)) // 3-18
            return ((raw / 18.0) * (max - min) + min).toInt().coerceIn(min, max)
        }

        val attributes = PlayerAttributes(
            physical = PhysicalAttributes(bellRating(), bellRating(), bellRating(), bellRating()),
            mental = MentalAttributes(bellRating(), bellRating(), bellRating()),
            technical = TechnicalAttributes(
                throwPower = if (position == Position.QB) bellRating(10, 20) else bellRating(1, 5),
                throwAccuracy = if (position == Position.QB) bellRating(10, 20) else bellRating(1, 5),
                routeRunning = if (position == Position.WR) bellRating(10, 20) else bellRating(1, 10),
                catching = if (position == Position.WR || position == Position.TE) bellRating(10, 20) else bellRating(1, 10),
                passBlock = if (position.type == PositionType.OFFENSE) bellRating(5, 15) else bellRating(1, 5),
                runBlock = if (position.type == PositionType.OFFENSE) bellRating(5, 15) else bellRating(1, 5),
                tackle = if (position.type == PositionType.DEFENSE) bellRating(10, 20) else bellRating(1, 10),
                manCoverage = if (position == Position.CB || position == Position.S) bellRating(10, 20) else bellRating(1, 8),
                zoneCoverage = if (position == Position.CB || position == Position.S) bellRating(10, 20) else bellRating(1, 8),
                passRush = if (position == Position.EDGE || position == Position.DT) bellRating(10, 20) else bellRating(1, 5),
                kickPower = if (position == Position.K || position == Position.P) bellRating(15, 20) else bellRating(1, 5),
                kickAccuracy = if (position == Position.K || position == Position.P) bellRating(15, 20) else bellRating(1, 5)
            )
        )

        return Player(
            id = "player_${Random.nextInt(1000000)}",
            firstName = firstName,
            lastName = lastName,
            age = Random.nextInt(21, 35),
            position = position,
            attributes = attributes,
            contract = ContractDetails(
                salary = (bellRating() * 500_000).toLong(),
                yearsRemaining = Random.nextInt(1, 6),
                isGuaranteed = Random.nextBoolean()
            )
        )
    }
}
