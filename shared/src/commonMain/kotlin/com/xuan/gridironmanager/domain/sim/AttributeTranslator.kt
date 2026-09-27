package com.xuan.gridironmanager.domain.sim

object AttributeTranslator {
    private const val INCH_TO_YARD = 0.0277778f
    private const val CHIP_SHOT_YDS = 20

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
    fun calculateMaxCatchHeightYards(
        heightInches: Int,
        verticalJumpRating: Int,
    ): Float {
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
    fun calculatePassAccuracyRadius(
        accuracyRating: Int,
        targetDistanceYards: Float,
    ): Float {
        val rating = accuracyRating.coerceIn(0, 99).toFloat()
        val varianceAt20 = 3.0f - (rating / 99.0f) * 2.5f
        return varianceAt20 * (targetDistanceYards / 20.0f)
    }

    /**
     * Maps kick power (0-99) to distance in yards.
     * 99 = 75 yards
     * 0 = 30 yards
     */
    fun calculateKickDistanceYards(kickPower: Int): Float {
        val rating = kickPower.coerceIn(0, 99).toFloat()
        return 30.0f + (rating / 99.0f) * 45.0f
    }

    /** Placement error in yards. Accurate kickers produce a much tighter landing distribution. */
    fun calculateKickPlacementError(kickAccuracy: Int, kickDistanceYards: Float): Float {
        val rating = kickAccuracy.coerceIn(0, 99).toFloat()
        val variance = 9.0f - (rating / 99.0f) * 7.5f
        return variance * (kickDistanceYards / 50.0f).coerceAtLeast(0.5f)
    }

    /**
     * Maps kick power (0-99) to hangtime in seconds.
     * 99 = 5.2s
     * 0 = 3.5s
     */
    fun calculateHangtimeSec(kickPower: Int): Float {
        val rating = kickPower.coerceIn(0, 99).toFloat()
        return 3.5f + (rating / 99.0f) * 1.7f
    }

    /**
     * Helper to get standing height in yards.
     */
    fun calculateStandingHeightYards(heightInches: Int): Float = heightInches * INCH_TO_YARD

    /**
     * Longest field goal a kicker can make, in yards.
     * 99 = 65 yards
     * 0 = 40 yards
     */
    fun calculateFieldGoalRangeYards(kickPower: Int): Int {
        val rating = kickPower.coerceIn(0, 99).toFloat()
        return (40.0f + (rating / 99.0f) * 25.0f).toInt()
    }

    /**
     * Chance (0..1) of making a field goal from [distanceYds].
     * Chip shots are near-automatic, falling off towards the edge of the kicker's range; accuracy scales the whole curve.
     */
    fun calculateFieldGoalMakeChance(
        distanceYds: Int,
        kickPower: Int,
        kickAccuracy: Int,
    ): Float {
        val range = calculateFieldGoalRangeYards(kickPower)
        if (distanceYds > range) return 0f

        val difficulty = ((distanceYds - CHIP_SHOT_YDS).toFloat() / (range - CHIP_SHOT_YDS)).coerceIn(0f, 1f)
        val accuracy = kickAccuracy.coerceIn(0, 99) / 99.0f
        return (0.99f - 0.38f * difficulty * difficulty) * (0.9f + 0.1f * accuracy)
    }
}
