package com.xuan.gridironmanager.ui.match

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xuan.gridironmanager.domain.sim.match.GamePhase
import com.xuan.gridironmanager.domain.sim.match.GameState
import com.xuan.gridironmanager.ui.match.components.FieldCanvas
import com.xuan.gridironmanager.ui.match.components.PlayCallPanel
import com.xuan.gridironmanager.ui.match.components.SimControls
import com.xuan.gridironmanager.ui.match.components.teamPalette

@Composable
fun MatchScreen(
    uiState: MatchUiState,
    homeTeamName: String,
    awayTeamName: String,
    homeTeamPrimaryColorHex: String,
    homeTeamSecondaryColorHex: String,
    awayTeamPrimaryColorHex: String,
    awayTeamSecondaryColorHex: String,
    actions: MatchActions,
    onBackClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gameState = uiState.gameState

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top Nav / Back
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
        ) {
            TextButton(onClick = onBackClicked) {
                Text("< Back to Dashboard")
            }
        }

        // Scoreboard
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(teamLabel(homeTeamName, hasBall = gameState.isHomePossession && !gameState.isGameOver), fontWeight = FontWeight.Bold)
                    Text(
                        "${gameState.homeScore} - ${gameState.awayScore}",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(teamLabel(awayTeamName, hasBall = !gameState.isHomePossession && !gameState.isGameOver), fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                ) {
                    val minutes = gameState.clockSeconds / 60
                    val seconds = gameState.clockSeconds % 60
                    Text(if (gameState.isGameOver) "FINAL" else "Q${gameState.quarter}  $minutes:${seconds.toString().padStart(2, '0')}")
                    Text(situationText(gameState))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Visualizer
        FieldCanvas(
            players = uiState.players,
            ballPos = uiState.ballPosition,
            lineOfScrimmageY = uiState.lineOfScrimmageY,
            firstDownMarkerY = uiState.firstDownMarkerY,
            overlay = uiState.overlay,
            isAttackingUp = uiState.isAttackingUp,
            offensePalette = if (gameState.isHomePossession) {
                teamPalette(homeTeamPrimaryColorHex, homeTeamSecondaryColorHex)
            } else {
                teamPalette(awayTeamPrimaryColorHex, awayTeamSecondaryColorHex)
            },
            defensePalette = if (gameState.isHomePossession) {
                teamPalette(awayTeamPrimaryColorHex, awayTeamSecondaryColorHex)
            } else {
                teamPalette(homeTeamPrimaryColorHex, homeTeamSecondaryColorHex)
            },
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Bottom Controls
        Card(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = uiState.playByPlayText,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 16.dp),
                )

                val canSnap = uiState.isMatchReady && !uiState.isPlayRunning && !gameState.isGameOver
                val playCall = uiState.playCall

                if (playCall.isUserChoosing && !gameState.isGameOver) {
                    PlayCallPanel(
                        playCall = playCall,
                        actions = actions,
                        enabled = canSnap,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Auto-call", modifier = Modifier.weight(1f))
                    Switch(checked = playCall.isAutoCall, onCheckedChange = actions::setAutoCall)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Assignments", modifier = Modifier.weight(1f))
                    Switch(checked = uiState.showAssignments, onCheckedChange = actions::setShowAssignments)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Debug Mode", modifier = Modifier.weight(1f))
                    Switch(checked = uiState.isDebugMode, onCheckedChange = actions::setDebugMode)
                }

                if (uiState.isDebugMode) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = actions::resetGame,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Reset Game (Debug)")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                SimControls(
                    speed = uiState.simSpeed,
                    onSpeedSelected = actions::setSimSpeed,
                    onQuickSim = actions::quickSim,
                    isQuickSimEnabled = canSnap,
                    modifier = Modifier.fillMaxWidth(),
                )

                Button(
                    onClick = actions::snapBall,
                    enabled = canSnap,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Text(if (gameState.isGameOver) "GAME OVER" else "SNAP BALL", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun teamLabel(
    name: String,
    hasBall: Boolean,
) = if (hasBall) "● $name" else name

private fun situationText(gameState: GameState): String =
    when {
        gameState.isGameOver -> ""
        gameState.phase == GamePhase.KICKOFF -> "Kickoff from the ${gameState.yardLine}"
        gameState.phase == GamePhase.EXTRA_POINT -> "Extra point try"
        else -> "${gameState.down} & ${gameState.distance} at YD ${gameState.yardLine}"
    }
