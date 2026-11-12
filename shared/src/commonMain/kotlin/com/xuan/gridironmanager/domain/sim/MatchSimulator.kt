package com.xuan.gridironmanager.domain.sim

import com.xuan.gridironmanager.domain.model.Matchup
import com.xuan.gridironmanager.domain.sim.match.DriveEngine
import com.xuan.gridironmanager.domain.sim.match.GamePhase
import com.xuan.gridironmanager.domain.sim.match.GameState
import com.xuan.gridironmanager.domain.sim.match.GameStats
import com.xuan.gridironmanager.domain.sim.match.GameStatsTracker
import com.xuan.gridironmanager.domain.sim.match.PlayDebugTrace
import com.xuan.gridironmanager.domain.sim.match.PlayOutcome
import com.xuan.gridironmanager.domain.sim.match.PlayResult
import com.xuan.gridironmanager.domain.sim.match.Rules
import com.xuan.gridironmanager.domain.sim.match.UNKNOWN_SEED
import com.xuan.gridironmanager.domain.sim.match.formatPlayDebugBlock
import com.xuan.gridironmanager.domain.sim.play.PlaySimulator
import com.xuan.gridironmanager.domain.sim.play.Snap
import com.xuan.gridironmanager.domain.sim.playbook.DefensiveCall
import com.xuan.gridironmanager.domain.sim.playbook.OffensivePlay
import com.xuan.gridironmanager.domain.sim.playbook.PlayCaller
import com.xuan.gridironmanager.domain.sim.playbook.Playbook
import com.xuan.gridironmanager.domain.sim.playbook.SnapBuilder
import kotlin.random.Random

data class PlayRecord(
    val outcome: PlayOutcome,
    val nextState: GameState,
    val durationSec: Float,
)

/**
 * Runs the plays of one match: lines up each snap, simulates it and applies the rules. The live match screen and
 * instant simulation go through the same steps, so a seeded game plays out identically either way.
 */
