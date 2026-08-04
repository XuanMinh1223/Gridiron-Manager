package com.xuan.gridironmanager.ui.match

import com.xuan.gridironmanager.domain.model.Vector3D
import com.xuan.gridironmanager.domain.sim.match.GameState
import com.xuan.gridironmanager.domain.sim.movement.RunningPlayer

data class MatchUiState(
    val gameState: GameState = GameState(),
    val players: List<RunningPlayer> = emptyList(),
    val ballPosition: Vector3D? = null,
    val playByPlayText: String = "Ready for kick-off",
    val isPlayRunning: Boolean = false,
    val lineOfScrimmageY: Float? = null,
    val firstDownMarkerY: Float? = null
)
