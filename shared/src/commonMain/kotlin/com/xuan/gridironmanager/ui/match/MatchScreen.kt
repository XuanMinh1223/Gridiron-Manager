package com.xuan.gridironmanager.ui.match

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xuan.gridironmanager.ui.match.components.FieldCanvas

@Composable
fun MatchScreen(
    uiState: MatchUiState,
    onSnapClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Scoreboard
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("HOME", fontWeight = FontWeight.Bold)
                    Text(
                        "${uiState.gameState.homeScore} - ${uiState.gameState.awayScore}",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text("AWAY", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    val minutes = uiState.gameState.clockSeconds / 60
                    val seconds = uiState.gameState.clockSeconds % 60
                    Text("Clock: ${minutes}:${seconds.toString().padStart(2, '0')}")
                    Text("${uiState.gameState.down} & ${uiState.gameState.distance} at YD ${uiState.gameState.yardLine}")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Visualizer
        FieldCanvas(
            players = uiState.players,
            ballPos = uiState.ballPosition,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Bottom Controls
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = uiState.playByPlayText,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                Button(
                    onClick = onSnapClicked,
                    enabled = !uiState.isPlayRunning,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text("SNAP BALL", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
