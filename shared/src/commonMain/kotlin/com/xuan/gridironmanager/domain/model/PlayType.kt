package com.xuan.gridironmanager.domain.model

enum class PlayType {
    RUN,
    PASS,
    KICKOFF,
    PUNT,
    FIELD_GOAL,

    /** The QB takes the snap and kneels to run the clock. */
    KNEEL,

    /** The QB spikes the ball to stop the clock. */
    SPIKE,
}
