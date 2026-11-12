package com.xuan.gridironmanager.ui.match.overlay

import com.xuan.gridironmanager.domain.model.Vector3D
import com.xuan.gridironmanager.domain.sim.match.GameState
import com.xuan.gridironmanager.domain.sim.movement.RunningPlayer
import com.xuan.gridironmanager.domain.sim.play.PlaySimulator
import com.xuan.gridironmanager.domain.sim.play.Snap
import com.xuan.gridironmanager.domain.sim.playbook.DefensiveCall
import com.xuan.gridironmanager.domain.sim.playbook.Playbook
import com.xuan.gridironmanager.domain.sim.playbook.SnapBuilder
import com.xuan.gridironmanager.testMatchup
import com.xuan.gridironmanager.ui.match.MatchPresenter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TacticalOverlayDisguiseTest {
    private val matchup = testMatchup()
    private val homeBall = GameState(yardLine = 30, isHomePossession = true)

    private fun snap(call: DefensiveCall) = SnapBuilder.build(homeBall, matchup, Playbook.QUICK_SLANTS, call)

    private fun defenseOverlay(
        players: List<RunningPlayer>,
        snap: Snap,
    ) = TacticalOverlay.build(players, snap.losWorldY, showOffense = false, showDefense = true)

    private fun defenseOverlay(snap: Snap) = defenseOverlay(snap.offense + snap.defense, snap)

    private fun TacticalOverlay.zoneCenters(): Set<Vector3D> = zones.map { it.center }.toSet()

    @Test
    fun testOverlayShowsTheShellBeforeRotationAndTheTrueCoverageAfter() {
        val snap = snap(Playbook.COVER_3_FROM_2)
        val simulator = PlaySimulator(snap, Random(1))
        val cover2 = defenseOverlay(snap(Playbook.COVER_2))
        val cover3 = defenseOverlay(snap(Playbook.COVER_3))

        val before = defenseOverlay(simulator.players, snap)
        assertEquals(cover2.zoneCenters(), before.zoneCenters(), "pre-snap overlay is the shell")

        repeat(9) { simulator.tick(0.05f) }
        val after = defenseOverlay(simulator.players, snap)
        assertEquals(cover3.zoneCenters(), after.zoneCenters(), "post-rotation overlay is the true call")
    }

    @Test
    fun testManShellBecomesZonesAfterRotation() {
        val snap = snap(Playbook.COVER_2_FROM_MAN)
        val simulator = PlaySimulator(snap, Random(1))

        val before = defenseOverlay(simulator.players, snap)
        assertTrue(before.manLinks.isNotEmpty())
        assertTrue(before.zones.size < 7)

        repeat(9) { simulator.tick(0.05f) }
        val after = defenseOverlay(simulator.players, snap)
        assertTrue(after.manLinks.isEmpty())
        assertEquals(7, after.zones.size)
    }

    @Test
    fun testPreSnapSnapDefenseIsTheShell() {
        val overlay = defenseOverlay(snap(Playbook.COVER_3_FROM_2))

        assertEquals(defenseOverlay(snap(Playbook.COVER_2)).zoneCenters(), overlay.zoneCenters())
    }

    @Test
    fun testPresenterNeverExposesTheCpusDisguisedCallToAnOffensiveUser() {
        val testDispatcher = StandardTestDispatcher()
        var sawHint = false
        for (seed in 1..60) {
            val presenter = MatchPresenter(TestScope(testDispatcher), testDispatcher, Random(seed))
            presenter.startMatch(matchup, homeBall)

            val state = presenter.uiState.value
            assertTrue(state.playCall.isUserOnOffense)
            assertNull(state.playCall.selectedDefense, "the CPU's call is never put in the UI state")
            assertFalse(state.playByPlayText.contains("from"))
            state.playCall.coverageHint?.let {
                sawHint = true
                assertTrue(it.text.startsWith("Read: "))
                assertTrue(it.text.endsWith("Confident") || it.text.endsWith("Uncertain"))
            }
        }
        assertTrue(sawHint, "some of 60 seeded plays carry a disguised CPU call")
    }

    @Test
    fun testUserDefenseSeesTheDisguisedCallNamesAsOptions() {
        val testDispatcher = StandardTestDispatcher()
        val presenter = MatchPresenter(TestScope(testDispatcher), testDispatcher, Random(1))
        presenter.startMatch(matchup, homeBall.copy(isHomePossession = false))

        val playCall = presenter.uiState.value.playCall
        assertEquals(Playbook.defenseOptions, playCall.defenseOptions)
        assertTrue(playCall.defenseOptions.any { it.name == "Cover 3 (from 2)" })
        assertNull(playCall.coverageHint)
        assertNotNull(playCall.selectedDefense)
    }
}
