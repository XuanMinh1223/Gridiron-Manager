package com.xuan.gridironmanager.domain.sim.playbook

enum class Coverage(
    val label: String,
) {
    MAN("Man"),
    COVER_2("Cover 2"),
    COVER_3("Cover 3"),
}

/** A zone landmark: [x] yards across from the middle of the field, [depth] yards off the line of scrimmage. */
data class ZoneSpot(
    val x: Float,
    val depth: Float,
)

/**
 * A defensive play call. Defensive linemen rush the passer, [blitzers] join them, defenders with a [zones] entry drop
 * into that zone, and everyone else plays man coverage. [coverage] is null for special teams units.
 */
data class DefensiveCall(
    val id: String,
    val name: String,
    val formation: Formation,
    val coverage: Coverage?,
    val zones: Map<String, ZoneSpot> = emptyMap(),
    val blitzers: Set<String> = emptySet(),
) {
    init {
        val slots = formation.nodes.map { it.slot }.toSet()
        require(slots.containsAll(zones.keys)) { "$name has zones for slots not in ${formation.name}" }
        require(slots.containsAll(blitzers)) { "$name blitzes slots not in ${formation.name}" }
        require(zones.keys.none { it in blitzers }) { "$name has a defender both blitzing and in zone coverage" }
    }
}
