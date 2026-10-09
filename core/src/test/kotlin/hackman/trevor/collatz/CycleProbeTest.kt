package hackman.trevor.collatz

import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CycleProbeTest {
    @Test
    fun lengthOneIsOnlyTheKnownCycle() {
        val result = proveCycleLength(1)
        assertEquals("(3x+1)/2^k = x", result.formula)
        assertEquals("x = (1) / (2^k - 3)", result.closedForm)
        assertTrue(result.solutions.isEmpty())
        assertEquals(OddCycle(BigInteger.ONE, listOf(2)), result.trivial)
        assertTrue(orbitReturns(BigInteger.ONE, listOf(2)))
    }

    @Test
    fun lengthsTwoThroughEightHaveNoWholeNumberAboveOne() {
        val results = CycleProbe().apply { proveThrough(8) }.results()
        assertEquals((1..8).toList(), results.map { it.length })
        assertNull(results[1].trivial)
        for (result in results.drop(1)) {
            assertTrue(result.solutions.isEmpty(), "length ${result.length} found ${result.solutions}")
        }
        // Max exponents are 1 then 2, so K <= 3. But 2^K > 3^2 needs K >= 4.
        assertEquals(0, results[1].branches)
    }

    @Test
    fun searchAgreesWithSolvingEveryExponentTuple() {
        for (length in 2..5) {
            val searched = proveCycleLength(length).solutions.map { it.start to it.halvings }.toSet()
            assertEquals(bruteForceCycles(length), searched)
        }
    }

    @Test
    fun halvingBoundCoversStepsThatStayAboveTheMinimum() {
        var x = BigInteger.valueOf(3)
        val limit = BigInteger.valueOf(2_000)
        while (x <= limit) {
            var value = x
            var steps = 0
            while (steps < 12) {
                val lifted = value.multiply(BigInteger.valueOf(3)).add(BigInteger.ONE)
                val k = lifted.getLowestSetBit()
                val next = lifted.shiftRight(k)
                if (next < x) break
                assertTrue(
                    k <= maxExactHalvings(steps),
                    "x=$x step=$steps value=$value k=$k bound=${maxExactHalvings(steps)}",
                )
                value = next
                steps++
            }
            x = x.add(BigInteger.TWO)
        }
    }

    @Test
    fun noNonTrivialCycleThroughLengthTwenty() {
        for (length in 17..20) {
            val result = proveCycleLength(length)
            assertTrue(result.solutions.isEmpty(), "length $length found ${result.solutions}")
        }
    }

    @Test
    fun proveThroughDoesNotRedoFinishedLengths() {
        val probe = CycleProbe().apply { proveThrough(4) }
        probe.proveThrough(4)
        assertEquals(4, probe.length)
        assertEquals(4, probe.results().size)
    }
}

private fun bruteForceCycles(length: Int): Set<Pair<BigInteger, List<Int>>> {
    val exponents = IntArray(length)
    val found = mutableSetOf<Pair<BigInteger, List<Int>>>()
    fun walk(step: Int) {
        if (step == length) {
            val start = solveTuple(exponents) ?: return
            if (start > BigInteger.ONE && orbitReturns(start, exponents.toList())) {
                found += start to exponents.toList()
            }
            return
        }
        for (k in 1..maxExactHalvings(step)) {
            exponents[step] = k
            walk(step + 1)
        }
    }
    walk(0)
    return found
}

/** Closed form x = Σ 3^{n-1-i} 2^{S_i} / (2^K - 3^n), or null when that is not an integer above 1. */
private fun solveTuple(exponents: IntArray): BigInteger? {
    val length = exponents.size
    var shift = 0
    var numerator = BigInteger.ZERO
    for (index in 0 until length) {
        numerator = numerator.add(BigInteger.valueOf(3).pow(length - 1 - index).shiftLeft(shift))
        shift += exponents[index]
    }
    val denominator = BigInteger.ONE.shiftLeft(shift).subtract(BigInteger.valueOf(3).pow(length))
    if (denominator.signum() <= 0) return null
    val division = numerator.divideAndRemainder(denominator)
    if (division[1] != BigInteger.ZERO) return null
    return division[0]
}

private fun orbitReturns(start: BigInteger, halvings: List<Int>): Boolean {
    var value = start
    for (k in halvings) {
        val lifted = value.multiply(BigInteger.valueOf(3)).add(BigInteger.ONE)
        if (lifted.getLowestSetBit() != k) return false
        value = lifted.shiftRight(k)
    }
    return value == start
}
