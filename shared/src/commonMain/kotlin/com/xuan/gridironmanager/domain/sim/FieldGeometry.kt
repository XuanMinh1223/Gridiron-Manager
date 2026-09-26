package com.xuan.gridironmanager.domain.sim

import com.xuan.gridironmanager.domain.sim.match.Rules

/**
 * World coordinates: x runs across the field (0 to [WIDTH_YDS]), y runs from the home goal line (0) to the away goal
 * line (100). End zones extend 10 yards beyond each goal line. The home team always attacks towards +y.
 */
object FieldGeometry {
    const val WIDTH_YDS = 53.3f
    const val CENTER_X = WIDTH_YDS / 2

    /** Converts a yard line from the possessing team's point of view (0 = own goal line) into world y. */
    fun toWorldY(
        yardLine: Int,
        isAttackingUp: Boolean,
    ): Float = if (isAttackingUp) yardLine.toFloat() else (Rules.FIELD_LENGTH_YDS - yardLine).toFloat()

    /** Keeps a world y inside the field of play, a yard short of either end line. */
    fun clampInsideEndLines(y: Float): Float {
        val limit = (Rules.END_ZONE_DEPTH_YDS - 1).toFloat()
        return y.coerceIn(-limit, Rules.FIELD_LENGTH_YDS + limit)
    }

    /** World y of the goal posts the offense is kicking at (they stand on the end line). */
    fun goalPostsWorldY(isAttackingUp: Boolean): Float =
        if (isAttackingUp) (Rules.FIELD_LENGTH_YDS + Rules.END_ZONE_DEPTH_YDS).toFloat() else -Rules.END_ZONE_DEPTH_YDS.toFloat()
}
