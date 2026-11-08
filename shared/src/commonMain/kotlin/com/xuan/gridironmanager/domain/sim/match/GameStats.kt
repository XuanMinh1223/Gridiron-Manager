package com.xuan.gridironmanager.domain.sim.match

import com.xuan.gridironmanager.domain.model.Matchup
import com.xuan.gridironmanager.domain.model.PlayType
import com.xuan.gridironmanager.domain.model.Player
import com.xuan.gridironmanager.domain.sim.play.Snap

data class PlayerGameStats(
    val teamId: String,
    val playerId: String,
    val name: String,
    val position: String,
    val passAttempts: Int = 0,
    val completions: Int = 0,
    val passYards: Int = 0,
    val passTouchdowns: Int = 0,
    val interceptionsThrown: Int = 0,
    val rushAttempts: Int = 0,
    val rushYards: Int = 0,
    val rushTouchdowns: Int = 0,
    val receptions: Int = 0,
    val receivingYards: Int = 0,
    val receivingTouchdowns: Int = 0,
    val tackles: Int = 0,
    val sacks: Int = 0,
    val interceptions: Int = 0,
    val fumblesLost: Int = 0,
    val fumblesRecovered: Int = 0,
    val fieldGoalsMade: Int = 0,
    val fieldGoalAttempts: Int = 0,
    val extraPointsMade: Int = 0,
    val extraPointAttempts: Int = 0,
    val returnYards: Int = 0,
    val returnTouchdowns: Int = 0,
)

data class GameStats(
    /** Keyed by player id. */
    val players: Map<String, PlayerGameStats> = emptyMap(),
) {
    fun totalsFor(teamId: String): TeamTotals {
        val team = players.values.filter { it.teamId == teamId }
        return TeamTotals(team.sumOf { it.passYards }, team.sumOf { it.rushYards }, team.sumOf { it.returnYards })
    }

    fun topPassers(count: Int = LEADERS_SHOWN) =
        players.values
            .filter { it.passAttempts > 0 }
            .sortedByDescending { it.passYards }
            .take(count)

    fun topRushers(count: Int = LEADERS_SHOWN) =
        players.values
            .filter { it.rushAttempts > 0 }
            .sortedByDescending { it.rushYards }
            .take(count)

    fun topReceivers(count: Int = LEADERS_SHOWN) =
        players.values
            .filter { it.receptions > 0 }
            .sortedByDescending { it.receivingYards }
            .take(count)

    private companion object {
        const val LEADERS_SHOWN = 3
    }
}

data class TeamTotals(
    val passYards: Int,
    val rushYards: Int,
    val returnYards: Int,
)

/** "James Smith" -> "J. Smith". */
private fun PlayerGameStats.shortName(): String = name.substringBefore(' ').take(1) + ". " + name.substringAfter(' ')

fun PlayerGameStats.passingLine() = "${shortName()} — $completions/$passAttempts, $passYards yds, $passTouchdowns TD"

fun PlayerGameStats.rushingLine() = "${shortName()} — $rushAttempts car, $rushYards yds, $rushTouchdowns TD"

fun PlayerGameStats.receivingLine() = "${shortName()} — $receptions rec, $receivingYards yds, $receivingTouchdowns TD"

/**
 * Accumulates per-player stats from the actors on each play's result. It never reads description strings.
 * Ids on results are snap ids (`"${slot}_${playerId}"`), so they are mapped back through the snap's slots.
 */
