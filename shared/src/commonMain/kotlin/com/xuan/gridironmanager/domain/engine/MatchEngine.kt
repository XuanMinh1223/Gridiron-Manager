package com.xuan.gridironmanager.domain.engine

import com.xuan.gridironmanager.domain.model.*
import kotlin.random.Random

object MatchEngine {
    fun resolvePlay(state: GameState, playCall: PlayCall): GameState {
        if (state.isGameOver) return state

        val result = when (playCall.type) {
            PlayType.RUN -> resolveRun(state)
            PlayType.PASS -> resolvePass(state)
            else -> PlayResult("Special teams not implemented", 0, playCall.type)
        }

        return applyResult(state, result)
    }

    private fun resolveRun(state: GameState): PlayResult {
        // Simple run logic: RB strength/speed vs Defense strength/tackle
        val offense = getPossessingTeam(state)
        val defense = getDefendingTeam(state)
        
        val rb = offense.roster.find { it.position == Position.RB } ?: offense.roster.first()
        val mlb = defense.roster.find { it.position == Position.LB } ?: defense.roster.first()

        val check = (rb.attributes.physical.strength + rb.attributes.physical.speed) - 
                    (mlb.attributes.physical.strength + mlb.attributes.technical.tackle)
        
        val baseYardage = Random.nextInt(-2, 8)
        val yardage = baseYardage + (check / 4)
        
        return PlayResult("Run by ${rb.lastName} for $yardage yards", yardage, PlayType.RUN)
    }

    private fun resolvePass(state: GameState): PlayResult {
        val offense = getPossessingTeam(state)
        val defense = getDefendingTeam(state)

        val qb = offense.roster.find { it.position == Position.QB } ?: offense.roster.first()
        val wr = offense.roster.find { it.position == Position.WR } ?: offense.roster.first()
        val edge = defense.roster.find { it.position == Position.EDGE } ?: defense.roster.first()
        val cb = defense.roster.find { it.position == Position.CB } ?: defense.roster.first()

        // Pass Rush Check
        val passRushSuccess = (edge.attributes.technical.passRush + edge.attributes.physical.speed) > 
                              (qb.attributes.mental.composure + Random.nextInt(0, 10))
        
        if (passRushSuccess && Random.nextFloat() < 0.2f) {
            return PlayResult("Sacked by ${edge.lastName}", -7, PlayType.PASS)
        }

        // Coverage Check
        val coverageMargin = (wr.attributes.technical.routeRunning + wr.attributes.physical.speed) - 
                             (cb.attributes.technical.manCoverage + cb.attributes.physical.speed)
        
        val isComplete = Random.nextInt(0, 20) + coverageMargin > 10

        return if (isComplete) {
            val yardage = Random.nextInt(5, 25) + (coverageMargin / 2)
            PlayResult("Complete pass to ${wr.lastName} for $yardage yards", yardage, PlayType.PASS)
        } else {
            PlayResult("Incomplete pass intended for ${wr.lastName}", 0, PlayType.PASS)
        }
    }

    private fun applyResult(state: GameState, result: PlayResult): GameState {
        var newYardLine = state.yardLine + result.yardage
        var newScoreHome = state.homeScore
        var newScoreAway = state.awayScore
        var isTouchdown = false
        var isTurnover = false

        if (newYardLine >= 100) {
            isTouchdown = true
            newYardLine = 100
            if (state.possession == TeamId.HOME) newScoreHome += 7 else newScoreAway += 7
        }

        val newDown: Int
        val newDistance: Int
        val newPossession: TeamId

        if (isTouchdown) {
            newDown = 1
            newDistance = 10
            newPossession = if (state.possession == TeamId.HOME) TeamId.AWAY else TeamId.HOME
            newYardLine = 25 // Kickoff simplified
        } else {
            if (result.yardage >= state.distance) {
                newDown = 1
                newDistance = 10
                newPossession = state.possession
            } else if (state.down >= 4) {
                isTurnover = true
                newDown = 1
                newDistance = 10
                newPossession = if (state.possession == TeamId.HOME) TeamId.AWAY else TeamId.HOME
                newYardLine = 100 - newYardLine // Flip field
            } else {
                newDown = state.down + 1
                newDistance = state.distance - result.yardage
                newPossession = state.possession
            }
        }

        return state.copy(
            yardLine = newYardLine,
            down = newDown,
            distance = newDistance,
            possession = newPossession,
            homeScore = newScoreHome,
            awayScore = newScoreAway,
            playHistory = state.playHistory + result.copy(isTouchdown = isTouchdown, isTurnover = isTurnover)
        )
    }

    private fun getPossessingTeam(state: GameState) = if (state.possession == TeamId.HOME) state.homeTeam else state.awayTeam
    private fun getDefendingTeam(state: GameState) = if (state.possession == TeamId.HOME) state.awayTeam else state.homeTeam
}
