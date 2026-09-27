package com.xuan.gridironmanager.domain.sim.match

import kotlin.math.roundToInt

/** Applies the rules of the game to the outcome of each play. Pure: the same inputs always produce the same state. */
class DriveEngine {
    /**
     * Returns the game state after [outcome], which took [playDurationSec] of game clock.
     *
     * @throws IllegalArgumentException if [outcome] cannot happen in the current [GamePhase] (e.g. a punt on a kickoff).
     */
    fun resolve(
        state: GameState,
        outcome: PlayOutcome,
        playDurationSec: Float = 0f,
    ): GameState {
        if (state.isGameOver) return state

        return when (state.phase) {
            // Tries are untimed, and the quarter cannot end until the try is over
            GamePhase.EXTRA_POINT -> {
                advanceClock(resolveExtraPoint(state, outcome))
            }

            GamePhase.KICKOFF -> {
                advanceClock(resolveKickoff(runClock(state, playDurationSec), outcome))
            }

            GamePhase.SCRIMMAGE -> {
                val next = resolveScrimmage(runClock(state, playDurationSec), outcome)
                if (next.phase == GamePhase.EXTRA_POINT) next else advanceClock(next)
            }
        }
    }

    private fun resolveKickoff(
        state: GameState,
        outcome: PlayOutcome,
    ): GameState {
        require(outcome is PlayOutcome.Kick) { "Only a kick can follow a kickoff, got $outcome" }
        val result = outcome.result
        require(result.outcomeType != KickOutcomeType.BLOCKED_RECOVERED && result.outcomeType != KickOutcomeType.FAIR_CATCH) { "Invalid free-kick outcome: ${result.outcomeType}" }
        if (result.outcomeType == KickOutcomeType.RETURN_TOUCHDOWN) return scoreKickReturnTouchdown(state)
        val spot =
            when (result.outcomeType) {
                KickOutcomeType.LANDING_ZONE_TOUCHBACK -> Rules.LANDING_ZONE_TOUCHBACK_YARD_LINE
                KickOutcomeType.TOUCHBACK -> Rules.KICKOFF_TOUCHBACK_YARD_LINE
                KickOutcomeType.KICK_OUT_OF_BOUNDS, KickOutcomeType.SHORT_KICK -> {
                    val penaltySpot = if (state.isSafetyFreeKick) Rules.SAFETY_KICK_OUT_OF_BOUNDS_YARD_LINE else Rules.KICKOFF_OUT_OF_BOUNDS_YARD_LINE
                    maxOf(penaltySpot, result.endYardLine)
                }
                else -> result.endYardLine
            }
        return if (result.recoveredByKickingTeam && spot == 0) scoreTouchdown(state) else if (result.recoveredByKickingTeam) retainPossession(state, Rules.FIELD_LENGTH_YDS - spot) else changeOfPossession(state, spot)
    }

    private fun resolveExtraPoint(
        state: GameState,
        outcome: PlayOutcome,
    ): GameState {
        val points =
            when (outcome) {
                is PlayOutcome.FieldGoal -> if (outcome.result.isGood) Rules.EXTRA_POINT_POINTS else 0
                // Anything short of reaching the end zone (including a turnover) simply ends the try
                is PlayOutcome.Scrimmage -> if (outcome.result.isTouchdown) Rules.TWO_POINT_CONVERSION_POINTS else 0
                is PlayOutcome.Kick -> throw IllegalArgumentException("A try cannot be a kickoff or punt, got $outcome")
            }
        return kickoffBy(addPoints(state, points, toPossessingTeam = true))
    }

    private fun resolveScrimmage(
        state: GameState,
        outcome: PlayOutcome,
    ): GameState =
        when (outcome) {
            // Punt
            is PlayOutcome.Kick -> {
                val result = outcome.result
                when {
                    result.outcomeType == KickOutcomeType.RETURN_TOUCHDOWN -> scoreKickReturnTouchdown(state)
                    result.recoveredByKickingTeam && result.endYardLine == 0 -> scoreTouchdown(state)
                    result.recoveredByKickingTeam -> retainPossession(state, Rules.FIELD_LENGTH_YDS - result.endYardLine)
                    else -> changeOfPossession(state, if (result.isTouchback) Rules.PUNT_TOUCHBACK_YARD_LINE else result.endYardLine)
                }
            }

            is PlayOutcome.FieldGoal -> {
                if (outcome.result.isGood) {
                    kickoffBy(addPoints(state, Rules.FIELD_GOAL_POINTS, toPossessingTeam = true))
                } else {
                    val spotOfKick = state.yardLine - Rules.FIELD_GOAL_SNAP_DEPTH_YDS
                    changeOfPossession(state, maxOf(Rules.MISSED_FIELD_GOAL_MIN_YARD_LINE, Rules.FIELD_LENGTH_YDS - spotOfKick))
                }
            }

            is PlayOutcome.Scrimmage -> {
                resolveScrimmagePlay(state, outcome.result)
            }
        }

