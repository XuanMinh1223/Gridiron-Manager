package com.xuan.gridironmanager.domain.sim.match

sealed interface PlayOutcome {
    val description: String

    data class Scrimmage(
        val result: PlayResult,
    ) : PlayOutcome {
        override val description get() = result.description
    }

    /** A kickoff or punt. */
    data class Kick(
        val result: KickResult,
    ) : PlayOutcome {
        override val description get() = result.description
    }

    /** A field goal or extra-point kick. */
    data class FieldGoal(
        val result: FieldGoalResult,
    ) : PlayOutcome {
        override val description get() = result.description
    }
}
