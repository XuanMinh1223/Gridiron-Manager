package com.xuan.gridironmanager.domain.sim.match

class DriveEngine {
    fun resolvePlay(
        currentState: GameState,
        result: PlayResult,
    ): GameState {
        if (currentState.isGameOver) return currentState

        val newYardLine = (currentState.yardLine + result.yardsGained).coerceIn(0, Rules.FIELD_LENGTH_YDS)

        val nextState =
            when {
                result.isTurnover -> {
                    val defenseYardLine = Rules.FIELD_LENGTH_YDS - newYardLine
                    // A turnover in the defense's own end zone is downed for a touchback
                    changeOfPossession(
                        currentState,
                        yardLine = if (defenseYardLine <= 0) Rules.INTERCEPTION_TOUCHBACK_YARD_LINE else defenseYardLine,
                    )
                }

                result.isTouchdown || newYardLine >= Rules.FIELD_LENGTH_YDS -> {
                    scoreTouchdown(currentState)
                }

                result.yardsGained >= currentState.distance -> {
                    currentState.copy(
                        down = 1,
                        distance = firstDownDistance(newYardLine),
                        yardLine = newYardLine,
                    )
                }

                currentState.down >= 4 -> {
                    // Turnover on downs
                    changeOfPossession(currentState, yardLine = Rules.FIELD_LENGTH_YDS - newYardLine)
                }

                else -> {
                    currentState.copy(
                        down = currentState.down + 1,
                        distance = currentState.distance - result.yardsGained,
                        yardLine = newYardLine,
                    )
                }
            }

        val clockKeepsRunning =
            !result.clockStops &&
                nextState.isHomePossession == currentState.isHomePossession &&
                !nextState.isKickoffPending
        val runoff = if (clockKeepsRunning) Rules.BETWEEN_PLAY_RUNOFF_SEC else 0

        return advanceClock(nextState.copy(clockSeconds = (nextState.clockSeconds - runoff).coerceAtLeast(0)))
    }

    fun resolveKickoff(
        currentState: GameState,
        result: KickResult,
    ): GameState = resolveKick(currentState, if (result.isTouchback) Rules.KICKOFF_TOUCHBACK_YARD_LINE else result.endYardLine)

    fun resolvePunt(
        currentState: GameState,
        result: KickResult,
    ): GameState = resolveKick(currentState, if (result.isTouchback) Rules.PUNT_TOUCHBACK_YARD_LINE else result.endYardLine)

    private fun resolveKick(
        currentState: GameState,
        receivingYardLine: Int,
    ): GameState {
        if (currentState.isGameOver) return currentState
        return advanceClock(changeOfPossession(currentState, receivingYardLine))
    }

    private fun scoreTouchdown(state: GameState): GameState =
        state.copy(
            down = 1,
            distance = Rules.FIRST_DOWN_DISTANCE,
            yardLine = Rules.KICKOFF_YARD_LINE,
            homeScore = if (state.isHomePossession) state.homeScore + Rules.TOUCHDOWN_POINTS else state.homeScore,
            awayScore = if (!state.isHomePossession) state.awayScore + Rules.TOUCHDOWN_POINTS else state.awayScore,
            // The scoring team keeps possession in order to kick off
            isKickoffPending = true,
        )

    private fun changeOfPossession(
        state: GameState,
        yardLine: Int,
    ): GameState =
        state.copy(
            down = 1,
            distance = firstDownDistance(yardLine),
            yardLine = yardLine,
            isHomePossession = !state.isHomePossession,
            isKickoffPending = false,
        )

    /** Rolls the quarter over once the clock expires, handling halftime and the end of the game. */
    private fun advanceClock(state: GameState): GameState {
        if (state.clockSeconds > 0) return state

        if (state.quarter >= Rules.QUARTERS) {
            return state.copy(clockSeconds = 0, isGameOver = true)
        }

        val nextQuarter = state.quarter + 1
        val rolledOver = state.copy(quarter = nextQuarter, clockSeconds = Rules.QUARTER_LENGTH_SEC)

        return if (nextQuarter == 3) {
            // Halftime: the home team kicked the opening kickoff, so the away team kicks to start the second half
            rolledOver.copy(
                down = 1,
                distance = Rules.FIRST_DOWN_DISTANCE,
                yardLine = Rules.KICKOFF_YARD_LINE,
                isHomePossession = false,
                isKickoffPending = true,
            )
        } else {
            rolledOver
        }
    }

    /** Inside the opponent's 10 the distance is "and goal". */
    private fun firstDownDistance(yardLine: Int): Int = minOf(Rules.FIRST_DOWN_DISTANCE, Rules.FIELD_LENGTH_YDS - yardLine).coerceAtLeast(1)
}
