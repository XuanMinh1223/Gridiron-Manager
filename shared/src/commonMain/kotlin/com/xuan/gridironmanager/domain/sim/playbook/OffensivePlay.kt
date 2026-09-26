package com.xuan.gridironmanager.domain.sim.playbook

import com.xuan.gridironmanager.domain.model.PlayType

enum class PlayCategory(
    val label: String,
) {
    RUN("Run"),
    SHORT_PASS("Short Pass"),
    MEDIUM_PASS("Medium Pass"),
    LONG_PASS("Long Pass"),
    FIELD_GOAL("Field Goal"),
    EXTRA_POINT("Extra Point"),
    PUNT("Punt"),
    KICKOFF("Kickoff"),
}

data class OffensivePlay(
    val id: String,
    val name: String,
    val category: PlayCategory,
    val type: PlayType,
    val formation: Formation,
    /** Routes keyed by formation slot. For run plays this includes the ball carrier's path; everyone else is a decoy. */
    val routes: Map<String, RouteTemplate> = emptyMap(),
    val ballCarrierSlot: String? = null,
    /** The quarterback's read order on pass plays, by slot. */
    val progression: List<String> = emptyList(),
) {
    init {
        val slots = formation.nodes.map { it.slot }.toSet()
        require(slots.containsAll(routes.keys)) { "$name has routes for slots not in ${formation.name}" }
        require(slots.containsAll(progression)) { "$name reads slots not in ${formation.name}" }
        require(progression.all { it in routes }) { "$name reads a slot that has no route" }
        require(ballCarrierSlot == null || ballCarrierSlot in routes) { "$name has no path for its ball carrier" }
        require((type == PlayType.RUN) == (ballCarrierSlot != null)) { "Run plays, and only run plays, have a ball carrier" }
        require((type == PlayType.PASS) == progression.isNotEmpty()) { "Pass plays, and only pass plays, have a progression" }
    }
}
