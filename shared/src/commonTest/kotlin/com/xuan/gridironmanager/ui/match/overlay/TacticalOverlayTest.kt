package com.xuan.gridironmanager.ui.match.overlay

import com.xuan.gridironmanager.domain.sim.match.GameState
import com.xuan.gridironmanager.domain.sim.movement.PlayerRole
import com.xuan.gridironmanager.domain.sim.play.Snap
import com.xuan.gridironmanager.domain.sim.playbook.DefensiveCall
import com.xuan.gridironmanager.domain.sim.playbook.OffensivePlay
import com.xuan.gridironmanager.domain.sim.playbook.Playbook
import com.xuan.gridironmanager.domain.sim.playbook.SnapBuilder
import com.xuan.gridironmanager.testMatchup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TacticalOverlayTest {
    private val matchup = testMatchup()

    private fun snap(
        play: OffensivePlay,
        call: DefensiveCall,
    ): Snap = SnapBuilder.build(GameState(yardLine = 30), matchup, play, call)

    private fun overlay(
        snap: Snap,
        showOffense: Boolean = true,
        showDefense: Boolean = true,
    ) = TacticalOverlay.build(snap.offense + snap.defense, snap.losWorldY, showOffense, showDefense)

    @Test
    fun testManCoverageDrawsRoutesAndMatchups() {
        val snap = snap(Playbook.FOUR_VERTICALS, Playbook.BASE_MAN)

        val overlay = overlay(snap)

        assertEquals(5, overlay.routes.size, "One route per receiver")
        assertEquals(5, overlay.manLinks.size, "Every receiver is matched up")
        assertEquals(2, overlay.zones.size, "The spare defenders play zones")
        assertTrue(overlay.blitzArrows.isEmpty())
    }

    @Test
    fun testZoneCoverageSplitsDeepAndUnderneath() {
        val overlay = overlay(snap(Playbook.CURL_FLAT, Playbook.COVER_2))

        assertEquals(2, overlay.zones.count { it.isDeep })
        assertEquals(5, overlay.zones.count { !it.isDeep })
        assertTrue(overlay.manLinks.isEmpty())
    }

    @Test
    fun testBlitzArrowPointsAtThePasser() {
        val snap = snap(Playbook.CURL_FLAT, Playbook.NICKEL_BLITZ)
        val passer = snap.offense.single { it.role == PlayerRole.PASSER }

        val arrow = overlay(snap).blitzArrows.single()

        assertEquals(passer.currentPos, arrow.to)
    }

    @Test
    fun testEachSideCanBeShownOnItsOwn() {
        val snap = snap(Playbook.CURL_FLAT, Playbook.BASE_MAN)

        val offenseOnly = overlay(snap, showDefense = false)
        assertTrue(offenseOnly.routes.isNotEmpty())
        assertTrue(offenseOnly.manLinks.isEmpty() && offenseOnly.zones.isEmpty())

        val defenseOnly = overlay(snap, showOffense = false)
        assertTrue(defenseOnly.routes.isEmpty())
        assertTrue(defenseOnly.manLinks.isNotEmpty())
    }

    @Test
    fun testBallCarrierPathOnlyHintsAtTheRunToTheEndZone() {
        val snap = snap(Playbook.INSIDE_ZONE, Playbook.BASE_MAN)
        val carrier = snap.offense.single { it.role == PlayerRole.BALL_CARRIER }

        val path = overlay(snap).routes.single { it.first() == carrier.currentPos }

        assertTrue(path.last().y - snap.losWorldY < 15f, "Carrier path should stop near the line, ended at ${path.last().y}")
    }

    @Test
    fun testEngagedBlocksAreDrawnBetweenBlockerAndDefender() {
        val snap = snap(Playbook.CURL_FLAT, Playbook.BASE_MAN)
        val guard = snap.offense.first { it.role == PlayerRole.BLOCKER }
        val tackle = snap.defense.first { it.role == PlayerRole.PASS_RUSHER }
        guard.blockingId = tackle.id

        val block = overlay(snap).blocks.single()

        assertEquals(Segment(guard.currentPos, tackle.currentPos), block)
    }

    @Test
    fun testCompletedRoutesAreNotDrawn() {
        val snap = snap(Playbook.QUICK_SLANTS, Playbook.BASE_MAN)
        snap.offense.forEach { player -> player.route?.let { player.currentWaypointIndex = it.waypoints.size } }

        assertTrue(overlay(snap).routes.isEmpty())
    }
}
