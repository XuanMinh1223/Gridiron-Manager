package com.xuan.gridironmanager.domain.sim.play

import com.xuan.gridironmanager.domain.model.PlayType
import com.xuan.gridironmanager.domain.model.Vector3D
import com.xuan.gridironmanager.domain.sim.FieldGeometry
import com.xuan.gridironmanager.domain.sim.movement.PlayerRole
import com.xuan.gridironmanager.domain.sim.movement.RunningPlayer

/** A defender's true coverage assignment, applied in place when a disguised call rotates. */
data class RotationAssignment(
    val slot: String,
    val role: PlayerRole,
    val coverageTargetId: String?,
    val zoneLandmark: Vector3D?,
)

/** The true assignments, keyed by defender slot, a disguised call rotates into [rotationSec] after the snap. */
data class RotationPlan(
    val rotationSec: Float,
    val assignments: Map<String, RotationAssignment>,
)

/**
 * Everything [PlaySimulator] needs to run one play: both teams lined up, and where the ball is snapped from.
 * [defense] always holds the pre-snap (shell) look; [rotation] holds the true call, if disguised.
 */
data class Snap(
    val offense: List<RunningPlayer>,
    val defense: List<RunningPlayer>,
    val playType: PlayType,
    /** Line of scrimmage from the offense's point of view (0 = own goal line). */
    val losYardLine: Int,
    val isAttackingUp: Boolean,
    /** Receiver ids in the quarterback's read order. */
    val progression: List<String> = emptyList(),
    val rotation: RotationPlan? = null,
) {
    val losWorldY: Float get() = FieldGeometry.toWorldY(losYardLine, isAttackingUp)
}
