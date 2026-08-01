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
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val fieldWidthYds = 53.3f
        val fieldHeightYds = 120.0f
        
        val scaleX = size.width / fieldWidthYds
        val scaleY = size.height / fieldHeightYds
        
        // Draw Field
        drawRect(
            color = Color(0xFF2E7D32),
            size = size
        )
        
        // Draw Yard Lines (every 10 yards)
        for (y in 0..120 step 10) {
            val yPos = y * scaleY
            drawLine(
                color = Color.White.copy(alpha = 0.5f),
                start = Offset(0f, yPos),
                end = Offset(size.width, yPos),
                strokeWidth = 2.dp.toPx()
            )
        }

        // Draw Players
        players.forEach { player ->
            val color = if (player.id.startsWith("D")) Color.Red else Color.Blue
            drawCircle(
                color = color,
                radius = 6.dp.toPx(),
                center = Offset(
                    player.currentPos.x * scaleX,
                    player.currentPos.y * scaleY
                )
            )
        }

        // Draw Ball
        ballPos?.let { pos ->
            val ballRadiusBase = 4.dp.toPx()
            val elevationScale = 1f + (pos.z / 5f) // Grow larger as it goes higher
            drawCircle(
                color = Color(0xFF5D4037), // Brown
                radius = ballRadiusBase * elevationScale,
                center = Offset(
                    pos.x * scaleX,
                    pos.y * scaleY
                )
            )
        }
    }
}
