package com.xuan.gridironmanager.domain.sim.playbook

import com.xuan.gridironmanager.ui.match.components.defenseCardSubtitle
import com.xuan.gridironmanager.ui.match.components.defenseCardTitle
import com.xuan.gridironmanager.ui.match.components.disguiseDetail
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayDiagramTest {
    private fun PlayDiagram.inViewport() =
        (offenseDots + defenseDots + routes.flatMap { it.points } + zones.map { it.center }).all {
            it.x in viewport.minX..viewport.maxX && it.y in viewport.minY..viewport.maxY
        }

    @Test
    fun testFormationNodesMapBehindAndBeyondTheLine() {
        val offense = offensiveDiagram(Playbook.QUICK_SLANTS)
        val defense = defensiveDiagram(Playbook.BASE_MAN)

        assertTrue(DiagramPoint(0f, -5f) in offense.offenseDots, "shotgun QB is 5 yards behind the line")
        assertTrue(DiagramPoint(-1f, 1f) in defense.defenseDots, "LDT is 1 yard beyond the line")
        assertEquals(11, offense.offenseDots.size)
        assertEquals(11, defense.defenseDots.size)
    }

    @Test
    fun testSlantFromTheLeftReceiverBreaksInsideTowardMidfield() {
        val slant = offensiveDiagram(Playbook.QUICK_SLANTS).routes.first { it.points.first() == DiagramPoint(-15f, -1f) }

        assertEquals(DiagramPoint(-15f, 1f), slant.points[1])
        assertTrue(slant.points.zipWithNext().all { (a, b) -> b.x >= a.x && b.y > a.y }, "x increases toward midfield as it goes downfield")
        assertEquals(DiagramPoint(1f, 17f), slant.points.last())
    }

    @Test
    fun testGoRouteFromTheRightReceiverRunsStraightUpfieldThirtyFiveYards() {
        val go = offensiveDiagram(Playbook.FOUR_VERTICALS).routes.first { it.points.first() == DiagramPoint(15f, -1f) }

        assertEquals(2, go.points.size)
        assertEquals(15f, go.points.last().x)
        assertEquals(35f, go.points.last().y - go.points.first().y)
    }

    @Test
    fun testRunPlayDrawsTheCarrierPathFromTheBackfield() {
        val diagram = offensiveDiagram(Playbook.INSIDE_ZONE)
        val runPaths = diagram.routes.filter { it.isRunPath }

        assertEquals(1, runPaths.size)
        assertEquals(DiagramPoint(0f, -6f), runPaths.single().points.first())
        assertTrue(runPaths.single().points.last().y > runPaths.single().points.first().y)
        assertTrue(diagram.routes.filter { !it.isRunPath }.isNotEmpty(), "decoy routes are still drawn as plain routes")
    }

    @Test
    fun testPassPlayHasNoRouteForTheQuarterbackAndNoRunPath() {
        val diagram = offensiveDiagram(Playbook.STICK)

        assertTrue(diagram.routes.none { it.isRunPath })
        assertEquals(Playbook.STICK.routes.size, diagram.routes.size)
    }

    @Test
    fun testKickingPlaysHaveDotsButNoRoutes() {
        for (play in listOf(Playbook.FIELD_GOAL, Playbook.PUNT, Playbook.KICKOFF)) {
            val diagram = offensiveDiagram(play)

            assertEquals(11, diagram.offenseDots.size, play.name)
            assertTrue(diagram.routes.isEmpty(), play.name)
        }
    }

    @Test
    fun testDisguisedCallPreviewShowsTheShellAndNeverTheTrueZones() {
        val preview = defensiveDiagram(Playbook.COVER_3_FROM_2)
        val cover2 = defensiveDiagram(Playbook.COVER_2)
        val cover3 = defensiveDiagram(Playbook.COVER_3)

        assertEquals(cover2.zones, preview.zones)
        assertTrue(DiagramZone(DiagramPoint(-13f, 18f), isDeep = true) in preview.zones)
        assertTrue(preview.zones.none { it in cover3.zones && it !in cover2.zones }, "no Cover 3-only zone is drawn")
    }

    @Test
    fun testManShellDrawsDotsOnly() {
        val preview = defensiveDiagram(Playbook.COVER_2_FROM_MAN)

        assertTrue(preview.zones.isEmpty())
        assertEquals(11, preview.defenseDots.size)
    }

    @Test
    fun testZonesAreMarkedDeepFromTwelveYards() {
        val zones = defensiveDiagram(Playbook.COVER_2).zones

        assertTrue(zones.single { it.center == DiagramPoint(-9f, 8f) }.isDeep.not())
        assertTrue(zones.single { it.center == DiagramPoint(0f, 9f) }.isDeep.not())
        assertTrue(zones.single { it.center == DiagramPoint(13f, 18f) }.isDeep)
    }

    @Test
    fun testNickelBlitzDrawsOneArrowFromTheLinebackerToTheLine() {
        val diagram = defensiveDiagram(Playbook.NICKEL_BLITZ)

        assertEquals(listOf(DiagramPoint(-3f, 5f) to DiagramPoint(-3f, 0f)), diagram.blitzArrows)
        assertTrue(defensiveDiagram(Playbook.COVER_3_FROM_2).blitzArrows.isEmpty())
    }

    @Test
    fun testEveryScrimmageDiagramFitsTheViewport() {
        for (play in Playbook.scrimmagePlays) assertTrue(offensiveDiagram(play).inViewport(), play.name)
        for (call in Playbook.defenseOptions) assertTrue(defensiveDiagram(call).inViewport(), call.name)
    }

    @Test
    fun testDisguisedCardsAreMarkedAndBaseCallsAreNot() {
        assertEquals("Cover 3 (from 2) · Disguised", defenseCardTitle(Playbook.COVER_3_FROM_2))
        assertEquals(Playbook.COVER_2.name, defenseCardTitle(Playbook.COVER_2))
        assertEquals("4-3", defenseCardSubtitle(Playbook.COVER_2))
        assertEquals("Nickel", defenseCardSubtitle(Playbook.NICKEL_BLITZ))
    }

    @Test
    fun testDetailLineNamesShellAndTrueCoverageOnlyForDisguisedCalls() {
        assertEquals("Shows Cover 2 pre-snap, rotates to Cover 3 at 0.4 s", disguiseDetail(Playbook.COVER_3_FROM_2))
        assertEquals("Shows Man pre-snap, rotates to Cover 2 at 0.4 s", disguiseDetail(Playbook.COVER_2_FROM_MAN))
        assertNull(disguiseDetail(Playbook.COVER_3))
    }
}
