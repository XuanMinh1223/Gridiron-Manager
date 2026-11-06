package com.xuan.gridironmanager.ui.match

import com.xuan.gridironmanager.domain.model.Matchup
import com.xuan.gridironmanager.domain.model.PlayType
import com.xuan.gridironmanager.domain.model.PlayerAttributes
import com.xuan.gridironmanager.domain.model.Position
import com.xuan.gridironmanager.domain.sim.MatchSimulator
import com.xuan.gridironmanager.domain.sim.ai.CoverageRead
import com.xuan.gridironmanager.domain.sim.match.GamePhase
import com.xuan.gridironmanager.domain.sim.match.GameState
import com.xuan.gridironmanager.domain.sim.match.GameStats
import com.xuan.gridironmanager.domain.sim.match.PlayOutcome
import com.xuan.gridironmanager.domain.sim.movement.RunningPlayer
import com.xuan.gridironmanager.domain.sim.play.Snap
import com.xuan.gridironmanager.domain.sim.playbook.DefensiveCall
import com.xuan.gridironmanager.domain.sim.playbook.OffensivePlay
import com.xuan.gridironmanager.domain.sim.playbook.Playbook
import com.xuan.gridironmanager.ui.match.overlay.TacticalOverlay
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

/**
 * Owns the live match: play calling for the user's team (with the CPU calling for the opponent), the pre-snap
 * preview, and playing each snap out in real time through [MatchSimulator].
 */
