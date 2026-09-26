package com.xuan.gridironmanager.ui.match.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.xuan.gridironmanager.domain.model.Vector3D
import com.xuan.gridironmanager.domain.sim.FieldGeometry
import com.xuan.gridironmanager.domain.sim.movement.RunningPlayer
import com.xuan.gridironmanager.ui.match.overlay.Segment
import com.xuan.gridironmanager.ui.match.overlay.TacticalOverlay
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val END_ZONE_DEPTH_YDS = 10f
private const val FIELD_LENGTH_WITH_END_ZONES_YDS = 120f
private const val ZONE_MARKER_RADIUS_YDS = 4f

private val GrassColor = Color(0xFF2E7D32)
private val EndZoneColor = Color(0xFF1B5E20)
private val LineOfScrimmageColor = Color(0xFF4FC3F7)
private val FirstDownColor = Color(0xFFFFEB3B)
private val OffenseColor = Color(0xFF0D47A1)
private val DefenseColor = Color(0xFFC62828)
private val RouteColor = Color(0xFFFFF176)
private val ManLinkColor = Color.White.copy(alpha = 0.7f)
private val DeepZoneColor = Color(0xFF42A5F5)
private val UnderneathZoneColor = Color(0xFFFFCA28)
private val BlitzColor = Color(0xFFFF5252)

/**
 * The field, with world coordinates (see [FieldGeometry]) mapped onto it at a uniform scale so distances and zone
 * circles keep their true proportions.
 */
@Composable
fun FieldCanvas(
    players: List<RunningPlayer>,
    ballPos: Vector3D?,
    modifier: Modifier = Modifier,
    lineOfScrimmageY: Float? = null,
    firstDownMarkerY: Float? = null,
    overlay: TacticalOverlay = TacticalOverlay.NONE,
) {
    Canvas(modifier = modifier) {
        val field = FieldTransform(size)

        drawField(field)
        lineOfScrimmageY?.let { drawYardLine(field, it, LineOfScrimmageColor) }
        firstDownMarkerY?.let { drawYardLine(field, it, FirstDownColor) }
        drawOverlay(field, overlay)

        players.forEach { player ->
            drawCircle(
                color = if (player.isOffense) OffenseColor else DefenseColor,
                radius = 6.dp.toPx(),
                center = field.toOffset(player.currentPos),
            )
            drawCircle(color = Color.White, radius = 6.dp.toPx(), center = field.toOffset(player.currentPos), style = Stroke(1.dp.toPx()))
        }

        ballPos?.let { pos ->
            // The ball grows with height so kicks and lobs read as airborne
            drawCircle(
                color = Color(0xFF5D4037),
                radius = 4.dp.toPx() * (1f + pos.z / 5f),
                center = field.toOffset(pos),
            )
        }
    }
}

/** Maps world yards to canvas pixels, centring the field in the available space. */
private class FieldTransform(
    canvasSize: Size,
) {
    val scale = min(canvasSize.width / FieldGeometry.WIDTH_YDS, canvasSize.height / FIELD_LENGTH_WITH_END_ZONES_YDS)
    val width = FieldGeometry.WIDTH_YDS * scale
    val height = FIELD_LENGTH_WITH_END_ZONES_YDS * scale
    val left = (canvasSize.width - width) / 2
    val top = (canvasSize.height - height) / 2

    /** World y runs from the home goal line (0) upwards, with the home end zone at the bottom of the screen. */
    fun toOffset(pos: Vector3D) = Offset(left + pos.x * scale, top + (FIELD_LENGTH_WITH_END_ZONES_YDS - (pos.y + END_ZONE_DEPTH_YDS)) * scale)

    fun yToPx(worldY: Float) = toOffset(Vector3D(0f, worldY, 0f)).y
}

