package com.xuan.gridironmanager.domain.sim.play

import com.xuan.gridironmanager.domain.model.Vector3D
import com.xuan.gridironmanager.domain.sim.movement.RunningPlayer

data class PlayState(
    val offense: List<RunningPlayer>,
    val defense: List<RunningPlayer>
)

object PlaySetup {
    fun initializeSnap(
        losYards: Float,
        offFormation: Formation,
        defFormation: Formation
    ): PlayState {
        val offense = offFormation.nodes.mapIndexed { index, node ->
            RunningPlayer(
                id = "off_${node.position}_$index",
                currentPos = Vector3D(
                    x = node.xOffset,
                    y = losYards - node.yOffset, // Offense is behind LOS
                    z = 0f
                ),
                speedYdsPerSec = 8f,
                route = null
            )
        }

        val defense = defFormation.nodes.mapIndexed { index, node ->
            RunningPlayer(
                id = "def_${node.position}_$index",
                currentPos = Vector3D(
                    x = node.xOffset,
                    y = losYards + node.yOffset, // Defense is in front of LOS
                    z = 0f
                ),
                speedYdsPerSec = 8f,
                route = null
            )
        }

        return PlayState(offense, defense)
    }
}
