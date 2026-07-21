package com.xuan.gridironmanager.domain.model

enum class Position(val abbreviation: String, val type: PositionType) {
    QB("QB", PositionType.OFFENSE),
    RB("RB", PositionType.OFFENSE),
    WR("WR", PositionType.OFFENSE),
    TE("TE", PositionType.OFFENSE),
    OT("OT", PositionType.OFFENSE),
    OG("OG", PositionType.OFFENSE),
    C("C", PositionType.OFFENSE),
    EDGE("EDGE", PositionType.DEFENSE),
    DT("DT", PositionType.DEFENSE),
    LB("LB", PositionType.DEFENSE),
    CB("CB", PositionType.DEFENSE),
    S("S", PositionType.DEFENSE),
    K("K", PositionType.SPECIAL_TEAMS),
    P("P", PositionType.SPECIAL_TEAMS)
}

enum class PositionType {
    OFFENSE, DEFENSE, SPECIAL_TEAMS
}
