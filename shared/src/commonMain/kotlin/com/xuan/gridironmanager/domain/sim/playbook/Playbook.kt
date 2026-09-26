package com.xuan.gridironmanager.domain.sim.playbook

import com.xuan.gridironmanager.domain.model.PlayType
import com.xuan.gridironmanager.domain.sim.match.GamePhase

private fun spot(
    x: Number,
    depth: Number,
) = ZoneSpot(x.toFloat(), depth.toFloat())

object Playbook {
    // Offense

    val INSIDE_ZONE =
        OffensivePlay(
            id = "inside_zone",
            name = "Inside Zone",
            category = PlayCategory.RUN,
            type = PlayType.RUN,
            formation = Formations.SINGLEBACK,
            routes = mapOf("RB" to Routes.INSIDE_ZONE, "X" to Routes.GO, "Z" to Routes.GO),
            ballCarrierSlot = "RB",
        )

    val OUTSIDE_ZONE =
        OffensivePlay(
            id = "outside_zone",
            name = "Outside Zone",
            category = PlayCategory.RUN,
            type = PlayType.RUN,
            formation = Formations.SINGLEBACK,
            routes = mapOf("RB" to Routes.OUTSIDE_ZONE, "X" to Routes.CURL, "Z" to Routes.GO),
            ballCarrierSlot = "RB",
        )

    val SHOTGUN_DRAW =
        OffensivePlay(
            id = "shotgun_draw",
            name = "Shotgun Draw",
            category = PlayCategory.RUN,
            type = PlayType.RUN,
            formation = Formations.SHOTGUN,
            routes = mapOf("RB" to Routes.DRAW, "X" to Routes.GO, "Z" to Routes.GO, "SLOT" to Routes.HITCH),
            ballCarrierSlot = "RB",
        )

    val QUICK_SLANTS =
        OffensivePlay(
            id = "quick_slants",
            name = "Quick Slants",
            category = PlayCategory.SHORT_PASS,
            type = PlayType.PASS,
            formation = Formations.SHOTGUN,
            routes =
                mapOf(
                    "X" to Routes.SLANT,
                    "Z" to Routes.SLANT,
                    "SLOT" to Routes.HITCH,
                    "TE" to Routes.STICK,
                    "RB" to Routes.CHECKDOWN,
                ),
            progression = listOf("X", "Z", "SLOT", "TE", "RB"),
        )

    val STICK =
        OffensivePlay(
            id = "stick",
            name = "Stick",
            category = PlayCategory.SHORT_PASS,
            type = PlayType.PASS,
            formation = Formations.SHOTGUN,
            routes =
                mapOf(
                    "TE" to Routes.STICK,
                    "SLOT" to Routes.QUICK_OUT,
                    "Z" to Routes.HITCH,
                    "X" to Routes.GO,
                    "RB" to Routes.CHECKDOWN,
                ),
            progression = listOf("TE", "SLOT", "Z", "X", "RB"),
        )

    val CURL_FLAT =
        OffensivePlay(
            id = "curl_flat",
            name = "Curl Flat",
            category = PlayCategory.MEDIUM_PASS,
            type = PlayType.PASS,
            formation = Formations.SHOTGUN,
            routes =
                mapOf(
                    "X" to Routes.CURL,
                    "Z" to Routes.CURL,
                    "SLOT" to Routes.FLAT,
                    "TE" to Routes.SEAM,
                    "RB" to Routes.CHECKDOWN,
                ),
            progression = listOf("X", "Z", "SLOT", "TE", "RB"),
        )

    val DIG_DRAG =
        OffensivePlay(
            id = "dig_drag",
            name = "Dig Drag",
            category = PlayCategory.MEDIUM_PASS,
            type = PlayType.PASS,
            formation = Formations.SINGLEBACK,
            routes =
                mapOf(
                    "X" to Routes.DIG,
                    "TE" to Routes.DRAG,
                    "Z" to Routes.CURL,
                    "TE2" to Routes.FLAT,
                    "RB" to Routes.CHECKDOWN,
                ),
            progression = listOf("X", "TE", "Z", "TE2", "RB"),
        )

    val FOUR_VERTICALS =
        OffensivePlay(
            id = "four_verticals",
            name = "Four Verticals",
            category = PlayCategory.LONG_PASS,
            type = PlayType.PASS,
            formation = Formations.SHOTGUN,
            routes =
                mapOf(
                    "X" to Routes.GO,
                    "Z" to Routes.GO,
                    "SLOT" to Routes.SEAM,
                    "TE" to Routes.SEAM,
                    "RB" to Routes.CHECKDOWN,
                ),
            progression = listOf("SLOT", "X", "Z", "TE", "RB"),
        )

    val POST_CORNER =
        OffensivePlay(
            id = "post_corner",
            name = "Post Corner",
            category = PlayCategory.LONG_PASS,
            type = PlayType.PASS,
            formation = Formations.SINGLEBACK,
            // TE2 stays in to block
            routes = mapOf("X" to Routes.POST, "Z" to Routes.CORNER, "TE" to Routes.DRAG, "RB" to Routes.CHECKDOWN),
            progression = listOf("Z", "X", "TE", "RB"),
        )

