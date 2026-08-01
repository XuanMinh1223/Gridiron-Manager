package com.xuan.gridironmanager.domain.sim.movement

import com.xuan.gridironmanager.domain.model.Vector3D
import kotlin.math.sqrt

object MovementEngine {
    fun updatePositions(players: List<RunningPlayer>, tickDeltaSec: Float) {
        for (player in players) {
            updatePlayerPosition(player, tickDeltaSec)
        }
    }

    private fun updatePlayerPosition(player: RunningPlayer, tickDeltaSec: Float) {
        val route = player.route ?: return
        if (player.currentWaypointIndex >= route.waypoints.size) return

        val target = route.waypoints[player.currentWaypointIndex]
        val dx = target.x - player.currentPos.x
        val dy = target.y - player.currentPos.y
        val distanceToTarget = sqrt(dx * dx + dy * dy)

        if (distanceToTarget < 0.2f) {
            player.currentWaypointIndex++
            return
        }

        val moveDistance = player.speedYdsPerSec * tickDeltaSec
        
        if (moveDistance >= distanceToTarget) {
            player.currentPos = Vector3D(target.x, target.y, player.currentPos.z)
            player.currentWaypointIndex++
        } else {
            val ratio = moveDistance / distanceToTarget
            player.currentPos = Vector3D(
                x = player.currentPos.x + dx * ratio,
                y = player.currentPos.y + dy * ratio,
                z = player.currentPos.z
            )
            
            // Re-check distance to target for waypoint advancement
            val newDx = target.x - player.currentPos.x
            val newDy = target.y - player.currentPos.y
            if (sqrt(newDx * newDx + newDy * newDy) < 0.2f) {
                player.currentWaypointIndex++
            }
        }
    }
}
