package com.xuan.gridironmanager.domain.sim.match

import com.xuan.gridironmanager.domain.sim.play.Snap
import com.xuan.gridironmanager.domain.sim.playbook.DefensiveCall
import com.xuan.gridironmanager.domain.sim.playbook.OffensivePlay

/** The seed of a game whose caller did not supply one (the live app plays from an unseeded random). */
const val UNKNOWN_SEED = -1L

/** A yard line from the offense's point of view (0 = its own goal line) as `OWN 32`, `50` or `OPP 41`. */
fun formatYardLine(yardLine: Int): String {
    val line = yardLine.coerceIn(0, Rules.FIELD_LENGTH_YDS)
    return when {
        line < Rules.FIELD_LENGTH_YDS / 2 -> "OWN $line"
        line == Rules.FIELD_LENGTH_YDS / 2 -> "50"
        else -> "OPP ${Rules.FIELD_LENGTH_YDS - line}"
    }
}

/** The scoreboard's `mm:ss` (minutes are not padded, as on screen). */
fun formatClock(clockSeconds: Int): String = "${clockSeconds / 60}:${(clockSeconds % 60).toString().padStart(2, '0')}"

private fun ordinal(down: Int) =
    when (down) {
        1 -> "1st"
        2 -> "2nd"
        3 -> "3rd"
        else -> "${down}th"
    }

/** `+6`, `0`, `-3`. */
internal fun signedYards(yards: Int) = if (yards > 0) "+$yards" else "$yards"

/**
 * One play as a block of plain text: a two-line header, the trace lines exactly as recorded, and an `OUTCOME:` and a
 * `STATS:` line. Pure and deterministic. It never re-derives a decision: a line that is not in the trace is not here.
 *
 * Beyond the play itself it needs the names of the calls (the snap carries none), and the state after the play for the
 * outcome line.
 */
fun formatPlayDebugBlock(
    preState: GameState,
    postState: GameState,
    snap: Snap,
    seed: Long,
    playNumber: Int,
    offensivePlay: OffensivePlay,
    defensiveCall: DefensiveCall,
    result: PlayResult,
    statsSummary: String,
): String {
    val defense =
        defensiveCall.disguise?.let { disguise ->
            val shell = disguise.shellCoverage?.label ?: "none"
            val truth = defensiveCall.coverage?.label ?: "none"
            "${defensiveCall.name} (shell $shell → true $truth)"
        } ?: defensiveCall.name

    val header =
        "--- Play $playNumber · Q${preState.quarter} ${formatClock(preState.clockSeconds)} · " +
            "${ordinal(preState.down)} & ${preState.distance} at ${formatYardLine(snap.losYardLine)} · " +
            "Home ${preState.homeScore} – Away ${preState.awayScore} · seed ${if (seed == UNKNOWN_SEED) "unknown" else seed.toString()} ---"

    return buildList {
        add(header)
        add("OFF: ${offensivePlay.formation.name} – ${offensivePlay.name} · DEF: $defense")
        addAll(result.debugTrace)
        val after = "${formatYardLine(postState.yardLine)} · ${ordinal(postState.down)} & ${postState.distance}"
        add("OUTCOME: ${result.description} · ${signedYards(result.yardsGained)} yd · $after")
        add("STATS: ${statsSummary.ifEmpty { "(none)" }}")
    }.joinToString("\n")
}
