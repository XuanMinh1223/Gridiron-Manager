package com.xuan.gridironmanager.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Team(
    val id: String,
    val city: String,
    val nickname: String,
    val abbreviation: String,
    val primaryColorHex: String,
    val secondaryColorHex: String
) {
    val fullName: String get() = "$city $nickname"
}

@Serializable
data class Division(
    val id: String,
    val name: String,
    val teams: List<Team>
)

@Serializable
data class Conference(
    val id: String,
    val name: String,
    val divisions: List<Division>
)

@Serializable
data class LeagueDatabase(
    val conferences: List<Conference>
)
