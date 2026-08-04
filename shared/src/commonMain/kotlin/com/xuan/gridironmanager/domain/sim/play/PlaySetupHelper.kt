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

    fun getKickoffFormation(): Formation {
        return Formation(
            name = "Kickoff",
            nodes = (0..10).map { i ->
                val x = if (i == 5) 0f else (i - 5) * 5f
                val pos = if (i == 5) Position.K else Position.S
                FormationNode(pos, x, 0f)
            }
        )
    }

    fun getKickReturnFormation(): Formation {
        return Formation(
            name = "Kick Return",
            nodes = (0..10).map { i ->
                val x = if (i == 5) 0f else (i - 5) * 8f
                val y = if (i == 5) 2f else 30f // Returner deep, blockers ahead
                val pos = if (i == 5) Position.RB else Position.LB
                FormationNode(pos, x, y)
            }
        )
    }

    fun getPuntFormation(): Formation {
        return Formation(
            name = "Punt",
            nodes = listOf(
                FormationNode(Position.P, 0f, 15f),
                FormationNode(Position.C, 0f, 0f),
                FormationNode(Position.OG, -1f, 0f),
                FormationNode(Position.OG, 1f, 0f),
                FormationNode(Position.OT, -2f, 0f),
                FormationNode(Position.OT, 2f, 0f),
                FormationNode(Position.WR, -25f, 0f), // Gunners
                FormationNode(Position.WR, 25f, 0f),
                FormationNode(Position.LB, -3f, 1f),
                FormationNode(Position.LB, 0f, 1f),
                FormationNode(Position.LB, 3f, 1f)
            )
        )
    }

    fun getPuntReturnFormation(): Formation {
        return Formation(
            name = "Punt Return",
            nodes = listOf(
                FormationNode(Position.RB, 0f, 45f), // Deep returner
                FormationNode(Position.CB, -25f, 1f), // Gunners hold-up
                FormationNode(Position.CB, 25f, 1f),
                FormationNode(Position.DT, -1f, 1f),
                FormationNode(Position.DT, 1f, 1f),
                FormationNode(Position.EDGE, -3f, 1f),
                FormationNode(Position.EDGE, 3f, 1f),
                FormationNode(Position.LB, 0f, 4f),
                FormationNode(Position.LB, -5f, 4f),
                FormationNode(Position.LB, 5f, 4f),
                FormationNode(Position.S, 0f, 15f)
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
            // UNLESS it's a Kick Return, where they are deep
            val yPos = if (isOffense) {
                losWorldY - (node.yOffset * directionMultiplier)
            } else {
                if (formation.name == "Kick Return" || formation.name == "Punt Return") {
                    losWorldY - (node.yOffset * directionMultiplier)
                } else {
                    losWorldY + (node.yOffset * directionMultiplier)
                }
            }
            
            val worldX = fieldCenterWorldX + (node.xOffset * directionMultiplier)
            
            // Simple logic for routes
            val route = when {
                isOffense && node.position == Position.K -> {
                    null // Kicker stays
                }
                isOffense && node.position == Position.P -> {
                    null // Punter stays
                }
                isOffense && (formation.name == "Kickoff" || formation.name == "Punt") && node.position != Position.K && node.position != Position.P -> {
                    // Coverage team sprints downfield
                    Route("Coverage", listOf(Waypoint(worldX, yPos + (80f * directionMultiplier))))
                }
                isOffense && node.position == Position.WR -> {
                    Route("Go", listOf(Waypoint(worldX, yPos + (40f * directionMultiplier))))
                }
                isOffense && node.position == Position.TE -> {
                    Route("Seam", listOf(Waypoint(worldX, yPos + (30f * directionMultiplier))))
                }
                isOffense && node.position == Position.RB -> {
                    Route("Dive", listOf(Waypoint(worldX, yPos + (30f * directionMultiplier))))
                }
                !isOffense && formation.name == "Kick Return" -> {
                    // Kickoff coverage is actually the "defense" in a Kick Return setup
                    Route("Coverage", listOf(Waypoint(worldX, yPos + (80f * directionMultiplier))))
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
