package hackman.trevor.collatz

import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoverageProbeTest {
    @Test
    fun firstTwoLevelsAreHalfThenThreeQuarters() {
        val probe = CoverageProbe()
        val evens = probe.advance()
        assertEquals(1, evens.bits)
        assertEquals(BigInteger.TWO, evens.modulus)
        assertEquals(BigInteger.ONE, evens.proved)
        assertEquals(listOf(BigInteger.ZERO), evens.newlySettled.map { it.residue })
        assertEquals(listOf(BigInteger.ONE), evens.openResidues)

        val oneModFour = probe.advance()
        assertEquals(2, oneModFour.bits)
        assertEquals(BigInteger.valueOf(4), oneModFour.modulus)
        assertEquals(BigInteger.valueOf(3), oneModFour.proved)
        assertEquals(listOf(BigInteger.ONE), oneModFour.newlySettled.map { it.residue })
        assertEquals("3t+1", oneModFour.newlySettled.single().image.formatPolynomial())
        assertEquals(listOf(BigInteger.valueOf(3)), oneModFour.openResidues)
    }

    @Test
    fun thirdLevelAddsNothingAndFourthSettlesThreeModSixteen() {
        val levels = CoverageProbe().apply { advanceTo(4) }.levels()
        val mod8 = levels[2]
        assertEquals(BigInteger.valueOf(6), mod8.proved)
        assertEquals(BigInteger.valueOf(8), mod8.modulus)
        assertTrue(mod8.newlySettled.isEmpty())
        assertEquals(listOf(3L, 7L).map(BigInteger::valueOf), mod8.openResidues)

        val mod16 = levels[3]
        assertEquals(BigInteger.valueOf(13), mod16.proved)
        assertEquals(listOf(BigInteger.valueOf(3)), mod16.newlySettled.map { it.residue })
        assertEquals(listOf(7L, 11L, 15L).map(BigInteger::valueOf), mod16.openResidues)
    }

    @Test
    fun sevenStaysOpenUntilModuloOneHundredTwentyEight() {
        val levels = CoverageProbe().apply { advanceTo(7) }.levels()
        val seven = BigInteger.valueOf(7)
        assertTrue(levels.take(6).none { level -> level.newlySettled.any { it.residue == seven } })
        assertTrue(levels[6].newlySettled.any { it.residue == seven })
        assertEquals("81t+5", levels[6].newlySettled.first { it.residue == seven }.image.formatPolynomial())
    }

    @Test
    fun provedShareNeverShrinksAndMatchesAFullCensus() {
        val levels = CoverageProbe().apply { advanceTo(10) }.levels()
        var previous = BigInteger.ZERO
        for (level in levels) {
            assertTrue(level.proved >= previous.shiftLeft(1))
            previous = level.proved
            val census = surveyModulus(level.bits)
            assertEquals(census.descendingCount.toBigInteger(), level.proved)
            assertEquals(census.openResidues, level.openResidues)
        }
    }

    @Test
    fun advanceToDoesNotRedoFinishedLevels() {
        val probe = CoverageProbe().apply { advanceTo(5) }
        probe.advanceTo(5)
        assertEquals(5, probe.depth)
        assertEquals(5, probe.levels().size)
        assertEquals(BigInteger.valueOf(28), probe.levels().last().proved)
        assertEquals(BigInteger.valueOf(32), probe.levels().last().modulus)
    }
}

private fun Int.toBigInteger(): BigInteger = BigInteger.valueOf(toLong())
