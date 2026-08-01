package com.xuan.gridironmanager.domain.model

data class GameState(
    val homeTeam: Team,
    val awayTeam: Team,
    val homeRoster: List<Player> = emptyList(),
    val awayRoster: List<Player> = emptyList(),
    val homeScore: Int = 0,
    val awayScore: Int = 0,
    val quarter: Int = 1,
    val clockSeconds: Int = 900, // 15 minutes
    val possession: TeamId,
    val yardLine: Int = 25, // 25 yard line
    val down: Int = 1,
    val distance: Int = 10,
    val playHistory: List<PlayResult> = emptyList(),
    val isGameOver: Boolean = false
)

enum class TeamId {
    HOME, AWAY
}

data class PlayResult(
    val description: String,
    val yardage: Int,
    val type: PlayType,
    val isTouchdown: Boolean = false,
    val isTurnover: Boolean = false
)
