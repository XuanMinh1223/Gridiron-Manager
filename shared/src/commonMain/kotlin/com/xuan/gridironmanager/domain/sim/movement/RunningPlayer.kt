package com.xuan.gridironmanager.domain.sim.movement

import com.xuan.gridironmanager.domain.model.Route
import com.xuan.gridironmanager.domain.model.Vector3D

data class RunningPlayer(
    val id: String,
    var currentPos: Vector3D,
    val speedYdsPerSec: Float,
    val route: Route?,
    var currentWaypointIndex: Int = 0
)
