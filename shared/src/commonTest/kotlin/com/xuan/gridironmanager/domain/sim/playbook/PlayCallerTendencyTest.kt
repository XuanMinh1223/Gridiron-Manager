package com.xuan.gridironmanager.domain.sim.playbook

import com.xuan.gridironmanager.domain.sim.match.GameState
import com.xuan.gridironmanager.testMatchup
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

class PlayCallerTendencyTest {
    private val matchup = testMatchup()
    private val draws = 600

    // The true coverage behind each call, including the disguised versions the CPU sometimes swaps in
    private val runStopIds = setOf("43_man", "43_cover_3", "43_cover_3_from_2", "nickel_blitz")
    private val deepIds = setOf("43_cover_3", "43_cover_3_from_2", "nickel_cover_2")

    private val secondAndSix = GameState(quarter = 2, down = 1, distance = 6, clockSeconds = 600, isHomePossession = true)

    private fun share(
        calls: List<DefensiveCall>,
        ids: Set<String>,
    ) = calls.count { it.id in ids } / calls.size.toFloat()

    private fun defense(
        state: GameState,
        seed: Int,
        history: PlayCaller.() -> Unit = {},
    ): List<DefensiveCall> {
        val caller = PlayCaller(matchup, Random(seed)).apply(history)
        return (1..draws).map { caller.callDefense(state) }
    }

    @Test
    fun testDefenseLeansTowardRunStopsAgainstARunHeavyOffense() {
        val baseline = share(defense(secondAndSix, seed = 1), runStopIds)
        val vsRunHeavy =
            share(
                defense(secondAndSix, seed = 1) { repeat(12) { recordPlay(secondAndSix, Playbook.INSIDE_ZONE, Playbook.COVER_3) } },
                runStopIds,
            )

        assertTrue(vsRunHeavy > baseline + 0.08f, "run-stop share was $baseline with no history and $vsRunHeavy against a run-heavy offense")
    }

    @Test
    fun testDefenseLeansTowardPassCoverageAgainstAPassHeavyOffense() {
        val passIds = setOf("43_cover_2", "43_cover_3", "nickel_cover_2", "43_cover_2_from_3", "43_cover_2_from_man", "43_cover_3_from_2")
        val baseline = share(defense(secondAndSix, seed = 2), passIds)
        val vsPassHeavy =
            share(
                defense(secondAndSix, seed = 2) { repeat(12) { recordPlay(secondAndSix, Playbook.QUICK_SLANTS, Playbook.COVER_3) } },
                passIds,
            )

        assertTrue(vsPassHeavy > baseline + 0.05f, "pass-coverage share was $baseline with no history and $vsPassHeavy against a pass-heavy offense")
    }

    @Test
    fun testNoHistoryChangesNothing() {
        // Same seed, and a tendency that is too thin to judge by (4 plays): identical calls
        val plain = defense(secondAndSix, seed = 3)
        val thin = defense(secondAndSix, seed = 3) { repeat(4) { recordPlay(secondAndSix, Playbook.INSIDE_ZONE, Playbook.COVER_3) } }

        assertTrue(plain == thin, "a four-play sample must not move the weights")
    }

    @Test
    fun testDefenseSitsBackWhenProtectingABigLeadLate() {
        val closeGame = GameState(quarter = 4, down = 1, distance = 6, clockSeconds = 100, homeScore = 14, awayScore = 17, isHomePossession = true)
        val bigLead = closeGame.copy(homeScore = 7, awayScore = 24)

        val normal = share(defense(closeGame, seed = 4), deepIds)
        val prevent = share(defense(bigLead, seed = 4), deepIds)

        assertTrue(prevent > normal + 0.10f, "deep-coverage share was $normal in a close game and $prevent protecting a 17-point lead")
    }

    @Test
    fun testTrailingLateIsPassHeavierAndLeadingLateIsRunHeavier() {
        fun runShare(
            state: GameState,
            seed: Int,
        ): Float {
            val caller = PlayCaller(matchup, Random(seed))
            val calls = (1..draws).map { caller.callOffense(state) }
            return calls.count { it.category == PlayCategory.RUN } / draws.toFloat()
        }

        val midGame = GameState(quarter = 2, down = 1, distance = 10, clockSeconds = 600, homeScore = 14, awayScore = 14)
        val trailing = GameState(quarter = 4, down = 1, distance = 10, clockSeconds = 100, homeScore = 10, awayScore = 17)
        val leading = GameState(quarter = 4, down = 1, distance = 10, clockSeconds = 150, homeScore = 24, awayScore = 17)

        val baseline = runShare(midGame, 5)
        val hurryUp = runShare(trailing, 5)
        val bleedClock = runShare(leading, 5)

        assertTrue(hurryUp < baseline - 0.10f, "run share was $baseline mid-game and $hurryUp trailing late")
        assertTrue(bleedClock > baseline + 0.15f, "run share was $baseline mid-game and $bleedClock leading late")
    }

    @Test
    fun testOvertimeCountsAsTheFinalPeriod() {
        val caller = PlayCaller(matchup, Random(6))
        val trailingInOvertime = GameState(quarter = 4, isOvertime = true, down = 1, distance = 10, clockSeconds = 100, homeScore = 17, awayScore = 20)
        val runShare = (1..draws).count { caller.callOffense(trailingInOvertime).category == PlayCategory.RUN } / draws.toFloat()

        assertTrue(runShare < 0.35f, "a trailing team in overtime should hurry up, ran ${(runShare * 100).toInt()}%")
    }
}
