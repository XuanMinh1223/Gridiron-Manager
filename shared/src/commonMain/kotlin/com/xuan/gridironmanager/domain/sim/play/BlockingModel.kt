package com.xuan.gridironmanager.domain.sim.play

import com.xuan.gridironmanager.domain.model.PlayType
import com.xuan.gridironmanager.domain.model.Vector3D
import com.xuan.gridironmanager.domain.sim.movement.MovementEngine
import com.xuan.gridironmanager.domain.sim.movement.PlayerRole
import com.xuan.gridironmanager.domain.sim.movement.RunningPlayer
import kotlin.math.pow
import kotlin.random.Random

/**
 * One-on-one blocking. At the snap each rusher is picked up by the nearest free blocker; spare blockers double-team
 * on pass plays or climb to the second level on runs. Each block is won over time by the defender, faster the more
 * they out-rate their blocker, and a rusher nobody picks up is free from the snap.
 */
class BlockingModel(
    offense: List<RunningPlayer>,
    defense: List<RunningPlayer>,
    private val playType: PlayType,
    random: Random,
) {
    private class Block(
        val blocker: RunningPlayer,
        val defender: RunningPlayer,
        var winRatePerSec: Float,
    ) {
        var progress = 0f
        var isEngaged = false
        var isShed = false
        var helpers = 0
    }

    private val blocks: List<Block>
    private val blocksByDefenderId: Map<String, Block>

    init {
        val available = offense.filter { it.role == PlayerRole.BLOCKER }.toMutableList()
        val rushers = defense.filter { it.role == PlayerRole.PASS_RUSHER || it.role == PlayerRole.BLITZER }
        val assigned = mutableListOf<Block>()

        fun pickUp(defender: RunningPlayer) {
            val blocker = available.minByOrNull { it.currentPos.distance2DTo(defender.currentPos) } ?: return
            available.remove(blocker)
            val block = Block(blocker, defender, winRatePerSec(blocker, defender, random))
            // Linemen facing each other across the line are engaged from the snap
            if (blocker.currentPos.distance2DTo(defender.currentPos) <= LINE_CONTACT_YDS) {
                block.isEngaged = true
                blocker.blockingId = defender.id
            }
            assigned += block
        }

        // Linemen first, then blitzers: an overloaded line leaves the blitzer free
        rushers.sortedBy { if (it.role == PlayerRole.BLITZER) 1 else 0 }.forEach(::pickUp)
        val lineBlockCount = assigned.size

        if (playType == PlayType.RUN) {
            // Spare blockers climb to the nearest linebackers and safeties
            defense
                .filter { it.role != PlayerRole.PASS_RUSHER && it.role != PlayerRole.BLITZER }
                .sortedBy { defender -> available.minOfOrNull { it.currentPos.distance2DTo(defender.currentPos) } ?: Float.MAX_VALUE }
                .filter { defender -> available.any { it.currentPos.distance2DTo(defender.currentPos) <= SECOND_LEVEL_REACH_YDS } }
                .forEach(::pickUp)
            // Blocks on the move at the second level are harder to sustain
            assigned.drop(lineBlockCount).forEach { it.winRatePerSec *= SECOND_LEVEL_SHED_FACTOR }
        } else {
            // Spare blockers help on the nearest rusher
            available.forEach { helper -> assigned.minByOrNull { it.defender.currentPos.distance2DTo(helper.currentPos) }?.let { it.helpers++ } }
        }

        blocks = assigned
        blocksByDefenderId = assigned.associateBy { it.defender.id }
    }

    /** A defender is held while engaged in a block they have not yet shed. */
    fun isBlocked(defender: RunningPlayer): Boolean = blocksByDefenderId[defender.id]?.let { it.isEngaged && !it.isShed } ?: false

    /**
     * Advances every block: blockers close on their man, then hold the point while the defender drives towards
     * [targetFor] (the passer or ball carrier), until the defender sheds the block.
     */
    fun tick(
        tickDeltaSec: Float,
        targetFor: (RunningPlayer) -> Vector3D?,
    ) {
        for (block in blocks) {
            if (block.isShed) continue
            val blocker = block.blocker
            val defender = block.defender

            if (!block.isEngaged) {
                MovementEngine.pursue(blocker, defender.currentPos, tickDeltaSec)
                block.isEngaged = blocker.currentPos.distance2DTo(defender.currentPos) <= CONTACT_YDS
                if (block.isEngaged) blocker.blockingId = defender.id
                continue
            }

            block.progress += tickDeltaSec * block.winRatePerSec / (1f + block.helpers * DOUBLE_TEAM_FACTOR)
            if (block.progress >= 1f) {
                block.isShed = true
                blocker.blockingId = null
                continue
            }

            // Hold the point: the defender drives slowly towards the ball, with the blocker squared up in front
            val target = targetFor(defender) ?: continue
            MovementEngine.moveToward(defender, target, DRIVE_SPEED_YDS_PER_SEC, tickDeltaSec)
            val toTarget = target.distance2DTo(defender.currentPos)
            if (toTarget > 0f) {
                val ratio = CONTACT_YDS / toTarget
                blocker.currentPos =
                    defender.currentPos.copy(
                        x = defender.currentPos.x + (target.x - defender.currentPos.x) * ratio,
                        y = defender.currentPos.y + (target.y - defender.currentPos.y) * ratio,
                    )
            }
        }
    }

    /**
     * How quickly [defender] beats [blocker]: one [meanSecondsToShed] on average between equals, twice as fast for
     * every [RATING_DOUBLING] points of advantage, with rep-to-rep variance.
     */
    private fun winRatePerSec(
        blocker: RunningPlayer,
        defender: RunningPlayer,
        random: Random,
    ): Float {
        val (blockerPower, defenderPower) =
            if (playType == PlayType.RUN) {
                (blocker.attributes.strength + blocker.attributes.blockPass) / 2f to (defender.attributes.strength + defender.attributes.tackle) / 2f
            } else {
                blocker.attributes.blockPass.toFloat() to (defender.attributes.strength + defender.attributes.speed) / 2f
            }
        val advantage = 2f.pow((defenderPower - blockerPower) / RATING_DOUBLING)
        val variance = MIN_VARIANCE + random.nextFloat() * (MAX_VARIANCE - MIN_VARIANCE)
        return advantage * variance / meanSecondsToShed()
    }

    private fun meanSecondsToShed(): Float =
        when (playType) {
            PlayType.RUN -> RUN_SHED_SEC
            PlayType.FIELD_GOAL -> FIELD_GOAL_SHED_SEC
            else -> PASS_SHED_SEC
        }

    private companion object {
        const val CONTACT_YDS = 1.2f
        const val LINE_CONTACT_YDS = 2.5f
        const val DRIVE_SPEED_YDS_PER_SEC = 0.8f
        const val DOUBLE_TEAM_FACTOR = 0.8f
        const val SECOND_LEVEL_REACH_YDS = 8f
        const val SECOND_LEVEL_SHED_FACTOR = 3f
        const val RATING_DOUBLING = 20f
        const val MIN_VARIANCE = 0.55f
        const val MAX_VARIANCE = 1.6f
        const val PASS_SHED_SEC = 0.9f
        const val RUN_SHED_SEC = 2.4f
        const val FIELD_GOAL_SHED_SEC = 3f
    }
}
