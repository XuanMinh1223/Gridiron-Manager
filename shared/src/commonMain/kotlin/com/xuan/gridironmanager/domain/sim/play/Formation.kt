package com.xuan.gridironmanager.domain.sim.play

import com.xuan.gridironmanager.domain.model.Position

data class FormationNode(
    val position: Position,
    val xOffset: Float,
    val yOffset: Float,
)

enum class FormationType {
    OFFENSE,
    DEFENSE,
    KICKOFF,
    KICK_RETURN,
    PUNT,
    PUNT_RETURN,
}

data class Formation(
    val name: String,
    val type: FormationType,
    val nodes: List<FormationNode>,
)
