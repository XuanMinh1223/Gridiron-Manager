package com.xuan.gridironmanager.domain.sim.playbook

import com.xuan.gridironmanager.domain.model.Position

private fun node(
    slot: String,
    position: Position,
    x: Number,
    y: Number,
) = FormationNode(slot, position, x.toFloat(), y.toFloat())

object Formations {
    private val offensiveLine =
        listOf(
            node("C", Position.C, 0, 0),
            node("LG", Position.OG, -1, 0),
            node("RG", Position.OG, 1, 0),
            node("LT", Position.OT, -2, 0),
            node("RT", Position.OT, 2, 0),
        )

    private val defensiveLine =
        listOf(
            node("LDE", Position.EDGE, -3, 1),
            node("LDT", Position.DT, -1, 1),
            node("RDT", Position.DT, 1, 1),
            node("RDE", Position.EDGE, 3, 1),
        )

    /** 3 WR, 1 TE, 1 RB with the QB in the gun. */
    val SHOTGUN =
        Formation(
            name = "Shotgun Trips",
            type = FormationType.OFFENSE,
            nodes =
                offensiveLine +
                    listOf(
                        node("QB", Position.QB, 0, 5),
                        node("RB", Position.RB, 2, 5),
                        node("X", Position.WR, -15, 1),
                        node("Z", Position.WR, 15, 1),
                        node("SLOT", Position.WR, -10, 1),
                        node("TE", Position.TE, 3, 1),
                    ),
        )

    /** 2 WR, 2 TE, 1 RB with the QB under center. */
    val SINGLEBACK =
        Formation(
            name = "Singleback Ace",
            type = FormationType.OFFENSE,
            nodes =
                offensiveLine +
                    listOf(
                        node("QB", Position.QB, 0, 1),
                        node("RB", Position.RB, 0, 6),
                        node("X", Position.WR, -15, 1),
                        node("Z", Position.WR, 15, 1),
                        node("TE", Position.TE, 3, 1),
                        node("TE2", Position.TE, -3, 1),
                    ),
        )

    val BASE_4_3 =
        Formation(
            name = "4-3",
            type = FormationType.DEFENSE,
            nodes =
                defensiveLine +
                    listOf(
                        node("WLB", Position.LB, -4, 4),
                        node("MLB", Position.LB, 0, 4),
                        node("SLB", Position.LB, 4, 4),
                        node("LCB", Position.CB, -15, 2),
                        node("RCB", Position.CB, 15, 2),
                        node("FS", Position.S, -5, 10),
                        node("SS", Position.S, 5, 10),
                    ),
        )

    /** Five defensive backs: a nickel corner replaces the middle linebacker. */
    val NICKEL =
        Formation(
            name = "Nickel",
            type = FormationType.DEFENSE,
            nodes =
                defensiveLine +
                    listOf(
                        node("WLB", Position.LB, -3, 5),
                        node("SLB", Position.LB, 3, 5),
                        node("LCB", Position.CB, -15, 2),
                        node("RCB", Position.CB, 15, 2),
                        node("NB", Position.CB, -10, 3),
                        node("FS", Position.S, -5, 11),
                        node("SS", Position.S, 5, 11),
                    ),
        )

    val KICKOFF =
        Formation(
            name = "Kickoff",
            type = FormationType.KICKOFF,
            nodes =
                (0..10).map { i ->
                    if (i == 5) node("K", Position.K, 0, 0) else node("KC$i", Position.S, (i - 5) * 5, -25)
                },
        )

    /** Setup unit on its own 35, five yards behind coverage; the returner waits near the goal line. */
    val KICK_RETURN =
        Formation(
            name = "Kick Return",
            type = FormationType.KICK_RETURN,
            nodes =
                (0..10).map { i ->
                    if (i == 5) node("KR", Position.RB, 0, 2) else node("KRB$i", Position.LB, (i - 5) * 8, 33)
                },
        )

    val PUNT =
        Formation(
            name = "Punt",
            type = FormationType.PUNT,
            nodes =
                offensiveLine +
                    listOf(
                        node("P", Position.P, 0, 15),
                        node("GL", Position.WR, -25, 0), // Gunners
                        node("GR", Position.WR, 25, 0),
                        node("PPL", Position.LB, -3, 1),
                        node("PP", Position.LB, 0, 1),
                        node("PPR", Position.LB, 3, 1),
                    ),
        )

    val PUNT_RETURN =
        Formation(
            name = "Punt Return",
            type = FormationType.PUNT_RETURN,
            nodes =
                defensiveLine +
                    listOf(
                        node("PR", Position.RB, 0, 45), // Deep returner
                        node("VL", Position.CB, -25, 1), // Vises holding up the gunners
                        node("VR", Position.CB, 25, 1),
                        node("WLB", Position.LB, -5, 4),
                        node("MLB", Position.LB, 0, 4),
                        node("SLB", Position.LB, 5, 4),
                        node("S", Position.S, 0, 15),
                    ),
        )

    /** The holder spots the ball 7 yards deep, with the kicker alongside. */
    val FIELD_GOAL =
        Formation(
            name = "Field Goal",
            type = FormationType.FIELD_GOAL,
            nodes =
                offensiveLine +
                    listOf(
                        node("K", Position.K, 0, 7),
                        node("H", Position.QB, 1, 7),
                        node("TE", Position.TE, 3, 0),
                        node("TE2", Position.TE, -3, 0),
                        node("WL", Position.OL, -4, 1),
                        node("WR", Position.OL, 4, 1),
                    ),
        )

    val FIELD_GOAL_BLOCK =
        Formation(
            name = "Field Goal Block",
            type = FormationType.FIELD_GOAL_BLOCK,
            nodes =
                defensiveLine +
                    listOf(
                        node("LEND", Position.LB, -5, 1),
                        node("REND", Position.LB, 5, 1),
                        node("MLB", Position.LB, 0, 4),
                        node("LCB", Position.CB, -8, 1),
                        node("RCB", Position.CB, 8, 1),
                        node("FS", Position.S, -3, 8),
                        node("SS", Position.S, 3, 8),
                    ),
        )
}
