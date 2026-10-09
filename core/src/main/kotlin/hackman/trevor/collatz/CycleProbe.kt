package hackman.trevor.collatz

import java.math.BigInteger

/**
 * Longest accelerated cycle the probe will try.
 * Length 20 takes about half a minute. Each added length costs several times as much as the previous one.
 */
const val MAX_CYCLE_LENGTH = 24

private val THREE: BigInteger = BigInteger.valueOf(3)
private val FOUR: BigInteger = BigInteger.valueOf(4)

/**
 * One accelerated step of an odd cycle: divide 3x+1 by exactly [halvings] twos and land on an odd integer.
 * [start] is the least odd member when this describes a real cycle other than 1.
 */
data class OddCycle(
    val start: BigInteger,
    val halvings: List<Int>,
)

/**
 * Result of trying to solve the length-[length] cycle equation for a whole number x > 1.
 *
 * [trivial] is the known cycle 1 → 4 → 2 → 1, written as one accelerated step (3·1+1)/2^2 = 1.
 * It is the only positive integer solution of length 1. Longer lengths also admit x = 1 by repeating
 * that step, which is the same cycle, not a new one.
 */
data class CycleLengthResult(
    val length: Int,
    val formula: String,
    val closedForm: String,
    val branches: Long,
    val solutions: List<OddCycle>,
    val trivial: OddCycle?,
    val explanation: String,
)

/**
 * Proves accelerated cycles of length 1, then 2, then 3, and so on, cannot contain an odd integer greater than 1.
 *
 * Length is the number of steps (3x+1)/2^k. [proveThrough] does not go backward.
 */
class CycleProbe {
    private val computed = ArrayList<CycleLengthResult>()

    /** Largest length already solved. Zero before the first call. */
    val length: Int get() = computed.size

    fun results(): List<CycleLengthResult> = computed.toList()

    fun proveNext(): CycleLengthResult {
        check(length < MAX_CYCLE_LENGTH) { "cycle probe stops at length $MAX_CYCLE_LENGTH" }
        val result = proveCycleLength(length + 1)
        computed += result
        return result
    }

    /** Solves every length from 1 through [target]. Does nothing when [length] is already there. */
    fun proveThrough(target: Int) {
        require(target in 1..MAX_CYCLE_LENGTH) {
            "target must be in 1..$MAX_CYCLE_LENGTH, was $target"
        }
        while (length < target) proveNext()
    }
}

/**
 * Solves the length-[length] equation.
 *
 * For length 1 the equation is (3x+1)/2^k = x, so x = 1/(2^k − 3). The only positive integer solution is x = 1, k = 2.
 * For a longer cycle the least odd member x > 1 must take k = 1 on its first step: any larger k sends (3x+1)/2^k below x.
 * Every later exponent is then bounded, because the next odd member has to stay at least x, and each tuple is solved exactly.
 */
fun proveCycleLength(length: Int, caseLog: ((String) -> Unit)? = null): CycleLengthResult {
    require(length in 1..MAX_CYCLE_LENGTH) { "length must be in 1..$MAX_CYCLE_LENGTH, was $length" }
    val formula = nestedFormula(length)
    val closedForm = closedForm(length)
    if (length == 1) {
        return CycleLengthResult(
            length = 1,
            formula = formula,
            closedForm = closedForm,
            branches = 0,
            solutions = emptyList(),
            trivial = OddCycle(BigInteger.ONE, listOf(2)),
            explanation = "2^k - 3 divides 1, so 2^k - 3 = 1, k = 2, and x = 1. " +
                "k = 1 gives x = -1. k > 2 gives a positive fraction smaller than 1. No whole number x > 1.",
        )
    }
    val search = CycleSearch(length, caseLog)
    search.run()
    val explanation = if (search.solutions.isEmpty()) {
        "No whole number x > 1. The first exponent is 1, and the other ${length - 1} exponents were checked " +
            "across ${search.branches} tuples. x = 1 with every exponent equal to 2 only repeats the known cycle."
    } else {
        "Found ${search.solutions.size} cycle(s) with x > 1."
    }
    return CycleLengthResult(
        length = length,
        formula = formula,
        closedForm = closedForm,
        branches = search.branches,
        solutions = search.solutions,
        trivial = null,
        explanation = explanation,
    )
}

