package com.xuan.gridironmanager.ui.match.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.xuan.gridironmanager.domain.model.Vector3D
import com.xuan.gridironmanager.domain.sim.movement.RunningPlayer

@Composable
fun FieldCanvas(
    players: List<RunningPlayer>,
    ballPos: Vector3D?,
    lineOfScrimmageY: Float? = null,
    firstDownMarkerY: Float? = null,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val fieldWidthYds = 53.3f
        val fieldHeightTotalYds = 120.0f
        val endzoneDepthYds = 10.0f
        
        val scaleX = size.width / fieldWidthYds
        val scaleY = size.height / fieldHeightTotalYds
        
        // Draw Main Field
        drawRect(
            color = Color(0xFF2E7D32),
            size = size
        )
        
        // Draw Endzones
        // Endzone 1 (Bottom, 0-10yds)
        drawRect(
            color = Color(0xFF1B5E20),
            topLeft = Offset(0f, 0f),
            size = Size(size.width, endzoneDepthYds * scaleY)
        )
        // Endzone 2 (Top, 110-120yds)
        drawRect(
            color = Color(0xFF1B5E20),
            topLeft = Offset(0f, 110f * scaleY),
            size = Size(size.width, endzoneDepthYds * scaleY)
        )

            // Draw Yard Lines (every 10 yards, from 10 to 110)
        for (y in 10..110 step 10) {
            val yPos = y * scaleY
            val alpha = if (y == 10 || y == 110) 1.0f else 0.5f
            drawLine(
                color = Color.White.copy(alpha = alpha),
                start = Offset(0f, yPos),
                end = Offset(size.width, yPos),
                strokeWidth = (if (y == 10 || y == 110) 3.dp else 2.dp).toPx()
            )
        }

        // Draw Line of Scrimmage (Blue)
        lineOfScrimmageY?.let { y ->
            val yPos = (y + endzoneDepthYds) * scaleY
            drawLine(
                color = Color.Blue.copy(alpha = 0.8f),
                start = Offset(0f, yPos),
                end = Offset(size.width, yPos),
                strokeWidth = 3.dp.toPx()
            )
        }

        // Draw First Down Marker (Yellow)
        firstDownMarkerY?.let { y ->
            val yPos = (y + endzoneDepthYds) * scaleY
            drawLine(
                color = Color.Yellow.copy(alpha = 0.8f),
                start = Offset(0f, yPos),
                end = Offset(size.width, yPos),
                strokeWidth = 3.dp.toPx()
            )
        }

        // Draw Hashes
        for (y in 10..110 step 1) {
            if (y % 5 != 0) {
                val yPos = y * scaleY
                // Left Hash
                drawLine(
                    color = Color.White.copy(alpha = 0.3f),
                    start = Offset(fieldWidthYds * 0.4f * scaleX, yPos),
                    end = Offset(fieldWidthYds * 0.42f * scaleX, yPos),
                    strokeWidth = 1.dp.toPx()
                )
                // Right Hash
                drawLine(
                    color = Color.White.copy(alpha = 0.3f),
                    start = Offset(fieldWidthYds * 0.58f * scaleX, yPos),
                    end = Offset(fieldWidthYds * 0.6f * scaleX, yPos),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }

        // Draw Goal Posts (Bottom)
        drawLine(
            color = Color.Yellow,
            start = Offset(fieldWidthYds * 0.45f * scaleX, 5f * scaleY),
            end = Offset(fieldWidthYds * 0.55f * scaleX, 5f * scaleY),
            strokeWidth = 4.dp.toPx()
        )
        // Draw Goal Posts (Top)
        drawLine(
            color = Color.Yellow,
            start = Offset(fieldWidthYds * 0.45f * scaleX, 115f * scaleY),
            end = Offset(fieldWidthYds * 0.55f * scaleX, 115f * scaleY),
            strokeWidth = 4.dp.toPx()
        )

        // Draw Players
        players.forEach { player ->
            val color = if (player.isOffense) Color.Blue else Color.Red
            // Map sim Y (0-100) to field Y (10-110)
            val fieldY = player.currentPos.y + endzoneDepthYds
            drawCircle(
                color = color,
                radius = 6.dp.toPx(),
                center = Offset(
                    player.currentPos.x * scaleX,
                    fieldY * scaleY
                )
            )
        }

        // Draw Ball
        ballPos?.let { pos ->
            val ballRadiusBase = 4.dp.toPx()
            val elevationScale = 1f + (pos.z / 5f)
            val fieldY = pos.y + endzoneDepthYds
            drawCircle(
                color = Color(0xFF5D4037),
                radius = ballRadiusBase * elevationScale,
                center = Offset(
                    pos.x * scaleX,
                    fieldY * scaleY
                )
            )
        }
    }
}
