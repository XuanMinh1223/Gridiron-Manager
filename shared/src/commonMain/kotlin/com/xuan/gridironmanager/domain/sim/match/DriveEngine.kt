package com.xuan.gridironmanager.domain.sim.match

class DriveEngine {
    fun resolvePlay(currentState: GameState, result: PlayResult): GameState {
        // 1. Clock Management (if not already handled by real-time sim)
        // If clockStops is false, we might deduct some "between play" time if it wasn't fully simulated.
        // For simplicity, we'll assume MatchPresenter handles real-time clock during the play.
        // But we might need to deduct time for the huddle/setup here if needed.
        
        var nextClockSeconds = currentState.clockSeconds
        var nextQuarter = currentState.quarter

        if (nextClockSeconds <= 0) {
            if (nextQuarter < 4) {
                nextQuarter++
                nextClockSeconds = 900
            } else {
                // Game Over logic could go here
                return currentState.copy(clockSeconds = 0)
            }
        }

        if (result.isTurnover) {
            return GameState(
                down = 1,
                distance = 10,
                yardLine = 100 - (currentState.yardLine + result.yardsGained).coerceIn(0, 100),
                homeScore = currentState.homeScore,
                awayScore = currentState.awayScore,
                quarter = nextQuarter,
                clockSeconds = nextClockSeconds,
                isHomePossession = !currentState.isHomePossession
            )
        }

        val newYardLine = (currentState.yardLine + result.yardsGained).coerceIn(0, 100)
        
        if (result.isTouchdown || newYardLine >= 100) {
            // NFL Rules: TD = 6 pts. Simplified: 6 + 1 (XP) = 7
            val points = 7 
            return GameState(
                down = 1,
                distance = 10,
                yardLine = 25, // Kickoff touchback
                homeScore = if (currentState.isHomePossession) currentState.homeScore + points else currentState.homeScore,
                awayScore = if (!currentState.isHomePossession) currentState.awayScore + points else currentState.awayScore,
                quarter = nextQuarter,
                clockSeconds = nextClockSeconds,
                isHomePossession = !currentState.isHomePossession
            )
        }

        val yardsForFirstDown = currentState.distance
        
        val (newDown, newDistance) = if (result.yardsGained >= yardsForFirstDown) {
            1 to 10
        } else {
            val updatedDown = currentState.down + 1
            if (updatedDown > 4) {
                // Turnover on downs
                return GameState(
                    down = 1,
                    distance = 10,
                    yardLine = 100 - newYardLine,
                    homeScore = currentState.homeScore,
                    awayScore = currentState.awayScore,
                    quarter = nextQuarter,
                    clockSeconds = nextClockSeconds,
                    isHomePossession = !currentState.isHomePossession
                )
            }
            updatedDown to (yardsForFirstDown - result.yardsGained)
        }

        return currentState.copy(
            down = newDown,
            distance = newDistance,
            yardLine = newYardLine,
            quarter = nextQuarter,
            clockSeconds = nextClockSeconds
        )
    }
}
