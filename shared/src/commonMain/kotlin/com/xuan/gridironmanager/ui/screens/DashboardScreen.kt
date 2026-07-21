package com.xuan.gridironmanager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xuan.gridironmanager.domain.model.Team

@Composable
fun DashboardScreen(
    myTeam: Team?,
    onStartGame: () -> Unit
) {
    Scaffold(
        topBar = {
            LargeTopAppBar(title = { Text("Gridiron Manager") })
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            if (myTeam != null) {
                Text(text = "Managing: ${myTeam.fullName}", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(16.dp))
                
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Team Status", style = MaterialTheme.typography.titleLarge)
                        Text("Salary Cap: $${myTeam.salaryCapUsed / 1_000_000}M / $${myTeam.salaryCapTotal / 1_000_000}M")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = onStartGame,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Enter Live Game")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Roster", style = MaterialTheme.typography.titleMedium)
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(myTeam.roster) { player ->
                        ListItem(
                            headlineContent = { Text(player.fullName) },
                            supportingContent = { Text("${player.position.abbreviation} | Age: ${player.age}") },
                            trailingContent = { Text("OVR: ${player.overallRating}") }
                        )
                    }
                }
            } else {
                Text("Initializing League...")
            }
        }
    }
}

