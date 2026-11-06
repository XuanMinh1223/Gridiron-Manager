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
    /** What each team has actually done this game. Empty until plays are recorded, so a fresh caller is unbiased. */
    val tendencies = TendencyTracker()

    /** Books a play that was really run (the team with the ball is the one whose tendency it adds to). */
    fun recordPlay(
        state: GameState,
        offensivePlay: OffensivePlay,
        defensiveCall: DefensiveCall,
    ) = tendencies.record(possessingTeamId(state), state, offensivePlay, defensiveCall)

    private fun possessingTeamId(state: GameState) = if (state.isHomePossession) matchup.homeTeam.id else matchup.awayTeam.id

    fun callOffense(state: GameState): OffensivePlay =
        when (state.phase) {
            GamePhase.KICKOFF -> Playbook.KICKOFF
            GamePhase.EXTRA_POINT -> if (shouldGoForTwo(state)) shortYardagePlay() else Playbook.EXTRA_POINT
            GamePhase.SCRIMMAGE -> clockManagementPlay(state) ?: if (state.down == 4) callFourthDown(state) else callByDownAndDistance(state)
        }

    /**
     * Kneel to run out the clock when leading late, spike to stop it when trailing by a score with no timeouts. Both
     * are situational only: never random, never on 4th down, and only at the end of a half (a kneel in the first
     * quarter would be absurd). No kneel on the goal line, where it would be a safety.
     */
    private fun clockManagementPlay(state: GameState): OffensivePlay? {
        val endOfHalf = state.quarter % 2 == 0
        return when {
            !endOfHalf || state.down > 3 -> null
            state.possessionScoreMargin > 0 && state.clockSeconds <= KNEEL_SECONDS && state.yardLine > 2 -> Playbook.KNEEL
            state.possessionScoreMargin in -ONE_SCORE_MARGIN..-1 && state.clockSeconds <= SPIKE_SECONDS && state.possessingTimeouts == 0 -> Playbook.SPIKE
            else -> null
        }
    }

    fun callDefense(state: GameState): DefensiveCall {
        val options =
            when {
                state.distance >= LONG_YARDAGE -> listOf(Playbook.NICKEL_COVER_2, Playbook.NICKEL_MAN, Playbook.COVER_3, Playbook.NICKEL_BLITZ)
                state.distance <= SHORT_YARDAGE -> listOf(Playbook.BASE_MAN, Playbook.COVER_3, Playbook.NICKEL_BLITZ)
                else -> Playbook.defensiveCalls
            }
        val call = weightedDefense(options, state)
        if (state.phase != GamePhase.SCRIMMAGE && state.phase != GamePhase.EXTRA_POINT) return call
        val disguised = Playbook.disguisedCounterparts[call.id]
        return if (random.nextFloat() < DISGUISE_CHANCE && disguised != null) disguised.random(random) else call
    }

    /**
     * Picks from [options] with weights: prevent coverage when protecting a big lead late, otherwise leaning on the
     * opponent's run/pass tendency on this down. With no tendency yet every weight is 1 and it is a plain random pick.
     */
    private fun weightedDefense(
        options: List<DefensiveCall>,
        state: GameState,
    ): DefensiveCall {
        val weights = options.map { defenseWeight(it, state) }
        if (weights.all { it == weights.first() }) return options.random(random)

        var roll = random.nextFloat() * weights.sum()
        for ((index, weight) in weights.withIndex()) {
            roll -= weight
            if (roll < 0f) return options[index]
        }
        return options.last()
    }

    private fun defenseWeight(
        call: DefensiveCall,
        state: GameState,
    ): Float {
        // The defense leads by 9+ late: sit back in deep coverage
        val protectingLead = isFinalPeriod(state) && state.possessionScoreMargin <= -PREVENT_LEAD && state.clockSeconds <= PREVENT_SECONDS
        if (protectingLead) return if (call.id in PREVENT_CALL_IDS) 2f else 0.5f

        val team = possessingTeamId(state)
        return when {
            tendencies.runRate(team, state.down) >= RUN_HEAVY -> if (call.id in RUN_STOP_CALL_IDS) 1.5f else 0.75f
            tendencies.passRate(team, state.down) >= PASS_HEAVY -> if (call.id in PASS_CALL_IDS) 1.5f else 0.75f
            else -> 1f
        }
    }

    private fun callFourthDown(state: GameState): OffensivePlay {
        val kickerRange = AttributeTranslator.calculateFieldGoalRangeYards(kickerFor(state).kickPower)
        val inFieldGoalRange = Rules.fieldGoalDistance(state.yardLine) <= minOf(kickerRange - FIELD_GOAL_RANGE_BUFFER_YDS, MAX_FIELD_GOAL_ATTEMPT_YDS)
        val isDesperate =
            isFinalPeriod(state) &&
                state.clockSeconds <= DESPERATION_SECONDS &&
                (state.possessionScoreMargin < -Rules.FIELD_GOAL_POINTS || (state.possessionScoreMargin < 0 && !inFieldGoalRange))

        return when {
            isDesperate -> callByDownAndDistance(state)
            state.distance <= 1 && state.yardLine >= GO_FOR_IT_ON_FOURTH_AND_ONE_YARD_LINE -> shortYardagePlay()
            inFieldGoalRange -> Playbook.FIELD_GOAL
            // Too far to kick but too close to punt
            state.yardLine >= NO_MANS_LAND_YARD_LINE && state.distance <= SHORT_YARDAGE + 1 -> callByDownAndDistance(state)
            else -> Playbook.PUNT
        }
    }

    private fun callByDownAndDistance(state: GameState): OffensivePlay {
        val distance = state.distance
        val baseRunChance =
            when {
                distance <= SHORT_YARDAGE -> 0.65f
                distance >= LONG_YARDAGE -> 0.25f
                else -> 0.45f
            }
        // Late-game mix only: hurry up when trailing, bleed the clock when leading. The between-play runoff is untouched.
        val runChance =
            when {
                isFinalPeriod(state) && state.possessionScoreMargin < 0 && state.clockSeconds <= HURRY_UP_SECONDS -> baseRunChance * 0.5f
                isFinalPeriod(state) && state.possessionScoreMargin > 0 && state.clockSeconds <= BLEED_CLOCK_SECONDS -> maxOf(baseRunChance, 0.75f)
                else -> baseRunChance
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
    private fun shouldGoForTwo(state: GameState): Boolean = isFinalPeriod(state) && state.possessionScoreMargin in GO_FOR_TWO_MARGINS

    /** The fourth quarter, or overtime, which is played as an extension of it. */
    private fun isFinalPeriod(state: GameState): Boolean = state.quarter >= Rules.QUARTERS || state.isOvertime

    private fun kickerFor(state: GameState): PlayerAttributes {
        val roster = if (state.isHomePossession) matchup.homeRoster else matchup.awayRoster
        return roster.firstOrNull { it.position == Position.K.abbreviation }?.attributes ?: PlayerAttributes.AVERAGE
    }

    private companion object {
        const val SHORT_YARDAGE = 2
        const val LONG_YARDAGE = 8

        /** Prototype: chance the CPU swaps its coverage pick for a disguised version of it. */
        const val DISGUISE_CHANCE = 0.30f
        const val FIELD_GOAL_RANGE_BUFFER_YDS = 5
        const val MAX_FIELD_GOAL_ATTEMPT_YDS = 55
        const val GO_FOR_IT_ON_FOURTH_AND_ONE_YARD_LINE = 40
        const val NO_MANS_LAND_YARD_LINE = 60
        const val DESPERATION_SECONDS = 300
        const val KNEEL_SECONDS = 40
        const val SPIKE_SECONDS = 60
        const val ONE_SCORE_MARGIN = 8
        const val HURRY_UP_SECONDS = 120
        const val BLEED_CLOCK_SECONDS = 180
        const val PREVENT_LEAD = 9
        const val PREVENT_SECONDS = 120

        /** Tendency thresholds: once a team runs this often (or passes this often) on a down, the defense leans into it. */
        const val RUN_HEAVY = 0.60f
        const val PASS_HEAVY = 0.65f

        val RUN_STOP_CALL_IDS = setOf(Playbook.BASE_MAN.id, Playbook.COVER_3.id, Playbook.NICKEL_BLITZ.id)
        val PASS_CALL_IDS = setOf(Playbook.COVER_2.id, Playbook.COVER_3.id, Playbook.NICKEL_COVER_2.id)
        val PREVENT_CALL_IDS = setOf(Playbook.COVER_3.id, Playbook.NICKEL_COVER_2.id)

        /** Score margins, after the touchdown, where a two-point try is the better percentage play. */
        val GO_FOR_TWO_MARGINS = setOf(-2, -5, -10, 1, 5)
    }
}
