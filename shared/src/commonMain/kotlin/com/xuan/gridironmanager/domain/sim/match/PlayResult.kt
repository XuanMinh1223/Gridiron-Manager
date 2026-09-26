package com.xuan.gridironmanager.domain.sim.match

data class PlayResult(
    /** Yards from the line of scrimmage to where the play ended (for turnovers: where the defense took possession). */
    val yardsGained: Int,
    val description: String,
    val isTouchdown: Boolean,
    val isTurnover: Boolean,
    val clockStops: Boolean = false,
)
