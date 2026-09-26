package com.xuan.gridironmanager.domain.model

/** The two teams and rosters taking part in a match. Static for the duration of the game. */
data class Matchup(
    val homeTeam: Team,
    val awayTeam: Team,
    val homeRoster: List<Player>,
    val awayRoster: List<Player>,
)
