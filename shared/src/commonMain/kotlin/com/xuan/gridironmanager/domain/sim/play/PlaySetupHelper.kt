package com.xuan.gridironmanager.domain.sim.play

import com.xuan.gridironmanager.domain.model.Player
import com.xuan.gridironmanager.domain.model.Position
import com.xuan.gridironmanager.domain.model.Route
import com.xuan.gridironmanager.domain.model.Vector3D
import com.xuan.gridironmanager.domain.model.Waypoint
import com.xuan.gridironmanager.domain.sim.movement.RunningPlayer
import com.xuan.gridironmanager.domain.sim.toRunningPlayer

object PlaySetupHelper {
    fun getShotgunFormation(): Formation {
        return Formation(
            name = "Shotgun",
            nodes = listOf(
                FormationNode(Position.QB, 0f, 5f),
                FormationNode(Position.C, 0f, 0f),
                FormationNode(Position.OG, -1f, 0f),
                FormationNode(Position.OG, 1f, 0f),
                FormationNode(Position.OT, -2f, 0f),
                FormationNode(Position.OT, 2f, 0f),
                FormationNode(Position.WR, -15f, 1f),
                FormationNode(Position.WR, 15f, 1f),
                FormationNode(Position.WR, -10f, 2f),
                FormationNode(Position.TE, 3f, 1f),
                FormationNode(Position.RB, 2f, 5f)
            )
        )
    }

    fun getBaseDefense(): Formation {
        return Formation(
            name = "Base 4-3",
            nodes = listOf(
                FormationNode(Position.DT, -1f, 1f),
                FormationNode(Position.DT, 1f, 1f),
                FormationNode(Position.EDGE, -3f, 1f),
                FormationNode(Position.EDGE, 3f, 1f),
                FormationNode(Position.LB, 0f, 4f),
                FormationNode(Position.LB, -4f, 4f),
                FormationNode(Position.LB, 4f, 4f),
                FormationNode(Position.CB, -15f, 2f),
                FormationNode(Position.CB, 15f, 2f),
                FormationNode(Position.S, -5f, 10f),
                FormationNode(Position.S, 5f, 10f)
            )
        )
    }

    fun createRunningPlayers(
        roster: List<Player>,
        formation: Formation,
        losWorldY: Float,
        isOffense: Boolean,
        isAttackingUp: Boolean
    ): List<RunningPlayer> {
        val availablePlayers = roster.toMutableList()
        val fieldCenterWorldX = 26.65f
        val directionMultiplier = if (isAttackingUp) 1f else -1f
        
        return formation.nodes.map { node ->
            val player = availablePlayers.find { it.position == node.position.abbreviation }
                ?: availablePlayers.firstOrNull()
                ?: throw IllegalStateException("No players available in roster")
            
            availablePlayers.remove(player)
            
            // For Home (Attacking Up): Offense is below LOS (y < losWorldY), Defense is above (y > losWorldY)
            // For Away (Attacking Down): Offense is above LOS (y > losWorldY), Defense is below (y < losWorldY)
            val yPos = if (isOffense) {
                losWorldY - (node.yOffset * directionMultiplier)
            } else {
                losWorldY + (node.yOffset * directionMultiplier)
            }
            
            val worldX = fieldCenterWorldX + (node.xOffset * directionMultiplier)
            
            // Simple logic for routes
            val route = when {
                isOffense && node.position == Position.WR -> {
                    Route("Go", listOf(Waypoint(worldX, yPos + (40f * directionMultiplier))))
                }
                isOffense && node.position == Position.TE -> {
                    Route("Seam", listOf(Waypoint(worldX, yPos + (30f * directionMultiplier))))
                }
                isOffense && node.position == Position.RB -> {
                    Route("Dive", listOf(Waypoint(worldX, yPos + (30f * directionMultiplier))))
                }
                !isOffense -> {
                    // Rush or Cover
                    val defensiveMove = if (node.yOffset < 3f) -10f else 5f
                    Route("Basic", listOf(Waypoint(worldX, yPos + (defensiveMove * directionMultiplier))))
                }
                else -> null
            }
            
            val runningPlayer = player.toRunningPlayer(route)
            
            runningPlayer.copy(
                id = "${node.position.abbreviation}_${player.id}",
                currentPos = Vector3D(worldX, yPos, 0f),
                isOffense = isOffense,
                speedYdsPerSec = if (isOffense) runningPlayer.speedYdsPerSec else runningPlayer.speedYdsPerSec * 0.7f // Defenders start slower/reacting
            )
        }
    }
}
