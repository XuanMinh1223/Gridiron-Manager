package com.xuan.gridironmanager.domain.sim.match

data class GameState(
    val down: Int = 1,
    val distance: Int = Rules.FIRST_DOWN_DISTANCE,
    val yardLine: Int = 25, // Distance from the possessing team's own end zone (0 to 100)
    val homeScore: Int = 0,
    val awayScore: Int = 0,
    val quarter: Int = 1,
    val clockSeconds: Int = Rules.QUARTER_LENGTH_SEC,
    val isHomePossession: Boolean = true,
    /** When true, the possessing team must kick off before the next scrimmage play. */
    val isKickoffPending: Boolean = false,
    val isGameOver: Boolean = false,
) {
    companion object {
        /** Start of the game: the home team kicks off from its own 35. */
        fun openingKickoff() =
            GameState(
                yardLine = Rules.KICKOFF_YARD_LINE,
                isHomePossession = true,
                isKickoffPending = true,
            )
    }
}
