package com.xuan.gridironmanager.domain.sim

import com.xuan.gridironmanager.domain.model.Player
import com.xuan.gridironmanager.domain.model.Route
import com.xuan.gridironmanager.domain.model.Vector3D
import com.xuan.gridironmanager.domain.model.VerticalReach
import com.xuan.gridironmanager.domain.sim.movement.RunningPlayer

fun Player.toRunningPlayer(route: Route? = null): RunningPlayer {
    val speedYdsPerSec = AttributeTranslator.calculateSpeedYardsPerSec(attributes.speed)
    val standingHeightYards = AttributeTranslator.calculateStandingHeightYards(physicalProfile.heightInches)
    val maxCatchHeightYards = AttributeTranslator.calculateMaxCatchHeightYards(
        physicalProfile.heightInches,
        attributes.verticalJump
    )

    return RunningPlayer(
        id = id,
        currentPos = Vector3D(0f, 0f, 0f),
        speedYdsPerSec = speedYdsPerSec,
        route = route,
        verticalReach = VerticalReach(
            heightYards = standingHeightYards,
            verticalLeapYards = maxCatchHeightYards - standingHeightYards
        )
    )
}
