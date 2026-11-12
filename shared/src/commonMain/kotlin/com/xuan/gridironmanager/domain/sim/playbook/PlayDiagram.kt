package com.xuan.gridironmanager.domain.sim.playbook

/** A point on a play diagram, in yards: [x] across the field from the middle, [y] downfield from the line of scrimmage (LOS = 0). */
data class DiagramPoint(
    val x: Float,
    val y: Float,
)

data class DiagramRoute(
    val points: List<DiagramPoint>,
    val isRunPath: Boolean = false,
)

data class DiagramZone(
    val center: DiagramPoint,
    val isDeep: Boolean,
)

/** The window of the field a diagram is drawn in, in yards. Cards on one row all share one viewport, hence one scale. */
data class DiagramViewport(
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float,
)

/** Everything needed to draw a play preview, with no UI dependencies. The offense always attacks towards +y. */
data class PlayDiagram(
    val offenseDots: List<DiagramPoint> = emptyList(),
    val defenseDots: List<DiagramPoint> = emptyList(),
    val routes: List<DiagramRoute> = emptyList(),
    val zones: List<DiagramZone> = emptyList(),
    /** Blitzer's alignment to the line of scrimmage directly in front of him. */
    val blitzArrows: List<Pair<DiagramPoint, DiagramPoint>> = emptyList(),
    val viewport: DiagramViewport = OFFENSE_VIEWPORT,
) {
    companion object {
        // Wider than +-22: Post Corner's Z route ends 25 yards from the middle; routes run up to 35 yards downfield
        val OFFENSE_VIEWPORT = DiagramViewport(minX = -26f, maxX = 26f, minY = -8f, maxY = 38f)

        // Defense never goes deeper than its ~20 yard zones, so a window sized to it keeps the card from looking half empty
        val DEFENSE_VIEWPORT = DiagramViewport(minX = -24f, maxX = 24f, minY = -3f, maxY = 26f)

        /** Same deep-zone threshold the tactical overlay uses. */
        const val DEEP_ZONE_MIN_DEPTH_YDS = 12f
    }
}

/** Offensive nodes line up [FormationNode.yOffset] yards behind the line, so they sit at negative y. */
private fun offenseSpot(node: FormationNode) = DiagramPoint(node.xOffset, -node.yOffset)

/** Defensive nodes line up in front of the line, so they sit at positive y. */
private fun defenseSpot(node: FormationNode) = DiagramPoint(node.xOffset, node.yOffset)

/** Mirrors `SnapBuilder.toWorldRoute` for an offense attacking up: the lineup spot, then each template point. */
private fun routePath(
    template: RouteTemplate,
    node: FormationNode,
): List<DiagramPoint> {
    val start = offenseSpot(node)
    val outsideSign = if (node.xOffset < 0f) -1f else 1f
    return listOf(start) + template.points.map { DiagramPoint(start.x + it.outside * outsideSign, start.y + it.downfield) }
}

fun offensiveDiagram(play: OffensivePlay): PlayDiagram {
    val nodesBySlot = play.formation.nodes.associateBy { it.slot }
    val routes =
        play.routes.map { (slot, template) ->
            DiagramRoute(routePath(template, nodesBySlot.getValue(slot)), isRunPath = slot == play.ballCarrierSlot)
        }
    return PlayDiagram(offenseDots = play.formation.nodes.map(::offenseSpot), routes = routes)
}

/** Draws the pre-snap look: for a disguised call that is the shell coverage, never the true one. */
fun defensiveDiagram(call: DefensiveCall): PlayDiagram {
    val shown = call.shell()
    val nodesBySlot = call.formation.nodes.associateBy { it.slot }
    return PlayDiagram(
        viewport = PlayDiagram.DEFENSE_VIEWPORT,
        defenseDots = call.formation.nodes.map(::defenseSpot),
        zones =
            shown.zones.values.map {
                DiagramZone(DiagramPoint(it.x, it.depth), isDeep = it.depth >= PlayDiagram.DEEP_ZONE_MIN_DEPTH_YDS)
            },
        blitzArrows =
            shown.blitzers.map { slot ->
                val from = defenseSpot(nodesBySlot.getValue(slot))
                from to DiagramPoint(from.x, 0f)
            },
    )
}
