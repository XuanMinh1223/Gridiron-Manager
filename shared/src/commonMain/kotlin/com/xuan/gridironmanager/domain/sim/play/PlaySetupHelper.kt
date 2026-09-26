package com.xuan.gridironmanager.domain.sim.play

import com.xuan.gridironmanager.domain.model.Player
import com.xuan.gridironmanager.domain.model.Position
import com.xuan.gridironmanager.domain.model.Route
import com.xuan.gridironmanager.domain.model.Vector3D
import com.xuan.gridironmanager.domain.model.Waypoint
import com.xuan.gridironmanager.domain.sim.movement.RunningPlayer
import com.xuan.gridironmanager.domain.sim.toRunningPlayer
import kotlin.math.abs
import kotlin.math.sign

object PlaySetupHelper {
    private const val FIELD_WITH_END_ZONES_YDS = 120f
    private const val DEFENDER_REACTION_SPEED_FACTOR = 0.9f
    private const val SLOT_MAX_X_OFFSET = 12f // Receivers split wider than this are outside receivers

    fun getShotgunFormation(): Formation =
        Formation(
            name = "Shotgun",
            type = FormationType.OFFENSE,
            nodes =
                listOf(
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
                    FormationNode(Position.RB, 2f, 5f),
                ),
        )

    fun getBaseDefense(): Formation =
        Formation(
            name = "Base 4-3",
            type = FormationType.DEFENSE,
            nodes =
                listOf(
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
                    FormationNode(Position.S, 5f, 10f),
                ),
        )

    fun getKickoffFormation(): Formation =
        Formation(
            name = "Kickoff",
            type = FormationType.KICKOFF,
            nodes =
                (0..10).map { i ->
                    val x = if (i == 5) 0f else (i - 5) * 5f
                    val pos = if (i == 5) Position.K else Position.S
                    FormationNode(pos, x, 0f)
                },
        )

    fun getKickReturnFormation(): Formation =
        Formation(
            name = "Kick Return",
            type = FormationType.KICK_RETURN,
            nodes =
                (0..10).map { i ->
                    val x = if (i == 5) 0f else (i - 5) * 8f
                    val y = if (i == 5) 2f else 30f // Returner deep, blockers ahead
                    val pos = if (i == 5) Position.RB else Position.LB
                    FormationNode(pos, x, y)
                },
        )

    fun getPuntFormation(): Formation =
        Formation(
            name = "Punt",
            type = FormationType.PUNT,
            nodes =
                listOf(
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
                    FormationNode(Position.LB, 3f, 1f),
                ),
        )

    fun getPuntReturnFormation(): Formation =
        Formation(
            name = "Punt Return",
            type = FormationType.PUNT_RETURN,
            nodes =
                listOf(
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
                    FormationNode(Position.S, 0f, 15f),
                ),
        )

    fun createRunningPlayers(
        roster: List<Player>,
        formation: Formation,
        losWorldY: Float,
        isOffense: Boolean,
        isAttackingUp: Boolean,
    ): List<RunningPlayer> {
        val availablePlayers = roster.toMutableList()
        val fieldCenterWorldX = 26.65f
        val directionMultiplier = if (isAttackingUp) 1f else -1f

        return formation.nodes.map { node ->
            val player =
                availablePlayers.find { it.position == node.position.abbreviation }
                    ?: availablePlayers.firstOrNull()
                    ?: throw IllegalStateException("No players available in roster")

            availablePlayers.remove(player)

            // For Home (Attacking Up): Offense is below LOS (y < losWorldY), Defense is above (y > losWorldY).
            // A kick return is anchored at the receiving goal line instead, so its players line up back towards the kicker.
            val yPos =
                if (isOffense || formation.type == FormationType.KICK_RETURN) {
                    losWorldY - (node.yOffset * directionMultiplier)
                } else {
                    losWorldY + (node.yOffset * directionMultiplier)
                }

            val worldX = fieldCenterWorldX + (node.xOffset * directionMultiplier)

            // Simple logic for routes
            val route =
                when {
                    isOffense && (node.position == Position.K || node.position == Position.P) -> {
                        null // Kicker / punter stays
                    }

                    isOffense && (formation.type == FormationType.KICKOFF || formation.type == FormationType.PUNT) -> {
                        // Coverage team sprints downfield
                        Route("Coverage", listOf(Waypoint(worldX, yPos + (80f * directionMultiplier))))
                    }

                    isOffense && node.position == Position.WR && abs(node.xOffset) >= SLOT_MAX_X_OFFSET -> {
                        Route("Go", listOf(Waypoint(worldX, yPos + (40f * directionMultiplier))))
                    }

                    isOffense && node.position == Position.WR -> {
                        // Slot receiver: quick out, breaking towards the near sideline
                        val stemY = yPos + (6f * directionMultiplier)
                        val outX = worldX + (8f * sign(node.xOffset) * directionMultiplier)
                        Route("Out", listOf(Waypoint(worldX, stemY), Waypoint(outX, stemY)))
                    }

                    isOffense && node.position == Position.TE -> {
                        Route("Stick", listOf(Waypoint(worldX, yPos + (6f * directionMultiplier))))
                    }

                    isOffense && node.position == Position.RB -> {
                        // Keep running until tackled or in the end zone
                        Route("Dive", listOf(Waypoint(worldX, yPos + (FIELD_WITH_END_ZONES_YDS * directionMultiplier))))
                    }

                    !isOffense && formation.type == FormationType.KICK_RETURN -> {
                        null // Return team holds its landmarks until the ball comes down
                    }

                    !isOffense -> {
                        // Rush or Cover
                        val defensiveMove = if (node.yOffset < 3f) -10f else 5f
                        Route("Basic", listOf(Waypoint(worldX, yPos + (defensiveMove * directionMultiplier))))
                    }

                    else -> {
                        null
                    }
                }

            val runningPlayer = player.toRunningPlayer(route)

            runningPlayer.copy(
                id = "${node.position.abbreviation}_${player.id}",
                currentPos = Vector3D(worldX, yPos, 0f),
                isOffense = isOffense,
                position = node.position,
                // Defenders start slower/reacting
                speedYdsPerSec = if (isOffense) runningPlayer.speedYdsPerSec else runningPlayer.speedYdsPerSec * DEFENDER_REACTION_SPEED_FACTOR,
            )
        }
    }
}