    val FIELD_GOAL =
        OffensivePlay("field_goal", "Field Goal", PlayCategory.FIELD_GOAL, PlayType.FIELD_GOAL, Formations.FIELD_GOAL)

    val EXTRA_POINT =
        OffensivePlay("extra_point", "Extra Point", PlayCategory.EXTRA_POINT, PlayType.FIELD_GOAL, Formations.FIELD_GOAL)

    val PUNT = OffensivePlay("punt", "Punt", PlayCategory.PUNT, PlayType.PUNT, Formations.PUNT)

    val KICKOFF = OffensivePlay("kickoff", "Kickoff", PlayCategory.KICKOFF, PlayType.KICKOFF, Formations.KICKOFF)

    /** Plays from scrimmage, which double as two-point tries. */
    val scrimmagePlays: List<OffensivePlay> =
        listOf(INSIDE_ZONE, OUTSIDE_ZONE, SHOTGUN_DRAW, QUICK_SLANTS, STICK, CURL_FLAT, DIG_DRAG, FOUR_VERTICALS, POST_CORNER)

    fun offensivePlaysFor(phase: GamePhase): List<OffensivePlay> =
        when (phase) {
            GamePhase.KICKOFF -> listOf(KICKOFF)
            GamePhase.EXTRA_POINT -> listOf(EXTRA_POINT) + scrimmagePlays
            GamePhase.SCRIMMAGE -> scrimmagePlays + listOf(FIELD_GOAL, PUNT)
        }

    // Defense

    val COVER_2 =
        DefensiveCall(
            id = "43_cover_2",
            name = "4-3 Cover 2",
            formation = Formations.BASE_4_3,
            coverage = Coverage.COVER_2,
            zones =
                mapOf(
                    "LCB" to spot(-20, 5),
                    "RCB" to spot(20, 5),
                    "WLB" to spot(-9, 8),
                    "MLB" to spot(0, 9),
                    "SLB" to spot(9, 8),
                    "FS" to spot(-13, 18),
                    "SS" to spot(13, 18),
                ),
        )

    val COVER_3 =
        DefensiveCall(
            id = "43_cover_3",
            name = "4-3 Cover 3",
            formation = Formations.BASE_4_3,
            coverage = Coverage.COVER_3,
            zones =
                mapOf(
                    "LCB" to spot(-18, 16),
                    "RCB" to spot(18, 16),
                    "FS" to spot(0, 20),
                    "WLB" to spot(-17, 6),
                    "MLB" to spot(-7, 9),
                    "SLB" to spot(7, 9),
                    "SS" to spot(17, 6),
                ),
        )

    val BASE_MAN = DefensiveCall("43_man", "4-3 Man", Formations.BASE_4_3, Coverage.MAN)

    val NICKEL_MAN = DefensiveCall("nickel_man", "Nickel Man", Formations.NICKEL, Coverage.MAN)

    val NICKEL_COVER_2 =
        DefensiveCall(
            id = "nickel_cover_2",
            name = "Nickel Cover 2",
            formation = Formations.NICKEL,
            coverage = Coverage.COVER_2,
            zones =
                mapOf(
                    "LCB" to spot(-20, 5),
                    "RCB" to spot(20, 5),
                    "NB" to spot(-9, 8),
                    "WLB" to spot(-3, 9),
                    "SLB" to spot(9, 8),
                    "FS" to spot(-13, 18),
                    "SS" to spot(13, 18),
                ),
        )

    val NICKEL_BLITZ = DefensiveCall("nickel_blitz", "Nickel Blitz", Formations.NICKEL, Coverage.MAN, blitzers = setOf("WLB"))

    val KICK_RETURN = DefensiveCall("kick_return", "Kick Return", Formations.KICK_RETURN, coverage = null)

    val PUNT_RETURN = DefensiveCall("punt_return", "Punt Return", Formations.PUNT_RETURN, coverage = null)

    val FIELD_GOAL_BLOCK =
        DefensiveCall(
            id = "field_goal_block",
            name = "Field Goal Block",
            formation = Formations.FIELD_GOAL_BLOCK,
            coverage = null,
            blitzers = setOf("LEND", "REND"),
        )

    /** Calls the defense can choose from on scrimmage downs and two-point tries. */
    val defensiveCalls: List<DefensiveCall> = listOf(COVER_2, COVER_3, BASE_MAN, NICKEL_MAN, NICKEL_COVER_2, NICKEL_BLITZ)

    /** Kicking plays reveal themselves at the line, so the defense sends out its special teams unit instead of its call. */
    fun defensiveUnitFor(
        playType: PlayType,
        call: DefensiveCall,
    ): DefensiveCall =
        when (playType) {
            PlayType.KICKOFF -> KICK_RETURN
            PlayType.PUNT -> PUNT_RETURN
            PlayType.FIELD_GOAL -> FIELD_GOAL_BLOCK
            PlayType.RUN, PlayType.PASS -> call
        }
}
