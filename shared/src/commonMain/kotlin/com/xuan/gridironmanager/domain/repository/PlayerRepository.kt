package com.xuan.gridironmanager.domain.repository

import com.xuan.gridironmanager.domain.model.Player
import com.xuan.gridironmanager.domain.model.PlayerDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

class PlayerRepository(defaultJsonLoader: () -> String) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _players = MutableStateFlow<List<Player>>(emptyList())
    val players: StateFlow<List<Player>> = _players.asStateFlow()

    init {
        loadCustomRoster(defaultJsonLoader())
    }

    fun loadCustomRoster(jsonString: String) {
        val database = json.decodeFromString<PlayerDatabase>(jsonString)
        _players.value = database.players
    }

    fun getPlayersByTeam(teamId: String): List<Player> {
        return _players.value.filter { it.teamId == teamId }
    }
}
