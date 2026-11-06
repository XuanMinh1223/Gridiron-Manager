package com.xuan.gridironmanager.domain.sim.playbook

import com.xuan.gridironmanager.domain.model.PlayType
import com.xuan.gridironmanager.domain.sim.match.GameState

/**
 * What each team has actually done so far this game: run/pass counts by down, the defensive calls faced by down, and
 * its most recent offensive plays. Starts empty, so a caller that has seen nothing behaves as it always did.
 * Only plays that were really run are recorded: suggestions are not plays.
 */
class TendencyTracker {
    private class TeamRecord {
        val runs = IntArray(DOWNS + 1)
        val passes = IntArray(DOWNS + 1)
        val defenseCallsByDown = Array(DOWNS + 1) { mutableMapOf<String, Int>() }
        val recentPlayIds = ArrayDeque<String>()
    }

    private val teams = mutableMapOf<String, TeamRecord>()

    /** [teamId] is the team with the ball on this play. */
    fun record(
        teamId: String,
        state: GameState,
        offensivePlay: OffensivePlay,
        defensiveCall: DefensiveCall,
    ) {
        val down = state.down.coerceIn(1, DOWNS)
        val team = teams.getOrPut(teamId) { TeamRecord() }
        when (offensivePlay.type) {
            PlayType.RUN -> team.runs[down]++
            PlayType.PASS -> team.passes[down]++
            else -> return // Kicks, kneels and spikes say nothing about run/pass tendency
        }
        val callsOnThisDown = team.defenseCallsByDown[down]
        callsOnThisDown[defensiveCall.id] = (callsOnThisDown[defensiveCall.id] ?: 0) + 1
        team.recentPlayIds.addLast(offensivePlay.id)
        if (team.recentPlayIds.size > RECENT_PLAYS) team.recentPlayIds.removeFirst()
    }

    /** Runs / (runs + passes) on [down], or 0.5 until there are at least [MIN_SAMPLE] plays to judge by. */
    fun runRate(
        teamId: String,
        down: Int,
    ): Float {
        val team = teams[teamId] ?: return NEUTRAL_RATE
        val d = down.coerceIn(1, DOWNS)
        val total = team.runs[d] + team.passes[d]
        return if (total < MIN_SAMPLE) NEUTRAL_RATE else team.runs[d].toFloat() / total
    }

    fun passRate(
        teamId: String,
        down: Int,
    ): Float = 1f - runRate(teamId, down)

    fun lastOffensivePlayIds(teamId: String): List<String> = teams[teamId]?.recentPlayIds?.toList() ?: emptyList()

    /** How many times the defense has called [callId] against [teamId] on [down]. */
    fun defenseCallCount(
        teamId: String,
        down: Int,
        callId: String,
    ): Int = teams[teamId]?.defenseCallsByDown?.get(down.coerceIn(1, DOWNS))?.get(callId) ?: 0

    companion object {
        private const val DOWNS = 4
        private const val RECENT_PLAYS = 10
        const val MIN_SAMPLE = 5
        private const val NEUTRAL_RATE = 0.5f
    }
}
