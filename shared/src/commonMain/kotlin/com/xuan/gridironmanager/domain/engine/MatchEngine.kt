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
        val offenseRoster = getPossessingRoster(state)
        val defenseRoster = getDefendingRoster(state)
        
        val rb = offenseRoster.find { it.position == "RB" } ?: offenseRoster.first()
        val lb = defenseRoster.find { it.position == "LB" } ?: defenseRoster.first()

        val check = (rb.attributes.speed + rb.attributes.strength) - 
                    (lb.attributes.strength + lb.attributes.tackle)
        
        val baseYardage = Random.nextInt(-2, 8)
        val yardage = baseYardage + (check / 10)
        
        return PlayResult("Run by ${rb.lastName} for $yardage yards", yardage, PlayType.RUN)
    }

    private fun resolvePass(state: GameState): PlayResult {
        val offenseRoster = getPossessingRoster(state)
        val defenseRoster = getDefendingRoster(state)

        val qb = offenseRoster.find { it.position == "QB" } ?: offenseRoster.first()
        val wr = offenseRoster.find { it.position == "WR" } ?: offenseRoster.first()
        val dl = defenseRoster.find { it.position == "DL" } ?: defenseRoster.first()
        val cb = defenseRoster.find { it.position == "CB" } ?: defenseRoster.first()

        // Pass Rush Check
        val passRushSuccess = (dl.attributes.speed + dl.attributes.strength) > 
                              (qb.attributes.awareness + Random.nextInt(0, 20))
        
        if (passRushSuccess && Random.nextFloat() < 0.2f) {
            return PlayResult("Sacked by ${dl.lastName}", -7, PlayType.PASS)
        }

        // Coverage Check
        val coverageMargin = (wr.attributes.routeRunning + wr.attributes.speed) - 
                             (cb.attributes.speed + cb.attributes.awareness)
        
        val isComplete = Random.nextInt(0, 50) + coverageMargin > 30

        return if (isComplete) {
            val yardage = Random.nextInt(5, 25) + (coverageMargin / 5)
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

    private fun getPossessingRoster(state: GameState) = if (state.possession == TeamId.HOME) state.homeRoster else state.awayRoster
    private fun getDefendingRoster(state: GameState) = if (state.possession == TeamId.HOME) state.awayRoster else state.homeRoster
}
