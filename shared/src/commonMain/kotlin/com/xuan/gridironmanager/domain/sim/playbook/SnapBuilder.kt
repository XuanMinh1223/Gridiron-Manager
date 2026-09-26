package com.xuan.gridironmanager.domain.sim.playbook

import com.xuan.gridironmanager.domain.model.Matchup
import com.xuan.gridironmanager.domain.model.PlayType
import com.xuan.gridironmanager.domain.model.Player
import com.xuan.gridironmanager.domain.model.Position
import com.xuan.gridironmanager.domain.model.Route
import com.xuan.gridironmanager.domain.model.Vector3D
import com.xuan.gridironmanager.domain.model.Waypoint
import com.xuan.gridironmanager.domain.sim.FieldGeometry
import com.xuan.gridironmanager.domain.sim.match.GamePhase
import com.xuan.gridironmanager.domain.sim.match.GameState
import com.xuan.gridironmanager.domain.sim.match.Rules
import com.xuan.gridironmanager.domain.sim.movement.PlayerRole
import com.xuan.gridironmanager.domain.sim.movement.RunningPlayer
import com.xuan.gridironmanager.domain.sim.play.Snap
import com.xuan.gridironmanager.domain.sim.toRunningPlayer
import kotlin.math.abs

/** Lines both teams up for a play call and gives every player their assignment. Deterministic: no randomness. */
object SnapBuilder {
    private const val FIELD_WITH_END_ZONES_YDS = 120f
    private const val KICK_COVERAGE_SPRINT_YDS = 80f
    private const val SIDELINE_MARGIN_YDS = 1f

    /** The kick return unit lines up from the receiving team's goal line (the kicking team's 98). */
    private const val KICK_RETURN_ANCHOR_YARD_LINE = 98
    private val DEFENSIVE_LINE = setOf(Position.DT, Position.DL, Position.EDGE)

    /** Man defenders left over once every receiver is covered play these zones instead: deep middle, then robber, then hooks. */
    private val SPARE_DEFENDER_ZONES = listOf(ZoneSpot(0f, 18f), ZoneSpot(0f, 9f), ZoneSpot(-8f, 8f), ZoneSpot(8f, 8f))

    fun build(
        state: GameState,
        matchup: Matchup,
        offensivePlay: OffensivePlay,
        defensiveCall: DefensiveCall,
    ): Snap {
        val isAttackingUp = state.isHomePossession
        val losYardLine =
            if (state.phase == GamePhase.EXTRA_POINT && offensivePlay.type == PlayType.FIELD_GOAL) Rules.EXTRA_POINT_YARD_LINE else state.yardLine
        val losWorldY = FieldGeometry.toWorldY(losYardLine, isAttackingUp)
        val (offenseRoster, defenseRoster) =
            if (state.isHomePossession) matchup.homeRoster to matchup.awayRoster else matchup.awayRoster to matchup.homeRoster

        val offense = buildOffense(offenseRoster, offensivePlay, losWorldY, isAttackingUp)

        val unit = Playbook.defensiveUnitFor(offensivePlay.type, defensiveCall)
        val defenseAnchorWorldY =
            if (unit.formation.type == FormationType.KICK_RETURN) FieldGeometry.toWorldY(KICK_RETURN_ANCHOR_YARD_LINE, isAttackingUp) else losWorldY
        val defense = buildDefense(defenseRoster, unit, defenseAnchorWorldY, isAttackingUp, offense)

        val offenseBySlot = offense.associateBy { it.slot }
        return Snap(
            offense = offense,
            defense = defense,
            playType = offensivePlay.type,
            losYardLine = losYardLine,
            isAttackingUp = isAttackingUp,
            progression = offensivePlay.progression.mapNotNull { offenseBySlot[it]?.id },
        )
    }

    private fun buildOffense(
        roster: List<Player>,
        play: OffensivePlay,
        losWorldY: Float,
        isAttackingUp: Boolean,
    ): List<RunningPlayer> {
        val direction = if (isAttackingUp) 1f else -1f
        return staff(roster, play.formation).map { (node, player) ->
            val start = Vector3D(worldX(node.xOffset, direction), losWorldY - node.yOffset * direction, 0f)
            val role = offensiveRole(play, node)
            val route =
                when {
                    role == PlayerRole.KICKER -> {
                        null
                    }

                    play.type == PlayType.KICKOFF || play.type == PlayType.PUNT -> {
                        // Coverage team sprints downfield
                        Route("Coverage", listOf(Waypoint(start.x, start.y + KICK_COVERAGE_SPRINT_YDS * direction)))
                    }

                    else -> {
                        play.routes[node.slot]?.let { toWorldRoute(it, node, start, direction, extendToEndZone = role == PlayerRole.BALL_CARRIER) }
                    }
                }
            player.toRunningPlayer(route).copy(
                id = "${node.slot}_${player.id}",
                currentPos = start,
                isOffense = true,
                position = node.position,
                role = role,
                slot = node.slot,
            )
        }
    }

    private fun offensiveRole(
        play: OffensivePlay,
        node: FormationNode,
    ): PlayerRole =
        when (play.type) {
            PlayType.KICKOFF, PlayType.PUNT, PlayType.FIELD_GOAL -> {
                when {
                    node.position == Position.K || node.position == Position.P -> PlayerRole.KICKER
                    play.type == PlayType.FIELD_GOAL -> PlayerRole.BLOCKER
                    else -> PlayerRole.ROUTE_ONLY
                }
            }

            PlayType.RUN -> {
                when {
                    node.slot == play.ballCarrierSlot -> PlayerRole.BALL_CARRIER
                    node.slot in play.routes -> PlayerRole.RECEIVER // Decoy routes that still draw coverage
                    node.position == Position.QB -> PlayerRole.ROUTE_ONLY
                    else -> PlayerRole.BLOCKER
                }
            }

            PlayType.PASS -> {
                when {
                    node.position == Position.QB -> PlayerRole.PASSER
                    node.slot in play.routes -> PlayerRole.RECEIVER
                    else -> PlayerRole.BLOCKER
                }
            }
        }

