package com.xuan.gridironmanager.domain.sim.ai

import com.xuan.gridironmanager.domain.model.PlayerAttributes
import com.xuan.gridironmanager.domain.sim.playbook.DefensiveCall
import kotlin.random.Random

/** The pre-snap coverage read shown to the human quarterback, e.g. "Read: Cover 3 — Confident". */
data class CoverageHint(
    val coverageLabel: String,
    val isConfident: Boolean,
) {
    val text: String get() = "Read: $coverageLabel — ${if (isConfident) "Confident" else "Uncertain"}"
}

/** The quarterback's pre-snap read of a disguised coverage. */
object CoverageRead {
    /** Seconds of read time a fooled quarterback loses. */
    const val FOOLED_READ_DELAY_SEC = 0.4f

    private const val BASE_CHANCE = 0.15f
    private const val CHANCE_PER_ATTRIBUTE_POINT = 0.0035f
    private const val MAX_CHANCE = 0.90f

    /** Probability the QB correctly identifies the TRUE coverage before the snap. */
    fun identifyProbability(attributes: PlayerAttributes): Float = identifyProbability(attributes.awareness, attributes.playRecognition)

    fun identifyProbability(
        awareness: Int,
        playRecognition: Int,
    ): Float = (BASE_CHANCE + CHANCE_PER_ATTRIBUTE_POINT * (awareness + playRecognition)).coerceIn(BASE_CHANCE, MAX_CHANCE)

    /**
     * What the human QB is told before the snap. A successful read names the true coverage; a failed one names the
     * shell, so a fooled hint never reveals the true call. Null when the call has no disguise.
     */
    fun hint(
        call: DefensiveCall,
        identified: Boolean,
    ): CoverageHint? {
        val disguise = call.disguise ?: return null
        val coverage = (if (identified) call.coverage else disguise.shellCoverage) ?: return null
        return CoverageHint(coverage.label, isConfident = identified)
    }

    /** One roll per pass play. */
    fun roll(
        attributes: PlayerAttributes,
        random: Random,
    ): Boolean = random.nextFloat() < identifyProbability(attributes)
}
