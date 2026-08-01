package com.xuan.gridironmanager.ui.roster

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xuan.gridironmanager.domain.model.Player
import com.xuan.gridironmanager.domain.model.Position
import com.xuan.gridironmanager.domain.model.PositionType
import com.xuan.gridironmanager.domain.repository.PlayerRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface RosterUiState {
    data object Loading : RosterUiState
    data class Success(val players: List<Player>) : RosterUiState
    data class Error(val message: String) : RosterUiState
}

class RosterPresenter(
    private val playerRepository: PlayerRepository,
    private val teamId: String
) : ViewModel() {

    val uiState: StateFlow<RosterUiState> = playerRepository.players
        .map { allPlayers ->
            val filteredPlayers = allPlayers.filter { it.teamId == teamId }
            if (filteredPlayers.isEmpty()) {
                RosterUiState.Loading // Or Error if we expect players to exist
            } else {
                val sortedPlayers = filteredPlayers.sortedWith(
                    compareBy<Player> { getPositionTypePriority(it.position) }
                        .thenBy { getPositionPriority(it.position) }
                        .thenByDescending { it.overallRating }
                )
                RosterUiState.Success(sortedPlayers)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = RosterUiState.Loading
        )

    private fun getPositionTypePriority(positionAbbr: String): Int {
        val position = Position.entries.find { it.abbreviation == positionAbbr }
        return when (position?.type) {
            PositionType.OFFENSE -> 1
            PositionType.DEFENSE -> 2
            PositionType.SPECIAL_TEAMS -> 3
            null -> 4
        }
    }

    private fun getPositionPriority(positionAbbr: String): Int {
        return when (positionAbbr) {
            "QB" -> 1
            "RB" -> 2
            "WR" -> 3
            "TE" -> 4
            "OT", "OG", "C", "OL" -> 5
            "EDGE", "DE", "DT", "DL" -> 6
            "LB", "MLB", "OLB" -> 7
            "CB" -> 8
            "S", "FS", "SS" -> 9
            "K" -> 10
            "P" -> 11
            else -> 99
        }
    }
}
