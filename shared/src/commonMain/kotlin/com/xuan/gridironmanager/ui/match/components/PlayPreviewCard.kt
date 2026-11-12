package com.xuan.gridironmanager.ui.match.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xuan.gridironmanager.domain.sim.playbook.DefensiveCall
import com.xuan.gridironmanager.domain.sim.playbook.DiagramPoint
import com.xuan.gridironmanager.domain.sim.playbook.OffensivePlay
import com.xuan.gridironmanager.domain.sim.playbook.PlayDiagram
import kotlin.math.min

private val CardWidth = 144.dp
private val CardHeight = 184.dp
private val DiagramHeight = 144.dp
private val NameHeight = 40.dp

/** Card title for an offensive play. */
fun offenseCardTitle(play: OffensivePlay): String = play.name

fun offenseCardSubtitle(play: OffensivePlay): String = play.category.label

/** Card title for a defensive call: disguised calls are marked, and the preview itself only ever draws the shell. */
fun defenseCardTitle(call: DefensiveCall): String = if (call.disguise != null) "${call.name} · Disguised" else call.name

fun defenseCardSubtitle(call: DefensiveCall): String = call.formation.name

/** One-line detail for a selected disguised call, or null for any other call. Only shown to the caller. */
fun disguiseDetail(call: DefensiveCall): String? {
    val disguise = call.disguise ?: return null
    val shell = disguise.shellCoverage?.label ?: "nothing"
    val truth = call.coverage?.label ?: "nothing"
    return "Shows $shell pre-snap, rotates to $truth at ${disguise.rotationSec} s"
}

/** A fixed-size Madden-style play card: a top-down diagram above the play name. The whole card selects the play. */
@Composable
fun PlayPreviewCard(
    diagram: PlayDiagram,
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(width = CardWidth, height = CardHeight),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 8.dp else 1.dp),
    ) {
        Column {
            PlayDiagramCanvas(diagram, Modifier.fillMaxWidth().height(DiagramHeight))
            Column(
                modifier = Modifier.fillMaxWidth().height(NameHeight).padding(horizontal = 6.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Draws [diagram] at one fixed uniform scale, clipped to the shared viewport, in the same style as [FieldCanvas]. */
@Composable
private fun PlayDiagramCanvas(
    diagram: PlayDiagram,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val view = diagram.viewport
        val viewWidth = view.maxX - view.minX
        val viewHeight = view.maxY - view.minY
        val scale = min(size.width / viewWidth, size.height / viewHeight)
        val left = (size.width - viewWidth * scale) / 2
        val top = (size.height - viewHeight * scale) / 2

        fun map(point: DiagramPoint) = Offset(left + (point.x - view.minX) * scale, top + (view.maxY - point.y) * scale)

        drawRect(GrassColor)
        clipRect(left, top, left + viewWidth * scale, top + viewHeight * scale) {
            val losY = map(DiagramPoint(0f, 0f)).y
            drawLine(LineOfScrimmageColor.copy(alpha = 0.9f), Offset(left, losY), Offset(left + viewWidth * scale, losY), strokeWidth = 2.dp.toPx())

            diagram.zones.forEach { zone ->
                val color = if (zone.isDeep) DeepZoneColor else UnderneathZoneColor
                val center = map(zone.center)
                drawCircle(color.copy(alpha = 0.18f), radius = ZONE_MARKER_RADIUS_YDS * scale, center = center)
                drawCircle(color.copy(alpha = 0.6f), radius = ZONE_MARKER_RADIUS_YDS * scale, center = center, style = Stroke(1.dp.toPx()))
            }

            diagram.routes.forEach { route ->
                val points = route.points.map(::map)
                val path = Path()
                points.forEachIndexed { index, offset -> if (index == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y) }
                drawPath(path, RouteColor, style = Stroke(2.dp.toPx()))
                if (points.size >= 2) drawArrowHead(points[points.size - 2], points.last(), RouteColor)
            }

            diagram.blitzArrows.forEach { (from, to) ->
                drawLine(BlitzColor, map(from), map(to), strokeWidth = 2.dp.toPx())
                drawArrowHead(map(from), map(to), BlitzColor)
            }

            val dotRadius = 3.5.dp.toPx()
            diagram.offenseDots.forEach { drawDot(map(it), OffenseColor, dotRadius) }
            diagram.defenseDots.forEach { drawDot(map(it), DefenseColor, dotRadius) }
        }
    }
}

private fun DrawScope.drawDot(
    center: Offset,
    color: Color,
    radius: Float,
) {
    drawCircle(color, radius, center)
    drawCircle(Color.White, radius, center, style = Stroke(1.dp.toPx()))
}
