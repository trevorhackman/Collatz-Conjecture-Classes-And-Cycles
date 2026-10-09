package hackman.trevor.collatz

import kotlin.io.path.readText
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProofLogTest {
    @Test
    fun logRecordsHalfThenThreeQuartersAndRulesOutALengthTwoCycle() {
        val root = createTempDirectory("collatz-proof")
        logProofs(root, cycleLengths = 2, classesBits = 2)

        val lengthOne = root.resolve("cycles/length-01.txt").readText()
        assertTrue(lengthOne.contains("x = 1"))
        assertTrue(lengthOne.contains("No whole number x > 1"))
        assertFalse(lengthOne.contains("SOLUTION"))

        val lengthTwo = root.resolve("cycles/length-02.txt").readText()
        assertTrue(lengthTwo.contains("2^K < 3^2"))
        assertTrue(lengthTwo.contains("Solutions with x > 1: 0"))
        assertFalse(lengthTwo.contains("SOLUTION x"))

        val cycles = root.resolve("cycles/summary.txt").readText()
        assertTrue(cycles.startsWith("The run searched accelerated cycles of length 1 through 2.\n"))
        assertTrue(cycles.contains("of memory"))
        assertTrue(cycles.contains("of storage"))
        assertTrue(cycles.contains("length 2: no x > 1"))

        val summary = root.resolve("classes/summary.txt").readText()
        assertTrue(summary.contains("2^1  50%  1/2"))
        assertTrue(summary.contains("2^2  75%  3/4"))

        val mod4 = root.resolve("classes/bits-02.txt").readText()
        assertTrue(mod4.contains("SETTLED 1 mod 4"))
        assertTrue(mod4.contains("3t+1"))
        assertTrue(mod4.contains("1 > 0"))
        assertTrue(mod4.contains("OPEN 3 mod 4"))
    }
}