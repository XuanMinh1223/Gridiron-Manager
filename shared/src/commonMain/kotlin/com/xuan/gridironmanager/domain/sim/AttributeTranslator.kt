package com.xuan.gridironmanager.domain.sim

object AttributeTranslator {
    private const val INCH_TO_YARD = 0.0277778f

    /**
     * Maps speed rating (0-99) to yards per second.
     * 99 = 10.0 yds/sec (Elite NFL speed)
     * 0 = 4.0 yds/sec
     */
    fun calculateSpeedYardsPerSec(speedRating: Int): Float {
        val rating = speedRating.coerceIn(0, 99).toFloat()
        return 4.0f + (rating / 99.0f) * 6.0f
    }

    /**
     * Calculates max catch height in yards based on height and jump rating.
     * 99 jump = 40 inches
     * 0 jump = 10 inches
     */
    fun calculateMaxCatchHeightYards(heightInches: Int, verticalJumpRating: Int): Float {
        val jumpRating = verticalJumpRating.coerceIn(0, 99).toFloat()
        val jumpInches = 10.0f + (jumpRating / 99.0f) * 30.0f
        return (heightInches + jumpInches) * INCH_TO_YARD
    }

    /**
     * Calculates radius of pass inaccuracy in yards.
     * variance scales with distance.
     * 99 accuracy = 0.5 yard variance at 20 yards.
     * 0 accuracy = 3.0 yard variance at 20 yards.
     */
    fun calculatePassAccuracyRadius(accuracyRating: Int, targetDistanceYards: Float): Float {
        val rating = accuracyRating.coerceIn(0, 99).toFloat()
        val varianceAt20 = 3.0f - (rating / 99.0f) * 2.5f
        return varianceAt20 * (targetDistanceYards / 20.0f)
    }
    
    /**
     * Helper to get standing height in yards.
     */
    fun calculateStandingHeightYards(heightInches: Int): Float {
        return heightInches * INCH_TO_YARD
    }
}
