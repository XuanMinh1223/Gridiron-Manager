package com.xuan.gridironmanager.domain.sim.play

import com.xuan.gridironmanager.domain.model.Position

data class FormationNode(
    val position: Position,
    val xOffset: Float,
    val yOffset: Float
)

data class Formation(
    val name: String,
    val nodes: List<FormationNode>
)
