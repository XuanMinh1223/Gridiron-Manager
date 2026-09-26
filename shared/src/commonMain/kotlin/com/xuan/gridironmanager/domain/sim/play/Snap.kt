package com.xuan.gridironmanager.domain.sim.play

import com.xuan.gridironmanager.domain.model.PlayType
import com.xuan.gridironmanager.domain.sim.FieldGeometry
import com.xuan.gridironmanager.domain.sim.movement.RunningPlayer

/** Everything [PlaySimulator] needs to run one play: both teams lined up, and where the ball is snapped from. */
data class Snap(
    val offense: List<RunningPlayer>,
    val defense: List<RunningPlayer>,
    val playType: PlayType,
    /** Line of scrimmage from the offense's point of view (0 = own goal line). */
    val losYardLine: Int,
    val isAttackingUp: Boolean,
    /** Receiver ids in the quarterback's read order. */
    val progression: List<String> = emptyList(),
) {
    val losWorldY: Float get() = FieldGeometry.toWorldY(losYardLine, isAttackingUp)
}
