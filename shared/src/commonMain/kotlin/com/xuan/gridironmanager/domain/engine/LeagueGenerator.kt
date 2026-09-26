package com.xuan.gridironmanager.domain.engine

import com.xuan.gridironmanager.domain.model.PhysicalProfile
import com.xuan.gridironmanager.domain.model.Player
import com.xuan.gridironmanager.domain.model.PlayerAttributes
import com.xuan.gridironmanager.domain.model.Position
import com.xuan.gridironmanager.domain.model.PositionType
import kotlin.random.Random

object LeagueGenerator {
    private val firstNames = listOf("James", "John", "Robert", "Michael", "William", "David", "Richard", "Joseph", "Thomas", "Charles")
    private val lastNames = listOf("Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis", "Rodriguez", "Martinez")
    private val offensiveLine = setOf(Position.OT, Position.OG, Position.C, Position.OL)

    fun generatePlayersForTeam(
        teamId: String,
        random: Random = Random.Default,
    ): List<Player> {
        val roster = mutableListOf<Player>()
        Position.entries.forEach { position ->
            val count =
                when (position) {
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
                roster.add(generatePlayer("${teamId}_${roster.size}", teamId, position, random))
            }
        }
        return roster
    }

    private fun generatePlayer(
        id: String,
        teamId: String,
        position: Position,
        random: Random,
    ): Player {
        fun rating(
            min: Int = 60,
            max: Int = 99,
        ): Int = random.nextInt(min, max + 1)

        val isKicker = position == Position.K || position == Position.P

        val attributes =
            PlayerAttributes(
                speed = rating(),
                acceleration = rating(),
                strength = rating(),
                verticalJump = rating(),
                awareness = rating(),
                playRecognition = rating(),
                throwPower = if (position == Position.QB) rating(80, 99) else rating(10, 40),
                throwAccuracy = if (position == Position.QB) rating(80, 99) else rating(10, 40),
                catching = if (position == Position.WR || position == Position.TE) rating(80, 99) else rating(20, 60),
                routeRunning = if (position == Position.WR) rating(80, 99) else rating(10, 50),
                blockPass = if (position in offensiveLine) rating(80, 99) else rating(10, 40),
                tackle = if (position.type == PositionType.DEFENSE) rating(70, 99) else rating(10, 50),
                kickPower = if (isKicker) rating(75, 99) else rating(10, 40),
                kickAccuracy = if (isKicker) rating(75, 99) else rating(10, 40),
            )

        return Player(
            id = id,
            teamId = teamId,
            firstName = firstNames[random.nextInt(firstNames.size)],
            lastName = lastNames[random.nextInt(lastNames.size)],
            position = position.abbreviation,
            age = random.nextInt(21, 35),
            yearsPro = random.nextInt(0, 15),
            physicalProfile = PhysicalProfile(random.nextInt(70, 80), random.nextInt(190, 320)),
            attributes = attributes,
        )
    }
}
