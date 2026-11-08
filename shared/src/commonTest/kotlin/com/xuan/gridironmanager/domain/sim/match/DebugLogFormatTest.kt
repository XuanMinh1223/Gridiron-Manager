package com.xuan.gridironmanager.domain.sim.match

import com.xuan.gridironmanager.domain.sim.playbook.DefensiveCall
import com.xuan.gridironmanager.domain.sim.playbook.Playbook
import com.xuan.gridironmanager.domain.sim.playbook.SnapBuilder
import com.xuan.gridironmanager.testMatchup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DebugLogFormatTest {
    private val preState = GameState(quarter = 3, clockSeconds = 252, down = 3, distance = 7, yardLine = 32, homeScore = 14, awayScore = 10)
    private val postState = preState.copy(down = 1, distance = 10, yardLine = 38)
    private val play = Playbook.FOUR_VERTICALS
    private val call = Playbook.NICKEL_COVER_2
    private val snap = SnapBuilder.build(preState, testMatchup(), play, call)
    private val result =
        PlayResult(
            yardsGained = 6,
            description = "QB scrambles for 6 yards, tackled by MLB",
            isTouchdown = false,
            isTurnover = false,
            debugTrace = listOf("t=0.60 reads begin", "t=0.65 scramble roll p=0.42 roll=0.31 → SCRAMBLE", "t=1.10 tackled by MLB at OWN 38"),
        )

    private fun block(
        defensiveCall: DefensiveCall = call,
        seed: Long = 12345L,
        summary: String = "rushAtt QB; rushYds QB +6; tackle MLB",
        playNumber: Int = 47,
    ) = formatPlayDebugBlock(preState, postState, snap, seed, playNumber, play, defensiveCall, result, summary)

    @Test
    fun testBlockHasTheLockedShape() {
        val expected =
            """
            --- Play 47 · Q3 4:12 · 3rd & 7 at OWN 32 · Home 14 – Away 10 · seed 12345 ---
            OFF: Shotgun Trips – Four Verticals · DEF: Nickel Cover 2
            t=0.60 reads begin
            t=0.65 scramble roll p=0.42 roll=0.31 → SCRAMBLE
            t=1.10 tackled by MLB at OWN 38
            OUTCOME: QB scrambles for 6 yards, tackled by MLB · +6 yd · OWN 38 · 1st & 10
            STATS: rushAtt QB; rushYds QB +6; tackle MLB
            """.trimIndent()

        assertEquals(expected, block())
    }

    @Test
    fun testHeaderCarriesSeedPlayNumberDownAndBothPlayNames() {
        val header = block().lines().take(2).joinToString("\n")

        assertTrue("seed 12345" in header)
        assertTrue("Play 47" in header)
        assertTrue("3rd & 7" in header)
        assertTrue("Four Verticals" in header && "Nickel Cover 2" in header)
    }

    @Test
    fun testBlockEndsWithOutcomeAndStats() {
        val lines = block().lines()

        assertTrue(lines[lines.size - 2].startsWith("OUTCOME: "))
        assertTrue(lines.last().startsWith("STATS: "))
    }

    @Test
    fun testTraceLinesAreCopiedVerbatimAndInOrder() {
        val lines = block().lines()

        assertEquals(result.debugTrace, lines.subList(2, 2 + result.debugTrace.size))
    }

    @Test
    fun testDisguisedCallShowsShellAndTrueCoverage() {
        val line = block(defensiveCall = Playbook.COVER_3_FROM_2).lines()[1]

        assertEquals("OFF: Shotgun Trips – Four Verticals · DEF: Cover 3 (from 2) (shell Cover 2 → true Cover 3)", line)
    }

    @Test
    fun testEmptySummaryReadsNone() {
        assertTrue(block(summary = "").endsWith("STATS: (none)"))
    }

    @Test
    fun testUnknownSeedIsSaidSoRatherThanMadeUp() {
        assertTrue("seed unknown ---" in block(seed = UNKNOWN_SEED))
    }

    @Test
    fun testTwoBlocksAreSeparatedByExactlyOneBlankLine() {
        val joined = listOf(block(playNumber = 1), block(playNumber = 2)).joinToString("\n\n")

        val blocks = joined.split("\n\n")
        assertEquals(2, blocks.size)
        assertTrue(blocks[0].startsWith("--- Play 1 ") && blocks[1].startsWith("--- Play 2 "))
        assertTrue("\n\n\n" !in joined)
    }

    @Test
    fun testYardLinesReadFromTheOffensesSideOfTheField() {
        assertEquals("OWN 32", formatYardLine(32))
        assertEquals("50", formatYardLine(50))
        assertEquals("OPP 41", formatYardLine(59))
        assertEquals("OWN 0", formatYardLine(0))
        assertEquals("OPP 0", formatYardLine(100))
        assertEquals("OWN 0", formatYardLine(-4), "an end-zone spot is clamped, not printed negative")
    }

    @Test
    fun testClockMatchesTheScoreboard() {
        assertEquals("4:12", formatClock(252))
        assertEquals("0:05", formatClock(5))
        assertEquals("15:00", formatClock(900))
    }

    @Test
    fun testFixedDecimalsAreStableAcrossPlatforms() {
        assertEquals("2.8", 2.8f.fixed(1))
        assertEquals("0.60", 0.6f.fixed(2))
        assertEquals("-3.0", (-3.04f).fixed(1))
        assertEquals("0.00", 0.004f.fixed(2))
        assertEquals("12", 12f.fixed(0))
        assertEquals("0.012", 0.0123f.fixed(3))
    }
}
