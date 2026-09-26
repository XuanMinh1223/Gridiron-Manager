package com.xuan.gridironmanager.domain.sim.match

object Rules {
    const val FIELD_LENGTH_YDS = 100
    const val END_ZONE_DEPTH_YDS = 10
    const val QUARTER_LENGTH_SEC = 900
    const val QUARTERS = 4
    const val TOUCHDOWN_POINTS = 7 // Simplified: TD (6) + automatic extra point (1)
    const val KICKOFF_YARD_LINE = 35
    const val KICKOFF_TOUCHBACK_YARD_LINE = 25
    const val KICKOFF_OUT_OF_BOUNDS_YARD_LINE = 40
    const val PUNT_TOUCHBACK_YARD_LINE = 20
    const val INTERCEPTION_TOUCHBACK_YARD_LINE = 20
    const val FIRST_DOWN_DISTANCE = 10

    /** Game clock that runs off between plays (huddle + play clock) when the clock is not stopped. */
    const val BETWEEN_PLAY_RUNOFF_SEC = 25
}
