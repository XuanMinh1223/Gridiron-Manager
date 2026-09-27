package com.xuan.gridironmanager.domain.sim.match

object Rules {
    const val FIELD_LENGTH_YDS = 100
    const val END_ZONE_DEPTH_YDS = 10
    const val QUARTER_LENGTH_SEC = 900
    const val QUARTERS = 4
    const val FIRST_DOWN_DISTANCE = 10

    const val TOUCHDOWN_POINTS = 6
    const val EXTRA_POINT_POINTS = 1
    const val TWO_POINT_CONVERSION_POINTS = 2
    const val FIELD_GOAL_POINTS = 3
    const val SAFETY_POINTS = 2

    const val KICKOFF_YARD_LINE = 35
    const val SAFETY_KICK_YARD_LINE = 20
    const val KICKOFF_TOUCHBACK_YARD_LINE = 35
    const val LANDING_ZONE_TOUCHBACK_YARD_LINE = 20
    const val KICKOFF_LANDING_ZONE_FRONT_YARD_LINE = 20
    const val KICKOFF_OUT_OF_BOUNDS_YARD_LINE = 40
    const val SAFETY_KICK_OUT_OF_BOUNDS_YARD_LINE = 50
    const val PUNT_TOUCHBACK_YARD_LINE = 20
    const val INTERCEPTION_TOUCHBACK_YARD_LINE = 20

    /** Line of scrimmage for a two-point try (the opponent's 2). */
    const val TWO_POINT_YARD_LINE = 98

    /** Line of scrimmage for an extra-point kick (the opponent's 15). */
    const val EXTRA_POINT_YARD_LINE = 85

    /** The holder sets the ball this far behind the line of scrimmage for a field goal. */
    const val FIELD_GOAL_SNAP_DEPTH_YDS = 7

    /** After a missed field goal the defense takes over at the spot of the kick, or at least at its own 20. */
    const val MISSED_FIELD_GOAL_MIN_YARD_LINE = 20

    /** Game clock that runs off between plays (huddle + play clock) when the clock is not stopped. */
    const val BETWEEN_PLAY_RUNOFF_SEC = 33

    /** Field goal distance: line of scrimmage to the goal line, plus the snap depth and the end zone (posts sit on the end line). */
    fun fieldGoalDistance(losYardLine: Int): Int = FIELD_LENGTH_YDS - losYardLine + FIELD_GOAL_SNAP_DEPTH_YDS + END_ZONE_DEPTH_YDS
}
