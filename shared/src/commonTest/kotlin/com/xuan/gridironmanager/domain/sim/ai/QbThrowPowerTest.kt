package com.xuan.gridironmanager.domain.sim.ai

import com.xuan.gridironmanager.domain.model.PlayerAttributes
import com.xuan.gridironmanager.domain.model.Position
import com.xuan.gridironmanager.domain.model.Vector3D
import com.xuan.gridironmanager.domain.sim.movement.RunningPlayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class QbThrowPowerTest {
    private val tick = 0.05f

    private fun player(
        id: String,
        y: Float,
        throwPower: Int = 50,
    ) = RunningPlayer(
        id = id,
        currentPos = Vector3D(26f, y, 0f),
        speedYdsPerSec = 8f,
        route = null,
        position = Position.WR,
        attributes = PlayerAttributes.AVERAGE.copy(throwPower = throwPower),
    )

    private fun qb(throwPower: Int) = player("QB", 20f, throwPower)

    /** The first throw when every receiver is wide open (no defenders). */
    private fun firstThrow(
        qb: RunningPlayer,
        receivers: List<RunningPlayer>,
    ): ThrowCommand {
        val brain = QbBrain(qb, receivers)
        repeat(100) { brain.evaluateTick(emptyList(), tick)?.let { command -> return command } }
        error("never threw")
    }

    @Test
    fun testStrongerArmGetsTheBallThereSooner() {
        val receiver = player("R", 45f)

        val strong = QbBrain(qb(99), listOf(receiver)).planThrow(receiver).totalFlightTimeSec
        val weak = QbBrain(qb(30), listOf(receiver)).planThrow(receiver).totalFlightTimeSec

        assertTrue(strong < weak, "99 power took $strong s, 30 power took $weak s")
    }

    @Test
    fun testAnAverageArmThrowsAtTheOldFixedSpeed() {
        val receiver = player("R", 40f) // 20 yards away

        assertEquals(1.0f, QbBrain(qb(50), listOf(receiver)).planThrow(receiver).totalFlightTimeSec, 0.001f)
    }

    @Test
    fun testReceiverBeyondTheArmIsNeverTheTarget() {
        val far = player("FAR", 70f) // 50 yards: past a 50-power arm (47.5), within a 99-power arm (59.75)
        val near = player("NEAR", 35f)

        val weakArmTarget = firstThrow(qb(50), listOf(far, near)).targetId
        val strongArmTarget = firstThrow(qb(99), listOf(far, near)).targetId

        assertEquals("NEAR", weakArmTarget)
        assertEquals("FAR", strongArmTarget)
    }

    @Test
    fun testNobodyInRangeMeansAThrowAwayWhenOutOfTime() {
        val far = player("FAR", 75f)
        val brain = QbBrain(qb(50), listOf(far))
        var command: ThrowCommand? = null
        var elapsed = 0f

        while (command == null && elapsed < 3f) {
            elapsed += tick
            command = brain.evaluateTick(emptyList(), tick)
        }

        assertTrue(assertNotNull(command).isThrowAway)
        assertEquals(QbBrain.DEFAULT_FORCE_THROW_SEC, elapsed, tick + 0.001f)
    }
}
