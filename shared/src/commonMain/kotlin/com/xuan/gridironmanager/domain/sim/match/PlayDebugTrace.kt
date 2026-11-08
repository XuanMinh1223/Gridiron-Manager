package com.xuan.gridironmanager.domain.sim.match

import kotlin.math.abs

/**
 * What happened during one play, one line per event, in the order it happened. Diagnostic only: nothing reads it back
 * into the sim, and it is never created at all when debugging is off (callers hold a null trace instead).
 */
class PlayDebugTrace {
    private val lines = mutableListOf<String>()

    fun line(text: String) {
        lines.add(text)
    }

    fun lines(): List<String> = lines.toList()
}

/** A trace line stamped with the play clock, e.g. `t=0.65 pressure: WLB at 2.8 yd`. */
fun PlayDebugTrace.at(
    elapsedSec: Float,
    text: String,
) = line("t=${elapsedSec.fixed(2)} $text")

/**
 * Formats a float with a fixed number of decimals. Common Kotlin has no String.format, and the log must read the same
 * on every platform, so this rounds with plain arithmetic.
 */
fun Float.fixed(decimals: Int): String {
    var factor = 1L
    repeat(decimals) { factor *= 10 }
    val scaled = (abs(this.toDouble()) * factor + 0.5).toLong()
    val whole = scaled / factor
    val fraction = if (decimals == 0) "" else "." + (scaled % factor).toString().padStart(decimals, '0')
    val sign = if (this < 0f && scaled > 0) "-" else ""
    return "$sign$whole$fraction"
}
