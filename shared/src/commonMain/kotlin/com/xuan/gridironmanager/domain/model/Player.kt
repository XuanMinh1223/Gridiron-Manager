package com.xuan.gridironmanager.domain.model

data class Player(
    val id: String,
    val firstName: String,
    val lastName: String,
    val age: Int,
    val position: Position,
    val attributes: PlayerAttributes,
    val contract: ContractDetails
) {
    val fullName: String get() = "$firstName $lastName"
    
    val overallRating: Int get() {
        // Simplified overall calculation
        return (attributes.physical.speed + attributes.physical.strength + 
                attributes.mental.composure + attributes.mental.playRecognition) / 4 
    }
}

data class ContractDetails(
    val salary: Long,
    val yearsRemaining: Int,
    val isGuaranteed: Boolean
)