private fun DrawScope.drawField(field: FieldTransform) {
    drawRect(GrassColor, topLeft = Offset(field.left, field.top), size = Size(field.width, field.height))
    drawRect(EndZoneColor, topLeft = Offset(field.left, field.top), size = Size(field.width, END_ZONE_DEPTH_YDS * field.scale))
    drawRect(
        EndZoneColor,
        topLeft = Offset(field.left, field.yToPx(0f)),
        size = Size(field.width, END_ZONE_DEPTH_YDS * field.scale),
    )

    // Yard lines every 5 yards, goal lines bold
    for (yard in 0..100 step 5) {
        val isGoalLine = yard == 0 || yard == 100
        val y = field.yToPx(yard.toFloat())
        drawLine(
            color =
                Color.White.copy(
                    alpha =
                        if (isGoalLine) {
                            1f
                        } else if (yard % 10 == 0) {
                            0.5f
                        } else {
                            0.25f
                        },
                ),
            start = Offset(field.left, y),
            end = Offset(field.left + field.width, y),
            strokeWidth = (if (isGoalLine) 3.dp else 1.dp).toPx(),
        )
    }

    // Hash marks every yard
    val hashLength = field.scale
    for (yard in 1 until 100) {
        if (yard % 5 == 0) continue
        val y = field.yToPx(yard.toFloat())
        for (hashX in listOf(FieldGeometry.WIDTH_YDS * 0.4f, FieldGeometry.WIDTH_YDS * 0.6f)) {
            val x = field.left + hashX * field.scale
            drawLine(Color.White.copy(alpha = 0.3f), Offset(x - hashLength / 2, y), Offset(x + hashLength / 2, y), strokeWidth = 1.dp.toPx())
        }
    }

    // Goal posts on the end lines
    for (postsY in listOf(-END_ZONE_DEPTH_YDS, 100f + END_ZONE_DEPTH_YDS)) {
        val y = field.yToPx(postsY)
        drawLine(
            color = Color.Yellow,
            start = Offset(field.left + (FieldGeometry.CENTER_X - 3f) * field.scale, y),
            end = Offset(field.left + (FieldGeometry.CENTER_X + 3f) * field.scale, y),
            strokeWidth = 4.dp.toPx(),
        )
    }
}

private fun DrawScope.drawYardLine(
    field: FieldTransform,
    worldY: Float,
    color: Color,
) {
    val y = field.yToPx(worldY)
    drawLine(color.copy(alpha = 0.9f), Offset(field.left, y), Offset(field.left + field.width, y), strokeWidth = 3.dp.toPx())
}

private fun DrawScope.drawOverlay(
    field: FieldTransform,
    overlay: TacticalOverlay,
) {
    overlay.zones.forEach { zone ->
        val color = if (zone.isDeep) DeepZoneColor else UnderneathZoneColor
        val center = field.toOffset(zone.center)
        drawCircle(color.copy(alpha = 0.18f), radius = ZONE_MARKER_RADIUS_YDS * field.scale, center = center)
        drawCircle(color.copy(alpha = 0.6f), radius = ZONE_MARKER_RADIUS_YDS * field.scale, center = center, style = Stroke(1.dp.toPx()))
        drawLine(color.copy(alpha = 0.5f), field.toOffset(zone.defenderPos), center, strokeWidth = 1.dp.toPx())
    }

    val dashes = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
    overlay.manLinks.forEach { link ->
        drawLine(ManLinkColor, field.toOffset(link.from), field.toOffset(link.to), strokeWidth = 1.5.dp.toPx(), pathEffect = dashes)
    }

    overlay.routes.forEach { route ->
        val path = Path()
        route.forEachIndexed { index, point ->
            val offset = field.toOffset(point)
            if (index == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
        }
        drawPath(path, RouteColor, style = Stroke(2.dp.toPx()))
        if (route.size >= 2) drawArrowHead(field.toOffset(route[route.size - 2]), field.toOffset(route.last()), RouteColor)
    }

    overlay.blitzArrows.forEach { arrow -> drawArrow(field, arrow, BlitzColor) }
}

private fun DrawScope.drawArrow(
    field: FieldTransform,
    segment: Segment,
    color: Color,
) {
    val from = field.toOffset(segment.from)
    val to = field.toOffset(segment.to)
    drawLine(color, from, to, strokeWidth = 2.5.dp.toPx())
    drawArrowHead(from, to, color)
}

private fun DrawScope.drawArrowHead(
    from: Offset,
    to: Offset,
    color: Color,
) {
    val angle = atan2(to.y - from.y, to.x - from.x)
    val length = 8.dp.toPx()
    val spread = 0.5f
    val path =
        Path().apply {
            moveTo(to.x, to.y)
            lineTo(to.x - length * cos(angle - spread), to.y - length * sin(angle - spread))
            lineTo(to.x - length * cos(angle + spread), to.y - length * sin(angle + spread))
            close()
        }
    drawPath(path, color)
}
