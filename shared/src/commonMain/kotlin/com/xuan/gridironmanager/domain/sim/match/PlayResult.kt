package com.xuan.gridironmanager.domain.sim.match

/** The players involved in a play, as snap ids (`"${slot}_${playerId}"`). Stats are computed from these, never from descriptions. */
data class PlayActors(
    val passerId: String? = null,
    val receiverId: String? = null,
    val carrierId: String? = null,
    val tacklerId: String? = null,
    val interceptorId: String? = null,
    val recovererId: String? = null,
)

data class PlayResult(
    /** Yards from the line of scrimmage to where the play ended (for turnovers: where the defense took possession). */
    val yardsGained: Int,
    val description: String,
    val isTouchdown: Boolean,
    val isTurnover: Boolean,
    val clockStops: Boolean = false,
    val actors: PlayActors = PlayActors(),
    val isSack: Boolean = false,
    val isPassComplete: Boolean = false,
    /** The QB kept it and ran: a rushing attempt, not a pass attempt. */
    val isScramble: Boolean = false,
)
