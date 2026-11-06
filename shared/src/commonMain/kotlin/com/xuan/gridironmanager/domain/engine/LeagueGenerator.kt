package com.xuan.gridironmanager.domain.engine

import com.xuan.gridironmanager.domain.model.PhysicalProfile
import com.xuan.gridironmanager.domain.model.Player
import com.xuan.gridironmanager.domain.model.PlayerAttributes
import com.xuan.gridironmanager.domain.model.Position
import com.xuan.gridironmanager.domain.model.PositionType
import kotlin.random.Random

object LeagueGenerator {
    private val firstNames =
        listOf(
            "James", "John", "Robert", "Michael", "William", "David", "Richard", "Joseph", "Thomas", "Charles",
            "Daniel", "Matthew", "Anthony", "Mark", "Donald", "Steven", "Paul", "Andrew", "Joshua", "Kenneth",
            "Kevin", "Brian", "George", "Timothy", "Ronald", "Edward", "Jason", "Jeffrey", "Ryan", "Jacob",
            "Gary", "Nicholas", "Eric", "Jonathan", "Stephen", "Larry", "Justin", "Scott", "Brandon", "Benjamin",
            "Samuel", "Gregory", "Alexander", "Patrick", "Frank", "Raymond", "Jack", "Dennis", "Jerry", "Tyler",
        )
    private val lastNames =
        listOf(
            "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis", "Rodriguez", "Martinez",
            "Hernandez", "Lopez", "Gonzalez", "Wilson", "Anderson", "Thomas", "Taylor", "Moore", "Jackson", "Martin",
            "Lee", "Perez", "Thompson", "White", "Harris", "Sanchez", "Clark", "Ramirez", "Lewis", "Robinson",
            "Walker", "Young", "Allen", "King", "Wright", "Scott", "Torres", "Nguyen", "Hill", "Flores",
            "Green", "Adams", "Nelson", "Baker", "Hall", "Rivera", "Campbell", "Mitchell", "Carter", "Roberts",
        )
    private val offensiveLine = setOf(Position.OT, Position.OG, Position.C, Position.OL)
    private const val MAX_NAME_RETRIES = 1_000

    fun generatePlayersForTeam(
        teamId: String,
        random: Random = Random.Default,
    ): List<Player> {
        val roster = mutableListOf<Player>()
        val usedNames = mutableSetOf<String>()
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
                roster.add(generatePlayer("${teamId}_${roster.size}", teamId, position, random, usedNames))
            }
        }
        return roster
    }

    /** Rating ranges for the physical attributes that separate positions, and the build (inches / pounds) that goes with them. */
    data class Physique(
        val speed: IntRange,
        val strength: IntRange,
        val heightInches: IntRange,
        val weightLbs: IntRange,
    )

    fun physiqueFor(position: Position): Physique =
        when (position) {
            Position.QB -> Physique(speed = 55..85, strength = 50..75, heightInches = 73..78, weightLbs = 200..240)
            Position.RB -> Physique(speed = 80..97, strength = 65..90, heightInches = 68..74, weightLbs = 190..230)
            Position.WR -> Physique(speed = 82..99, strength = 50..75, heightInches = 70..76, weightLbs = 170..220)
            Position.TE -> Physique(speed = 68..86, strength = 70..90, heightInches = 75..79, weightLbs = 230..270)
            Position.OT, Position.OG, Position.C, Position.OL ->
                Physique(speed = 45..70, strength = 80..99, heightInches = 75..80, weightLbs = 290..330)
            Position.EDGE -> Physique(speed = 70..88, strength = 72..92, heightInches = 74..78, weightLbs = 240..280)
            Position.DT, Position.DL -> Physique(speed = 55..75, strength = 80..99, heightInches = 74..79, weightLbs = 270..320)
            Position.LB -> Physique(speed = 72..90, strength = 70..90, heightInches = 72..76, weightLbs = 220..250)
            Position.CB -> Physique(speed = 82..99, strength = 45..70, heightInches = 70..74, weightLbs = 180..210)
            Position.S -> Physique(speed = 80..95, strength = 55..80, heightInches = 70..74, weightLbs = 190..220)
            Position.K, Position.P -> Physique(speed = 40..65, strength = 30..55, heightInches = 70..76, weightLbs = 180..220)
        }

    private fun uniqueName(
        random: Random,
        usedNames: MutableSet<String>,
    ): Pair<String, String> {
        repeat(MAX_NAME_RETRIES) {
            val first = firstNames[random.nextInt(firstNames.size)]
            val last = lastNames[random.nextInt(lastNames.size)]
            if (usedNames.add("$first $last")) return first to last
        }
        error("Could not find an unused name after $MAX_NAME_RETRIES tries: the name lists are too small")
    }

    private fun generatePlayer(
        id: String,
        teamId: String,
        position: Position,
        random: Random,
        usedNames: MutableSet<String>,
    ): Player {
        fun rating(
            min: Int = 60,
            max: Int = 99,
        ): Int = random.nextInt(min, max + 1)

        val isKicker = position == Position.K || position == Position.P
        val physique = physiqueFor(position)

        val attributes =
            PlayerAttributes(
                speed = rating(physique.speed.first, physique.speed.last),
                acceleration = rating(physique.speed.first, physique.speed.last),
                strength = rating(physique.strength.first, physique.strength.last),
                verticalJump = rating(),
                awareness = rating(),
                playRecognition = rating(),
                throwPower = if (position == Position.QB) rating(80, 99) else rating(10, 40),
                throwAccuracy = if (position == Position.QB) rating(80, 99) else rating(10, 40),
                catching = if (position == Position.WR || position == Position.TE) rating(80, 99) else rating(20, 60),
                routeRunning = if (position == Position.WR) rating(80, 99) else rating(10, 50),
                blockPass =
                    when (position) {
                        in offensiveLine -> rating(80, 99)
                        Position.TE -> rating(60, 85)
                        Position.RB -> rating(40, 70)
                        Position.WR -> rating(20, 50)
                        else -> rating(10, 40)
                    },
                tackle = if (position.type == PositionType.DEFENSE) rating(70, 99) else rating(10, 50),
                kickPower = if (isKicker) rating(75, 99) else rating(10, 40),
                kickAccuracy = if (isKicker) rating(75, 99) else rating(10, 40),
            )

        val (firstName, lastName) = uniqueName(random, usedNames)
        // Age follows from the draft age and the years since, so a 21-year-old can't have 14 years in the league
        val draftAge = random.nextInt(21, 24)
        val yearsPro = random.nextInt(0, 15)

        return Player(
            id = id,
            teamId = teamId,
            firstName = firstName,
            lastName = lastName,
            position = position.abbreviation,
            age = draftAge + yearsPro,
            yearsPro = yearsPro,
            physicalProfile =
                PhysicalProfile(
                    heightInches = random.nextInt(physique.heightInches.first, physique.heightInches.last + 1),
                    weightLbs = random.nextInt(physique.weightLbs.first, physique.weightLbs.last + 1),
                ),
            attributes = attributes,
        )
    }
}
