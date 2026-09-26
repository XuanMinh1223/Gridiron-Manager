package com.xuan.gridironmanager.domain.sim.vision

import com.xuan.gridironmanager.domain.model.Vector3D
import kotlin.math.sqrt

data class VisionResult(
    val visible: Boolean,
    val distanceYds: Float,
    val angleDegrees: Float,
    val blockerId: String? = null,
)

/** Geometry-only perception queries. Awareness belongs to the caller, not this class. */
object VisionEngine {
    fun inspect(
        observer: Vector3D,
        facing: Vector3D,
        target: Vector3D,
        fieldOfViewDegrees: Float,
        blockers: List<Pair<String, Vector3D>> = emptyList(),
        observerId: String? = null,
        targetId: String? = null,
        blockerRadiusYds: Float = DEFAULT_BLOCKER_RADIUS_YDS,
    ): VisionResult {
        val dx = target.x - observer.x
        val dy = target.y - observer.y
        val distance = sqrt(dx * dx + dy * dy)
        if (distance == 0f) return VisionResult(true, 0f, 0f)

        val facingLength = sqrt(facing.x * facing.x + facing.y * facing.y)
        if (facingLength == 0f) return VisionResult(false, distance, 180f)

        val dot = ((facing.x * dx + facing.y * dy) / (facingLength * distance)).coerceIn(-1f, 1f)
        val angle = kotlin.math.acos(dot) * 180f / kotlin.math.PI.toFloat()
        val inFieldOfView = angle <= fieldOfViewDegrees / 2f
        if (!inFieldOfView) return VisionResult(false, distance, angle)

        val blocker =
            blockers
                .firstOrNull { (id, position) ->
                    id != observerId && id != targetId && distanceToSegment(position, observer, target) <= blockerRadiusYds
                }?.first
        return VisionResult(blocker == null, distance, angle, blocker)
    }

    fun direction(
        from: Vector3D,
        to: Vector3D,
    ): Vector3D {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val length = sqrt(dx * dx + dy * dy)
        return if (length == 0f) Vector3D(0f, 0f, 0f) else Vector3D(dx / length, dy / length, 0f)
    }

    private fun distanceToSegment(
        point: Vector3D,
        start: Vector3D,
        end: Vector3D,
    ): Float {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared == 0f) return point.distance2DTo(start)
        val projection = (((point.x - start.x) * dx + (point.y - start.y) * dy) / lengthSquared).coerceIn(0f, 1f)
        val nearest = Vector3D(start.x + projection * dx, start.y + projection * dy, 0f)
        return point.distance2DTo(nearest)
    }

    private const val DEFAULT_BLOCKER_RADIUS_YDS = 0.5f
}
