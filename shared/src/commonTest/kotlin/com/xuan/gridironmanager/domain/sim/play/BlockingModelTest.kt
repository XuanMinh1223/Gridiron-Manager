package com.xuan.gridironmanager.domain.sim.play

import com.xuan.gridironmanager.domain.model.PlayType
import com.xuan.gridironmanager.domain.model.PlayerAttributes
import com.xuan.gridironmanager.domain.model.Vector3D
import com.xuan.gridironmanager.domain.sim.movement.PlayerRole
import com.xuan.gridironmanager.domain.sim.movement.RunningPlayer
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlockingModelTest {
    private val tick = 0.05f
    private val quarterback = Vector3D(26f, 45f, 0f)

    private fun blocker(
        id: String,
        x: Float,
        blockPass: Int = 80,
        strength: Int = 80,
    ) = RunningPlayer(
        id = id,
        currentPos = Vector3D(x, 50f, 0f),
        speedYdsPerSec = 7f,
        route = null,
        role = PlayerRole.BLOCKER,
        attributes = PlayerAttributes.AVERAGE.copy(blockPass = blockPass, strength = strength),
    )

    private fun rusher(
        id: String,
        x: Float,
        y: Float = 51f,
        rating: Int = 80,
        role: PlayerRole = PlayerRole.PASS_RUSHER,
    ) = RunningPlayer(
        id = id,
        currentPos = Vector3D(x, y, 0f),
        speedYdsPerSec = 7f,
        route = null,
        isOffense = false,
        role = role,
        attributes = PlayerAttributes.AVERAGE.copy(strength = rating, speed = rating, tackle = rating),
    )

    /** Seconds until [defender] is no longer blocked. */
    private fun secondsToShed(
        model: BlockingModel,
        defender: RunningPlayer,
    ): Float {
        var elapsed = 0f
        while (model.isBlocked(defender) && elapsed < 20f) {
            model.tick(tick) { quarterback }
            elapsed += tick
        }
        return elapsed
    }

    @Test
    fun testLinemenAreEngagedFromTheSnap() {
        val guard = blocker("G", 26f)
        val tackle = rusher("DT", 26.5f)

        val model = BlockingModel(listOf(guard), listOf(tackle), PlayType.PASS, Random(1))

        assertTrue(model.isBlocked(tackle))
        assertEquals("DT", guard.blockingId)
    }

    @Test
    fun testRusherBeyondContactRangeMustCloseBeforeBeingBlocked() {
        val guard = blocker("G", 26f)
        val tackle = rusher("DT", 26f, y = 52f)
        val model = BlockingModel(listOf(guard), listOf(tackle), PlayType.PASS, Random(1))

        assertFalse(model.isBlocked(tackle))
        assertNull(guard.blockingId)
        model.tick(tick) { quarterback }
        assertFalse(model.isBlocked(tackle))
    }

    @Test
    fun testBetterRusherWinsSooner() {
        fun shedTime(rating: Int): Float {
            val tackle = rusher("DT", 26.5f, rating = rating)
            val model = BlockingModel(listOf(blocker("G", 26f)), listOf(tackle), PlayType.PASS, Random(7))
            return secondsToShed(model, tackle)
        }

        assertTrue(shedTime(rating = 99) < shedTime(rating = 60))
    }

    @Test
    fun testDoubleTeamHoldsLonger() {
        val single = rusher("DT", 26.5f)
        val double = rusher("DT", 26.5f)

        val oneOnOne = secondsToShed(BlockingModel(listOf(blocker("G", 26f)), listOf(single), PlayType.PASS, Random(3)), single)
        val doubled = secondsToShed(BlockingModel(listOf(blocker("G", 26f), blocker("C", 25f)), listOf(double), PlayType.PASS, Random(3)), double)

        assertTrue(doubled > oneOnOne)
    }

    @Test
    fun testBlitzerIsFreeWhenTheLineIsOutnumbered() {
        val tackle = rusher("DT", 26.5f)
        val blitzer = rusher("LB", 30f, y = 54f, role = PlayerRole.BLITZER)

        val model = BlockingModel(listOf(blocker("G", 26f)), listOf(tackle, blitzer), PlayType.PASS, Random(1))

        assertTrue(model.isBlocked(tackle), "Linemen are picked up before blitzers")
        assertFalse(model.isBlocked(blitzer))
    }

    @Test
    fun testSpareBlockersClimbToTheSecondLevelOnRuns() {
        val guard = blocker("G", 26f)
        val center = blocker("C", 25f)
        val linebacker = rusher("MLB", 25f, y = 54f, role = PlayerRole.ZONE_COVERAGE)
        val model = BlockingModel(listOf(guard, center), listOf(rusher("DT", 26.5f), linebacker), PlayType.RUN, Random(1))

        assertFalse(model.isBlocked(linebacker), "Not engaged until the blocker gets there")
        repeat(20) { model.tick(tick) { quarterback } }

        assertTrue(model.isBlocked(linebacker))
    }

    @Test
    fun testEngagedRusherCollapsesThePocketWithTheBlockerInFront() {
        val guard = blocker("G", 26f)
        val tackle = rusher("DT", 26f, rating = 40)
        val model = BlockingModel(listOf(guard), listOf(tackle), PlayType.PASS, Random(1))

        repeat(10) { model.tick(tick) { quarterback } }

        assertTrue(model.isBlocked(tackle))
        assertTrue(tackle.currentPos.y < 51f, "The rusher drives towards the quarterback")
        assertTrue(guard.currentPos.y < tackle.currentPos.y, "The blocker stays between rusher and quarterback")
    }

    @Test
    fun testShedBlockClearsTheBlockersEngagement() {
        val guard = blocker("G", 26f, blockPass = 30)
        val edge = rusher("DE", 26.5f, rating = 99)
        val model = BlockingModel(listOf(guard), listOf(edge), PlayType.PASS, Random(1))

        secondsToShed(model, edge)

        assertNull(guard.blockingId)
    }
}
