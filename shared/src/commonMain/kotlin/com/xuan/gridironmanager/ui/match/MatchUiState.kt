package com.xuan.gridironmanager.ui.match

import com.xuan.gridironmanager.domain.model.Vector3D
import com.xuan.gridironmanager.domain.sim.match.GameState
import com.xuan.gridironmanager.domain.sim.movement.RunningPlayer
import com.xuan.gridironmanager.domain.sim.playbook.DefensiveCall
import com.xuan.gridironmanager.domain.sim.playbook.OffensivePlay
import com.xuan.gridironmanager.ui.match.overlay.TacticalOverlay

data class MatchUiState(
    val gameState: GameState = GameState.openingKickoff(),
    val players: List<RunningPlayer> = emptyList(),
    val ballPosition: Vector3D? = null,
    val playByPlayText: String = "Ready for kick-off",
    val isMatchReady: Boolean = false,
    val isPlayRunning: Boolean = false,
    val lineOfScrimmageY: Float? = null,
    val firstDownMarkerY: Float? = null,
    val playCall: PlayCallState = PlayCallState(),
    val simSpeed: SimSpeed = SimSpeed.X1,
    val showAssignments: Boolean = true,
    val isDebugMode: Boolean = false,
    val overlay: TacticalOverlay = TacticalOverlay.NONE,
    /** Direction of the currently previewed/live offense in world coordinates. */
    val isAttackingUp: Boolean = true,
)

/** The user's play-calling options for the next snap. */
data class PlayCallState(
    val isUserOnOffense: Boolean = true,
    /** When on, the CPU calls the user's plays too. */
    val isAutoCall: Boolean = false,
    /** Offensive plays the user can choose from; empty when the user is on defense. */
    val offenseOptions: List<OffensivePlay> = emptyList(),
    /** Defensive calls the user can choose from; empty when the user is on offense or the call doesn't matter (kickoffs). */
    val defenseOptions: List<DefensiveCall> = emptyList(),
    val selectedOffense: OffensivePlay? = null,
    val selectedDefense: DefensiveCall? = null,
) {
    /** Whether there is a meaningful choice for the user to make before the snap. */
    val isUserChoosing: Boolean
        get() = !isAutoCall && (if (isUserOnOffense) offenseOptions.size > 1 else defenseOptions.isNotEmpty())
}
