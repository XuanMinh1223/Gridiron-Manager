package com.xuan.gridironmanager.domain.sim.playbook

import com.xuan.gridironmanager.domain.model.Position

/**
 * One player's alignment. [xOffset] is yards across the field from the middle (positive = the offense's right),
 * [yOffset] is yards from the line of scrimmage (behind it for the offense, in front of it for the defense).
 */
data class FormationNode(
    val slot: String,
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
    FIELD_GOAL,
    FIELD_GOAL_BLOCK,
}

data class Formation(
    val name: String,
    val type: FormationType,
    val nodes: List<FormationNode>,
) {
    init {
        require(nodes.size == 11) { "$name must line up 11 players, has ${nodes.size}" }
        require(nodes.map { it.slot }.toSet().size == nodes.size) { "$name has duplicate slots" }
    }
}
