package com.xuan.gridironmanager.domain.sim.match

data class KickResult(
    val endYardLine: Int,
    val description: String,
    val isTouchback: Boolean,
    val isOutOfBounds: Boolean,
    val isTouchdown: Boolean = false
)
