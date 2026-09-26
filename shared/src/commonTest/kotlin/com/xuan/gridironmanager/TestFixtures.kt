package com.xuan.gridironmanager

import com.xuan.gridironmanager.domain.engine.LeagueGenerator
import com.xuan.gridironmanager.domain.model.Matchup
import com.xuan.gridironmanager.domain.model.Team
import kotlin.random.Random

fun testTeam(id: String) =
    Team(id = id, city = "City $id", nickname = "Team $id", abbreviation = id, primaryColorHex = "#000000", secondaryColorHex = "#FFFFFF")

/** Two full generated rosters. The same seed always produces the same matchup. */
fun testMatchup(seed: Int = 1): Matchup {
    val random = Random(seed)
    return Matchup(
        homeTeam = testTeam("HOM"),
        awayTeam = testTeam("AWY"),
        homeRoster = LeagueGenerator.generatePlayersForTeam("HOM", random),
        awayRoster = LeagueGenerator.generatePlayersForTeam("AWY", random),
    )
}