class GameStatsTracker(
    matchup: Matchup,
) {
    private val roster: Map<String, Player> = (matchup.homeRoster + matchup.awayRoster).associateBy { it.id }
    private val stats = mutableMapOf<String, PlayerGameStats>()

    fun snapshot() = GameStats(stats.toMap())

    /**
     * What the last [record] call actually booked, as `; `-separated entries (`passAtt QB; sack WLB; tackle WLB`), or
     * empty if it booked nothing. Names are formation slots. Diagnostic text only: it is built from what was recorded.
     */
    var lastRecordSummary: String = ""
        private set

    /** [state] is the state before the play: it says whether this was an extra-point try. */
    fun record(
        state: GameState,
        snap: Snap,
        outcome: PlayOutcome,
    ) {
        // Snap ids carry the slot as a prefix; strip it by slot rather than guessing, since player ids contain '_' too
        val slotById = (snap.offense + snap.defense).associate { it.id to it.slot }
        val entries = mutableListOf<String>()

        fun note(
            kind: String,
            snapId: String?,
            yards: Int? = null,
        ) {
            entries += listOfNotNull(kind, slotById[snapId] ?: snapId, yards?.let(::signedYards)).joinToString(" ")
        }

        /** Applies [change] to the player, returning whether there was a player to apply it to. */
        fun update(
            snapId: String?,
            change: (PlayerGameStats) -> PlayerGameStats,
        ): Boolean {
            if (snapId == null) return false
            val playerId = slotById[snapId]?.let { snapId.removePrefix("${it}_") } ?: snapId
            val player = roster[playerId] ?: return false
            val current = stats[playerId] ?: PlayerGameStats(player.teamId, player.id, player.fullName, player.position)
            stats[playerId] = change(current)
            return true
        }

        when (outcome) {
            is PlayOutcome.Scrimmage -> recordScrimmage(snap.playType, outcome.result, ::update, ::note)
            is PlayOutcome.FieldGoal -> {
                val isExtraPoint = state.phase == GamePhase.EXTRA_POINT
                update(outcome.result.kickerId) {
                    if (isExtraPoint) {
                        it.copy(extraPointAttempts = it.extraPointAttempts + 1, extraPointsMade = it.extraPointsMade + if (outcome.result.isGood) 1 else 0)
                    } else {
                        it.copy(fieldGoalAttempts = it.fieldGoalAttempts + 1, fieldGoalsMade = it.fieldGoalsMade + if (outcome.result.isGood) 1 else 0)
                    }
                }
            }

            is PlayOutcome.Kick -> {
                val kick = outcome.result
                val returned =
                    update(kick.returnerId) {
                        it.copy(
                            returnYards = it.returnYards + kick.returnYards,
                            returnTouchdowns = it.returnTouchdowns + if (kick.outcomeType == KickOutcomeType.RETURN_TOUCHDOWN) 1 else 0,
                        )
                    }
                if (returned) note("returnYds", kick.returnerId, kick.returnYards)
                if (kick.outcomeType == KickOutcomeType.RETURN_FUMBLE_RECOVERED && kick.recoveredByKickingTeam) {
                    if (update(kick.returnerId) { it.copy(fumblesLost = it.fumblesLost + 1) }) note("fumbleLost", kick.returnerId)
                    if (update(kick.recovererId) { it.copy(fumblesRecovered = it.fumblesRecovered + 1) }) note("fumbleRec", kick.recovererId)
                }
            }
        }
        lastRecordSummary = entries.joinToString("; ")
    }

    private fun recordScrimmage(
        playType: PlayType,
        result: PlayResult,
        update: (String?, (PlayerGameStats) -> PlayerGameStats) -> Boolean,
        note: (String, String?, Int?) -> Unit,
    ) {
        val actors = result.actors
        val yards = result.yardsGained

        when {
            result.isSack -> {
                // No pass attempt and no sack yardage accounting: the lost yards are already in the field position
                if (update(actors.tacklerId) { it.copy(sacks = it.sacks + 1) }) note("sack", actors.tacklerId, null)
            }

            result.isScramble || playType == PlayType.RUN || playType == PlayType.KNEEL -> {
                val rushed =
                    update(actors.carrierId) {
                        it.copy(
                            rushAttempts = it.rushAttempts + 1,
                            rushYards = it.rushYards + yards,
                            rushTouchdowns = it.rushTouchdowns + if (result.isTouchdown) 1 else 0,
                        )
                    }
                if (rushed) {
                    note("rushAtt", actors.carrierId, null)
                    note("rushYds", actors.carrierId, yards)
                }
            }

            playType == PlayType.PASS || playType == PlayType.SPIKE -> {
                val threw =
                    update(actors.passerId) {
                        it.copy(
                            passAttempts = it.passAttempts + 1,
                            completions = it.completions + if (result.isPassComplete) 1 else 0,
                            passYards = it.passYards + if (result.isPassComplete) yards else 0,
                            passTouchdowns = it.passTouchdowns + if (result.isPassComplete && result.isTouchdown) 1 else 0,
                            interceptionsThrown = it.interceptionsThrown + if (actors.interceptorId != null) 1 else 0,
                        )
                    }
                if (threw) {
                    note("passAtt", actors.passerId, null)
                    if (result.isPassComplete) {
                        note("completion", actors.receiverId, null)
                        note("passYds", actors.passerId, yards)
                    }
                }
                if (result.isPassComplete) {
                    update(actors.receiverId) {
                        it.copy(
                            receptions = it.receptions + 1,
                            receivingYards = it.receivingYards + yards,
                            receivingTouchdowns = it.receivingTouchdowns + if (result.isTouchdown) 1 else 0,
                        )
                    }
                }
            }
        }

        // A tackle is a play that ends by being brought down: not a score, not a turnover. Sacks count as tackles.
        if ((result.isSack || (!result.isTouchdown && !result.isTurnover)) && actors.tacklerId != null) {
            if (update(actors.tacklerId) { it.copy(tackles = it.tackles + 1) }) note("tackle", actors.tacklerId, null)
        }

        // Takeaways. A defensive score on a takeaway is credited only through the takeaway itself.
        if (result.isTurnover) {
            if (actors.interceptorId != null) {
                if (update(actors.interceptorId) { it.copy(interceptions = it.interceptions + 1) }) note("INT", actors.interceptorId, null)
            } else {
                if (update(actors.carrierId) { it.copy(fumblesLost = it.fumblesLost + 1) }) note("fumbleLost", actors.carrierId, null)
                if (update(actors.recovererId) { it.copy(fumblesRecovered = it.fumblesRecovered + 1) }) note("fumbleRec", actors.recovererId, null)
            }
        }
    }
}
