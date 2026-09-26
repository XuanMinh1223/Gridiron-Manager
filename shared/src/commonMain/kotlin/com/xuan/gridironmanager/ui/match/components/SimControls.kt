package com.xuan.gridironmanager.ui.match.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xuan.gridironmanager.ui.match.SimSpeed

/** Playback speed chips and the Quick Sim button. */
@Composable
fun SimControls(
    speed: SimSpeed,
    onSpeedSelected: (SimSpeed) -> Unit,
    onQuickSim: () -> Unit,
    isQuickSimEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SimSpeed.entries.forEach { option ->
            FilterChip(
                selected = option == speed,
                onClick = { onSpeedSelected(option) },
                label = { Text(option.label) },
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onQuickSim, enabled = isQuickSimEnabled) {
            Text("Quick Sim")
        }
    }
}
