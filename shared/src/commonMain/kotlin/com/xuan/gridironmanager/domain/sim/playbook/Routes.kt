package com.xuan.gridironmanager.domain.sim.playbook

/**
 * A point on a route relative to where the player lined up. [outside] is yards towards the player's near sideline
 * (negative = towards the middle of the field), [downfield] is yards towards the opponent's end zone.
 */
data class RoutePoint(
    val outside: Float,
    val downfield: Float,
)

data class RouteTemplate(
    val name: String,
    val points: List<RoutePoint>,
)

private fun route(
    name: String,
    vararg points: Pair<Number, Number>,
) = RouteTemplate(name, points.map { (outside, downfield) -> RoutePoint(outside.toFloat(), downfield.toFloat()) })

/** The route tree, plus ball-carrier paths for run plays (the carrier keeps running straight after the last point). */
object Routes {
    val GO = route("Go", 0 to 35)
    val SEAM = route("Seam", 0 to 30)
    val SLANT = route("Slant", 0 to 2, -8 to 10, -16 to 18)
    val HITCH = route("Hitch", 0 to 6, 0 to 5)
    val CURL = route("Curl", 0 to 12, -1 to 10)
    val QUICK_OUT = route("Quick Out", 0 to 4, 12 to 4)
    val STICK = route("Stick", 0 to 6)
    val FLAT = route("Flat", 3 to 1, 12 to 3)
    val POST = route("Post", 0 to 12, -10 to 30)
    val CORNER = route("Corner", 0 to 12, 10 to 24)
    val DIG = route("Dig", 0 to 12, -18 to 12)
    val DRAG = route("Drag", 0 to 2, -22 to 4)
    val CHECKDOWN = route("Checkdown", 3 to 2, 5 to 4)

    val INSIDE_ZONE = route("Inside Zone", 1 to 4)
    val OUTSIDE_ZONE = route("Outside Zone", 5 to 4, 9 to 8)
    val DRAW = route("Draw", 0 to 1, 0 to 3)
}