class MatchSimulator(
    private val matchup: Matchup,
    private val random: Random = Random.Default,
    private val driveEngine: DriveEngine = DriveEngine(),
    /** The seed [random] was created from, for the debug log header. [UNKNOWN_SEED] when the caller did not say. */
    val seed: Long = UNKNOWN_SEED,
) {
    val playCaller = PlayCaller(matchup, random)
    private val statsTracker = GameStatsTracker(matchup)

    val gameStats: GameStats get() = statsTracker.snapshot()

    /**
     * Turns per-play tracing and the game log on or off. Off by default, and when off no trace is built at all, so the
     * sim runs exactly as it always did. Toggling mid-game only affects the plays that follow.
     */
    var debugEnabled: Boolean = false

    /** One formatted block per completed play, in play order. Diagnostic only: nothing reads it back into the sim. */
    val debugLog: MutableList<String> = mutableListOf()
    val debugLogText: String get() = debugLog.joinToString("\n\n")

    /** Plays completed so far this game (the next play is this + 1). Counted whether or not debugging is on. */
    private var playsCompleted = 0

    private class Lineup(
        val snap: Snap,
        val offensivePlay: OffensivePlay,
        val defensiveCall: DefensiveCall,
    )

    private var lineup: Lineup? = null
    private var activeSnap: Snap? = null
    private var activeTrace: PlayDebugTrace? = null

    /**
     * Books a finished play into the box score. [state] is the state *before* the play. Both the live screen and
     * [runPlay] call this, so a stat is recorded exactly once per play that was actually run.
     */
    fun recordPlay(
        state: GameState,
        snap: Snap,
        outcome: PlayOutcome,
    ) = statsTracker.record(state, snap, outcome)

    fun lineUp(
        state: GameState,
        offensivePlay: OffensivePlay,
        defensiveCall: DefensiveCall,
    ): Snap =
        SnapBuilder.build(state, matchup, offensivePlay, defensiveCall).also {
            lineup = Lineup(it, offensivePlay, defensiveCall)
        }

    fun startPlay(
        snap: Snap,
        qbIdentified: Boolean? = null,
    ): PlaySimulator {
        // A fresh trace per play, and none at all when debugging is off
        val trace = if (debugEnabled) PlayDebugTrace() else null
        activeSnap = snap
        activeTrace = trace
        return PlaySimulator(snap, random, qbIdentified, trace)
    }

    /**
     * Applies the rules to a finished play. With debugging on this also files the play's log block, which reports the
     * stats tracker's summary of *this* play, so callers must [recordPlay] before they resolve.
     */
    fun resolve(
        state: GameState,
        outcome: PlayOutcome,
        playDurationSec: Float,
    ): GameState {
        val next = driveEngine.resolve(state, outcome, playDurationSec)
        playsCompleted++
        if (debugEnabled) appendDebugBlock(state, next, outcome)
        return next
    }

    private fun appendDebugBlock(
        preState: GameState,
        postState: GameState,
        outcome: PlayOutcome,
    ) {
        // Only a play lined up and started through this simulator has the names and trace the block needs
        val played = lineup?.takeIf { it.snap === activeSnap } ?: return
        val trace = activeTrace?.lines().orEmpty()
        val result =
            when (outcome) {
                is PlayOutcome.Scrimmage -> outcome.result.copy(debugTrace = trace)
                is PlayOutcome.Kick -> PlayResult(outcome.result.returnYards, outcome.description, isTouchdown = false, isTurnover = false, debugTrace = trace)
                is PlayOutcome.FieldGoal -> PlayResult(0, outcome.description, isTouchdown = false, isTurnover = false, debugTrace = trace)
            }
        debugLog +=
            formatPlayDebugBlock(
                preState = preState,
                postState = postState,
                snap = played.snap,
                seed = seed,
                playNumber = playsCompleted,
                offensivePlay = played.offensivePlay,
                // Kicking plays swap in a special teams unit for the call, and that is who actually lined up
                defensiveCall = Playbook.defensiveUnitFor(played.offensivePlay.type, played.defensiveCall),
                result = result,
                statsSummary = statsTracker.lastRecordSummary,
            )
    }

    /**
     * The CPU's timeout: a trailing team late in the fourth quarter or overtime stops the clock before the snap. Only
     * the team with the ball can call one, and not if a timeout is already called (so it never double-counts with the
     * user's button) or none are left. Callers decide whether the possessing team is CPU-controlled.
     */
    fun maybeCallTimeout(state: GameState): GameState {
        val isLate = (state.quarter >= Rules.QUARTERS || state.isOvertime) && state.clockSeconds <= CPU_TIMEOUT_CLOCK_SEC
        return if (isLate && state.possessionScoreMargin < 0 && state.phase != GamePhase.KICKOFF) state.withTimeoutCalled() else state
    }

    /** Simulates one play without any delay between ticks. */
    fun runPlay(
        state: GameState,
        offensivePlay: OffensivePlay,
        defensiveCall: DefensiveCall,
    ): PlayRecord {
        // Both sides are CPU here, so the possessing team may call a timeout
        val calledState = maybeCallTimeout(state)
        val snap = lineUp(calledState, offensivePlay, defensiveCall)
        val play = startPlay(snap)
        var outcome: PlayOutcome?
        do {
            outcome = play.tick(TICK_DELTA_SEC)
        } while (outcome == null)
        recordPlay(calledState, snap, outcome)
        playCaller.recordPlay(calledState, offensivePlay, defensiveCall)
        return PlayRecord(outcome, resolve(calledState, outcome, play.elapsedSec), play.elapsedSec)
    }

    /** Plays out the rest of the game instantly, with the CPU calling plays for both teams. */
    fun simulateRestOfGame(state: GameState): GameState {
        var current = state
        repeat(MAX_PLAYS_PER_GAME) {
            if (current.isGameOver) return current
            current = runPlay(current, playCaller.callOffense(current), playCaller.callDefense(current)).nextState
        }
        error("Game did not finish within $MAX_PLAYS_PER_GAME plays")
    }

    companion object {
        /** Simulation step. Fixed, so results never depend on playback speed. */
        const val TICK_DELTA_SEC = 0.05f
        private const val CPU_TIMEOUT_CLOCK_SEC = 120
        private const val MAX_PLAYS_PER_GAME = 2_000
    }
}
