package com.xuan.gridironmanager.ui.match.components

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.xuan.gridironmanager.domain.model.PlayType
import com.xuan.gridironmanager.domain.sim.playbook.defensiveDiagram
import com.xuan.gridironmanager.domain.sim.playbook.offensiveDiagram
import com.xuan.gridironmanager.ui.match.MatchActions
import com.xuan.gridironmanager.ui.match.PlayCallState
import kotlinx.coroutines.launch

/** Horizontal, drag-scrollable row of play preview cards for the user's next snap. */
@Composable
fun PlayCallPanel(
    playCall: PlayCallState,
    actions: MatchActions,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Column(modifier = modifier) {
        Text(
            text = if (playCall.isUserOnOffense) "Your offense" else "Your defense",
            style = MaterialTheme.typography.labelLarge,
        )
        // Only relevant to the QB on a pass play: the read of a disguised coverage
        playCall.coverageHint?.takeIf { playCall.isUserOnOffense && playCall.selectedOffense?.type == PlayType.PASS }?.let {
            Text(text = it.text, style = MaterialTheme.typography.bodySmall)
        }
        LazyRow(
            state = listState,
            // A mouse drag does not scroll a LazyRow on desktop by default, so scroll with the drag explicitly.
            // A tap without movement still reaches the card; a drag past touch slop is consumed here.
            modifier =
                Modifier.pointerInput(Unit) {
                    detectHorizontalDragGestures { _, dragAmount ->
                        scope.launch { listState.scrollBy(-dragAmount) }
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 8.dp),
        ) {
            if (playCall.isUserOnOffense) {
                items(playCall.offenseOptions, key = { it.id }) { play ->
                    val diagram = remember(play) { offensiveDiagram(play) }
                    PlayPreviewCard(
                        diagram = diagram,
                        title = offenseCardTitle(play),
                        subtitle = offenseCardSubtitle(play),
                        selected = play == playCall.selectedOffense,
                        enabled = enabled,
                        onClick = { actions.selectOffensivePlay(play) },
                    )
                }
            } else {
                items(playCall.defenseOptions, key = { it.id }) { call ->
                    val diagram = remember(call) { defensiveDiagram(call) }
                    PlayPreviewCard(
                        diagram = diagram,
                        title = defenseCardTitle(call),
                        subtitle = defenseCardSubtitle(call),
                        selected = call == playCall.selectedDefense,
                        enabled = enabled,
                        onClick = { actions.selectDefensiveCall(call) },
                    )
                }
            }
        }
        // The caller's own disguised call: naming the true coverage here is not a leak
        playCall.selectedDefense?.takeIf { !playCall.isUserOnOffense }?.let(::disguiseDetail)?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall)
        }
    }
}
