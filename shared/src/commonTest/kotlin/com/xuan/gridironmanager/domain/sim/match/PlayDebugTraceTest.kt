package com.xuan.gridironmanager.domain.sim.match

import com.xuan.gridironmanager.domain.model.PlayerAttributes
import com.xuan.gridironmanager.domain.model.Position
import com.xuan.gridironmanager.domain.model.Vector3D
import com.xuan.gridironmanager.domain.sim.MatchSimulator
import com.xuan.gridironmanager.domain.sim.ai.QbBrain
import com.xuan.gridironmanager.domain.sim.ai.ThrowCommand
import com.xuan.gridironmanager.domain.sim.movement.RunningPlayer
import com.xuan.gridironmanager.testMatchup
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlayDebugTraceTest {
    private class Play(
        val outcome: PlayOutcome,
        val block: String?,
    ) {
        val result: PlayResult? get() = (outcome as? PlayOutcome.Scrimmage)?.result
        val statsLine: String? get() = block?.lines()?.last()
    }

    private class Game(
        val simulator: MatchSimulator,
        val plays: List<Play>,
        val finalState: GameState,
    )

    /** Plays a seeded CPU game through [MatchSimulator.runPlay], with debugging on or off. */
    private fun simulate(
        seed: Int,
        debug: Boolean,
    ): Game {
        val simulator = MatchSimulator(testMatchup(seed), Random(seed), seed = seed.toLong()).also { it.debugEnabled = debug }
        val plays = mutableListOf<Play>()
        var state = GameState.openingKickoff()
        while (!state.isGameOver) {
            val offense = simulator.playCaller.callOffense(state)
            val record = simulator.runPlay(state, offense, simulator.playCaller.callDefense(state))
            plays += Play(record.outcome, simulator.debugLog.lastOrNull())
            state = record.nextState
        }
        return Game(simulator, plays, state)
    }

    /** The first play across a few seeded games that satisfies [matches], so each test shows a real play of its kind. */
    private fun findPlay(matches: (Play) -> Boolean): Play {
        for (seed in 1..20) {
            simulate(seed, debug = true).plays.firstOrNull(matches)?.let { return it }
        }
        error("no play matched in 20 seeded games")
    }

    // --- Whole-play behavior, through the simulator -------------------------------------------------------------

    @Test
    fun testSackPlayTracesTheSackAndIsNotARush() {
        val play = findPlay { it.result?.isSack == true }
        val block = assertNotNull(play.block)

        assertTrue("SACKED by" in block, block)
        assertTrue("→ SCRAMBLE" !in block, "a sack is not a scramble")
        assertTrue(play.result!!.debugTrace.isNotEmpty())
        val stats = play.statsLine!!
        assertTrue("sack" in stats, stats)
        assertTrue("rushAtt QB" !in stats, "the tracker must not book a sack as a QB rush: $stats")
    }

    @Test
    fun testScramblePlayTracesTheRollAndIsARush() {
        val play = findPlay { it.result?.isScramble == true }
        val block = assertNotNull(play.block)

        assertTrue("scramble roll" in block, block)
        assertTrue("→ SCRAMBLE" in block, block)
        assertTrue(play.result!!.isScramble)
        assertTrue("rushAtt QB" in play.statsLine!!, play.statsLine)
    }

    @Test
    fun testCompletionTracesTheThrowAndTheCatch() {
        val play = findPlay { it.result?.isPassComplete == true }
        val block = assertNotNull(play.block)

        assertTrue("throw →" in block, block)
        assertTrue("CAUGHT" in block, block)
        val stats = play.statsLine!!
        assertTrue("passAtt" in stats && "completion" in stats, stats)
    }

    @Test
    fun testTheSackStaysConsistentWithTheTrackersOwnWords() {
        val stats = findPlay { it.result?.isSack == true }.statsLine!!

        // Vocabulary: slots, not names; a sack is credited to the defender, and counts as a tackle for him too
        assertTrue(Regex("sack [A-Z0-9]+").containsMatchIn(stats), stats)
        assertTrue("tackle" in stats, stats)
    }

    @Test
    fun testKickPlaysAreLoggedWithTheirResult() {
        val game = simulate(1, debug = true)

        val kickoff = assertNotNull(game.simulator.debugLog.firstOrNull())
        assertTrue("kick: " in kickoff, kickoff)
        assertTrue(kickoff.contains("landing"), kickoff)
        // The defense that lined up on a kickoff is the return unit, not whatever scrimmage call the CPU had picked
        assertTrue("DEF: Kick Return" in kickoff, kickoff)
    }

    @Test
    fun testOneBlockPerPlayNumberedFromOne() {
        val game = simulate(2, debug = true)
        val log = game.simulator.debugLog

        assertEquals(game.plays.size, log.size)
        assertTrue(log.first().startsWith("--- Play 1 "))
        assertTrue(log.last().startsWith("--- Play ${log.size} "))
        assertTrue(log.all { "seed 2 ---" in it })
        val text = game.simulator.debugLogText
        assertEquals(log.size, text.split("\n\n").size, "blocks never contain a blank line")
    }

    // --- Debug off ------------------------------------------------------------------------------------------------

    @Test
    fun testDebugOffRecordsNothing() {
        val game = simulate(3, debug = false)

        assertEquals("", game.simulator.debugLogText)
        assertTrue(game.simulator.debugLog.isEmpty())
        val scrimmage = game.plays.mapNotNull { it.result }
        assertTrue(scrimmage.isNotEmpty())
        assertTrue(scrimmage.all { it.debugTrace.isEmpty() }, "no trace is built when debugging is off")
    }

    @Test
    fun testTurningDebugOffMidGameStopsRecording() {
        val simulator = MatchSimulator(testMatchup(4), Random(4)).also { it.debugEnabled = true }
        var state = GameState.openingKickoff()
        repeat(6) { state = simulator.runPlay(state, simulator.playCaller.callOffense(state), simulator.playCaller.callDefense(state)).nextState }
        val loggedWhileOn = simulator.debugLog.size

        simulator.debugEnabled = false
        repeat(6) { state = simulator.runPlay(state, simulator.playCaller.callOffense(state), simulator.playCaller.callDefense(state)).nextState }

        assertEquals(6, loggedWhileOn)
        assertEquals(loggedWhileOn, simulator.debugLog.size)
    }

    // --- The two properties that make the log trustworthy -----------------------------------------------------------

    @Test
    fun testTheLogIsIdenticalAcrossRunsOfTheSameSeed() {
        val first = simulate(5, debug = true).simulator.debugLogText
        val second = simulate(5, debug = true).simulator.debugLogText

        assertTrue(first.isNotEmpty())
        assertEquals(first, second)
    }

    @Test
    fun testDebuggingDoesNotChangeTheGame() {
        for (seed in 1..3) {
            val on = simulate(seed, debug = true)
            val off = simulate(seed, debug = false)

            assertEquals(off.finalState.homeScore, on.finalState.homeScore, "home score, seed $seed")
            assertEquals(off.finalState.awayScore, on.finalState.awayScore, "away score, seed $seed")
            assertEquals(off.plays.map { it.outcome.description }, on.plays.map { it.outcome.description }, "play-by-play, seed $seed")
            assertEquals(off.simulator.gameStats, on.simulator.gameStats, "box score, seed $seed")
        }
    }

    // --- QbBrain trace points, in isolation ---------------------------------------------------------------------

    private val tick = 0.05f

    private fun player(
        id: String,
        x: Float,
        y: Float,
    ) = RunningPlayer(
        id = id,
        currentPos = Vector3D(x, y, 0f),
        speedYdsPerSec = 8f,
        route = null,
        position = Position.WR,
        slot = id,
        attributes = PlayerAttributes.AVERAGE,
    )

    private val qb = player("QB", 26f, 20f)
    private val receivers = listOf(player("R1", 10f, 35f), player("R2", 26f, 38f), player("R3", 42f, 35f))
    private val coverage = receivers.map { player("D_${it.id}", it.currentPos.x, it.currentPos.y) }

    /** Runs a brain until it commits, returning its trace lines and the command it gave. */
    private fun runBrain(
        defenders: List<RunningPlayer>,
        trace: PlayDebugTrace?,
    ): Pair<List<String>, ThrowCommand?> {
        val brain = QbBrain(qb, receivers, trace = trace)
        var command: ThrowCommand? = null
        var ticks = 0
        while (command == null && ticks < 80) {
            ticks++
            command = brain.evaluateTick(defenders, tick)
        }
        return (trace?.lines() ?: emptyList()) to command
    }

    @Test
    fun testOpenReceiverTraceReadsInOrder() {
        val (lines, command) = runBrain(emptyList(), PlayDebugTrace())

        assertEquals("R1", command!!.targetId)
        assertTrue(lines[0].endsWith("reads begin"), lines.toString())
        assertTrue(lines[1].contains("read 1: R1") && lines[1].endsWith("→ OPEN"), lines.toString())
        // Phase 1 (§3): the decision starts a windup, and the throw line fires when the windup completes
        assertTrue(lines[2].contains("windup 0.15 s → R1"), lines.toString())
        assertTrue(lines[3].contains("throw → R1") && lines[3].contains("speed 20.0 yd/s"), lines.toString())
        assertEquals(4, lines.size)
    }

    @Test
    fun testCoveredReceiversAreTracedAsNotOpenUntilTheForceThrow() {
        val (lines, command) = runBrain(coverage, PlayDebugTrace())

        assertTrue(command!!.isThrowAway)
        assertTrue(lines.any { it.contains("read 1: R1") && it.endsWith("→ NOT OPEN") }, lines.toString())
        assertTrue(lines.any { it.endsWith("force-throw time reached") }, lines.toString())
        assertTrue(lines.last().endsWith("throwaway (force-throw time)"), lines.toString())
    }

    @Test
    fun testEachReadIsTracedOnceNotEveryTick() {
        val (lines, _) = runBrain(coverage, PlayDebugTrace())

        val readLines = lines.filter { it.contains(" read ") }
        assertEquals(readLines.size, readLines.toSet().size, "no read line is repeated")
        assertEquals(3, readLines.size, "three receivers, three reads")
    }

    @Test
    fun testPressureIsTracedOncePerRusher() {
        val rusher = player("RUSH", 26f, 23f) // 3 yards from the QB, inside the pressure radius but outside the sack radius
        val (lines, _) = runBrain(coverage + rusher, PlayDebugTrace())

        assertEquals(1, lines.count { it.contains("pressure: RUSH at") }, lines.toString())
        assertTrue(lines.last().contains("throwaway (forced by pressure)"), lines.toString())
    }

    @Test
    fun testSackIsTracedWithTheSacker() {
        val rusher = player("RUSH", 26f, 21f) // 1 yard away: inside the sack radius
        val trace = PlayDebugTrace()
        val brain = QbBrain(qb, receivers, trace = trace)

        brain.evaluateTick(listOf(rusher), tick)

        assertTrue(trace.lines().any { it.contains("SACKED by RUSH (1.0 yd)") }, trace.lines().toString())
    }

    @Test
    fun testTracingNeverChangesWhatTheBrainDecides() {
        for (defenders in listOf(emptyList(), coverage, coverage + player("RUSH", 26f, 23f))) {
            val traced = runBrain(defenders, PlayDebugTrace()).second
            val untraced = runBrain(defenders, null).second

            assertEquals(untraced?.targetId, traced?.targetId)
            assertEquals(untraced?.isThrowAway, traced?.isThrowAway)
            assertEquals(untraced?.trajectory?.totalFlightTimeSec, traced?.trajectory?.totalFlightTimeSec)
        }
    }
}
