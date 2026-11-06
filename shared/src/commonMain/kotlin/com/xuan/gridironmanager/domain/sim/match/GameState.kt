package com.xuan.gridironmanager.domain.sim.match

enum class GamePhase {
    /** The possessing team must kick off (after a score, at the start of each half, or after a safety). */
    KICKOFF,

    /** The possessing team just scored a touchdown and attempts an extra point or a two-point conversion. */
    EXTRA_POINT,

    /** Normal down-and-distance play. */
    SCRIMMAGE,
}

data class GameState(
    val down: Int = 1,
    val distance: Int = Rules.FIRST_DOWN_DISTANCE,
    val yardLine: Int = 25, // Distance from the possessing team's own end zone (0 to 100)
    val homeScore: Int = 0,
    val awayScore: Int = 0,
    val quarter: Int = 1,
    val clockSeconds: Int = Rules.QUARTER_LENGTH_SEC,
    val isHomePossession: Boolean = true,
    val phase: GamePhase = GamePhase.SCRIMMAGE,
    val isGameOver: Boolean = false,
    /** A safety free kick uses a distinct spot and short/out-of-bounds enforcement. */
    val isSafetyFreeKick: Boolean = false,
    /** Sudden-death overtime: the first score ends the game. */
    val isOvertime: Boolean = false,
    val homeTimeouts: Int = Rules.TIMEOUTS_PER_HALF,
    val awayTimeouts: Int = Rules.TIMEOUTS_PER_HALF,
    /** A timeout has been called for the next play: it skips the between-play runoff, then clears. */
    val timeoutCalled: Boolean = false,
) {
    val possessingTimeouts: Int get() = if (isHomePossession) homeTimeouts else awayTimeouts

    /** Spends a timeout for the possessing team if it has one and none is already called; otherwise returns this unchanged. */
    fun withTimeoutCalled(): GameState =
        when {
            timeoutCalled || possessingTimeouts <= 0 -> this
            isHomePossession -> copy(homeTimeouts = homeTimeouts - 1, timeoutCalled = true)
            else -> copy(awayTimeouts = awayTimeouts - 1, timeoutCalled = true)
        }

    /** Points ahead (positive) or behind (negative) from the possessing team's point of view. */
    val possessionScoreMargin: Int get() = if (isHomePossession) homeScore - awayScore else awayScore - homeScore

    companion object {
        /** Start of the game: the home team kicks off from its own 35. */
        fun openingKickoff() =
            GameState(
                yardLine = Rules.KICKOFF_YARD_LINE,
                isHomePossession = true,
                phase = GamePhase.KICKOFF,
            )
    }
}
