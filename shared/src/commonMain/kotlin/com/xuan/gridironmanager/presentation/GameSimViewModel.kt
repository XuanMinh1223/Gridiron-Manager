package com.xuan.gridironmanager.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xuan.gridironmanager.domain.engine.LeagueGenerator
import com.xuan.gridironmanager.domain.model.Matchup
import com.xuan.gridironmanager.domain.model.PlayType
import com.xuan.gridironmanager.domain.repository.PlayerRepository
import com.xuan.gridironmanager.domain.repository.TeamRepository
import com.xuan.gridironmanager.domain.sim.match.DriveEngine
import com.xuan.gridironmanager.domain.sim.match.Rules
import com.xuan.gridironmanager.domain.sim.play.PlaySetupHelper
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

    private val matchPresenter = MatchPresenter(DriveEngine(), viewModelScope, random = random)

    /** Single source of truth for the live match. */
    val matchUiState: StateFlow<MatchUiState> = matchPresenter.uiState

    init {
        viewModelScope.launch { setUpLeague() }
    }

    private suspend fun setUpLeague() {
        val teamsJson = loadTeamsJson()
        val teams = TeamRepository { teamsJson }.getAllTeams()
        playerRepository.setPlayers(teams.flatMap { LeagueGenerator.generatePlayersForTeam(it.id, random) })

        val (homeTeam, awayTeam) = teams.shuffled(random).take(2)
        _matchup.value =
            Matchup(
                homeTeam = homeTeam,
                awayTeam = awayTeam,
                homeRoster = playerRepository.getPlayersByTeam(homeTeam.id),
                awayRoster = playerRepository.getPlayersByTeam(awayTeam.id),
            )
    }

    fun startVisualPlay() {
        val matchup = _matchup.value ?: return
        val uiState = matchUiState.value
        val state = uiState.gameState
        if (uiState.isPlayRunning || state.isGameOver) return

        val isHomePossession = state.isHomePossession

        // Determine Play Type (NFL Logic)
        val playType =
            when {
                state.isKickoffPending -> PlayType.KICK
                state.down == 4 && state.distance > 5 -> PlayType.PUNT // 4th and long
                else -> if (random.nextBoolean()) PlayType.RUN else PlayType.PASS
            }

        // Use appropriate formations
        val (offFormation, defFormation) =
            when (playType) {
                PlayType.KICK -> PlaySetupHelper.getKickoffFormation() to PlaySetupHelper.getKickReturnFormation()
                PlayType.PUNT -> PlaySetupHelper.getPuntFormation() to PlaySetupHelper.getPuntReturnFormation()
                else -> PlaySetupHelper.getShotgunFormation() to PlaySetupHelper.getBaseDefense()
            }

        // World coordinates: Home attacks towards 100 (+Y), Away attacks towards 0 (-Y)
        val offLosWorldY = if (isHomePossession) state.yardLine.toFloat() else (Rules.FIELD_LENGTH_YDS - state.yardLine).toFloat()

        // The kick return team lines up from its own goal line
        val defLosWorldY =
            when (playType) {
                PlayType.KICK -> if (isHomePossession) 98f else 2f
                else -> offLosWorldY
            }

        val offense =
            PlaySetupHelper.createRunningPlayers(
                roster = if (isHomePossession) matchup.homeRoster else matchup.awayRoster,
                formation = offFormation,
                losWorldY = offLosWorldY,
                isOffense = true,
                isAttackingUp = isHomePossession,
            )

        val defense =
            PlaySetupHelper.createRunningPlayers(
                roster = if (isHomePossession) matchup.awayRoster else matchup.homeRoster,
                formation = defFormation,
                losWorldY = defLosWorldY,
                isOffense = false,
                isAttackingUp = isHomePossession,
            )

        matchPresenter.snapBall(offense, defense, playType, isHomePossession)
    }
}
