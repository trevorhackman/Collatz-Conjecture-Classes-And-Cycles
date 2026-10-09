package hackman.trevor.collatz

import java.math.BigInteger

internal fun BigInteger.isEven(): Boolean = !testBit(0)

/**
 * One Collatz step.
 * Even [n] goes to n/2. Odd [n] goes to 3n+1.
 */
fun collatzStep(n: BigInteger): BigInteger {
    require(n > BigInteger.ZERO) { "Collatz step is defined on positive integers, was $n" }
    return if (n.isEven()) n.shiftRight(1) else n.multiply(BigInteger.valueOf(3)).add(BigInteger.ONE)
}

/** First iterate strictly smaller than the start, and how many steps that took. */
data class StoppingTime(
    val steps: Int,
    val value: BigInteger,
)

enum class StopReason {
    /** Walk reached 1. */
    ReachedOne,

    /** A value repeated before the walk reached 1. The repeated value is the last entry. */
    Cycle,

    /** [trajectoryOf] stopped because it hit its step cap. */
    StepLimit,
}

/**
 * Collatz walk starting at [start], which is included in [values].
 *
 * [stoppingTime] is the first entry strictly below [start], when one occurs inside this walk.
 * The walk itself continues until 1, a repeated value, or [maxSteps].
 * 1 is a cycle, so its stopping time is absent: nothing in the cycle is smaller than 1.
 */
data class Trajectory(
    val start: BigInteger,
    val values: List<BigInteger>,
    val stoppingTime: StoppingTime?,
    val reason: StopReason,
)

/**
 * Walks [start] until 1, a repeated value, or [maxSteps] applications of [collatzStep].
 */
fun trajectoryOf(start: BigInteger, maxSteps: Int = 100_000): Trajectory {
    require(start > BigInteger.ZERO) { "trajectory starts at a positive integer, was $start" }
    require(maxSteps > 0) { "maxSteps must be positive, was $maxSteps" }

    val values = ArrayList<BigInteger>(minOf(maxSteps + 1, 256))
    values += start
    if (start == BigInteger.ONE) {
        return Trajectory(start, values, stoppingTime = null, reason = StopReason.ReachedOne)
    }

    val seen = HashSet<BigInteger>()
    seen += start
    var current = start
    var stoppingTime: StoppingTime? = null
    var steps = 0
    while (steps < maxSteps) {
        current = collatzStep(current)
        steps++
        if (stoppingTime == null && current < start) {
            stoppingTime = StoppingTime(steps, current)
        }
        values += current
        if (current == BigInteger.ONE) {
            return Trajectory(start, values, stoppingTime, StopReason.ReachedOne)
        }
        if (!seen.add(current)) {
            return Trajectory(start, values, stoppingTime, StopReason.Cycle)
        }
    }
    return Trajectory(start, values, stoppingTime, StopReason.StepLimit)
}