    private fun resolveScrimmagePlay(
        state: GameState,
        result: PlayResult,
    ): GameState {
        // Where the play ended, from the offense's point of view. Beyond 0..100 means inside an end zone.
        val spot = state.yardLine + result.yardsGained
        val newYardLine = spot.coerceIn(0, Rules.FIELD_LENGTH_YDS)

        val nextState =
            when {
                result.isTurnover && spot <= 0 -> {
                    // Defense recovers in the offense's end zone
                    scoreTouchdown(changeOfPossession(state, yardLine = Rules.FIELD_LENGTH_YDS))
                }

                result.isTurnover -> {
                    val defenseYardLine = Rules.FIELD_LENGTH_YDS - newYardLine
                    // A turnover in the defense's own end zone is downed for a touchback
                    changeOfPossession(
                        state,
                        yardLine = if (defenseYardLine <= 0) Rules.INTERCEPTION_TOUCHBACK_YARD_LINE else defenseYardLine,
                    )
                }

                result.isTouchdown || spot >= Rules.FIELD_LENGTH_YDS -> {
                    scoreTouchdown(state)
                }

                spot <= 0 -> {
                    // Safety: two points to the defense, and the offense free-kicks from its own 20
                    addPoints(state, Rules.SAFETY_POINTS, toPossessingTeam = false).copy(
                        down = 1,
                        distance = Rules.FIRST_DOWN_DISTANCE,
                        yardLine = Rules.SAFETY_KICK_YARD_LINE,
                        phase = GamePhase.KICKOFF,
                        isSafetyFreeKick = true,
                    )
                }

                result.yardsGained >= state.distance -> {
                    state.copy(
                        down = 1,
                        distance = firstDownDistance(newYardLine),
                        yardLine = newYardLine,
                    )
                }

                state.down >= 4 -> {
                    // Turnover on downs
                    changeOfPossession(state, yardLine = Rules.FIELD_LENGTH_YDS - newYardLine)
                }

                else -> {
                    state.copy(
                        down = state.down + 1,
                        distance = state.distance - result.yardsGained,
                        yardLine = newYardLine,
                    )
                }
            }

        val clockKeepsRunning =
            !result.clockStops &&
                nextState.phase == GamePhase.SCRIMMAGE &&
                nextState.isHomePossession == state.isHomePossession
        return if (clockKeepsRunning) runClock(nextState, Rules.BETWEEN_PLAY_RUNOFF_SEC.toFloat()) else nextState
    }

    private fun runClock(
        state: GameState,
        seconds: Float,
    ): GameState = state.copy(clockSeconds = (state.clockSeconds - seconds.roundToInt()).coerceAtLeast(0))

    /** Six points to the possessing team, who then attempt the try from the two-point line. */
    private fun scoreTouchdown(state: GameState): GameState =
        addPoints(state, Rules.TOUCHDOWN_POINTS, toPossessingTeam = true).copy(
            down = 1,
            distance = Rules.FIELD_LENGTH_YDS - Rules.TWO_POINT_YARD_LINE,
            yardLine = Rules.TWO_POINT_YARD_LINE,
            phase = GamePhase.EXTRA_POINT,
        )

    /** A kick return scores for the receiving team, which then owns the try. */
    private fun scoreKickReturnTouchdown(state: GameState): GameState = scoreTouchdown(state.copy(isHomePossession = !state.isHomePossession))

    /** The possessing team kicks off, as it does after scoring. */
    private fun kickoffBy(state: GameState): GameState =
        state.copy(
            down = 1,
            distance = Rules.FIRST_DOWN_DISTANCE,
            yardLine = Rules.KICKOFF_YARD_LINE,
            phase = GamePhase.KICKOFF,
            isSafetyFreeKick = false,
        )

    private fun addPoints(
        state: GameState,
        points: Int,
        toPossessingTeam: Boolean,
    ): GameState {
        val toHome = state.isHomePossession == toPossessingTeam
        return state.copy(
            homeScore = if (toHome) state.homeScore + points else state.homeScore,
            awayScore = if (toHome) state.awayScore else state.awayScore + points,
        )
    }

    private fun changeOfPossession(
        state: GameState,
        yardLine: Int,
    ): GameState =
        state.copy(
            down = 1,
            distance = firstDownDistance(yardLine),
            yardLine = yardLine,
            isHomePossession = !state.isHomePossession,
            phase = GamePhase.SCRIMMAGE,
            isSafetyFreeKick = false,
        )

    private fun retainPossession(state: GameState, yardLine: Int): GameState =
        state.copy(down = 1, distance = firstDownDistance(yardLine), yardLine = yardLine, phase = GamePhase.SCRIMMAGE, isSafetyFreeKick = false)

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
                phase = GamePhase.KICKOFF,
                isSafetyFreeKick = false,
            )
        } else {
            rolledOver
        }
    }

    /** Inside the opponent's 10 the distance is "and goal". */
    private fun firstDownDistance(yardLine: Int): Int = minOf(Rules.FIRST_DOWN_DISTANCE, Rules.FIELD_LENGTH_YDS - yardLine).coerceAtLeast(1)
}
