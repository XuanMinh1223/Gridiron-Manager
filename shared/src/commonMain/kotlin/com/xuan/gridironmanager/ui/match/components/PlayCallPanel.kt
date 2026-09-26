package com.xuan.gridironmanager.ui.match.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xuan.gridironmanager.ui.match.MatchActions
import com.xuan.gridironmanager.ui.match.PlayCallState

/** Horizontal list of the user's play options for the next snap. */
@Composable
fun PlayCallPanel(
    playCall: PlayCallState,
    actions: MatchActions,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = if (playCall.isUserOnOffense) "Your offense" else "Your defense",
            style = MaterialTheme.typography.labelLarge,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (playCall.isUserOnOffense) {
                items(playCall.offenseOptions, key = { it.id }) { play ->
                    FilterChip(
                        selected = play == playCall.selectedOffense,
                        onClick = { actions.selectOffensivePlay(play) },
                        enabled = enabled,
                        label = { Text("${play.name} · ${play.category.label}") },
                    )
                }
            } else {
                items(playCall.defenseOptions, key = { it.id }) { call ->
                    FilterChip(
                        selected = call == playCall.selectedDefense,
                        onClick = { actions.selectDefensiveCall(call) },
                        enabled = enabled,
                        label = { Text(call.name) },
                    )
                }
            }
        }
    }
}
