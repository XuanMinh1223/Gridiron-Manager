package com.xuan.gridironmanager.ui.match.overlay

import com.xuan.gridironmanager.domain.model.Vector3D
import com.xuan.gridironmanager.domain.sim.movement.PlayerRole
import com.xuan.gridironmanager.domain.sim.movement.RunningPlayer
import kotlin.math.abs

data class Segment(
    val from: Vector3D,
    val to: Vector3D,
)

data class ZoneMarker(
    val center: Vector3D,
    val defenderPos: Vector3D,
    val isDeep: Boolean,
)

/** Madden-style assignment drawing for the field, in world coordinates. */
data class TacticalOverlay(
    /** Each route as a polyline from the player's current position through their remaining waypoints. */
    val routes: List<List<Vector3D>> = emptyList(),
    val manLinks: List<Segment> = emptyList(),
    val zones: List<ZoneMarker> = emptyList(),
    val blitzArrows: List<Segment> = emptyList(),
    /** Blockers and the defenders they are engaged with. */
    val blocks: List<Segment> = emptyList(),
) {
    companion object {
        val NONE = TacticalOverlay()

        private const val DEEP_ZONE_MIN_DEPTH_YDS = 12f

        /** How much of a ball carrier's path to draw beyond its last turn (the path itself runs to the end zone). */
        private const val CARRIER_PATH_PREVIEW_YDS = 6f

        fun build(
            players: List<RunningPlayer>,
            losWorldY: Float,
            showOffense: Boolean,
            showDefense: Boolean,
        ): TacticalOverlay {
            val byId = players.associateBy { it.id }
            val offense = if (showOffense) players.filter { it.isOffense } else emptyList()
            val defense = if (showDefense) players.filterNot { it.isOffense } else emptyList()
            val passer = players.find { it.role == PlayerRole.PASSER }

            return TacticalOverlay(
                routes = offense.mapNotNull { routePath(it) },
                manLinks =
                    defense
                        .filter { it.role == PlayerRole.MAN_COVERAGE }
                        .mapNotNull { defender -> defender.coverageTargetId?.let(byId::get)?.let { Segment(defender.currentPos, it.currentPos) } },
                zones =
                    defense.mapNotNull { defender ->
                        defender.zoneLandmark?.takeIf { defender.role == PlayerRole.ZONE_COVERAGE }?.let { landmark ->
                            ZoneMarker(landmark, defender.currentPos, isDeep = abs(landmark.y - losWorldY) >= DEEP_ZONE_MIN_DEPTH_YDS)
                        }
                    },
                blitzArrows =
                    if (passer == null) {
                        emptyList()
                    } else {
                        defense.filter { it.role == PlayerRole.BLITZER }.map { Segment(it.currentPos, passer.currentPos) }
                    },
                blocks = offense.mapNotNull { blocker -> blocker.blockingId?.let(byId::get)?.let { Segment(blocker.currentPos, it.currentPos) } },
            )
        }

        private fun routePath(player: RunningPlayer): List<Vector3D>? {
            if (player.role != PlayerRole.RECEIVER && player.role != PlayerRole.BALL_CARRIER) return null
            val waypoints = player.route?.waypoints ?: return null
            val remaining = waypoints.drop(player.currentWaypointIndex).map { Vector3D(it.x, it.y, 0f) }
            if (remaining.isEmpty()) return null
            val path = listOf(player.currentPos) + remaining
            if (player.role != PlayerRole.BALL_CARRIER) return path

            // The final leg runs all the way to the end zone: only hint at its direction
            val turn = path[path.size - 2]
            val direction = if (path.last().y > turn.y) 1f else -1f
            return path.dropLast(1) + turn.copy(y = turn.y + CARRIER_PATH_PREVIEW_YDS * direction)
        }
    }
}
