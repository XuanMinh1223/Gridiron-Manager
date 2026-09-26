package com.xuan.gridironmanager.domain.sim.movement

import com.xuan.gridironmanager.domain.model.Vector3D
import kotlin.math.min
import kotlin.math.sqrt

object MovementEngine {
    private const val WAYPOINT_ARRIVAL_RADIUS = 0.2f
    private const val PREDICTION_STEP_SEC = 0.05f
    private const val MAX_LEAD_SEC = 1.5f

    fun updatePositions(
        players: List<RunningPlayer>,
        tickDeltaSec: Float,
    ) {
        for (player in players) {
            updatePlayerPosition(player, tickDeltaSec)
        }
    }

    /** Moves [player] straight toward [target] at full speed, ignoring their route. */
    fun pursue(
        player: RunningPlayer,
        target: Vector3D,
        tickDeltaSec: Float,
    ) {
        val dx = target.x - player.currentPos.x
        val dy = target.y - player.currentPos.y
        val distance = sqrt(dx * dx + dy * dy)
        if (distance == 0f) return

        val moveDistance = min(player.speedYdsPerSec * tickDeltaSec, distance)
        player.currentPos =
            Vector3D(
                x = player.currentPos.x + dx / distance * moveDistance,
                y = player.currentPos.y + dy / distance * moveDistance,
                z = player.currentPos.z,
            )
    }

    /**
     * Moves [player] along a pursuit angle: towards where a target at [targetPos], moving at [targetVelocity]
     * (yards per second), will be by the time [player] can get there.
     */
    fun intercept(
        player: RunningPlayer,
        targetPos: Vector3D,
        targetVelocity: Vector3D,
        tickDeltaSec: Float,
    ) {
        val leadSec = if (player.speedYdsPerSec > 0f) min(player.currentPos.distance2DTo(targetPos) / player.speedYdsPerSec, MAX_LEAD_SEC) else 0f
        val aim = targetPos.copy(x = targetPos.x + targetVelocity.x * leadSec, y = targetPos.y + targetVelocity.y * leadSec)
        pursue(player, aim, tickDeltaSec)
    }

    /** Where [player] will be after [secondsAhead] if they keep running their route. Does not move [player]. */
    fun predictPosition(
        player: RunningPlayer,
        secondsAhead: Float,
    ): Vector3D {
        val ghost = player.copy()
        var elapsed = 0f
        while (elapsed < secondsAhead) {
            val step = min(PREDICTION_STEP_SEC, secondsAhead - elapsed)
            updatePlayerPosition(ghost, step)
            elapsed += step
        }
        return ghost.currentPos
    }

    private fun updatePlayerPosition(
        player: RunningPlayer,
        tickDeltaSec: Float,
    ) {
        val route = player.route ?: return
        if (player.currentWaypointIndex >= route.waypoints.size) return

        val target = route.waypoints[player.currentWaypointIndex]
        val dx = target.x - player.currentPos.x
        val dy = target.y - player.currentPos.y
        val distanceToTarget = sqrt(dx * dx + dy * dy)

        if (distanceToTarget < WAYPOINT_ARRIVAL_RADIUS) {
            player.currentWaypointIndex++
            return
        }

        val moveDistance = player.speedYdsPerSec * tickDeltaSec

        if (moveDistance >= distanceToTarget) {
            player.currentPos = Vector3D(target.x, target.y, player.currentPos.z)
            player.currentWaypointIndex++
        } else {
            val ratio = moveDistance / distanceToTarget
            player.currentPos =
                Vector3D(
                    x = player.currentPos.x + dx * ratio,
                    y = player.currentPos.y + dy * ratio,
                    z = player.currentPos.z,
                )

            // Re-check distance to target for waypoint advancement
            val newDx = target.x - player.currentPos.x
            val newDy = target.y - player.currentPos.y
            if (sqrt(newDx * newDx + newDy * newDy) < WAYPOINT_ARRIVAL_RADIUS) {
                player.currentWaypointIndex++
            }
        }
    }
}
