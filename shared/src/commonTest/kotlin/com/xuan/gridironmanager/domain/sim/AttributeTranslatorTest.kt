package com.xuan.gridironmanager.domain.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AttributeTranslatorTest {
    @Test
    fun testCalculateSpeedYardsPerSec() {
        assertEquals(10.0f, AttributeTranslator.calculateSpeedYardsPerSec(99), 0.001f)
        assertEquals(4.0f, AttributeTranslator.calculateSpeedYardsPerSec(0), 0.001f)
        assertEquals(7.0f, AttributeTranslator.calculateSpeedYardsPerSec(50), 0.1f)
    }

    @Test
    fun testCalculateMaxCatchHeightYards() {
        // 72 inches (6 feet) = 2.0 yards
        // 99 jump = 40 inches = 1.111 yards
        // Total = 3.111 yards
        val maxReach = AttributeTranslator.calculateMaxCatchHeightYards(72, 99)
        assertEquals(3.111f, maxReach, 0.001f)
    }

    @Test
    fun testCalculatePassAccuracyRadius() {
        // At 20 yards
        // 99 accuracy = 0.5 radius
        // 0 accuracy = 3.0 radius
        assertEquals(0.5f, AttributeTranslator.calculatePassAccuracyRadius(99, 20f), 0.001f)
        assertEquals(3.0f, AttributeTranslator.calculatePassAccuracyRadius(0, 20f), 0.001f)

        // At 40 yards, radius should double
        assertEquals(1.0f, AttributeTranslator.calculatePassAccuracyRadius(99, 40f), 0.001f)
        assertEquals(6.0f, AttributeTranslator.calculatePassAccuracyRadius(0, 40f), 0.001f)
    }

    @Test
    fun testFieldGoalRangeGrowsWithKickPower() {
        assertEquals(40, AttributeTranslator.calculateFieldGoalRangeYards(0))
        assertEquals(65, AttributeTranslator.calculateFieldGoalRangeYards(99))
    }

    @Test
    fun testFieldGoalMakeChance() {
        // Beyond range is impossible
        assertEquals(0f, AttributeTranslator.calculateFieldGoalMakeChance(distanceYds = 50, kickPower = 0, kickAccuracy = 99))

        val chipShot = AttributeTranslator.calculateFieldGoalMakeChance(distanceYds = 25, kickPower = 90, kickAccuracy = 90)
        val longKick = AttributeTranslator.calculateFieldGoalMakeChance(distanceYds = 55, kickPower = 90, kickAccuracy = 90)
        assertTrue(chipShot > 0.95f, "Chip shot chance was $chipShot")
        assertTrue(longKick in 0.5f..0.8f, "55-yard chance was $longKick")

        // Accuracy scales the whole curve
        assertTrue(
            AttributeTranslator.calculateFieldGoalMakeChance(40, 90, kickAccuracy = 99) >
                AttributeTranslator.calculateFieldGoalMakeChance(40, 90, kickAccuracy = 40),
        )
    }
}
