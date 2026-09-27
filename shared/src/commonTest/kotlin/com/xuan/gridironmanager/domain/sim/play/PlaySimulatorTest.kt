package com.xuan.gridironmanager.domain.sim.play

import com.xuan.gridironmanager.domain.model.PlayType
import com.xuan.gridironmanager.domain.model.PlayerAttributes
import com.xuan.gridironmanager.domain.model.Position
import com.xuan.gridironmanager.domain.model.Route
import com.xuan.gridironmanager.domain.model.Vector3D
import com.xuan.gridironmanager.domain.model.Waypoint
import com.xuan.gridironmanager.domain.sim.FieldGeometry
import com.xuan.gridironmanager.domain.sim.match.GameState
import com.xuan.gridironmanager.domain.sim.match.KickOutcomeType
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
        // Kick power 0 = 30 yards. From the kicking team's 35 the ball lands at the receiving 35.
        for (isAttackingUp in listOf(true, false)) {
            val snap = Snap(listOf(kicker(if (isAttackingUp) 35f else 65f, kickPower = 0)), emptyList(), PlayType.KICKOFF, 35, isAttackingUp)

            val outcome = assertIs<PlayOutcome.Kick>(run(PlaySimulator(snap)))

            assertFalse(outcome.result.isTouchback)
            assertTrue(outcome.result.endYardLine in 0..99)
            assertTrue(outcome.result.intendedLandingYardLine in 0..100)
        }
    }

    @Test
    fun testReceivingUnitDoesNotMoveBeforeKickIsFielded() {
        val returner = RunningPlayer(
            id = "R",
            currentPos = Vector3D(FieldGeometry.CENTER_X, 98f, 0f),
            speedYdsPerSec = 8f,
            route = null,
            isOffense = false,
            role = PlayerRole.ROUTE_ONLY,
            attributes = PlayerAttributes.AVERAGE,
        )
        val snap = Snap(listOf(kicker(35f, kickPower = 0)), listOf(returner), PlayType.KICKOFF, 35, true)
        val simulator = PlaySimulator(snap)
        simulator.tick(0.5f)

        assertEquals(98f, returner.currentPos.y)
    }

    @Test
    fun testKickReturnRunsForMultipleTicksAndMovesReturner() {
        val returner =
            RunningPlayer(
                id = "R",
                currentPos = Vector3D(FieldGeometry.CENTER_X, 70f, 0f),
                speedYdsPerSec = 8f,
                route = null,
                isOffense = false,
                role = PlayerRole.ROUTE_ONLY,
                attributes = PlayerAttributes.AVERAGE,
            )
        val snap = Snap(listOf(kicker(35f, kickPower = 60, kickAccuracy = 99)), listOf(returner), PlayType.KICKOFF, 35, true)
        val simulator = PlaySimulator(snap, Random(4))
        var moved = false
        var outcome: PlayOutcome? = null
        repeat(160) {
            val before = returner.currentPos.y
            if (outcome == null) outcome = simulator.tick(tick)
            if (returner.currentPos.y < before) moved = true
        }
        assertTrue(moved || assertIs<PlayOutcome.Kick>(outcome).result.outcomeType in setOf(KickOutcomeType.TOUCHBACK, KickOutcomeType.DOWNED), "Returner should run or the kick should be downed")
        assertIs<PlayOutcome.Kick>(outcome ?: run(simulator))
    }

    @Test
    fun testKickAccuracyAddsLateralAndLongitudinalLandingError() {
        fun landing(seed: Int, accuracy: Int): Vector3D {
            val snap = Snap(listOf(kicker(35f, kickPower = 50, kickAccuracy = accuracy)), emptyList(), PlayType.KICKOFF, 35, true)
            val simulator = PlaySimulator(snap, Random(seed))
            run(simulator)
            return simulator.ballPosition!!
        }

        val inaccurate = landing(7, 0)
        val accurate = landing(7, 99)

        assertTrue(inaccurate.x != FieldGeometry.CENTER_X)
        assertTrue(kotlin.math.abs(inaccurate.x - FieldGeometry.CENTER_X) > kotlin.math.abs(accurate.x - FieldGeometry.CENTER_X))
        assertTrue(inaccurate.y != accurate.y)
    }

    @Test
    fun testSeededPuntWithoutReturnerBouncesOrIsTouchback() {
        val snap = Snap(listOf(kicker(35f, kickPower = 50, kickAccuracy = 99)), emptyList(), PlayType.PUNT, 50, true)
        val result = assertIs<PlayOutcome.Kick>(run(PlaySimulator(snap, Random(7)))).result
        assertTrue(result.outcomeType == KickOutcomeType.DOWNED || result.outcomeType == KickOutcomeType.TOUCHBACK)
        assertEquals(result, assertIs<PlayOutcome.Kick>(run(PlaySimulator(snap.copy(offense = listOf(kicker(35f, 50, 99))), Random(7)))).result)
    }

    @Test
    fun testSeededPuntFairCatchAndMuffAreTyped() {
        fun punt(seed: Int): KickOutcomeType {
            val receiver = RunningPlayer("PR", Vector3D(FieldGeometry.CENTER_X, 81f, 0f), 8f, null, isOffense = false, slot = "PR")
            val cover = RunningPlayer("C", Vector3D(FieldGeometry.CENTER_X, 81f, 0f), 0f, null)
            val snap = Snap(listOf(kicker(35f, 40, 99), cover), listOf(receiver), PlayType.PUNT, 50, true)
            return assertIs<PlayOutcome.Kick>(run(PlaySimulator(snap, Random(seed)))).result.outcomeType
        }
        val types = (0 until 120).map(::punt).toSet()
        assertTrue(KickOutcomeType.FAIR_CATCH in types, "$types")
        assertTrue(KickOutcomeType.MUFF_RECOVERED in types, "$types")
        assertEquals(punt(12), punt(12))
    }

    @Test
    fun testBlockedPuntProducesRecoveryOutcome() {
        val rusher = RunningPlayer("D", Vector3D(FieldGeometry.CENTER_X, 20f, 0f), 0f, null, isOffense = false, role = PlayerRole.PASS_RUSHER)
        val snap = Snap(listOf(kicker(20f, 50)), listOf(rusher), PlayType.PUNT, 35, true)
        val result = assertIs<PlayOutcome.Kick>(run(PlaySimulator(snap, Random(3)))).result
        assertEquals(KickOutcomeType.BLOCKED_RECOVERED, result.outcomeType)
        assertFalse(result.recoveredByKickingTeam)
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
