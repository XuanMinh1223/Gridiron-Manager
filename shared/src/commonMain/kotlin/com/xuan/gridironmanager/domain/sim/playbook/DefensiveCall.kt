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
 * The look a disguised call shows before the snap. The call's own [DefensiveCall.coverage] and [DefensiveCall.zones]
 * are the true coverage the defense rotates into after [rotationSec]. A disguise carries no formation or blitzers, so
 * only coverage assignments can ever rotate.
 */
data class CoverageDisguise(
    val shellCoverage: Coverage?,
    val shellZones: Map<String, ZoneSpot> = emptyMap(),
    val rotationSec: Float = DEFAULT_ROTATION_SEC,
) {
    companion object {
        const val DEFAULT_ROTATION_SEC = 0.4f
        val ROTATION_RANGE_SEC = 0.2f..0.8f
    }
}

/**
 * A defensive play call. Defensive linemen rush the passer, [blitzers] join them, defenders with a [zones] entry drop
 * into that zone, and everyone else plays man coverage. [coverage] is null for special teams units. A [disguise]
 * shows a different coverage until it rotates into this one shortly after the snap.
 */
data class DefensiveCall(
    val id: String,
    val name: String,
    val formation: Formation,
    val coverage: Coverage?,
    val zones: Map<String, ZoneSpot> = emptyMap(),
    val blitzers: Set<String> = emptySet(),
    val disguise: CoverageDisguise? = null,
) {
    init {
        val slots = formation.nodes.map { it.slot }.toSet()
        require(slots.containsAll(zones.keys)) { "$name has zones for slots not in ${formation.name}" }
        require(slots.containsAll(blitzers)) { "$name blitzes slots not in ${formation.name}" }
        require(zones.keys.none { it in blitzers }) { "$name has a defender both blitzing and in zone coverage" }
        disguise?.let {
            require(it.rotationSec in CoverageDisguise.ROTATION_RANGE_SEC) { "$name rotates at ${it.rotationSec}s, outside ${CoverageDisguise.ROTATION_RANGE_SEC}" }
            require(slots.containsAll(it.shellZones.keys)) { "$name has shell zones for slots not in ${formation.name}" }
            require(it.shellZones.keys.none { slot -> slot in blitzers }) { "$name has a blitzer in a shell zone" }
            require(it.shellCoverage != coverage || it.shellZones != zones) { "$name has a disguise identical to its true coverage" }
        }
    }

    /** The call as the offense sees it before the snap. */
    fun shell(): DefensiveCall =
        disguise?.let {
            copy(coverage = it.shellCoverage, zones = it.shellZones, disguise = null)
        } ?: this
}
