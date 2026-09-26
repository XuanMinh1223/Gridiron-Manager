package com.xuan.gridironmanager.domain.sim.play

import com.xuan.gridironmanager.domain.model.PlayType
import com.xuan.gridironmanager.domain.model.PlayerAttributes
import com.xuan.gridironmanager.domain.model.Position
import com.xuan.gridironmanager.domain.model.Route
import com.xuan.gridironmanager.domain.model.Vector3D
import com.xuan.gridironmanager.domain.model.Waypoint
import com.xuan.gridironmanager.domain.sim.FieldGeometry
import com.xuan.gridironmanager.domain.sim.match.GameState
import com.xuan.gridironmanager.domain.sim.match.PlayOutcome
import com.xuan.gridironmanager.domain.sim.movement.PlayerRole
import com.xuan.gridironmanager.domain.sim.movement.RunningPlayer
import com.xuan.gridironmanager.domain.sim.playbook.Playbook
import com.xuan.gridironmanager.domain.sim.playbook.SnapBuilder
import com.xuan.gridironmanager.testMatchup
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PlaySimulatorTest {
    private val tick = 0.05f
    private val matchup = testMatchup()

    private fun run(simulator: PlaySimulator): PlayOutcome {
        while (true) {
            simulator.tick(tick)?.let { return it }
        }
    }

    private fun kicker(
        y: Float,
        kickPower: Int,
        kickAccuracy: Int = 50,
    ) = RunningPlayer(
        id = "K",
        currentPos = Vector3D(FieldGeometry.CENTER_X, y, 0f),
        speedYdsPerSec = 0f,
        route = null,
        position = Position.K,
        role = PlayerRole.KICKER,
        attributes = PlayerAttributes.AVERAGE.copy(kickPower = kickPower, kickAccuracy = kickAccuracy),
    )

    @Test
    fun testUntouchedRunnerScoresInsteadOfTimingOut() {
        val lineup = SnapBuilder.build(GameState(yardLine = 95), matchup, Playbook.INSIDE_ZONE, Playbook.BASE_MAN)
        val simulator = PlaySimulator(lineup.copy(defense = emptyList()))

        val outcome = assertIs<PlayOutcome.Scrimmage>(run(simulator))

        assertTrue(outcome.result.isTouchdown)
        assertTrue(simulator.elapsedSec < PlaySimulator.MAX_PLAY_DURATION_SEC)
    }

    @Test
    fun testKickDistanceIsMeasuredFromTheKicker() {
        // Kick power 0 = 30 yards. From the kicking team's 35 the ball lands at the receiving 35, returned 15 yards.
        for (isAttackingUp in listOf(true, false)) {
            val snap = Snap(listOf(kicker(if (isAttackingUp) 35f else 65f, kickPower = 0)), emptyList(), PlayType.KICKOFF, 35, isAttackingUp)

            val outcome = assertIs<PlayOutcome.Kick>(run(PlaySimulator(snap)))

            assertFalse(outcome.result.isTouchback)
            assertEquals(50, outcome.result.endYardLine)
        }
    }

    @Test
    fun testFieldGoalBeyondRangeFallsShort() {
        // Kick power 0 = 40-yard range; from the 50 the kick is 67 yards
        val snap = Snap(listOf(kicker(43f, kickPower = 0)), emptyList(), PlayType.FIELD_GOAL, 50, isAttackingUp = true)

        val outcome = assertIs<PlayOutcome.FieldGoal>(run(PlaySimulator(snap)))

        assertFalse(outcome.result.isGood)
        assertEquals(67, outcome.result.distanceYds)
        assertTrue(outcome.result.description.contains("short"))
    }

    @Test
    fun testEliteKickerMakesChipShots() {
        val makes =
            (0 until 50).count { seed ->
                val snap = Snap(listOf(kicker(83f, kickPower = 99, kickAccuracy = 99)), emptyList(), PlayType.FIELD_GOAL, 90, isAttackingUp = true)
                assertIs<PlayOutcome.FieldGoal>(run(PlaySimulator(snap, Random(seed)))).result.isGood
            }

        assertTrue(makes >= 45, "Made $makes of 50 27-yard kicks")
    }

    @Test
    fun testQbNeverTargetsLinemen() {
        // The only other offensive player is a wide open center, who is not in the progression
        val qb = RunningPlayer("QB", Vector3D(FieldGeometry.CENTER_X, 45f, 0f), 0f, null, position = Position.QB, role = PlayerRole.PASSER)
        val center = RunningPlayer("C", Vector3D(FieldGeometry.CENTER_X, 50f, 0f), 0f, null, position = Position.C, role = PlayerRole.BLOCKER)
        val snap = Snap(listOf(qb, center), emptyList(), PlayType.PASS, 50, isAttackingUp = true)

        val outcome = run(PlaySimulator(snap))

        assertEquals("Play whistled dead.", outcome.description)
    }

    @Test
    fun testElusiveCarriersBreakMoreTacklesAgainstPoorTacklers() {
        fun brokenTackleRate(tackle: Int): Float {
            val broken =
                (0 until 200).count { seed ->
                    val carrier =
                        RunningPlayer(
                            id = "RB",
                            currentPos = Vector3D(FieldGeometry.CENTER_X, 30f, 0f),
                            speedYdsPerSec = 9f,
                            route = Route("Dive", listOf(Waypoint(FieldGeometry.CENTER_X, 200f))),
                            role = PlayerRole.BALL_CARRIER,
                            attributes = PlayerAttributes.AVERAGE.copy(speed = 95, acceleration = 95, strength = 60),
                        )
                    val tackler =
                        RunningPlayer(
                            id = "LB",
                            currentPos = Vector3D(FieldGeometry.CENTER_X, 40f, 0f),
                            speedYdsPerSec = 0f,
                            route = null,
                            isOffense = false,
                            role = PlayerRole.ROUTE_ONLY,
                            attributes = PlayerAttributes.AVERAGE.copy(tackle = tackle),
                        )
                    val snap = Snap(listOf(carrier), listOf(tackler), PlayType.RUN, 30, isAttackingUp = true)
                    val description = run(PlaySimulator(snap, Random(seed))).description
                    description.contains("TOUCHDOWN") || description.contains("after a")
                }
            return broken / 200f
        }

        val againstPoorTackler = brokenTackleRate(tackle = 40)
        val againstGoodTackler = brokenTackleRate(tackle = 99)

        assertTrue(againstPoorTackler > againstGoodTackler, "Broke $againstPoorTackler vs $againstGoodTackler")
        assertTrue(againstGoodTackler < 0.25f, "A sure tackler should usually bring the runner down")
    }

    @Test
    fun testSeededPlayIsReproducible() {
        fun outcome(seed: Int): PlayOutcome {
            val snap = SnapBuilder.build(GameState(yardLine = 40), matchup, Playbook.CURL_FLAT, Playbook.COVER_2)
            return run(PlaySimulator(snap, Random(seed)))
        }

        assertEquals(outcome(7), outcome(7))
    }

    @Test
    fun testEveryScrimmagePlayFinishesAgainstEveryDefense() {
        for (play in Playbook.scrimmagePlays) {
            for (call in Playbook.defensiveCalls) {
                val snap = SnapBuilder.build(GameState(yardLine = 30), matchup, play, call)
                val simulator = PlaySimulator(snap, Random(3))

                val outcome = run(simulator)

                assertTrue(outcome.description != "Play whistled dead.", "${play.name} vs ${call.name} timed out")
            }
        }
    }
}
