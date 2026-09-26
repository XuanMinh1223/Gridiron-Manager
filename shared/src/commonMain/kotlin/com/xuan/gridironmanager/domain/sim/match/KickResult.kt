package com.xuan.gridironmanager.domain.sim.match

/** Result of a kickoff or punt, from the receiving team's point of view. */
data class KickResult(
    val endYardLine: Int,
    val description: String,
    val isTouchback: Boolean,
    val isOutOfBounds: Boolean,
)

data class FieldGoalResult(
    val isGood: Boolean,
    val distanceYds: Int,
    val description: String,
)
