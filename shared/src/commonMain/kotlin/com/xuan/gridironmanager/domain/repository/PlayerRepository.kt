package com.xuan.gridironmanager.domain.repository

import com.xuan.gridironmanager.domain.model.Player
import com.xuan.gridironmanager.domain.model.PlayerDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

class PlayerRepository(
    initialPlayers: List<Player> = emptyList(),
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private val _players = MutableStateFlow(initialPlayers)
    val players: StateFlow<List<Player>> = _players.asStateFlow()

    constructor(defaultJsonLoader: () -> String) : this() {
        loadCustomRoster(defaultJsonLoader())
    }

    fun loadCustomRoster(jsonString: String) {
        val database = json.decodeFromString<PlayerDatabase>(jsonString)
        _players.value = database.players
    }

    fun setPlayers(players: List<Player>) {
        _players.value = players
    }

    fun getPlayersByTeam(teamId: String): List<Player> = _players.value.filter { it.teamId == teamId }
}
