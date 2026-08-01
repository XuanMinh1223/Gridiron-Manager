package com.xuan.gridironmanager.domain.sim.match

data class PlayResult(
    val yardsGained: Int,
    val description: String,
    val isTouchdown: Boolean,
    val isTurnover: Boolean
)
