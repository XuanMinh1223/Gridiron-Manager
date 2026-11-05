package com.xuan.gridironmanager.domain.sim.playbook

import com.xuan.gridironmanager.domain.sim.match.GamePhase
import com.xuan.gridironmanager.domain.sim.match.GameState
import com.xuan.gridironmanager.domain.sim.movement.PlayerRole
import com.xuan.gridironmanager.testMatchup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SnapBuilderDisguiseTest {
    private val matchup = testMatchup()
    private val homeBall = GameState(yardLine = 30, isHomePossession = true)
    private val disguised = listOf(Playbook.COVER_3_FROM_2, Playbook.COVER_2_FROM_3, Playbook.COVER_2_FROM_MAN)

    /** The undisguised call the shell / true half of a disguised call should line up the same as. */
    private fun plain(
        call: DefensiveCall,
        shell: Boolean,
    ) = call.copy(
        coverage = if (shell) call.disguise!!.shellCoverage else call.coverage,
        zones = if (shell) call.disguise!!.shellZones else call.zones,
        disguise = null,
    )

    @Test
    fun testDefenseIsTheShellAndRotationIsTheTrueCall() {
        for (call in disguised) {
            val snap = SnapBuilder.build(homeBall, matchup, Playbook.QUICK_SLANTS, call)
            val shell = SnapBuilder.build(homeBall, matchup, Playbook.QUICK_SLANTS, plain(call, shell = true))
            val truth = SnapBuilder.build(homeBall, matchup, Playbook.QUICK_SLANTS, plain(call, shell = false))
            val rotation = assertNotNull(snap.rotation, call.name)

            assertEquals(0.4f, rotation.rotationSec, call.name)
            for ((actual, expected) in snap.defense.zip(shell.defense)) {
                assertEquals(expected.role, actual.role, "${call.name} ${actual.slot}")
                assertEquals(expected.coverageTargetId, actual.coverageTargetId, "${call.name} ${actual.slot}")
                assertEquals(expected.zoneLandmark, actual.zoneLandmark, "${call.name} ${actual.slot}")
            }
            for (expected in truth.defense) {
                val assignment = assertNotNull(rotation.assignments[expected.slot], "${call.name} ${expected.slot}")
                assertEquals(expected.role, assignment.role, "${call.name} ${expected.slot}")
                assertEquals(expected.coverageTargetId, assignment.coverageTargetId, "${call.name} ${expected.slot}")
                assertEquals(expected.zoneLandmark, assignment.zoneLandmark, "${call.name} ${expected.slot}")
            }
        }
    }

    @Test
    fun testShellAndTrueLineUpInTheSamePlaces() {
        for (call in disguised) {
            val snap = SnapBuilder.build(homeBall, matchup, Playbook.QUICK_SLANTS, call)
            val truth = SnapBuilder.build(homeBall, matchup, Playbook.QUICK_SLANTS, plain(call, shell = false))

            assertEquals(truth.defense.map { it.id to it.currentPos }, snap.defense.map { it.id to it.currentPos }, call.name)
        }
    }

    @Test
    fun testRushersAreTheSameInShellAndTruth() {
        for (call in disguised) {
            val snap = SnapBuilder.build(homeBall, matchup, Playbook.QUICK_SLANTS, call)
            val rotation = assertNotNull(snap.rotation)
            val rushing = setOf(PlayerRole.PASS_RUSHER, PlayerRole.BLITZER)

            for (defender in snap.defense) {
                assertEquals(defender.role in rushing, rotation.assignments.getValue(defender.slot!!).role in rushing, "${call.name} ${defender.slot}")
            }
        }
    }

    @Test
    fun testBuildingTwiceGivesTheSameDefenseAndRotation() {
        for (call in disguised) {
            val first = SnapBuilder.build(homeBall, matchup, Playbook.CURL_FLAT, call)
            val second = SnapBuilder.build(homeBall, matchup, Playbook.CURL_FLAT, call)

            assertEquals(first.rotation, second.rotation, call.name)
            assertEquals(first.defense, second.defense, call.name)
        }
    }

    @Test
    fun testUndisguisedCallsHaveNoRotation() {
        for (call in Playbook.defensiveCalls) {
            assertNull(SnapBuilder.build(homeBall, matchup, Playbook.QUICK_SLANTS, call).rotation, call.name)
        }
    }

    @Test
    fun testKickingPlaysNeverCarryADisguise() {
        val kickoffState = GameState(phase = GamePhase.KICKOFF, yardLine = 35)
        for (play in listOf(Playbook.PUNT, Playbook.FIELD_GOAL)) {
            assertNull(SnapBuilder.build(homeBall, matchup, play, Playbook.COVER_3_FROM_2).rotation, play.name)
        }
        assertNull(SnapBuilder.build(kickoffState, matchup, Playbook.KICKOFF, Playbook.COVER_3_FROM_2).rotation)
    }
}
