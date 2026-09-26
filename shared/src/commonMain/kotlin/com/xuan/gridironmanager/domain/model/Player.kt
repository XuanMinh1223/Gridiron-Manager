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

    val overallRating: Int get() {
        return (attributes.speed + attributes.acceleration + attributes.strength + attributes.awareness) / 4
    }
}

@Serializable
data class PlayerDatabase(
    val formatVersion: Int = 1,
    val players: List<Player>,
)