/**
 * Largest k such that (3z+1)/2^k can still be at least the cycle minimum, after [stepsAlreadyDone] accelerated steps.
 *
 * The largest possible z at that point is the path that divided by exactly one 2 each time,
 * z = (3/2)^j (x+1) − 1 with x ≥ 3. That forces 2^k < 4·(3/2)^j, i.e. 2^{k+j−2} < 3^j.
 */
internal fun maxExactHalvings(stepsAlreadyDone: Int): Int {
    require(stepsAlreadyDone >= 0)
    val threePower = THREE.pow(stepsAlreadyDone)
    var k = 1
    while (BigInteger.ONE.shiftLeft((k + stepsAlreadyDone - 2).coerceAtLeast(0)) < threePower ||
        k + stepsAlreadyDone - 2 < 0
    ) {
        k++
    }
    return k - 1
}

internal fun nestedFormula(length: Int): String {
    var body = "x"
    for (index in 1..length) {
        val inner = if (body == "x") "3x+1" else "3($body)+1"
        val exponent = if (length == 1) "k" else "k$index"
        body = "($inner)/2^$exponent"
    }
    return "$body = x"
}

internal fun closedForm(length: Int): String {
    val terms = (0 until length).joinToString(" + ") { index ->
        val coefficient = length - 1 - index
        val coefficientText = when (coefficient) {
            0 -> ""
            1 -> "3"
            else -> "3^$coefficient"
        }
        val powerText = when (index) {
            0 -> "1"
            1 -> "2^k1"
            else -> "2^{${exponentSum(index)}}"
        }
        when {
            index == 0 && coefficientText.isEmpty() -> "1"
            index == 0 -> coefficientText
            coefficientText.isEmpty() -> powerText
            else -> "$coefficientText·$powerText"
        }
    }
    val denominator = if (length == 1) {
        "2^k - 3"
    } else {
        "2^{${exponentSum(length)}} - 3^$length"
    }
    return "x = ($terms) / ($denominator)"
}

private fun exponentSum(last: Int): String = if (last == 1) "k1" else "k1+···+k$last"

private class CycleSearch(
    val length: Int,
    val caseLog: ((String) -> Unit)?,
) {
    var branches: Long = 0
    val solutions = mutableListOf<OddCycle>()
    private val halvings = IntArray(length)
    private val kMaxByStep = IntArray(length) { maxExactHalvings(it) }

    /** Smallest total exponent K with 2^K > 3^length. Equals ceil(length * log2(3)). */
    private val minimumHalvings: Int = THREE.pow(length).bitLength()

    /** maximumHalvingsFrom[step] is the largest total still available at that step, including it. */
    private val maximumHalvingsFrom: IntArray = IntArray(length + 1).also { suffix ->
        for (index in length - 1 downTo 0) {
            suffix[index] = kMaxByStep[index] + suffix[index + 1]
        }
    }

    fun run() {
        val start = Window(
            low = BigInteger.valueOf(3),
            high = null,
            modulus = FOUR,
            residue = BigInteger.valueOf(3),
        )
        search(step = 0, alpha = BigInteger.ONE, beta = BigInteger.ZERO, shift = 0, window = start)
    }

    private fun search(step: Int, alpha: BigInteger, beta: BigInteger, shift: Int, window: Window) {
        val kMax = kMaxByStep[step]
        val kLow = (minimumHalvings - shift - maximumHalvingsFrom[step + 1]).coerceAtLeast(1)
        if (kLow > kMax) {
            noteThrough(
                step - 1,
                "every completion has total exponent K <= ${shift + maximumHalvingsFrom[step]} " +
                    "< $minimumHalvings = ceil($length log2(3)), so 2^K < 3^$length",
            )
            return
        }
        for (k in kLow..kMax) {
            branches++
            halvings[step] = k
            val nextAlpha = alpha.multiply(THREE)
            val nextBeta = beta.multiply(THREE).add(BigInteger.ONE.shiftLeft(shift))
            val nextShift = shift + k
            when (val constraint = window.constrainExplained(nextAlpha, nextBeta, nextShift)) {
                is Constraint.Rejected -> note(step, constraint.reason)
                is Constraint.Kept ->
                    if (step + 1 == length) {
                        accept(nextAlpha, nextBeta, nextShift, constraint.window)
                    } else {
                        search(step + 1, nextAlpha, nextBeta, nextShift, constraint.window)
                    }
            }
        }
    }

    private fun note(step: Int, reason: String) {
        noteThrough(step, reason)
    }

    private fun noteThrough(lastIndex: Int, reason: String) {
        val log = caseLog ?: return
        if (lastIndex < 0) {
            log(reason)
        } else {
            log(exponentsThrough(lastIndex) + " | " + reason)
        }
    }

    private fun exponentsThrough(step: Int): String {
        val text = StringBuilder("k=")
        for (index in 0..step) {
            if (index > 0) text.append(',')
            text.append(halvings[index])
        }
        return text.toString()
    }

    private fun accept(alpha: BigInteger, beta: BigInteger, shift: Int, window: Window) {
        val denominator = BigInteger.ONE.shiftLeft(shift).subtract(alpha)
        val numerator = beta
        if (denominator.signum() <= 0) {
            note(
                length - 1,
                "2^$shift = ${BigInteger.ONE.shiftLeft(shift)} <= 3^$length = $alpha, so x is not a positive integer",
            )
            return
        }
        val division = numerator.divideAndRemainder(denominator)
        if (division[1] != BigInteger.ZERO) {
            note(length - 1, "x = $numerator / $denominator is not an integer (remainder ${division[1]})")
            return
        }
        val start = division[0]
        if (!window.contains(start)) {
            note(
                length - 1,
                "x = $numerator / $denominator = $start is an integer, but not an admissible minimum " +
                    "(need x >= ${window.low}, x <= ${window.high ?: "∞"}, x ≡ ${window.residue} (mod ${window.modulus}))",
            )
            return
        }
        note(length - 1, "SOLUTION x = $start")
        val steps = halvings.copyOf(length)
        check(orbitIsExactCycle(start, steps)) {
            "Length $length produced x = $start with exponents ${steps.toList()}, but that orbit is not a cycle."
        }
        solutions += OddCycle(start, steps.toList())
    }
}

