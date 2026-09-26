package com.xuan.gridironmanager.ui.match

import com.xuan.gridironmanager.domain.sim.playbook.DefensiveCall
import com.xuan.gridironmanager.domain.sim.playbook.OffensivePlay

/** User interactions on the live match screen. */
interface MatchActions {
    fun selectOffensivePlay(play: OffensivePlay)

    fun selectDefensiveCall(call: DefensiveCall)

    fun setAutoCall(enabled: Boolean)

    fun snapBall()
}
