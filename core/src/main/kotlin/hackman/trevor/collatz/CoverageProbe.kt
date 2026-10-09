package hackman.trevor.collatz

import java.math.BigInteger

/**
 * Deepest modulus [CoverageProbe] will split to while retaining every settled trace.
 * The residue walk is the exhibit of each settled class. [mBucketShares] counts those classes by parity words.
 */
const val MAX_PROBE_BITS = 24

/**
 * One modulus visited by [CoverageProbe].
 *
 * [proved] counts residues modulo [modulus] that are settled, including residues already settled
 * at a coarser power of two. The share of positive integers is [proved]/[modulus].
 */
data class CoverageLevel(
    val bits: Int,
    val modulus: BigInteger,
    val proved: BigInteger,
    val newlySettled: List<ResidueAnalysis.Descends>,
    val openResidues: List<BigInteger>,
)

/**
 * Settles residue classes modulo successive powers of two.
 *
 * The probe starts from the single class of all integers and splits only classes that are still open.
 * The first advance settles 0 mod 2: half of all positive integers. The second settles 1 mod 4, and
 * the proved share is then 3/4. Each later advance analyzes the two children of every class still open.
 *
 * [advanceTo] never goes backward. Levels already computed stay in [levels].
 */
class CoverageProbe {
    private var openResidues: List<BigInteger> = listOf(BigInteger.ZERO)
    private var bits: Int = 0
    private val computed = ArrayList<CoverageLevel>()

    /** Highest power of two already split. Zero before the first [advance]. */
    val depth: Int get() = bits

    fun levels(): List<CoverageLevel> = computed.toList()

    fun advance(): CoverageLevel {
        check(bits < MAX_PROBE_BITS) { "probe stops at 2^$MAX_PROBE_BITS" }
        val parentModulus = BigInteger.ONE.shiftLeft(bits)
        val modulus = parentModulus.shiftLeft(1)
        val newlySettled = ArrayList<ResidueAnalysis.Descends>()
        val stillOpen = ArrayList<BigInteger>(openResidues.size * 2)
        for (parent in openResidues) {
            classify(parent, modulus, newlySettled, stillOpen)
            classify(parent.add(parentModulus), modulus, newlySettled, stillOpen)
        }
        newlySettled.sortBy { it.residue }
        stillOpen.sort()
        bits += 1
        val level = CoverageLevel(
            bits = bits,
            modulus = modulus,
            proved = modulus.subtract(BigInteger.valueOf(stillOpen.size.toLong())),
            newlySettled = newlySettled,
            openResidues = stillOpen,
        )
        computed += level
        openResidues = stillOpen
        return level
    }

    /** Splits through `2^[targetBits]`. Does nothing when [depth] is already there. */
    fun advanceTo(targetBits: Int) {
        require(targetBits in 1..MAX_PROBE_BITS) {
            "targetBits must be in 1..$MAX_PROBE_BITS, was $targetBits"
        }
        while (bits < targetBits) advance()
    }
}

private fun classify(
    residue: BigInteger,
    modulus: BigInteger,
    newlySettled: MutableList<ResidueAnalysis.Descends>,
    stillOpen: MutableList<BigInteger>,
) {
    when (val analysis = analyzeResidueClass(residue, modulus)) {
        is ResidueAnalysis.Descends -> newlySettled += analysis
        is ResidueAnalysis.Open,
        is ResidueAnalysis.Loops,
        is ResidueAnalysis.Unresolved,
        -> stillOpen += residue
    }
}
