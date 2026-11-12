package com.xuan.gridironmanager.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class PhysicalProfile(
    val heightInches: Int,
    val weightLbs: Int,
)

@Serializable
data class PlayerAttributes(
    val speed: Int,
    val acceleration: Int,
    val strength: Int,
    val verticalJump: Int,
    val throwPower: Int = 0,
    val throwAccuracy: Int = 0,
    val catching: Int = 0,
    val routeRunning: Int = 0,
    val blockPass: Int = 0,
    val tackle: Int = 0,
    val kickPower: Int = 0,
    val kickAccuracy: Int = 0,
    val awareness: Int,
    val playRecognition: Int,
) {
    companion object {
        /** A middle-of-the-road player, rated 50 across the board. */
        val AVERAGE =
            PlayerAttributes(
                speed = 50,
                acceleration = 50,
                strength = 50,
                verticalJump = 50,
                throwPower = 50,
                throwAccuracy = 50,
                catching = 50,
                routeRunning = 50,
                blockPass = 50,
                tackle = 50,
                kickPower = 50,
                kickAccuracy = 50,
                awareness = 50,
                playRecognition = 50,
            )
    }
}

@Serializable
data class Player(
    val id: String,
    val teamId: String,
    val firstName: String,
    val lastName: String,
    val position: String,
    val age: Int,
    val yearsPro: Int,
    val physicalProfile: PhysicalProfile,
    val attributes: PlayerAttributes,
) {
    val fullName: String get() = "$firstName $lastName"

    /** The average of the four ratings that matter most at this player's position. */
    val overallRating: Int get() {
        val a = attributes
        val sum =
            when (Position.entries.find { it.abbreviation == position }) {
                Position.QB -> a.throwAccuracy + a.throwPower + a.awareness + a.speed
                Position.RB -> a.speed + a.acceleration + a.strength + a.catching
                Position.WR -> a.speed + a.catching + a.routeRunning + a.acceleration
                Position.TE -> a.catching + a.strength + a.blockPass + a.speed
                Position.OT, Position.OG, Position.C, Position.OL -> a.blockPass + a.strength + a.awareness + a.speed
                Position.EDGE, Position.DT, Position.DL -> a.strength + a.tackle + a.speed + a.awareness
                Position.LB -> a.tackle + a.speed + a.awareness + a.playRecognition
                Position.CB -> a.speed + a.awareness + a.playRecognition + a.acceleration
                Position.S -> a.tackle + a.awareness + a.playRecognition + a.speed
                Position.K, Position.P -> a.kickPower + a.kickAccuracy + a.awareness + a.speed
                null -> a.speed + a.acceleration + a.strength + a.awareness
            }
        return sum / 4
    }
}

@Serializable
data class PlayerDatabase(
    val formatVersion: Int = 1,
    val players: List<Player>,
)
