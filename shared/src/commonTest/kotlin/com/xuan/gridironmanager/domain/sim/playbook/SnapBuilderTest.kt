package com.xuan.gridironmanager.domain.sim.playbook

import com.xuan.gridironmanager.domain.model.PlayType
import com.xuan.gridironmanager.domain.model.Position
import com.xuan.gridironmanager.domain.sim.FieldGeometry
import com.xuan.gridironmanager.domain.sim.match.GamePhase
import com.xuan.gridironmanager.domain.sim.match.GameState
import com.xuan.gridironmanager.domain.sim.match.Rules
import com.xuan.gridironmanager.domain.sim.movement.PlayerRole
import com.xuan.gridironmanager.testMatchup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SnapBuilderTest {
    private val matchup = testMatchup()
    private val homeBall = GameState(yardLine = 30, isHomePossession = true)

    @Test
    fun testEveryPlayLinesUpElevenWithUniqueIds() {
        for (play in Playbook.offensivePlaysFor(GamePhase.SCRIMMAGE) + Playbook.KICKOFF) {
            val snap = SnapBuilder.build(homeBall, matchup, play, Playbook.COVER_3)

            assertEquals(11, snap.offense.size, play.name)
            assertEquals(11, snap.defense.size, play.name)
            assertEquals(22, (snap.offense + snap.defense).map { it.id }.toSet().size, "${play.name} has duplicate ids")
        }
    }

    @Test
    fun testPassPlayRolesAndReadOrder() {
        val snap = SnapBuilder.build(homeBall, matchup, Playbook.QUICK_SLANTS, Playbook.BASE_MAN)

        assertEquals(PlayerRole.PASSER, snap.offense.single { it.slot == "QB" }.role)
        assertEquals(listOf("X", "Z", "SLOT", "TE", "RB"), snap.progression.map { id -> snap.offense.single { it.id == id }.slot })
        assertTrue(snap.offense.filter { it.position in setOf(Position.C, Position.OG, Position.OT) }.all { it.role == PlayerRole.BLOCKER })
    }

    @Test
    fun testRunPlayHasOneBallCarrierWhosePathReachesTheEndZone() {
        val snap = SnapBuilder.build(homeBall, matchup, Playbook.INSIDE_ZONE, Playbook.BASE_MAN)

        val carrier = snap.offense.single { it.role == PlayerRole.BALL_CARRIER }
        assertEquals("RB", carrier.slot)
        assertTrue(
            carrier.route!!
                .waypoints
                .last()
                .y > Rules.FIELD_LENGTH_YDS,
        )
    }

    @Test
    fun testManCoverageCoversEveryReceiverWithSparesInZones() {
        val snap = SnapBuilder.build(homeBall, matchup, Playbook.FOUR_VERTICALS, Playbook.BASE_MAN)

        val receiverIds =
            snap.offense
                .filter { it.role == PlayerRole.RECEIVER }
                .map { it.id }
                .toSet()
        val coveredIds = snap.defense.mapNotNull { it.coverageTargetId }.toSet()
        assertEquals(receiverIds, coveredIds)
        // 7 defenders off the line, 5 receivers: the 2 spares play zones
        assertEquals(2, snap.defense.count { it.role == PlayerRole.ZONE_COVERAGE })
    }

    @Test
    fun testOutsideReceiversDrawTheCornerbacks() {
        val snap = SnapBuilder.build(homeBall, matchup, Playbook.FOUR_VERTICALS, Playbook.NICKEL_MAN)
        val byId = snap.offense.associateBy { it.id }

        val xCoveredBy = snap.defense.single { it.coverageTargetId?.let(byId::get)?.slot == "X" }
        val zCoveredBy = snap.defense.single { it.coverageTargetId?.let(byId::get)?.slot == "Z" }
        assertEquals(setOf("LCB", "RCB"), setOf(xCoveredBy.slot, zCoveredBy.slot))
    }

    @Test
    fun testZoneDefendersGetLandmarksAndBlitzersRush() {
        val cover2 = SnapBuilder.build(homeBall, matchup, Playbook.CURL_FLAT, Playbook.COVER_2)
        assertEquals(7, cover2.defense.count { it.role == PlayerRole.ZONE_COVERAGE && it.zoneLandmark != null })
        assertEquals(4, cover2.defense.count { it.role == PlayerRole.PASS_RUSHER })

        val blitz = SnapBuilder.build(homeBall, matchup, Playbook.CURL_FLAT, Playbook.NICKEL_BLITZ)
        assertEquals(PlayerRole.BLITZER, blitz.defense.single { it.slot == "WLB" }.role)
    }

    @Test
    fun testFormationMirrorsWhenTheAwayTeamHasTheBall() {
        val home = SnapBuilder.build(homeBall, matchup, Playbook.CURL_FLAT, Playbook.COVER_2)
        val away = SnapBuilder.build(homeBall.copy(isHomePossession = false), matchup, Playbook.CURL_FLAT, Playbook.COVER_2)

        val homeX = home.offense.single { it.slot == "X" }.currentPos
        val awayX = away.offense.single { it.slot == "X" }.currentPos
        assertEquals(homeX.x, 2 * FieldGeometry.CENTER_X - awayX.x, 0.001f)
        assertEquals(homeX.y, Rules.FIELD_LENGTH_YDS - awayX.y, 0.001f)
    }

    @Test
    fun testKickingPlaysBringOutTheSpecialTeamsUnit() {
        assertEquals(
            "KR",
            SnapBuilder
                .build(homeBall, matchup, Playbook.KICKOFF, Playbook.COVER_2)
                .defense
                .first { it.position == Position.RB }
                .slot,
        )
        assertNotNull(SnapBuilder.build(homeBall, matchup, Playbook.PUNT, Playbook.COVER_2).defense.find { it.slot == "PR" })
        assertTrue(SnapBuilder.build(homeBall, matchup, Playbook.FIELD_GOAL, Playbook.COVER_2).defense.none { it.role == PlayerRole.ZONE_COVERAGE })
    }

    @Test
    fun testKickoffSetupUnitsLineUpFiveYardsApart() {
        for (homeKicking in listOf(true, false)) {
            val snap = SnapBuilder.build(homeBall.copy(yardLine = Rules.KICKOFF_YARD_LINE, isHomePossession = homeKicking), matchup, Playbook.KICKOFF, Playbook.COVER_2)
            val direction = if (homeKicking) 1f else -1f
            val kickerY = snap.offense.single { it.slot == "K" }.currentPos.y
            assertEquals(Rules.KICKOFF_YARD_LINE.toFloat(), if (homeKicking) kickerY else Rules.FIELD_LENGTH_YDS - kickerY)
            assertTrue(snap.offense.filter { it.slot?.startsWith("KC") == true }.all { (it.currentPos.y - kickerY) * direction == 25f })
            assertTrue(snap.defense.filter { it.slot?.startsWith("KRB") == true }.all { (it.currentPos.y - kickerY) * direction == 30f })
        }
    }

    @Test
    fun testPuntReturnerLinesUpDownfield() {
        val snap = SnapBuilder.build(homeBall, matchup, Playbook.PUNT, Playbook.COVER_2)

        assertEquals(
            30f + 45f,
            snap.defense
                .single { it.slot == "PR" }
                .currentPos.y,
        )
    }

    @Test
    fun testExtraPointIsKickedFromTheFifteen() {
        val tryState = GameState(yardLine = Rules.TWO_POINT_YARD_LINE, phase = GamePhase.EXTRA_POINT)

        assertEquals(Rules.EXTRA_POINT_YARD_LINE, SnapBuilder.build(tryState, matchup, Playbook.EXTRA_POINT, Playbook.COVER_2).losYardLine)
        assertEquals(Rules.TWO_POINT_YARD_LINE, SnapBuilder.build(tryState, matchup, Playbook.INSIDE_ZONE, Playbook.COVER_2).losYardLine)
    }

    @Test
    fun testKickerDoesNotRunARoute() {
        val snap = SnapBuilder.build(homeBall, matchup, Playbook.FIELD_GOAL, Playbook.COVER_2)

        assertNull(snap.offense.single { it.role == PlayerRole.KICKER }.route)
    }

    @Test
    fun testExtremeFormationOffsetsAreClampedToField() {
        val baseNodes = Playbook.INSIDE_ZONE.formation.nodes
        val extremeNodes =
            baseNodes.mapIndexed { index, node ->
                if (index == 0) node.copy(xOffset = 100f) else node
            }
        val extremePlay =
            OffensivePlay(
                id = "extreme",
                name = "Extreme",
                category = PlayCategory.RUN,
                type = PlayType.RUN,
                formation =
                    Formation(
                        name = "Extreme",
                        type = FormationType.OFFENSE,
                        nodes = extremeNodes,
                    ),
                ballCarrierSlot = "RB",
                routes = mapOf("RB" to Routes.INSIDE_ZONE),
            )
        val snap = SnapBuilder.build(homeBall, matchup, extremePlay, Playbook.COVER_2)
        val player = snap.offense.first { it.slot == baseNodes[0].slot }
        assertTrue(player.currentPos.x >= 1f)
        assertTrue(player.currentPos.x <= FieldGeometry.WIDTH_YDS - 1f)
    }
}
