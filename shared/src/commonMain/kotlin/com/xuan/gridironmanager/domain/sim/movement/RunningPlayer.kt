package com.xuan.gridironmanager.domain.sim.movement

import com.xuan.gridironmanager.domain.model.PlayerAttributes
import com.xuan.gridironmanager.domain.model.Position
import com.xuan.gridironmanager.domain.model.Route
import com.xuan.gridironmanager.domain.model.Vector3D
import com.xuan.gridironmanager.domain.model.VerticalReach

/** What a player is responsible for on the current play. */
enum class PlayerRole {
    PASSER,
    BALL_CARRIER,
    RECEIVER,
    BLOCKER,
    KICKER,
    PASS_RUSHER,
    BLITZER,
    MAN_COVERAGE,
    ZONE_COVERAGE,

    /** Simply runs its route, e.g. kick coverage and return units. */
    ROUTE_ONLY,
}

data class RunningPlayer(
    val id: String,
    var currentPos: Vector3D,
    val speedYdsPerSec: Float,
    val route: Route?,
    val verticalReach: VerticalReach = VerticalReach(),
    var currentWaypointIndex: Int = 0,
    val isOffense: Boolean = true,
    /** The position this player is lined up at for the current play. */
    val position: Position? = null,
    val attributes: PlayerAttributes = PlayerAttributes.AVERAGE,
    val role: PlayerRole = PlayerRole.ROUTE_ONLY,
    /** Formation slot this player lined up in, e.g. "X" or "MLB". */
    val slot: String? = null,
    /** For [PlayerRole.MAN_COVERAGE]: the id of the receiver being covered. */
    val coverageTargetId: String? = null,
    /** For [PlayerRole.ZONE_COVERAGE]: the spot on the field this defender drops to. */
    val zoneLandmark: Vector3D? = null,
)
