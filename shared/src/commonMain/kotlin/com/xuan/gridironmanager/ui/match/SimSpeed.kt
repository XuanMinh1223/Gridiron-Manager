package com.xuan.gridironmanager.ui.match

/** Playback speed for the live match. Only the delay between ticks changes; the simulation itself is identical. */
enum class SimSpeed(
    val multiplier: Int,
) {
    X1(1),
    X2(2),
    X5(5),
    X10(10),
    ;

    val label: String get() = "${multiplier}x"
}
