package hackman.trevor.collatz

import java.math.BigInteger
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoverageShareTest {
    @Test
    fun matchesTheStoredShareThroughSixteen() {
        val levels = coverageShares(16)
        assertEquals(1L, levels[0].proved)
        assertEquals(2L, levels[0].modulus)
        assertEquals(3L, levels[1].proved)
        assertEquals(4L, levels[1].modulus)
        assertEquals(63422L, levels[15].proved)
        assertEquals(65536L, levels[15].modulus)
    }

    @Test
    fun advanceMatchesCoverageShares() {
        var open = longArrayOf(0L)
        for (parentBits in 0 until 8) {
            val (level, stillOpen) = advanceCoverageShare(open, parentBits)
            assertEquals(parentBits + 1, level.bits)
            assertEquals((1L shl level.bits) - stillOpen.size, level.proved)
            open = stillOpen
        }
        val full = coverageShares(8)
        assertEquals(full.last().proved, (1L shl 8) - open.size)
        assertEquals(full.last().open, open.size.toLong())
    }

    @Test
    fun mForBitsMatchesFloorLog3OfPowerOfTwo() {
        assertEquals(0, mForBits(1))
        assertEquals(1, mForBits(2))
        assertEquals(1, mForBits(3))
        assertEquals(2, mForBits(4))
        assertEquals(22, mForBits(35))
        assertEquals(22, mForBits(36))
    }

    @Test
    fun heapSummaryMatchesTheOpeningRowsAndStopsWhenTheStateWillNotFit() {
        val root = java.nio.file.Files.createTempDirectory("coverage-heap")
        val summary = root.resolve("summary.txt")
        val bits = writeParityCoverageUntilHeap(heapBytes = 1L shl 30, summary = summary, maxBits = 6)
        assertEquals(6, bits)
        val text = summary.toFile().readText()
        assertTrue(text.contains("2^1  50%  1/2"))
        assertTrue(text.contains("2^6  87.5%  56/64"))
        assertTrue(text.contains("1  2^2..2^3  75%     6/8"))
        assertTrue(text.contains("3  2^5..2^6  87.5%   56/64"))

        val tiny = root.resolve("tiny.txt")
        val stopped = writeParityCoverageUntilHeap(heapBytes = 3, summary = tiny)
        assertEquals(1, stopped)
        val tinyText = tiny.toFile().readText()
        assertTrue(tinyText.contains("2^1  50%  1/2"))
        assertFalse(tinyText.contains("2^2"))
    }

    @Test
    fun mBucketSharesMatchTheScannedTableAndContinue() {
        val rows = mBucketShares(23)
        assertEquals(24, rows.size)
        assertEquals("0  2^1  50%  1/2  50%", rows[0].formatLine())
        assertEquals("1  2^2..2^3  75%  6/8  25%", rows[1].formatLine())
        assertEquals("2  2^4  81.25%  13/16  18.75%", rows[2].formatLine())
        assertEquals(
            "22  2^35..2^36  99.23%  68191793982/68719476736  0.77%",
            rows[22].formatLine(),
        )
        assertEquals(
            "23  2^37..2^38  99.3%  272943149762/274877906944  0.7%",
            rows[23].formatLine(),
        )
        val through64 = mBucketShares(largestProvedM(64))
        assertEquals(40, through64.last().m)
        assertEquals(64, through64.last().lastBits)
        val previous = rows[22]
        val next = rows[23]
        assertTrue(next.open < previous.open.shiftLeft(next.lastBits - previous.lastBits))
    }

    @Test
    fun writeCoverageSummaryAddsMBucketSection() {
        val root = Files.createTempDirectory("coverage-m")
        val summary = root.resolve("summary.txt")
        writeCoverageSummary(
            listOf(
                CoverageShareRow(1, 1, 2),
                CoverageShareRow(2, 3, 4),
                CoverageShareRow(3, 6, 8),
                CoverageShareRow(4, 13, 16),
            ),
            summary,
        )
        val text = summary.toFile().readText()
        assertTrue(text.contains("bits  proved  proved/modulus\n"))
        assertTrue(text.contains("m      bits  proved  proved/modulus"))
        assertTrue(text.contains("0       2^1  50%     1/2"))
        assertTrue(text.contains("1  2^2..2^3  75%     6/8"))
        assertTrue(text.contains("2       2^4  81.25%  13/16"))
    }

    @Test
    fun bitsForMMatchesFloorLog2OfPowerOfThree() {
        assertEquals(1, bitsForM(0))
        assertEquals(3, bitsForM(1))
        assertEquals(4, bitsForM(2))
        assertEquals(6, bitsForM(3))
        assertEquals(36, bitsForM(22))
        assertEquals(0, mForBits(bitsForM(0)))
        assertEquals(22, mForBits(bitsForM(22)))
    }

    @Test
    fun openStoppingCountMatchesSummaryThroughM22() {
        val expectedOpenAtEndOfM = longArrayOf(
            1, 2, 3, 8, 13, 38, 128, 226, 734, 1295, 4228, 14990, 27328,
            93222, 168807, 573162, 1037374, 3524586, 12771274, 23642078,
            82694966, 151917636, 527682754,
        )
        for (m in expectedOpenAtEndOfM.indices) {
            val row = coverageShareRowForM(m)
            assertEquals(bitsForM(m), row.bits)
            assertEquals(expectedOpenAtEndOfM[m], row.open)
            assertEquals((1L shl row.bits) - expectedOpenAtEndOfM[m], row.proved)
        }
    }

    @Test
    fun coverageShareRowsThroughMMatchResidueScanThroughSixteen() {
        // Bucket m=10 runs through bit 17; compare only the shared prefix through 16.
        val fromLattice = coverageShareRowsThroughM(mForBits(16)).filter { it.bits <= 16 }
        val fromScan = coverageShares(16)
        assertEquals(fromScan.size, fromLattice.size)
        for (i in fromScan.indices) {
            assertEquals(fromScan[i].bits, fromLattice[i].bits)
            assertEquals(fromScan[i].proved, fromLattice[i].proved)
            assertEquals(fromScan[i].modulus, fromLattice[i].modulus)
        }
    }

    @Test
    fun percentKeepsTwoDecimalsUntilTheyWouldHideTheRemainder() {
        assertEquals("50%", formatPercent(1, 2))
        assertEquals("87.5%", formatPercent(56, 64))
        assertEquals("81.25%", formatPercent(13, 16))
        assertEquals("99.23%", formatPercent(68191793982L, 68719476736L))
        assertEquals("0.77%", formatPercent(68719476736L - 68191793982L, 68719476736L))
        assertEquals("99.3%", formatPercent(272943149762L, 274877906944L))
        assertEquals("0.7%", formatPercent(274877906944L - 272943149762L, 274877906944L))
        val two64 = BigInteger.ONE.shiftLeft(64)
        val proved64 = BigInteger("18419201878695679948")
        assertEquals("99.85%", formatPercent(proved64, two64))
        assertEquals("0.15%", formatPercent(two64.subtract(proved64), two64))

        val modulus135 = BigInteger("43556142965880123323311949751266331066368")
        val proved135 = BigInteger("43554014005740668234631659843979759423408")
        assertEquals("99.9951121472%", formatPercent(proved135, modulus135))
        assertEquals("0.0048878528%", formatPercent(modulus135.subtract(proved135), modulus135))

        val modulus = BigInteger.TEN.pow(30)
        assertEquals("99.9999999999999999999999999999%", formatPercent(modulus.subtract(BigInteger.ONE), modulus))
        assertEquals("0.0000000000000000000000000001%", formatPercent(BigInteger.ONE, modulus))
        assertEquals(
            "99.9999999999999999999953954919%",
            formatPercent(modulus.subtract(BigInteger("46045081")), modulus),
        )
        assertEquals("0.0000000000000000000046045081%", formatPercent(BigInteger("46045081"), modulus))
    }
}
