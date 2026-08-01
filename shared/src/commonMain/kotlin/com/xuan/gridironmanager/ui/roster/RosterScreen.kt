package com.xuan.gridironmanager.ui.roster

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xuan.gridironmanager.domain.model.Player
import com.xuan.gridironmanager.domain.model.Position
import com.xuan.gridironmanager.domain.model.PositionType

@Composable
fun RosterScreen(
    uiState: RosterUiState,
    onPlayerClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (uiState) {
            is RosterUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            is RosterUiState.Error -> {
                Text(
                    text = uiState.message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            is RosterUiState.Success -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.players) { player ->
                        PlayerRow(
                            player = player,
                            onClick = { onPlayerClick(player.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlayerRow(
    player: Player,
    onClick: () -> Unit
) {
    val positionInfo = Position.entries.find { it.abbreviation == player.position }
    val badgeColor = when (positionInfo?.type) {
        PositionType.OFFENSE -> Color(0xFF2196F3) // Blue
        PositionType.DEFENSE -> Color(0xFFF44336) // Red
        PositionType.SPECIAL_TEAMS -> Color(0xFF4CAF50) // Green
        null -> Color.Gray
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Position Badge
            Surface(
                color = badgeColor,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.size(width = 40.dp, height = 24.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = player.position,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Name and Age
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = player.fullName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Age: ${player.age} | Exp: ${player.yearsPro}y",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Overall Rating
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = player.overallRating.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}
