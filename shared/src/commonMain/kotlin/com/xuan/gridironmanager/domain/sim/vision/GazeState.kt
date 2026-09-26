package com.xuan.gridironmanager.domain.sim.vision

import com.xuan.gridironmanager.domain.model.Vector3D
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class GazeState(
    var facing: Vector3D,
    var targetId: String? = null,
) {
    fun turnToward(
        target: Vector3D,
        maxTurnDegrees: Float,
        tickDeltaSec: Float,
    ) {
        val desired = VisionEngine.direction(Vector3D(0f, 0f, 0f), target)
        if (desired.x == 0f && desired.y == 0f) return
        val currentLength = sqrt(facing.x * facing.x + facing.y * facing.y)
        val current = if (currentLength == 0f) desired else Vector3D(facing.x / currentLength, facing.y / currentLength, 0f)
        val angle = acos((current.x * desired.x + current.y * desired.y).coerceIn(-1f, 1f))
        val maxTurn = maxTurnDegrees * tickDeltaSec * PI.toFloat() / 180f
        if (angle <= maxTurn || maxTurn <= 0f) {
            facing = desired
            return
        }
        val cross = current.x * desired.y - current.y * desired.x
        val signedTurn = if (cross >= 0f) maxTurn else -maxTurn
        facing =
            Vector3D(
                x = current.x * cos(signedTurn) - current.y * sin(signedTurn),
                y = current.x * sin(signedTurn) + current.y * cos(signedTurn),
                z = 0f,
            )
    }
}
