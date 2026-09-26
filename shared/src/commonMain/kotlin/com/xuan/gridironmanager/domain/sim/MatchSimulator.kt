package com.xuan.gridironmanager.domain.sim

import com.xuan.gridironmanager.domain.model.Matchup
import com.xuan.gridironmanager.domain.sim.match.DriveEngine
import com.xuan.gridironmanager.domain.sim.match.GameState
import com.xuan.gridironmanager.domain.sim.match.PlayOutcome
import com.xuan.gridironmanager.domain.sim.play.PlaySimulator
import com.xuan.gridironmanager.domain.sim.play.Snap
import com.xuan.gridironmanager.domain.sim.playbook.DefensiveCall
import com.xuan.gridironmanager.domain.sim.playbook.OffensivePlay
import com.xuan.gridironmanager.domain.sim.playbook.PlayCaller
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
) {
    val playCaller = PlayCaller(matchup, random)

    fun lineUp(
        state: GameState,
        offensivePlay: OffensivePlay,
        defensiveCall: DefensiveCall,
    ): Snap = SnapBuilder.build(state, matchup, offensivePlay, defensiveCall)

    fun startPlay(snap: Snap): PlaySimulator = PlaySimulator(snap, random)

    fun resolve(
        state: GameState,
        outcome: PlayOutcome,
        playDurationSec: Float,
    ): GameState = driveEngine.resolve(state, outcome, playDurationSec)

    /** Simulates one play without any delay between ticks. */
    fun runPlay(
        state: GameState,
        offensivePlay: OffensivePlay,
        defensiveCall: DefensiveCall,
    ): PlayRecord {
        val play = startPlay(lineUp(state, offensivePlay, defensiveCall))
        var outcome: PlayOutcome?
        do {
            outcome = play.tick(TICK_DELTA_SEC)
        } while (outcome == null)
        return PlayRecord(outcome, resolve(state, outcome, play.elapsedSec), play.elapsedSec)
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
        private const val MAX_PLAYS_PER_GAME = 2_000
    }
}
