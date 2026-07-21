package com.xuan.gridironmanager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xuan.gridironmanager.domain.model.*

@Composable
fun LiveGameScreen(
    gameState: GameState?,
    onSimPlay: (PlayCall) -> Unit,
    onQuickSim: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live Game") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                }
            )
        }
    ) { padding ->
        if (gameState == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(Modifier.padding(padding).padding(16.dp)) {
                Scoreboard(gameState)
                
                Spacer(Modifier.height(16.dp))
                
                Text(
                    text = "${gameState.down}${getOrdinal(gameState.down)} & ${gameState.distance} at ${gameState.yardLine} yd line",
                    style = MaterialTheme.typography.headlineSmall
                )
                
                Spacer(Modifier.height(16.dp))
                
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Button(onClick = { onSimPlay(PlayCall(PlayType.RUN, Formation.SINGLEBACK)) }) {
                        Text("Sim Run")
                    }
                    Button(onClick = { onSimPlay(PlayCall(PlayType.PASS, Formation.SHOTGUN)) }) {
                        Text("Sim Pass")
                    }
                    OutlinedButton(onClick = onQuickSim) {
                        Text("Sim Drive")
                    }
                }

                Spacer(Modifier.height(24.dp))
                
                Text("Play-by-Play", style = MaterialTheme.typography.titleMedium)
                LazyColumn(Modifier.weight(1f)) {
                    items(gameState.playHistory.reversed()) { play ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(play.description, Modifier.padding(12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Scoreboard(state: GameState) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.awayTeam.name, style = MaterialTheme.typography.titleSmall)
                Text("${state.awayScore}", style = MaterialTheme.typography.displaySmall)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Q${state.quarter}", style = MaterialTheme.typography.titleSmall)
                val minutes = state.clockSeconds / 60
                val seconds = state.clockSeconds % 60
                Text("${minutes}:${seconds.toString().padStart(2, '0')}", style = MaterialTheme.typography.titleMedium)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.homeTeam.name, style = MaterialTheme.typography.titleSmall)
                Text("${state.homeScore}", style = MaterialTheme.typography.displaySmall)
            }
        }
    }
}

private fun getOrdinal(n: Int) = when (n) {
    1 -> "st"
    2 -> "nd"
    3 -> "rd"
    else -> "th"
}
