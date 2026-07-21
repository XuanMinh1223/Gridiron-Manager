package com.xuan.gridironmanager.domain.career

import com.xuan.gridironmanager.domain.model.Team

data class CareerState(
    val currentSeason: Int = 2024,
    val currentWeek: Int = 1,
    val teams: List<Team>,
    val standings: Map<String, TeamRecord> = emptyMap(),
    val schedule: List<ScheduledGame> = emptyList()
)

data class TeamRecord(
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0
)

data class ScheduledGame(
    val week: Int,
    val homeTeamId: String,
    val awayTeamId: String,
    val isCompleted: Boolean = false,
    val homeScore: Int? = null,
    val awayScore: Int? = null
)
