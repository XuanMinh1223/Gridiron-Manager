package com.xuan.gridironmanager.domain.sim.play

import com.xuan.gridironmanager.domain.model.PlayType
import com.xuan.gridironmanager.domain.model.PlayerAttributes
import com.xuan.gridironmanager.domain.model.Position
import com.xuan.gridironmanager.domain.model.Vector3D
import com.xuan.gridironmanager.domain.model.VerticalReach
import com.xuan.gridironmanager.domain.sim.match.PlayOutcome
import com.xuan.gridironmanager.domain.sim.movement.PlayerRole
import com.xuan.gridironmanager.domain.sim.movement.RunningPlayer
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PlaySimulatorRatingsTest {
    private val tick = 0.05f

    private fun qb() =
        RunningPlayer(
            id = "QB",
            currentPos = Vector3D(26f, 25f, 0f),
            speedYdsPerSec = 7f,
            route = null,
            position = Position.QB,
            role = PlayerRole.PASSER,
            slot = "QB",
            attributes = PlayerAttributes.AVERAGE.copy(throwAccuracy = 99),
        )

    private fun receiver(catching: Int) =
        RunningPlayer(
            id = "WR",
            currentPos = Vector3D(26f, 50f, 0f),
            speedYdsPerSec = 8f,
            route = null,
            position = Position.WR,
            role = PlayerRole.RECEIVER,
            slot = "WR",
            attributes = PlayerAttributes.AVERAGE.copy(catching = catching),
        )

    /** A stationary defender standing in the throwing lane, tall enough to get a hand on the ball. */
    private fun laneDefender(
        catching: Int,
        awareness: Int,
    ) = RunningPlayer(
        id = "DB",
        currentPos = Vector3D(26f, 32f, 0f),
        speedYdsPerSec = 8f,
        route = null,
        isOffense = false,
        position = Position.CB,
        role = PlayerRole.ROUTE_ONLY,
        slot = "DB",
        verticalReach = VerticalReach(heightYards = 3f, verticalLeapYards = 2f),
        attributes = PlayerAttributes.AVERAGE.copy(catching = catching, awareness = awareness),
    )

    private fun throwTo(
        receiver: RunningPlayer,
        defenders: List<RunningPlayer>,
        seed: Int,
    ): String {
        val snap = Snap(listOf(qb(), receiver), defenders, PlayType.PASS, 30, true, progression = listOf(receiver.id))
        val simulator = PlaySimulator(snap, Random(seed))
        while (true) {
            simulator.tick(tick)?.let { return assertIs<PlayOutcome.Scrimmage>(it).result.description }
        }
    }

    private fun interceptions(
        catching: Int,
        awareness: Int,
        plays: Int = 300,
    ) = (1..plays).count { throwTo(receiver(75), listOf(laneDefender(catching, awareness)), it) == "INTERCEPTED!" }

    @Test
    fun testLanePickChanceFollowsTheDefendersRatings() {
        assertEquals(0.40f, PlaySimulator(Snap(emptyList(), emptyList(), PlayType.PASS, 30, true)).interceptionChance(40, 80), 0.001f)
        val simulator = PlaySimulator(Snap(emptyList(), emptyList(), PlayType.PASS, 30, true))
        assertTrue(simulator.interceptionChance(60, 99) > simulator.interceptionChance(40, 80))
        assertTrue(simulator.interceptionChance(40, 80) > simulator.interceptionChance(20, 60))
        assertEquals(0.595f, simulator.interceptionChance(99, 99), 0.001f)
        assertEquals(0.225f, simulator.interceptionChance(20, 30), 0.001f)
    }

    @Test
    fun testBallHawkingDefendersPickOffMoreThrows() {
        val skilled = interceptions(catching = 90, awareness = 99)
        val weak = interceptions(catching = 10, awareness = 40)

        assertTrue(skilled > weak, "skilled DB had $skilled picks, weak DB had $weak")
        assertTrue(skilled in 100..240, "expected roughly 57% of 300, got $skilled")
        assertTrue(weak in 30..120, "expected roughly 22% of 300, got $weak")
    }

    @Test
    fun testFumblesAreRecoveredByTheSideWithMoreNearby() {
        fun at(
            id: String,
            isOffense: Boolean,
            dx: Float,
        ) = RunningPlayer(
            id,
            Vector3D(26f + dx, 40f, 0f),
            7f,
            null,
            isOffense = isOffense,
            position = Position.LB,
            slot = id,
        )

        val carrier = at("RB", true, 0f)
        val defenders = listOf(at("D1", false, 1f), at("D2", false, -2f), at("D3", false, 3f))
        val simulator = PlaySimulator(Snap(listOf(carrier, at("OL", true, 2f)), defenders, PlayType.RUN, 30, true), Random(3))

        val recoveries = (1..1_000).map { simulator.resolveFumbleRecovery(carrier) }

        val defenseShare = recoveries.count { it.defenseRecovers } / 1_000f
        assertTrue(defenseShare > 0.55f, "3 defenders against 2 offensive players should favour the defense, share was $defenseShare")
        assertTrue(recoveries.filter { it.defenseRecovers }.all { it.recoverer?.id == "D1" }, "the nearest defender gets the ball")
        assertTrue(recoveries.filter { !it.defenseRecovers }.all { it.recoverer?.id == "RB" }, "the carrier is the nearest offensive player")
    }

    @Test
    fun testCatchChanceIsClampedBelowCertainty() {
        val simulator = PlaySimulator(Snap(emptyList(), emptyList(), PlayType.PASS, 30, true))

        assertEquals(0.99f, simulator.catchChance(99), 0.0001f)
        assertEquals(0.68f, simulator.catchChance(0), 0.0001f)
        // 75-catching is untouched by the clamp
        assertEquals(0.93f, simulator.catchChance(75), 0.0001f)
    }

    @Test
    fun testEvenAPerfectReceiverDropsAPassNowAndThen() {
        val drops = (1..1_500).count { throwTo(receiver(99), emptyList(), it) == "Dropped pass!" }

        assertTrue(drops > 0, "a 99-catching receiver should still drop about 1% of catchable passes")
        assertTrue(drops < 100, "but not often: $drops drops in 1500")
    }
}