class MatchPresenter(
    private val scope: CoroutineScope,
    private val simDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val random: Random = Random.Default,
    private val userIsHome: Boolean = true,
) : MatchActions {
    private val _uiState = MutableStateFlow(MatchUiState())
    val uiState: StateFlow<MatchUiState> = _uiState.asStateFlow()

    private var simulator: MatchSimulator? = null
    private var currentMatchup: Matchup? = null

    // The CPU's calls for the next snap, for whichever sides it controls
    private var cpuOffense: OffensivePlay? = null
    private var cpuDefense: DefensiveCall? = null

    // The user's QB's pre-snap read of the CPU's coverage, if it is disguised and the user is on offense
    private var userQbIdentified: Boolean? = null

    fun startMatch(
        matchup: Matchup,
        initialState: GameState = GameState.openingKickoff(),
    ) {
        currentMatchup = matchup
        simulator = MatchSimulator(matchup, random)
        _uiState.update { it.copy(gameState = initialState, gameStats = GameStats(), isMatchReady = true) }
        preparePlayCalls()
        showPreview()
    }

    override fun setDebugMode(enabled: Boolean) {
        _uiState.update { it.copy(isDebugMode = enabled) }
    }

    override fun resetGame() {
        val matchup = currentMatchup ?: return
        startMatch(matchup)
    }

    override fun selectOffensivePlay(play: OffensivePlay) {
        _uiState.update { it.copy(playCall = it.playCall.copy(selectedOffense = play)) }
        showPreview()
    }

    override fun selectDefensiveCall(call: DefensiveCall) {
        _uiState.update { it.copy(playCall = it.playCall.copy(selectedDefense = call)) }
        showPreview()
    }

    override fun setAutoCall(enabled: Boolean) {
        _uiState.update { it.copy(playCall = it.playCall.copy(isAutoCall = enabled)) }
        showPreview()
    }

    override fun setShowAssignments(enabled: Boolean) {
        _uiState.update { it.copy(showAssignments = enabled) }
        if (_uiState.value.isPlayRunning) return
        showPreview()
    }

    override fun setSimSpeed(speed: SimSpeed) {
        _uiState.update { it.copy(simSpeed = speed) }
    }

    override fun snapBall() {
        val simulator = simulator ?: return
        val current = _uiState.value
        if (current.isPlayRunning || current.gameState.isGameOver) return

        // If the CPU has the ball it may call a timeout; if the user has it, only the user's button can
        val userHasBall = current.gameState.isHomePossession == userIsHome
        val state = if (userHasBall) current.gameState else simulator.maybeCallTimeout(current.gameState)
        val offensivePlay = offensiveCallForSnap(current.playCall) ?: return
        val defensiveCall = defensiveCallForSnap(current.playCall) ?: return
        val snap = simulator.lineUp(state, offensivePlay, defensiveCall)

        _uiState.update {
            it.withLineup(snap, state).copy(
                isPlayRunning = true,
                playByPlayText = snapText(snap.playType),
                overlay = liveOverlay(snap.offense + snap.defense, snap.losWorldY),
            )
        }

        scope.launch(simDispatcher) {
            val play = simulator.startPlay(snap, userQbIdentified)
            var outcome: PlayOutcome?
            do {
                delay(scaledMillis(TICK_MILLIS).milliseconds)
                outcome = play.tick(MatchSimulator.TICK_DELTA_SEC)
                val players = play.players.map { p -> p.copy() }
                _uiState.update {
                    it.copy(
                        gameState = liveClock(state, play.elapsedSec),
                        players = players,
                        ballPosition = play.ballPosition,
                        overlay = liveOverlay(players, snap.losWorldY),
                    )
                }
            } while (outcome == null)

            val nextState = simulator.resolve(state, outcome, play.elapsedSec)
            simulator.recordPlay(state, snap, outcome)
            simulator.playCaller.recordPlay(state, offensivePlay, defensiveCall)
            val summary =
                if (state.phase == GamePhase.KICKOFF) outcome.description
                else "${outcome.description} (${offensivePlay.name} vs ${defensiveCall.name})"
            _uiState.update {
                it.copy(
                    gameState = nextState,
                    gameStats = simulator.gameStats,
                    isPlayRunning = false,
                    playByPlayText = if (nextState.isGameOver) "$summary That's the final whistle!" else summary,
                    ballPosition = null,
                    overlay = TacticalOverlay.NONE,
                )
            }
            preparePlayCalls()

            // Leave the final frame up for a moment before lining up for the next snap
            delay(scaledMillis(RESULT_PAUSE_MILLIS).milliseconds)
            showPreview()
        }
    }

    override fun callTimeout() {
        val current = _uiState.value
        if (current.isPlayRunning || current.gameState.isGameOver || current.gameState.phase == GamePhase.KICKOFF) return
        _uiState.update { it.copy(gameState = it.gameState.withTimeoutCalled()) }
    }

    override fun quickSim() {
        val simulator = simulator ?: return
        val current = _uiState.value
        if (current.isPlayRunning || current.gameState.isGameOver) return

        _uiState.update { it.copy(isPlayRunning = true, playByPlayText = "Simulating the rest of the game...", ballPosition = null) }
        scope.launch(simDispatcher) {
            val finalState = simulator.simulateRestOfGame(current.gameState)
            _uiState.update {
                it.copy(
                    gameState = finalState,
                    gameStats = simulator.gameStats,
                    isPlayRunning = false,
                    players = emptyList(),
                    lineOfScrimmageY = null,
                    firstDownMarkerY = null,
                    overlay = TacticalOverlay.NONE,
                    playByPlayText = "Quick sim complete. Final score: ${finalState.homeScore}-${finalState.awayScore}.",
                )
            }
        }
    }

    /** CPU calls both sides, then offers the user their options with the CPU's suggestion preselected. */
    private fun preparePlayCalls() {
        val simulator = simulator ?: return
        val state = _uiState.value.gameState
        if (state.isGameOver) return

        cpuOffense = simulator.playCaller.callOffense(state)
        cpuDefense = simulator.playCaller.callDefense(state)
        val isUserOnOffense = state.isHomePossession == userIsHome

        // The human QB's read of a disguised coverage is rolled once per play, here, and handed to the simulator at the
        // snap so the hint and the play always agree. The CPU's true call stays out of the UI state.
        val disguisedCall = cpuDefense?.takeIf { isUserOnOffense && it.disguise != null && state.phase != GamePhase.KICKOFF }
        userQbIdentified = disguisedCall?.let { CoverageRead.roll(userQbAttributes(), random) }
        val coverageHint = disguisedCall?.let { call -> userQbIdentified?.let { CoverageRead.hint(call, it) } }

        _uiState.update {
            it.copy(
                playCall =
                    it.playCall.copy(
                        isUserOnOffense = isUserOnOffense,
                        offenseOptions = if (isUserOnOffense) Playbook.offensivePlaysFor(state.phase) else emptyList(),
                        defenseOptions = if (!isUserOnOffense && state.phase != GamePhase.KICKOFF) Playbook.defenseOptions else emptyList(),
                        selectedOffense = cpuOffense,
                        // Only the user's own call is exposed: on offense the CPU's call may be a disguise
                        selectedDefense = if (isUserOnOffense) null else cpuDefense,
                        coverageHint = coverageHint,
                    ),
            )
        }
    }

    /** Lines both teams up for the calls that would run if the ball were snapped now, showing the user's assignments. */
    private fun showPreview() {
        val simulator = simulator ?: return
        val current = _uiState.value
        if (current.isPlayRunning || current.gameState.isGameOver) return

        val offensivePlay = offensiveCallForSnap(current.playCall) ?: return
        val defensiveCall = defensiveCallForSnap(current.playCall) ?: return
        val snap = simulator.lineUp(current.gameState, offensivePlay, defensiveCall)
        val isUserOnOffense = current.playCall.isUserOnOffense

        _uiState.update {
            it.withLineup(snap, current.gameState).copy(
                ballPosition = null,
                overlay =
                    TacticalOverlay.build(
                        players = snap.offense + snap.defense,
                        losWorldY = snap.losWorldY,
                        showOffense = it.showAssignments && isUserOnOffense,
                        showDefense = it.showAssignments && !isUserOnOffense,
                    ),
            )
        }
    }

    private fun userQbAttributes(): PlayerAttributes {
        val roster = currentMatchup?.let { if (userIsHome) it.homeRoster else it.awayRoster }.orEmpty()
        return roster.firstOrNull { it.position == Position.QB.abbreviation }?.attributes ?: PlayerAttributes.AVERAGE
    }

    private fun MatchUiState.withLineup(
        snap: Snap,
        state: GameState,
    ): MatchUiState {
        val direction = if (snap.isAttackingUp) 1f else -1f
        val showsFirstDownLine = state.phase == GamePhase.SCRIMMAGE && (snap.playType == PlayType.RUN || snap.playType == PlayType.PASS)
        return copy(
            players = snap.offense + snap.defense,
            lineOfScrimmageY = snap.losWorldY,
            firstDownMarkerY = if (showsFirstDownLine) snap.losWorldY + state.distance * direction else null,
            isAttackingUp = snap.isAttackingUp,
        )
    }

    private fun liveOverlay(
        players: List<RunningPlayer>,
        losWorldY: Float,
    ): TacticalOverlay {
        val show = _uiState.value.showAssignments
        return if (show) TacticalOverlay.build(players, losWorldY, showOffense = true, showDefense = true) else TacticalOverlay.NONE
    }

    private fun offensiveCallForSnap(playCall: PlayCallState): OffensivePlay? =
        if (playCall.isUserOnOffense && !playCall.isAutoCall) playCall.selectedOffense else cpuOffense

    private fun defensiveCallForSnap(playCall: PlayCallState): DefensiveCall? =
        if (!playCall.isUserOnOffense && !playCall.isAutoCall) playCall.selectedDefense else cpuDefense

    private fun scaledMillis(millis: Long) = millis / _uiState.value.simSpeed.multiplier

    /** The scoreboard clock while a play is running. Tries are untimed. */
    private fun liveClock(
        state: GameState,
        elapsedSec: Float,
    ): GameState = if (state.phase == GamePhase.EXTRA_POINT) state else state.copy(clockSeconds = (state.clockSeconds - elapsedSec.toInt()).coerceAtLeast(0))

    private fun snapText(playType: PlayType) =
        when (playType) {
            PlayType.RUN -> "Hand-off!"
            PlayType.PASS -> "Ball is snapped!"
            PlayType.KICKOFF, PlayType.PUNT -> "Ready for the kick!"
            PlayType.FIELD_GOAL -> "The snap, the hold..."
            PlayType.KNEEL -> "Victory formation..."
            PlayType.SPIKE -> "Quick snap!"
        }

    private companion object {
        const val TICK_MILLIS = 50L // At 1x, one 0.05 s simulation tick per 50 ms: real time
        const val RESULT_PAUSE_MILLIS = 1_500L
    }
}