private sealed interface Constraint {
    class Kept(val window: Window) : Constraint
    class Rejected(val reason: String) : Constraint
}

private data class Window(
    val low: BigInteger,
    val high: BigInteger?,
    val modulus: BigInteger,
    val residue: BigInteger,
) {
    fun contains(value: BigInteger): Boolean {
        if (value < low) return false
        if (high != null && value > high) return false
        return value.mod(modulus) == residue
    }

    /**
     * Keeps the odd integers x for which (alpha·x + beta) / 2^shift is an odd integer at least x.
     * alpha is a power of 3, so it is invertible modulo every power of 2.
     */
    fun constrainExplained(alpha: BigInteger, beta: BigInteger, shift: Int): Constraint {
        val mod = BigInteger.ONE.shiftLeft(shift + 1)
        val target = BigInteger.ONE.shiftLeft(shift).subtract(beta).mod(mod)
        val nextResidue = alpha.modInverse(mod).multiply(target).mod(mod)
        if (nextResidue.mod(modulus) != residue) {
            return Constraint.Rejected(
                "x ≡ $nextResidue (mod $mod) contradicts x ≡ $residue (mod $modulus)",
            )
        }
        var cappedHigh = high
        val twoShift = BigInteger.ONE.shiftLeft(shift)
        if (twoShift > alpha) {
            val cap = beta.divide(twoShift.subtract(alpha))
            if (cappedHigh == null || cap < cappedHigh) cappedHigh = cap
            if (cappedHigh < low) {
                return Constraint.Rejected("x<=$cap < $low")
            }
        }
        val aligned = copy(modulus = mod, residue = nextResidue, high = cappedHigh).align()
            ?: return Constraint.Rejected(
                "no integer x in [$low, ${cappedHigh ?: "∞"}] with x ≡ $nextResidue (mod $mod)",
            )
        return Constraint.Kept(aligned)
    }

    fun align(): Window? {
        val lowDelta = residue.subtract(low.mod(modulus)).mod(modulus)
        val alignedLow = low.add(lowDelta)
        val alignedHigh = high?.let { high ->
            val highDelta = high.mod(modulus).subtract(residue).mod(modulus)
            high.subtract(highDelta)
        }
        if (alignedHigh != null && alignedHigh < alignedLow) return null
        return copy(low = alignedLow, high = alignedHigh)
    }
}

private fun orbitIsExactCycle(start: BigInteger, halvings: IntArray): Boolean {
    var value = start
    for (k in halvings) {
        if (value < start || value.isEven()) return false
        val lifted = value.multiply(THREE).add(BigInteger.ONE)
        if (lifted.getLowestSetBit() != k) return false
        value = lifted.shiftRight(k)
    }
    return value == start
}
