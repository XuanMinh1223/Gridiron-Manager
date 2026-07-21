package com.xuan.gridironmanager.domain.model

data class PhysicalAttributes(
    val speed: Int,
    val strength: Int,
    val agility: Int,
    val stamina: Int
)

data class MentalAttributes(
    val composure: Int,
    val playRecognition: Int,
    val decisionMaking: Int
)

data class TechnicalAttributes(
    val throwPower: Int = 1,
    val throwAccuracy: Int = 1,
    val routeRunning: Int = 1,
    val catching: Int = 1,
    val passBlock: Int = 1,
    val runBlock: Int = 1,
    val tackle: Int = 1,
    val manCoverage: Int = 1,
    val zoneCoverage: Int = 1,
    val passRush: Int = 1,
    val kickPower: Int = 1,
    val kickAccuracy: Int = 1
)

data class PlayerAttributes(
    val physical: PhysicalAttributes,
    val mental: MentalAttributes,
    val technical: TechnicalAttributes
)
