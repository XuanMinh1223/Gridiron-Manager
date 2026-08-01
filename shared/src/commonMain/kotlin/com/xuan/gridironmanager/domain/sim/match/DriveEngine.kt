package com.xuan.gridironmanager.domain.sim.match

class DriveEngine {
    fun resolvePlay(currentState: GameState, result: PlayResult): GameState {
        if (result.isTurnover) {
            // Basic turnover logic: reset to 1st & 10 at the current yard line (for simplicity in prototype)
            // In a real game, possession would flip.
            return GameState(
                down = 1,
                distance = 10,
                yardLine = 100 - (currentState.yardLine + result.yardsGained).coerceIn(0, 100),
                homeScore = currentState.homeScore,
                awayScore = currentState.awayScore,
                clockSeconds = (currentState.clockSeconds - 30).coerceAtLeast(0)
            )
        }

        if (result.isTouchdown) {
            return GameState(
                down = 1,
                distance = 10,
                yardLine = 25, // Kickoff touchback
                homeScore = currentState.homeScore + 7, // Assume XP is good
                awayScore = currentState.awayScore,
                clockSeconds = (currentState.clockSeconds - 30).coerceAtLeast(0)
            )
        }

        val newYardLine = (currentState.yardLine + result.yardsGained).coerceIn(0, 100)
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
                    clockSeconds = (currentState.clockSeconds - 30).coerceAtLeast(0)
                )
            }
            updatedDown to (yardsForFirstDown - result.yardsGained)
        }

        return currentState.copy(
            down = newDown,
            distance = newDistance,
            yardLine = newYardLine,
            clockSeconds = (currentState.clockSeconds - 30).coerceAtLeast(0)
        )
    }
}
