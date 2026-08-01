package com.xuan.gridironmanager.domain.sim.movement

import com.xuan.gridironmanager.domain.model.Route
import com.xuan.gridironmanager.domain.model.Vector3D
import com.xuan.gridironmanager.domain.model.VerticalReach

data class RunningPlayer(
    val id: String,
    var currentPos: Vector3D,
    val speedYdsPerSec: Float,
    val route: Route?,
    val verticalReach: VerticalReach = VerticalReach(),
    var currentWaypointIndex: Int = 0
)
