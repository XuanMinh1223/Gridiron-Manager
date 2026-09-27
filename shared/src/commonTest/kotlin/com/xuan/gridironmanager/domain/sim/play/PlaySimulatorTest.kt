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
    fun testKickoffReturnerTracksBallWhileSetupPlayersWait() {
        val snap = SnapBuilder.build(GameState.openingKickoff(), matchup, Playbook.KICKOFF, Playbook.BASE_MAN)
        val returner = snap.defense.single { it.slot == "KR" }
        val blocker = snap.defense.first { it.slot?.startsWith("KRB") == true }
        val coverage = snap.offense.first { it.slot?.startsWith("KC") == true }
        val start = returner.currentPos
        val blockerStart = blocker.currentPos
        val coverageStart = coverage.currentPos
        val simulator = PlaySimulator(snap, Random(4))
        repeat(40) { simulator.tick(tick) }

        assertTrue(returner.currentPos != start, "Deep returner should track the descending kickoff")
        assertEquals(blockerStart, blocker.currentPos)
        assertEquals(coverageStart, coverage.currentPos)
    }

    @Test
    fun testUnfieldedKickAtGoalLineIsNotDownedAtZero() {
        val snap = Snap(listOf(kicker(35f, kickPower = 77, kickAccuracy = 99)), emptyList(), PlayType.KICKOFF, 35, true)
        val results = (0 until 200).map { seed -> assertIs<PlayOutcome.Kick>(run(PlaySimulator(snap, Random(seed)))).result }
        val atGoalLine = results.filter { it.landingYardLine == 0 }

        assertTrue(atGoalLine.isNotEmpty())
        assertTrue(atGoalLine.all { it.isTouchback && it.outcomeType != KickOutcomeType.DOWNED })
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
    fun testKickoffBlockersMoveAndEngageAfterFielding() {
        val returner = RunningPlayer("R", Vector3D(FieldGeometry.CENTER_X, 88f, 0f), 7f, null, isOffense = false, slot = "KR")
        val blocker = RunningPlayer("B", Vector3D(FieldGeometry.CENTER_X, 67f, 0f), 8f, null, isOffense = false, slot = "KRB0")
        val coverage = RunningPlayer("C", Vector3D(FieldGeometry.CENTER_X, 35f, 0f), 8f, null, slot = "KC0")
        val snap = Snap(listOf(kicker(35f, 50, 99), coverage), listOf(returner, blocker), PlayType.KICKOFF, 35, true)
        val simulator = PlaySimulator(snap, Random(4))
        val startingY = blocker.currentPos.y
        repeat(15) { simulator.tick(tick) }
        assertEquals(startingY, blocker.currentPos.y, "Setup players cannot move before fielding")

        var moved = false
        repeat(250) {
            simulator.tick(tick)
            if (blocker.currentPos.y != startingY) moved = true
        }
        assertTrue(moved, "Kickoff blocker should pursue coverage after fielding")
    }

    @Test
    fun testKickoffCoverageMovesAtMostOneSpeedStepPerReturnTick() {
        val returner = RunningPlayer("R", Vector3D(FieldGeometry.CENTER_X, 95f, 0f), 7f, null, isOffense = false, slot = "KR")
        val coverage = RunningPlayer("C", Vector3D(FieldGeometry.CENTER_X, 60f, 0f), 8f, null, slot = "KC0")
        val snap = Snap(listOf(kicker(35f, 50, 99), coverage), listOf(returner), PlayType.KICKOFF, 35, true)
        val simulator = PlaySimulator(snap, Random(4))
        var previousY = coverage.currentPos.y
        repeat(250) {
            simulator.tick(tick)
            val movement = kotlin.math.abs(coverage.currentPos.y - previousY)
            assertTrue(movement <= coverage.speedYdsPerSec * tick + 0.001f, "Coverage moved $movement yards in one tick")
            previousY = coverage.currentPos.y
        }
    }

    @Test
    fun testKickoffSetupUnitsWaitThenBlockersRetreat() {
        val snap = SnapBuilder.build(GameState.openingKickoff(), matchup, Playbook.KICKOFF, Playbook.COVER_2)
        val kicker = snap.offense.single { it.slot == "K" }
        val accurateKicker = kicker.copy(attributes = kicker.attributes.copy(kickPower = 65, kickAccuracy = 99))
        val fieldableSnap = snap.copy(offense = snap.offense.map { if (it === kicker) accurateKicker else it })
        val blocker = fieldableSnap.defense.first { it.slot?.startsWith("KRB") == true }
        val coverage = fieldableSnap.offense.first { it.slot?.startsWith("KC") == true }
        val blockerStart = blocker.currentPos
        val coverageStart = coverage.currentPos
        val simulator = PlaySimulator(fieldableSnap, Random(4))
        repeat(60) { simulator.tick(tick) }

        assertEquals(blockerStart, blocker.currentPos)
        assertEquals(coverageStart, coverage.currentPos)
        repeat(150) { simulator.tick(tick) }
        assertTrue(blocker.currentPos.y > blockerStart.y, "Receiving blockers retreat toward their goal line after fielding")
        assertTrue(coverage.currentPos != coverageStart, "Coverage releases after fielding")
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
    fun testPuntReturnUnitTracksLandingAndGunnersDuringFlight() {
        val snap = SnapBuilder.build(GameState(yardLine = 35), matchup, Playbook.PUNT, Playbook.BASE_MAN)
        val returner = snap.defense.single { it.slot == "PR" }
        val vice = snap.defense.single { it.slot == "VL" }
        val laneBlocker = snap.defense.single { it.slot == "S" }
        val frontLineman = snap.defense.first { it.role == PlayerRole.PASS_RUSHER }
        val returnerStart = returner.currentPos
        val viceStart = vice.currentPos
        val laneStart = laneBlocker.currentPos
        val frontStart = frontLineman.currentPos
        val simulator = PlaySimulator(snap, Random(4))

        repeat(25) { simulator.tick(tick) }

        assertTrue(returner.currentPos != returnerStart, "Punt returner should track the kick during flight")
        assertTrue(vice.currentPos != viceStart, "Vice should track the gunner during flight")
        assertTrue(laneBlocker.currentPos != laneStart, "Return blockers should drop into lanes during flight")
        assertTrue(frontLineman.currentPos != frontStart, "Punt front line should drop into return lanes during flight")
    }

    @Test
    fun testPuntReturnerDoesNotTeleportToLandingSpot() {
        val returner = RunningPlayer("PR", Vector3D(FieldGeometry.CENTER_X, 75f, 0f), 0f, null, isOffense = false, slot = "PR")
        val snap = Snap(listOf(kicker(35f, 40, 99)), listOf(returner), PlayType.PUNT, 50, true)
        val result = assertIs<PlayOutcome.Kick>(run(PlaySimulator(snap, Random(4)))).result

        assertEquals(75f, returner.currentPos.y)
        assertTrue(result.outcomeType == KickOutcomeType.DOWNED || result.outcomeType == KickOutcomeType.TOUCHBACK)
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
    fun testReceiverCutsAlongBackOfEndZoneAndManDefenderFollows() {
        for (attackingUp in listOf(true, false)) {
            val direction = if (attackingUp) 1f else -1f
            val backLine = if (attackingUp) 109f else -9f
            val quarterback = RunningPlayer("QB", Vector3D(FieldGeometry.CENTER_X, if (attackingUp) 90f else 10f, 0f), 0f, null, role = PlayerRole.PASSER)
            val receiver = RunningPlayer(
                "WR", Vector3D(10f, backLine - direction, 0f), 8f,
                Route("Fade", listOf(Waypoint(10f, backLine + direction * 20f))), role = PlayerRole.RECEIVER,
            )
            val defender = RunningPlayer("CB", Vector3D(10f, backLine - 2f * direction, 0f), 8f, null, isOffense = false, role = PlayerRole.MAN_COVERAGE, coverageTargetId = "WR")
            val simulator = PlaySimulator(Snap(listOf(quarterback, receiver), listOf(defender), PlayType.PASS, 90, attackingUp, listOf("WR")), Random(3))
            repeat(4) { simulator.tick(tick) }
            val beforeCutX = receiver.currentPos.x
            val beforeDefenderX = defender.currentPos.x
            repeat(5) { simulator.tick(tick) }

            assertEquals(backLine, receiver.currentPos.y, 0.001f)
            assertTrue(receiver.currentPos.x > beforeCutX, "Receiver should cross the back of the end zone")
            assertTrue(defender.currentPos.x > beforeDefenderX, "Man defender should follow the crossing receiver")
        }
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
