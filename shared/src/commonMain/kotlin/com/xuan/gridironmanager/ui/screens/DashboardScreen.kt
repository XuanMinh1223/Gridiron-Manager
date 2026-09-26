package com.xuan.gridironmanager.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xuan.gridironmanager.domain.model.Team

@Composable
fun DashboardScreen(
    myTeam: Team?,
    opponent: Team?,
    onViewRoster: () -> Unit,
    onStartGame: () -> Unit,
) {
    Scaffold(
        topBar = {
            LargeTopAppBar(title = { Text("Gridiron Manager") })
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            if (myTeam != null) {
                Text(text = "Managing: ${myTeam.fullName}", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(16.dp))

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Team Status", style = MaterialTheme.typography.titleLarge)
                        Text("Colors: ${myTeam.primaryColorHex} / ${myTeam.secondaryColorHex}")
                        if (opponent != null) {
                            Text("Next game: vs ${opponent.fullName}")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onStartGame,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Enter Live Game")
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onViewRoster,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("View Roster")
                }
            } else {
                Text("Initializing League...")
            }
        }
    }
}
