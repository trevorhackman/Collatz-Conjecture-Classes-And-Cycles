package hackman.trevor.collatz

import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CollatzTest {
    @Test
    fun evenHalvesAndOddAppliesThreeNPlusOne() {
        assertEquals(BigInteger.valueOf(5), collatzStep(BigInteger.TEN))
        assertEquals(BigInteger.valueOf(4), collatzStep(BigInteger.ONE))
        assertEquals(BigInteger.valueOf(2), collatzStep(BigInteger.valueOf(4)))
        assertEquals(BigInteger.ONE, collatzStep(BigInteger.TWO))
    }

    @Test
    fun fiveDropsBelowItselfInThreeStepsThenReachesOne() {
        val trajectory = trajectoryOf(BigInteger.valueOf(5))
        assertEquals(
            listOf(5L, 16L, 8L, 4L, 2L, 1L).map(BigInteger::valueOf),
            trajectory.values,
        )
        assertEquals(StoppingTime(3, BigInteger.valueOf(4)), trajectory.stoppingTime)
        assertEquals(StopReason.ReachedOne, trajectory.reason)
    }

    @Test
    fun twentySevenFirstDropsToTwentyThree() {
        val trajectory = trajectoryOf(BigInteger.valueOf(27))
        assertEquals(StoppingTime(96, BigInteger.valueOf(23)), trajectory.stoppingTime)
        assertEquals(StopReason.ReachedOne, trajectory.reason)
    }

    @Test
    fun oneCyclesAndHasNoStoppingTime() {
        val trajectory = trajectoryOf(BigInteger.ONE)
        assertEquals(listOf(BigInteger.ONE), trajectory.values)
        assertNull(trajectory.stoppingTime)
        assertEquals(StopReason.ReachedOne, trajectory.reason)
    }

    @Test
    fun stepCapStopsBeforeADrop() {
        val trajectory = trajectoryOf(BigInteger.valueOf(27), maxSteps = 2)
        assertEquals(listOf(27L, 82L, 41L).map(BigInteger::valueOf), trajectory.values)
        assertNull(trajectory.stoppingTime)
        assertEquals(StopReason.StepLimit, trajectory.reason)
    }

    @Test
    fun nonPositiveStartIsRejected() {
        assertTrue(runCatching { trajectoryOf(BigInteger.ZERO) }.isFailure)
        assertTrue(runCatching { collatzStep(BigInteger.valueOf(-2)) }.isFailure)
    }
}
