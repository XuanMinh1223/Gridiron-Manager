package com.xuan.gridironmanager.domain.model

enum class PlayType {
    RUN, PASS, KICK, PUNT
}

enum class Formation {
    SHOTGUN, I_FORMATION, SINGLEBACK, NICKEL, DIME, GOAL_LINE
}

enum class Coverage {
    MAN, ZONE_2, ZONE_3, ZONE_4, BLITZ
}

data class PlayCall(
    val type: PlayType,
    val formation: Formation,
    val coverage: Coverage? = null
)
