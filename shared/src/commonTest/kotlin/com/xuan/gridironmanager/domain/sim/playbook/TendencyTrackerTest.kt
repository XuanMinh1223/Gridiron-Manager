package com.xuan.gridironmanager.domain.sim.playbook

import com.xuan.gridironmanager.domain.sim.match.GameState
import kotlin.test.Test
import kotlin.test.assertEquals

class TendencyTrackerTest {
    private val firstDown = GameState(down = 1)

    private fun TendencyTracker.run(
        team: String,
        times: Int,
        down: Int = 1,
    ) = repeat(times) { record(team, firstDown.copy(down = down), Playbook.INSIDE_ZONE, Playbook.COVER_3) }

    private fun TendencyTracker.pass(
        team: String,
        times: Int,
        down: Int = 1,
    ) = repeat(times) { record(team, firstDown.copy(down = down), Playbook.QUICK_SLANTS, Playbook.COVER_3) }

    @Test
    fun testNoHistoryIsNeutral() {
        val tracker = TendencyTracker()

        assertEquals(0.5f, tracker.runRate("A", 1))
        assertEquals(0.5f, tracker.passRate("A", 1))
        assertEquals(emptyList(), tracker.lastOffensivePlayIds("A"))
    }

    @Test
    fun testRateIsTheRecordedShareOnceThereIsEnoughToJudgeBy() {
        val tracker = TendencyTracker()
        tracker.run("A", 6)
        tracker.pass("A", 4)

        assertEquals(0.6f, tracker.runRate("A", 1), 0.001f)
        assertEquals(0.4f, tracker.passRate("A", 1), 0.001f)
    }

    @Test
    fun testTooFewPlaysIsStillNeutral() {
        val tracker = TendencyTracker()
        tracker.run("A", TendencyTracker.MIN_SAMPLE - 1)

        assertEquals(0.5f, tracker.runRate("A", 1))
    }

    @Test
    fun testDownsAndTeamsAreTrackedSeparately() {
        val tracker = TendencyTracker()
        tracker.run("A", 8, down = 1)
        tracker.pass("A", 8, down = 3)
        tracker.pass("B", 8, down = 1)

        assertEquals(1f, tracker.runRate("A", 1))
        assertEquals(0f, tracker.runRate("A", 3))
        assertEquals(0f, tracker.runRate("B", 1))
        assertEquals(0.5f, tracker.runRate("A", 2), "nothing recorded on second down")
    }

    @Test
    fun testKicksAndClockPlaysSayNothingAboutTendency() {
        val tracker = TendencyTracker()
        repeat(10) {
            tracker.record("A", firstDown, Playbook.PUNT, Playbook.COVER_3)
            tracker.record("A", firstDown, Playbook.KNEEL, Playbook.COVER_3)
            tracker.record("A", firstDown, Playbook.SPIKE, Playbook.COVER_3)
        }

        assertEquals(0.5f, tracker.runRate("A", 1))
        assertEquals(emptyList(), tracker.lastOffensivePlayIds("A"))
    }

    @Test
    fun testKeepsTheLastTenPlays() {
        val tracker = TendencyTracker()
        tracker.run("A", 8)
        tracker.pass("A", 5)

        val recent = tracker.lastOffensivePlayIds("A")
        assertEquals(10, recent.size)
        assertEquals(List(5) { Playbook.QUICK_SLANTS.id }, recent.takeLast(5))
    }

    @Test
    fun testCountsTheDefensiveCallsFaced() {
        val tracker = TendencyTracker()
        tracker.run("A", 3)

        assertEquals(3, tracker.defenseCallCount("A", 1, Playbook.COVER_3.id))
        assertEquals(0, tracker.defenseCallCount("A", 2, Playbook.COVER_3.id))
    }
}
