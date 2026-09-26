package com.xuan.gridironmanager.ui.match

import com.xuan.gridironmanager.domain.sim.playbook.DefensiveCall
import com.xuan.gridironmanager.domain.sim.playbook.OffensivePlay

/** User interactions on the live match screen. */
interface MatchActions {
    fun selectOffensivePlay(play: OffensivePlay)

    fun selectDefensiveCall(call: DefensiveCall)

    fun setAutoCall(enabled: Boolean)

    fun snapBall()

    fun setShowAssignments(enabled: Boolean)

    fun setDebugMode(enabled: Boolean)

    fun resetGame()

    fun setSimSpeed(speed: SimSpeed)

    /** Instantly plays out the rest of the game, with the CPU calling plays for both teams. */
    fun quickSim()
}
