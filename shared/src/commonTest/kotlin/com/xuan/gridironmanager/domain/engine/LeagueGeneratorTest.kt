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
    fun testWholeDefenseCanTackle() {
        val defenders = playersAt(*Position.entries.filter { it.type == PositionType.DEFENSE }.toTypedArray())
        assertTrue(defenders.all { it.attributes.tackle >= 70 })
    }
}
