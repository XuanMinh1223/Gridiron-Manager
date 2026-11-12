package com.xuan.gridironmanager.domain.engine

import com.xuan.gridironmanager.domain.model.Position
import com.xuan.gridironmanager.domain.model.PositionType
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LeagueGeneratorTest {
    private val roster = LeagueGenerator.generatePlayersForTeam("T1", Random(42))

    private fun playersAt(vararg positions: Position) = roster.filter { player -> positions.any { it.abbreviation == player.position } }

    @Test
    fun testPlayerIdsAreUnique() {
        assertEquals(roster.size, roster.map { it.id }.toSet().size)
    }

    @Test
    fun testSameSeedGeneratesSameRoster() {
        assertEquals(roster, LeagueGenerator.generatePlayersForTeam("T1", Random(42)))
    }

    @Test
    fun testSpecialistsCanKick() {
        val specialists = playersAt(Position.K, Position.P)
        assertTrue(specialists.isNotEmpty())
        assertTrue(specialists.all { it.attributes.kickPower >= 75 && it.attributes.kickAccuracy >= 75 })
    }

    @Test
    fun testWholeOffensiveLineCanPassBlock() {
        assertTrue(playersAt(Position.OT, Position.OG, Position.C, Position.OL).all { it.attributes.blockPass >= 80 })
    }

    @Test
    fun testEveryRosterHasSixtyUniqueFullNames() {
        for (seed in 1..3) {
            val team = LeagueGenerator.generatePlayersForTeam("T$seed", Random(seed))

            assertEquals(60, team.size)
            assertEquals(60, team.map { it.fullName }.toSet().size, "seed $seed has duplicate names")
        }
    }

    @Test
    fun testAgeFollowsFromDraftAgeAndYearsPro() {
        for (seed in 1..3) {
            for (player in LeagueGenerator.generatePlayersForTeam("T$seed", Random(seed))) {
                assertTrue(player.age - player.yearsPro in 21..23, "${player.fullName}: age ${player.age}, ${player.yearsPro} years pro")
                assertTrue(player.yearsPro in 0..14)
            }
        }
    }

    @Test
    fun testBuildMatchesThePosition() {
        for (seed in 1..3) {
            for (player in LeagueGenerator.generatePlayersForTeam("T$seed", Random(seed))) {
                val physique = LeagueGenerator.physiqueFor(Position.entries.single { it.abbreviation == player.position })

                assertTrue(player.physicalProfile.heightInches in physique.heightInches, "${player.position} is ${player.physicalProfile.heightInches} in")
                assertTrue(player.physicalProfile.weightLbs in physique.weightLbs, "${player.position} is ${player.physicalProfile.weightLbs} lb")
            }
        }
        // The table itself: a receiver is never built like a lineman
        assertTrue(LeagueGenerator.physiqueFor(Position.WR).weightLbs.last < LeagueGenerator.physiqueFor(Position.OT).weightLbs.first)
    }

    @Test
    fun testOverallRatingIsPositionWeighted() {
        val qb = playersAt(Position.QB).first()
        val a = qb.attributes
        assertEquals((a.throwAccuracy + a.throwPower + a.awareness + a.speed) / 4, qb.overallRating)

        val wr = playersAt(Position.WR).first()
        val w = wr.attributes
        assertEquals((w.speed + w.catching + w.routeRunning + w.acceleration) / 4, wr.overallRating)

        val kicker = playersAt(Position.K).first()
        val k = kicker.attributes
        assertEquals((k.kickPower + k.kickAccuracy + k.awareness + k.speed) / 4, kicker.overallRating)
    }

    @Test
    fun testWholeDefenseCanTackle() {
        val defenders = playersAt(*Position.entries.filter { it.type == PositionType.DEFENSE }.toTypedArray())
        assertTrue(defenders.all { it.attributes.tackle >= 70 })
    }
}