    private fun buildDefense(
        roster: List<Player>,
        call: DefensiveCall,
        anchorWorldY: Float,
        isAttackingUp: Boolean,
        offense: List<RunningPlayer>,
    ): List<RunningPlayer> {
        val direction = if (isAttackingUp) 1f else -1f
        val formationType = call.formation.type

        val defenders =
            staff(roster, call.formation).map { (node, player) ->
                // The kick return unit is anchored at its goal line and lines up back towards the kicker
                val y = if (formationType == FormationType.KICK_RETURN) anchorWorldY - node.yOffset * direction else anchorWorldY + node.yOffset * direction
                val start = Vector3D(worldX(node.xOffset, direction), y, 0f)
                val role =
                    when {
                        node.slot in call.blitzers -> PlayerRole.BLITZER
                        formationType == FormationType.KICK_RETURN || formationType == FormationType.PUNT_RETURN -> PlayerRole.ROUTE_ONLY
                        node.position in DEFENSIVE_LINE -> PlayerRole.PASS_RUSHER
                        node.slot in call.zones -> PlayerRole.ZONE_COVERAGE
                        call.coverage == null -> PlayerRole.ROUTE_ONLY
                        else -> PlayerRole.MAN_COVERAGE
                    }
                val route =
                    if (formationType == FormationType.PUNT_RETURN) {
                        // Front line rushes the punter, everyone else drops back to block for the returner
                        val move = if (node.yOffset < 3f) -10f else 5f
                        Route("Return", listOf(Waypoint(start.x, start.y + move * direction)))
                    } else {
                        null
                    }
                player.toRunningPlayer(route).copy(
                    id = "${node.slot}_${player.id}",
                    currentPos = start,
                    isOffense = false,
                    position = node.position,
                    role = role,
                    slot = node.slot,
                    zoneLandmark = call.zones[node.slot]?.let { worldSpot(it, anchorWorldY, direction) },
                )
            }

        return assignManCoverage(defenders, offense, anchorWorldY, direction)
    }

    /**
     * Every eligible receiver (widest first) is picked up by the nearest free man defender. Man defenders left over
     * play a spare zone instead.
     */
    private fun assignManCoverage(
        defenders: List<RunningPlayer>,
        offense: List<RunningPlayer>,
        losWorldY: Float,
        direction: Float,
    ): List<RunningPlayer> {
        val receivers =
            offense
                .filter { it.role == PlayerRole.RECEIVER || it.role == PlayerRole.BALL_CARRIER }
                .sortedByDescending { abs(it.currentPos.x - FieldGeometry.CENTER_X) }
        val unassigned = defenders.filter { it.role == PlayerRole.MAN_COVERAGE }.toMutableList()
        val targets = mutableMapOf<String, String>()
        for (receiver in receivers) {
            val defender = unassigned.minByOrNull { it.currentPos.distance2DTo(receiver.currentPos) } ?: break
            targets[defender.id] = receiver.id
            unassigned.remove(defender)
        }
        val spareZones = unassigned.zip(SPARE_DEFENDER_ZONES).associate { (defender, spot) -> defender.id to worldSpot(spot, losWorldY, direction) }

        return defenders.map { defender ->
            when (defender.id) {
                in targets -> defender.copy(coverageTargetId = targets.getValue(defender.id))
                in spareZones -> defender.copy(role = PlayerRole.ZONE_COVERAGE, zoneLandmark = spareZones.getValue(defender.id))
                else -> defender
            }
        }
    }

    /** Pairs each formation node with a player at that position, falling back to anyone left if the position runs out. */
    private fun staff(
        roster: List<Player>,
        formation: Formation,
    ): List<Pair<FormationNode, Player>> {
        val available = roster.toMutableList()
        return formation.nodes.map { node ->
            val player =
                available.find { it.position == node.position.abbreviation }
                    ?: available.firstOrNull()
                    ?: throw IllegalStateException("Not enough players to line up ${formation.name}")
            available.remove(player)
            node to player
        }
    }

    private fun toWorldRoute(
        template: RouteTemplate,
        node: FormationNode,
        start: Vector3D,
        direction: Float,
        extendToEndZone: Boolean,
    ): Route {
        // "Outside" is towards the sideline the player lined up nearest to
        val outsideSign = (if (node.xOffset < 0f) -1f else 1f) * direction
        val waypoints =
            template.points.map { point ->
                Waypoint(clampToField(start.x + point.outside * outsideSign), start.y + point.downfield * direction)
            }
        val path = if (extendToEndZone) waypoints + waypoints.last().let { Waypoint(it.x, it.y + FIELD_WITH_END_ZONES_YDS * direction) } else waypoints
        return Route(template.name, path)
    }

    private fun worldX(
        xOffset: Float,
        direction: Float,
    ): Float = clampToField(FieldGeometry.CENTER_X + xOffset * direction)

    private fun worldSpot(
        spot: ZoneSpot,
        losWorldY: Float,
        direction: Float,
    ) = Vector3D(worldX(spot.x, direction), FieldGeometry.clampInsideEndLines(losWorldY + spot.depth * direction), 0f)

    private fun clampToField(x: Float) = x.coerceIn(SIDELINE_MARGIN_YDS, FieldGeometry.WIDTH_YDS - SIDELINE_MARGIN_YDS)
}
