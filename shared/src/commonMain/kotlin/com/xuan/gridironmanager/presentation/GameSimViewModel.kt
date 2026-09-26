package com.xuan.gridironmanager.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xuan.gridironmanager.domain.engine.LeagueGenerator
import com.xuan.gridironmanager.domain.model.Matchup
import com.xuan.gridironmanager.domain.repository.PlayerRepository
import com.xuan.gridironmanager.domain.repository.TeamRepository
import com.xuan.gridironmanager.ui.match.MatchActions
import com.xuan.gridironmanager.ui.match.MatchPresenter
import com.xuan.gridironmanager.ui.match.MatchUiState
import gridironmanager.shared.generated.resources.Res
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

class GameSimViewModel(
    private val random: Random = Random.Default,
    private val loadTeamsJson: suspend () -> String = { Res.readBytes("files/teams.json").decodeToString() },
) : ViewModel() {
    private val _matchup = MutableStateFlow<Matchup?>(null)
    val matchup: StateFlow<Matchup?> = _matchup.asStateFlow()

    val playerRepository = PlayerRepository()

    private val matchPresenter = MatchPresenter(viewModelScope, random = random)

    /** Single source of truth for the live match. */
    val matchUiState: StateFlow<MatchUiState> = matchPresenter.uiState
    val matchActions: MatchActions = matchPresenter

    init {
        viewModelScope.launch { setUpLeague() }
    }

    private suspend fun setUpLeague() {
        val teamsJson = loadTeamsJson()
        val teams = TeamRepository { teamsJson }.getAllTeams()
        playerRepository.setPlayers(teams.flatMap { LeagueGenerator.generatePlayersForTeam(it.id, random) })

        val (homeTeam, awayTeam) = teams.shuffled(random).take(2)
        val matchup =
            Matchup(
                homeTeam = homeTeam,
                awayTeam = awayTeam,
                homeRoster = playerRepository.getPlayersByTeam(homeTeam.id),
                awayRoster = playerRepository.getPlayersByTeam(awayTeam.id),
            )
        _matchup.value = matchup
        matchPresenter.startMatch(matchup)
    }
}
