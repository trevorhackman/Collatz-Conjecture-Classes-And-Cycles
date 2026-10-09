package hackman.trevor.collatz

import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ResidueClassTest {
    @Test
    fun evensDescendInOneStep() {
        val analysis = analyzeResidueClass(BigInteger.ZERO, BigInteger.TWO) as ResidueAnalysis.Descends
        assertEquals(1, analysis.steps.size)
        assertEquals(CollatzOperation.Halve, analysis.steps.single().operation)
        assertEquals("t", analysis.image.formatPolynomial())
    }

    @Test
    fun oneModFourDescendsToThreeTPlusOne() {
        val analysis = analyzeResidueClass(BigInteger.ONE, BigInteger.valueOf(4)) as ResidueAnalysis.Descends
        assertEquals(3, analysis.steps.size)
        assertEquals("3t+1", analysis.image.formatPolynomial())
    }

    @Test
    fun threeModFourIsOpen() {
        val analysis = analyzeResidueClass(BigInteger.valueOf(3), BigInteger.valueOf(4))
        assertIs<ResidueAnalysis.Open>(analysis)
    }

    @Test
    fun threeModSixteenDescends() {
        val analysis = analyzeResidueClass(BigInteger.valueOf(3), BigInteger.valueOf(16)) as ResidueAnalysis.Descends
        assertEquals(6, analysis.steps.size)
        assertEquals("9t+2", analysis.image.formatPolynomial())
    }

    @Test
    fun elevenModThirtyTwoDescends() {
        val analysis = analyzeResidueClass(BigInteger.valueOf(11), BigInteger.valueOf(32)) as ResidueAnalysis.Descends
        assertEquals("27t+10", analysis.image.formatPolynomial())
    }

    @Test
    fun sevenModEightClimbsUntilParitySplits() {
        val analysis = analyzeResidueClass(BigInteger.valueOf(7), BigInteger.valueOf(8)) as ResidueAnalysis.Open
        assertEquals("27t+26", analysis.image.formatPolynomial())
    }

    @Test
    fun allOnesResidueGrowsByThreeHalvesUntilTheModulusIsSpent() {
        val modulus = BigInteger.ONE.shiftLeft(5)
        val analysis = analyzeResidueClass(modulus.subtract(BigInteger.ONE), modulus) as ResidueAnalysis.Open
        assertEquals(THREE.pow(5).toString() + "t+" + THREE.pow(5).subtract(BigInteger.ONE), analysis.image.formatPolynomial())
        assertEquals(10, analysis.steps.size)
    }

    @Test
    fun twentySevenModTwoToTheFiftyNineDescends() {
        val modulus = BigInteger.ONE.shiftLeft(59)
        val analysis = analyzeResidueClass(BigInteger.valueOf(27), modulus) as ResidueAnalysis.Descends
        assertEquals(96, analysis.steps.size)
        assertEquals(THREE.pow(37), analysis.image.coefficient)
        assertEquals(BigInteger.valueOf(23), analysis.image.constant)
        assertEquals(BigInteger.valueOf(23), analysis.image.apply(BigInteger.ZERO))
    }

    @Test
    fun shortStepCapDoesNotPretendAClassFailed() {
        val analysis = analyzeResidueClass(BigInteger.ONE, BigInteger.valueOf(4), maxSteps = 1)
        assertIs<ResidueAnalysis.Unresolved>(analysis)
    }

    @Test
    fun censusModSixteenLeavesThreeOpenResidues() {
        val survey = surveyModulus(4)
        assertEquals(13, survey.descendingCount)
        assertEquals(listOf(7L, 11L, 15L).map(BigInteger::valueOf), survey.openResidues)
    }

    @Test
    fun censusModThirtyTwoLeavesTheKnownOpenResidues() {
        val survey = surveyModulus(5)
        assertEquals(28, survey.descendingCount)
        assertEquals(listOf(7L, 15L, 27L, 31L).map(BigInteger::valueOf), survey.openResidues)
    }

    @Test
    fun sevenBecomesSettledModOneHundredTwentyEight() {
        val analysis = analyzeResidueClass(BigInteger.valueOf(7), BigInteger.valueOf(128)) as ResidueAnalysis.Descends
        assertEquals(11, analysis.steps.size)
        assertEquals("81t+5", analysis.image.formatPolynomial())
    }

    @Test
    fun descendingClassesMatchConcreteMembers() {
        val survey = surveyModulus(5)
        for (analysis in survey.classes.filterIsInstance<ResidueAnalysis.Descends>()) {
            for (t in 0..30) {
                val n = analysis.modulus.multiply(BigInteger.valueOf(t.toLong())).add(analysis.residue)
                if (n <= BigInteger.ONE) continue
                val image = analysis.image.apply(BigInteger.valueOf(t.toLong()))
                assertTrue("$n -> $image") { image < n }
                assertTrue("$n missing $image") { trajectoryOf(n).values.contains(image) }
            }
        }
    }

    @Test
    fun openPrefixIsTheRealTrajectoryUntilItSplits() {
        val analysis = analyzeResidueClass(BigInteger.valueOf(7), BigInteger.valueOf(8)) as ResidueAnalysis.Open
        val start = BigInteger.valueOf(15) // t = 1, n = 8*1+7
        val values = trajectoryOf(start).values
        var expected = start
        for (step in analysis.steps) {
            expected = when (step.operation) {
                CollatzOperation.Halve -> expected.shiftRight(1)
                CollatzOperation.ThreeNPlusOne -> expected.multiply(THREE).add(BigInteger.ONE)
            }
            assertEquals(step.value.apply(BigInteger.ONE), expected)
            assertTrue(values.contains(expected))
        }
        assertTrue(analysis.image.apply(BigInteger.ONE) >= start)
    }
}

private val THREE: BigInteger = BigInteger.valueOf(3)
