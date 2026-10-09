package hackman.trevor.collatz

import java.math.BigInteger

/** Largest [surveyModulus] exponent. A single class can still be analyzed at a taller power of two. */
const val MAX_CENSUS_BITS = 16

private val THREE: BigInteger = BigInteger.valueOf(3)

enum class CollatzOperation {
    ThreeNPlusOne,
    Halve,
}

/**
 * Value `coefficient * t + constant` shared by every integer in a residue class.
 * [t] is the same parameter as in `n = modulus * t + residue`.
 */
data class AffineValue(
    val coefficient: BigInteger,
    val constant: BigInteger,
) {
    fun apply(t: BigInteger): BigInteger = coefficient.multiply(t).add(constant)

    /** Polynomial in t, such as `9t+2` or `t`. */
    fun formatPolynomial(): String {
        val coefficientText = when (coefficient) {
            BigInteger.ZERO -> null
            BigInteger.ONE -> "t"
            else -> "${coefficient}t"
        }
        if (coefficientText == null) return constant.toString()
        if (constant == BigInteger.ZERO) return coefficientText
        return if (constant.signum() > 0) "$coefficientText+$constant" else "$coefficientText$constant"
    }
}

data class ResidueStep(
    val operation: CollatzOperation,
    val value: AffineValue,
)

/**
 * Shared Collatz prefix of `n = modulus * t + residue`.
 *
 * While the coefficient stays even, every such n has the same parity, so the next step is forced.
 * [Descends] means that prefix reaches a value strictly smaller than n for every n > 1 in the class.
 * [Open] means the next parity depends on t before that happens.
 */
sealed interface ResidueAnalysis {
    val residue: BigInteger
    val modulus: BigInteger
    val steps: List<ResidueStep>
    val image: AffineValue

    data class Descends(
        override val residue: BigInteger,
        override val modulus: BigInteger,
        override val steps: List<ResidueStep>,
        override val image: AffineValue,
    ) : ResidueAnalysis

    data class Open(
        override val residue: BigInteger,
        override val modulus: BigInteger,
        override val steps: List<ResidueStep>,
        override val image: AffineValue,
    ) : ResidueAnalysis

    /** The determined prefix repeated an affine value, so it never falls below the start. */
    data class Loops(
        override val residue: BigInteger,
        override val modulus: BigInteger,
        override val steps: List<ResidueStep>,
        override val image: AffineValue,
    ) : ResidueAnalysis

    /** [maxSteps] ran out while the prefix was still determined and still not below the start. */
    data class Unresolved(
        override val residue: BigInteger,
        override val modulus: BigInteger,
        override val steps: List<ResidueStep>,
        override val image: AffineValue,
    ) : ResidueAnalysis
}

/**
 * Follows the Collatz steps forced by [residue] modulo [modulus].
 *
 * [modulus] has to be at least 2. It does not have to be a power of two; a power of two is what
 * locks a pure parity prefix, which is what [surveyModulus] enumerates.
 * [maxSteps] bounds the walk. Hitting it yields [ResidueAnalysis.Unresolved], not a failed class.
 */
fun analyzeResidueClass(
    residue: BigInteger,
    modulus: BigInteger,
    maxSteps: Int = 100_000,
): ResidueAnalysis {
    require(modulus >= BigInteger.TWO) { "modulus must be at least 2, was $modulus" }
    require(residue >= BigInteger.ZERO && residue < modulus) {
        "residue must lie in 0 until modulus, was $residue mod $modulus"
    }
    require(maxSteps > 0) { "maxSteps must be positive, was $maxSteps" }

    var coefficient = modulus
    var constant = residue
    val trace = ArrayList<ResidueStep>(16)
    val seen = HashSet<AffineValue>()
    seen += AffineValue(coefficient, constant)

    while (trace.size < maxSteps) {
        if (!coefficient.isEven()) {
            return ResidueAnalysis.Open(residue, modulus, trace.toList(), AffineValue(coefficient, constant))
        }
        val operation: CollatzOperation
        if (constant.isEven()) {
            operation = CollatzOperation.Halve
            coefficient = coefficient.shiftRight(1)
            constant = constant.shiftRight(1)
        } else {
            operation = CollatzOperation.ThreeNPlusOne
            coefficient = coefficient.multiply(THREE)
            constant = constant.multiply(THREE).add(BigInteger.ONE)
        }
        val image = AffineValue(coefficient, constant)
        trace += ResidueStep(operation, image)
        if (imageBelowEveryMember(image, modulus, residue)) {
            return ResidueAnalysis.Descends(residue, modulus, trace.toList(), image)
        }
        if (!seen.add(image)) {
            return ResidueAnalysis.Loops(residue, modulus, trace.toList(), image)
        }
    }
    return ResidueAnalysis.Unresolved(residue, modulus, trace.toList(), AffineValue(coefficient, constant))
}

/**
 * Census of every residue modulo `2^[bits]`.
 * [classes] is ordered by residue, so `classes[r]` is residue r.
 */
data class ModulusSurvey(
    val bits: Int,
    val modulus: BigInteger,
    val classes: List<ResidueAnalysis>,
) {
    val descendingCount: Int
        get() = classes.count { it is ResidueAnalysis.Descends }

    val openResidues: List<BigInteger>
        get() = classes.filterIsInstance<ResidueAnalysis.Open>().map { it.residue }
}

/** Every residue modulo `2^[bits]`, with [bits] in `1..[MAX_CENSUS_BITS]`. */
fun surveyModulus(bits: Int): ModulusSurvey {
    require(bits in 1..MAX_CENSUS_BITS) { "bits must be in 1..$MAX_CENSUS_BITS, was $bits" }
    val modulus = BigInteger.ONE.shiftLeft(bits)
    val classes = ArrayList<ResidueAnalysis>(modulus.toInt())
    var residue = BigInteger.ZERO
    while (residue < modulus) {
        classes += analyzeResidueClass(residue, modulus)
        residue = residue.add(BigInteger.ONE)
    }
    return ModulusSurvey(bits, modulus, classes)
}

/**
 * True when `image` is strictly below `n = modulus * t + residue` for every integer t with n > 1.
 *
 * Members n <= 1 are not part of the claim: 0 is outside the map, and 1 cycles without ever decreasing.
 */
private fun imageBelowEveryMember(image: AffineValue, modulus: BigInteger, residue: BigInteger): Boolean {
    val tMin = if (residue > BigInteger.ONE) BigInteger.ZERO else BigInteger.ONE
    val coefficient = image.coefficient
    val constant = image.constant
    if (coefficient < modulus) {
        val denominator = modulus.subtract(coefficient)
        val numerator = constant.subtract(residue)
        return denominator.multiply(tMin) > numerator
    }
    if (coefficient == modulus) return constant < residue
    return false
}
