package com.xuan.gridironmanager.domain.model

data class VerticalReach(
    val heightYards: Float = 2.0f,
    val verticalLeapYards: Float = 0.8f
) {
    val maxCatchHeightYards: Float get() = heightYards + verticalLeapYards
}
