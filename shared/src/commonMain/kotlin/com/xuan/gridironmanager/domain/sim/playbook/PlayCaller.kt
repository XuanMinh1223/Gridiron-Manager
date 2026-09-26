package com.xuan.gridironmanager.domain.sim.playbook

import com.xuan.gridironmanager.domain.model.Matchup
import com.xuan.gridironmanager.domain.model.PlayerAttributes
import com.xuan.gridironmanager.domain.model.Position
import com.xuan.gridironmanager.domain.sim.AttributeTranslator
import com.xuan.gridironmanager.domain.sim.match.GamePhase
import com.xuan.gridironmanager.domain.sim.match.GameState
import com.xuan.gridironmanager.domain.sim.match.Rules
import kotlin.random.Random

/** CPU play calling for either side of the ball, based on the game situation. */
class PlayCaller(
    private val matchup: Matchup,
    private val random: Random,
) {
    fun callOffense(state: GameState): OffensivePlay =
        when (state.phase) {
            GamePhase.KICKOFF -> Playbook.KICKOFF
            GamePhase.EXTRA_POINT -> if (shouldGoForTwo(state)) shortYardagePlay() else Playbook.EXTRA_POINT
            GamePhase.SCRIMMAGE -> if (state.down == 4) callFourthDown(state) else callByDownAndDistance(state.distance)
        }

    fun callDefense(state: GameState): DefensiveCall {
        val options =
            when {
                state.distance >= LONG_YARDAGE -> listOf(Playbook.NICKEL_COVER_2, Playbook.NICKEL_MAN, Playbook.COVER_3, Playbook.NICKEL_BLITZ)
                state.distance <= SHORT_YARDAGE -> listOf(Playbook.BASE_MAN, Playbook.COVER_3, Playbook.NICKEL_BLITZ)
                else -> Playbook.defensiveCalls
            }
        return options.random(random)
    }

    private fun callFourthDown(state: GameState): OffensivePlay {
        val kickerRange = AttributeTranslator.calculateFieldGoalRangeYards(kickerFor(state).kickPower)
        val inFieldGoalRange = Rules.fieldGoalDistance(state.yardLine) <= minOf(kickerRange - FIELD_GOAL_RANGE_BUFFER_YDS, MAX_FIELD_GOAL_ATTEMPT_YDS)
        val isDesperate =
            state.quarter == Rules.QUARTERS &&
                state.clockSeconds <= DESPERATION_SECONDS &&
                (state.possessionScoreMargin < -Rules.FIELD_GOAL_POINTS || (state.possessionScoreMargin < 0 && !inFieldGoalRange))

        return when {
            isDesperate -> callByDownAndDistance(state.distance)
            state.distance <= 1 && state.yardLine >= GO_FOR_IT_ON_FOURTH_AND_ONE_YARD_LINE -> shortYardagePlay()
            inFieldGoalRange -> Playbook.FIELD_GOAL
            // Too far to kick but too close to punt
            state.yardLine >= NO_MANS_LAND_YARD_LINE && state.distance <= SHORT_YARDAGE + 1 -> callByDownAndDistance(state.distance)
            else -> Playbook.PUNT
        }
    }

    private fun callByDownAndDistance(distance: Int): OffensivePlay {
        val runChance =
            when {
                distance <= SHORT_YARDAGE -> 0.65f
                distance >= LONG_YARDAGE -> 0.25f
                else -> 0.45f
            }
        if (random.nextFloat() < runChance) return playIn(PlayCategory.RUN)

        // Weights for short / medium / long passes
        val (short, medium) =
            when {
                distance >= 15 -> 0.2f to 0.4f
                distance >= LONG_YARDAGE -> 0.3f to 0.45f
                else -> 0.55f to 0.35f
            }
        val roll = random.nextFloat()
        return when {
            roll < short -> playIn(PlayCategory.SHORT_PASS)
            roll < short + medium -> playIn(PlayCategory.MEDIUM_PASS)
            else -> playIn(PlayCategory.LONG_PASS)
        }
    }

    private fun shortYardagePlay() = playIn(if (random.nextBoolean()) PlayCategory.RUN else PlayCategory.SHORT_PASS)

    private fun playIn(category: PlayCategory) = Playbook.scrimmagePlays.filter { it.category == category }.random(random)

    /** Standard late-game chart: go for two when it ties the game, or makes the lead worth more than a field goal / touchdown. */
    private fun shouldGoForTwo(state: GameState): Boolean = state.quarter == Rules.QUARTERS && state.possessionScoreMargin in GO_FOR_TWO_MARGINS

    private fun kickerFor(state: GameState): PlayerAttributes {
        val roster = if (state.isHomePossession) matchup.homeRoster else matchup.awayRoster
        return roster.firstOrNull { it.position == Position.K.abbreviation }?.attributes ?: PlayerAttributes.AVERAGE
    }

    private companion object {
        const val SHORT_YARDAGE = 2
        const val LONG_YARDAGE = 8
        const val FIELD_GOAL_RANGE_BUFFER_YDS = 5
        const val MAX_FIELD_GOAL_ATTEMPT_YDS = 55
        const val GO_FOR_IT_ON_FOURTH_AND_ONE_YARD_LINE = 40
        const val NO_MANS_LAND_YARD_LINE = 60
        const val DESPERATION_SECONDS = 300

        /** Score margins, after the touchdown, where a two-point try is the better percentage play. */
        val GO_FOR_TWO_MARGINS = setOf(-2, -5, -10, 1, 5)
    }
}
