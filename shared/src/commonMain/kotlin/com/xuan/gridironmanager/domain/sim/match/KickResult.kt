package com.xuan.gridironmanager.domain.sim.match

/** Result of a kickoff or punt, from the receiving team's point of view. */
data class KickResult(
    val endYardLine: Int,
    val description: String,
    val isTouchback: Boolean,
    val isOutOfBounds: Boolean,
    /** Intended and actual landing spots, from the receiving team's perspective. */
    val intendedLandingYardLine: Int = endYardLine,
    val landingYardLine: Int = endYardLine,
    val returnYards: Int = 0,
    val outcomeType: KickOutcomeType =
        when {
            isTouchback -> KickOutcomeType.TOUCHBACK
            isOutOfBounds -> KickOutcomeType.KICK_OUT_OF_BOUNDS
            else -> KickOutcomeType.RETURN_TACKLED
        },
    /** The team that kicked retains the ball only on a legal recovery of a live kick or muff. */
    val recoveredByKickingTeam: Boolean = false,
)

enum class KickOutcomeType {
    TOUCHBACK,
    KICK_OUT_OF_BOUNDS,
    RETURN_TACKLED,
    RETURN_OUT_OF_BOUNDS,
    RETURN_TOUCHDOWN,
    FAIR_CATCH,
    DOWNED,
    MUFF_RECOVERED,
    RETURN_FUMBLE_RECOVERED,
    BLOCKED_RECOVERED,
    SHORT_KICK,
    /** A kick first touched the landing zone, then became dead in the end zone. */
    LANDING_ZONE_TOUCHBACK,
}

data class FieldGoalResult(
    val isGood: Boolean,
    val distanceYds: Int,
    val description: String,
)
