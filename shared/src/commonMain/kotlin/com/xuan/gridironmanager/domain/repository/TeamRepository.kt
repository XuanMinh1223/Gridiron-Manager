package com.xuan.gridironmanager.domain.repository

import com.xuan.gridironmanager.domain.model.LeagueDatabase
import com.xuan.gridironmanager.domain.model.Team
import kotlinx.serialization.json.Json

class TeamRepository(defaultJsonLoader: () -> String) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val database: LeagueDatabase = json.decodeFromString(defaultJsonLoader())

    fun getAllTeams(): List<Team> {
        return database.conferences.flatMap { conference ->
            conference.divisions.flatMap { division ->
                division.teams
            }
        }
    }
}
