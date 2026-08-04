package com.xuan.gridironmanager.domain.sim.match

data class GameState(
    val down: Int = 1,
    val distance: Int = 10,
    val yardLine: Int = 25, // Distance from own endzone (0 to 100)
    val homeScore: Int = 0,
    val awayScore: Int = 0,
    val quarter: Int = 1,
    val clockSeconds: Int = 900, // 15 minutes
    val isHomePossession: Boolean = true
)
