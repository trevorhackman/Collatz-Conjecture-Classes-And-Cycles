package hackman.trevor.collatz

import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.Arrays
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * Proved share of positive integers after splitting residue classes through `2^[bits]`.
 * [proved] counts residues modulo [modulus]. The open classes are exactly [modulus] - [proved].
 */
data class CoverageShare(
    val bits: Int,
    val proved: Long,
    val modulus: Long,
    val millis: Long,
) {
    val open: Long get() = modulus - proved
}

/**
 * Same settlement rule as [CoverageProbe], but it keeps only the open residues.
 * Settled classes are counted and discarded, so the run can pass the moduli where storing every trace
 * would exhaust memory.
 *
 * Stops at [maxBits], or earlier when the proved share reaches [stopAtPercent].
 * [maxBits] cannot exceed 62, because the modulus has to fit in a signed 64-bit integer.
 */
fun coverageShares(
    maxBits: Int,
    stopAtPercent: Double = 100.0,
    onLevel: (CoverageShare) -> Unit = {},
): List<CoverageShare> {
    require(maxBits in 1..62) { "maxBits must be in 1..62, was $maxBits" }
    var open = longArrayOf(0L)
    val levels = ArrayList<CoverageShare>(maxBits)
    var bits = 0
    while (bits < maxBits) {
        val (level, stillOpen) = advanceCoverageShare(open, bits)
        levels += level
        onLevel(level)
        open = stillOpen
        bits = level.bits
        if (percent(level) >= stopAtPercent) break
        if (level.millis > 180_000) break
    }
    return levels
}

/**
 * One step from open residues at [parentBits] to modulus `2^[parentBits+1]`.
 * [open] must be the unsettled residues modulo `2^[parentBits]` (modulo 1 when [parentBits] is 0).
 */
fun advanceCoverageShare(open: LongArray, parentBits: Int): Pair<CoverageShare, LongArray> {
    require(parentBits in 0..61) { "parentBits must be in 0..61, was $parentBits" }
    val started = System.nanoTime()
    val parentModulus = if (parentBits == 0) 1L else 1L shl parentBits
    val bits = parentBits + 1
    val modulus = 1L shl bits
    val modulusValue = BigInteger.valueOf(modulus)
    val children = LongArray(open.size * 2)
    var cursor = 0
    for (parent in open) {
        children[cursor++] = parent
        children[cursor++] = parent + parentModulus
    }
    val stillOpen = Arrays.stream(children).parallel().filter { residue ->
        analyzeResidueClass(BigInteger.valueOf(residue), modulusValue) !is ResidueAnalysis.Descends
    }.toArray()
    stillOpen.sort()
    val level = CoverageShare(
        bits = bits,
        proved = modulus - stillOpen.size,
        modulus = modulus,
        millis = (System.nanoTime() - started) / 1_000_000,
    )
    return level to stillOpen
}

/**
 * Bit length of the written share table. The parity-word count itself has no such cap.
 */
const val PARITY_PROOF_BITS = 1000

private val THREE = BigInteger.valueOf(3)

/**
 * Largest m with 3^m <= 2^[bits], i.e. floor(log_3(2^bits)).
 * Named m so it is not confused with the Collatz halving count k in (3n+1)/2^k.
 */
fun mForBits(bits: Int): Int {
    require(bits >= 0) { "bits must be >= 0, was $bits" }
    var m = 0
    var powerOfThree = BigInteger.ONE
    val limit = BigInteger.ONE.shiftLeft(bits)
    while (true) {
        val next = powerOfThree.multiply(THREE)
        if (next > limit) return m
        powerOfThree = next
        m += 1
    }
}

/**
 * Largest bit length in the m-bucket: floor((m+1) log_2 3) = floor(log_2(3^{m+1})).
 * The bucket covers bits [bitsForM(m-1)+1, bitsForM(m)] (bit 1 when m is 0).
 */
fun bitsForM(m: Int): Int {
    require(m >= 0) { "m must be >= 0, was $m" }
    return THREE.pow(m + 1).bitLength() - 1
}

/**
 * Number of residues mod 2^[bits] still open under the Collatz stopping-time test.
 * Same count as [mBucketShares]: active parity words of length [bits]. OEIS A076227.
 * [bits] stops at 62 because the returned count is a signed 64-bit integer.
 */
fun openStoppingCount(bits: Int): Long {
    require(bits in 0..62) { "bits must be in 0..62, was $bits" }
    if (bits == 0) return 1L
    return openCountsThrough(bits).last().longValueExact()
}

/**
 * Exact proved/modulus row at the end of bucket m, from the parity-vector count (no residue scan).
 * The end bit has to be at most 62 so the modulus fits in a signed 64-bit integer.
 */
fun coverageShareRowForM(m: Int): CoverageShareRow {
    val bits = bitsForM(m)
    require(bits <= 62) { "bucket m=$m ends at 2^$bits, past a signed 64-bit modulus" }
    val modulus = 1L shl bits
    val open = openStoppingCount(bits)
    return CoverageShareRow(bits, modulus - open, modulus)
}

/**
 * Every bit-level proved/modulus row through the end of bucket [maxM].
 * One pass of [mBucketShares]. Inside a bucket the open count doubles each bit.
 * The end bit of [maxM] has to be at most 62.
 */
fun coverageShareRowsThroughM(maxM: Int): List<CoverageShareRow> {
    require(maxM >= 0) { "maxM must be >= 0, was $maxM" }
    val endBits = bitsForM(maxM)
    require(endBits <= 62) { "bucket m=$maxM ends at 2^$endBits, past a signed 64-bit modulus" }
    val openAtBit = openCountsThrough(endBits)
    return (1..endBits).map { bits ->
        val modulus = 1L shl bits
        val open = openAtBit[bits - 1].longValueExact()
        CoverageShareRow(bits, modulus - open, modulus)
    }
}

data class CoverageShareRow(val bits: Int, val proved: Long, val modulus: Long) {
    val open: Long get() = modulus - proved
}

private val SUMMARY_HEADER =
    "Positive integers forced strictly below their start by a shared Collatz prefix.\n" +
        "The share is proved/modulus: every residue not still open was settled at this modulus or a coarser one.\n" +
        "\n" +
        "bits  proved  proved/modulus\n"

fun writeCoverageSummary(rows: List<CoverageShareRow>, summary: Path = Path.of("data/classes/summary.txt")) {
    val sorted = rows.sortedBy { it.bits }
    summary.parent.createDirectories()
    summary.bufferedWriter().use { out ->
        out.write(SUMMARY_HEADER)
        for (row in sorted) {
            out.write("2^${row.bits}  ${formatPercent(row.proved, row.modulus)}  ${row.proved}/${row.modulus}\n")
        }
        out.write(formatMBucketSection(sorted))
    }
}

/**
 * One row per m(b) = floor(log_3(2^b)). Within a bucket the proved percent is constant; the displayed
 * fraction is taken from the largest b in that bucket.
 */
fun formatMBucketSection(rows: List<CoverageShareRow>): String {
    if (rows.isEmpty()) return ""
    val byM = rows.groupBy { mForBits(it.bits) }.toSortedMap()
    val lines = ArrayList<String>()
    lines += ""
    lines += "Grouped by m(b) = floor(log_3(2^b)), the largest m with 3^m <= 2^b."
    lines += "Row m covers bits floor(m log_2 3)+1 through k(m)=floor((m+1) log_2 3); fraction is at k(m)."
    lines += "Open count at k(m) is the number of parity words that stay above 3^a > 2^j."
    lines += ""
    val table = ArrayList<MBucketFields>()
    for ((m, group) in byM) {
        val sorted = group.sortedBy { it.bits }
        val first = sorted.first()
        val last = sorted.last()
        for (row in sorted) {
            check(row.proved * last.modulus == last.proved * row.modulus) {
                "proved share changed inside m=$m between 2^${row.bits} and 2^${last.bits}"
            }
        }
        val bitsLabel =
            if (first.bits == last.bits) "2^${first.bits}"
            else "2^${first.bits}..2^${last.bits}"
        table += MBucketFields(
            m.toString(),
            bitsLabel,
            formatPercent(last.proved, last.modulus),
            "${last.proved}/${last.modulus}",
        )
    }
    lines += alignMBucketTable(table).trimEnd('\n')
    return lines.joinToString("\n", postfix = "\n")
}

fun parseSummaryRows(summary: Path = Path.of("data/classes/summary.txt")): List<CoverageShareRow> {
    if (!summary.exists()) return emptyList()
    val rows = ArrayList<CoverageShareRow>()
    for (line in summary.readText().lines()) {
        val bits = Regex("""^2\^(\d+)\b""").find(line) ?: continue
        val fraction = Regex("""(\d+)/(\d+)""").find(line) ?: continue
        rows += CoverageShareRow(
            bits = bits.groupValues[1].toInt(),
            proved = fraction.groupValues[1].toLong(),
            modulus = fraction.groupValues[2].toLong(),
        )
    }
    return rows
}

private fun percent(level: CoverageShare): Double = level.proved.toDouble() / level.modulus.toDouble() * 100.0

/**
 * One m-bucket of the proved share. [proved] and [modulus] are taken at [lastBits], the finest
 * power of two in the bucket. Every bit from [firstBits] through [lastBits] has the same share.
 */
data class MBucketShare(
    val m: Int,
    val firstBits: Int,
    val lastBits: Int,
    val proved: BigInteger,
    val modulus: BigInteger,
) {
    val open: BigInteger get() = modulus.subtract(proved)

    fun formatLine(): String {
        val bitsLabel =
            if (firstBits == lastBits) "2^$firstBits"
            else "2^$firstBits..2^$lastBits"
        return "$m  $bitsLabel  ${formatPercent(proved, modulus)}  " +
            "$proved/$modulus  ${formatPercent(open, modulus)}"
    }
}

/** Open counts at bit lengths 1 through [bits], from one pass of the active-parity-word list. */
fun openCountsThrough(bits: Int): List<BigInteger> {
    require(bits >= 1) { "bits must be >= 1, was $bits" }
    val active = ActiveParityWords()
    val counts = ArrayList<BigInteger>(bits)
    for (b in 1..bits) {
        active.advanceTo(b, mForBits(b))
        counts += active.open
    }
    return counts
}

/**
 * Proved-share rows for m = 0 through [throughM].
 *
 * An open class modulo 2^b is a parity word of length b that stays active at every prefix: a prefix
 * of length k with j odd steps survives only while 3^j > 2^k. The words are counted by j. One new
 * bit adds the two one-step extensions and drops every word with 3^j <= 2^b. That list has length
 * b - m(b). While m(b) is unchanged the cutoff does not move, every open class splits into two open
 * children, and the share is constant, so the bucket is one row at its last bit.
 */
fun mBucketShares(throughM: Int): List<MBucketShare> {
    require(throughM >= 0) { "throughM must be >= 0, was $throughM" }
    val lastBits = bitsForM(throughM)
    val openAtBit = openCountsThrough(lastBits)
    val rows = ArrayList<MBucketShare>(throughM + 1)
    var bucketStart = 1
    var bucketM = 0
    for (bits in 1..lastBits) {
        val m = mForBits(bits)
        if (m != bucketM) {
            rows += mBucketShare(bucketM, bucketStart, bits - 1, openAtBit[bits - 2])
            bucketM = m
            bucketStart = bits
        }
    }
    rows += mBucketShare(bucketM, bucketStart, lastBits, openAtBit[lastBits - 1])
    check(rows.last().m == throughM) { "last bucket is m=${rows.last().m}, not $throughM" }
    return rows
}

private fun mBucketShare(m: Int, firstBits: Int, lastBits: Int, open: BigInteger): MBucketShare {
    val modulus = BigInteger.ONE.shiftLeft(lastBits)
    return MBucketShare(
        m = m,
        firstBits = firstBits,
        lastBits = lastBits,
        proved = modulus.subtract(open),
        modulus = modulus,
    )
}

/**
 * Active parity words at the current bit length, grouped by how many odd steps they contain.
 * [counts] is indexed by `j - minJ`. Every stored j satisfies j >= m(b) + 1, i.e. 3^j > 2^b.
 */
private class ActiveParityWords {
    private var minJ: Int = 0
    private val counts = ArrayList<BigInteger>(64)
    private var openTotal: BigInteger = BigInteger.ONE

    val open: BigInteger
        get() = openTotal

    val stateSize: Int
        get() = counts.size

    /** Bytes of the live counters: limb array plus a BigInteger header per count. */
    fun estimatedStateBytes(): Long {
        var bytes = 0L
        for (count in counts) {
            val limbs = (count.bitLength() + 31) ushr 5
            bytes += 48L + 4L * limbs
        }
        return bytes
    }

    init {
        counts += BigInteger.ONE
    }

    fun advanceTo(bits: Int, m: Int) {
        val threshold = m + 1
        val oldMaxJ = minJ + counts.lastIndex
        val newMaxJ = oldMaxJ + 1
        val next = ArrayList<BigInteger>(newMaxJ - threshold + 1)
        var total = BigInteger.ZERO
        for (j in threshold..newMaxJ) {
            var sum = BigInteger.ZERO
            if (j in minJ..oldMaxJ) sum = counts[j - minJ]
            val parent = j - 1
            if (parent in minJ..oldMaxJ) sum = sum.add(counts[parent - minJ])
            next += sum
            total = total.add(sum)
        }
        counts.clear()
        var start = 0
        while (start < next.size && next[start].signum() == 0) start++
        var end = next.size
        while (end > start && next[end - 1].signum() == 0) end--
        for (index in start until end) counts += next[index]
        minJ = threshold + start
        openTotal = total
        check(counts.isNotEmpty()) { "no active parity word at 2^$bits" }
    }
}

/**
 * Largest m whose bucket ends at or before [maxBits].
 */
fun largestProvedM(maxBits: Int = PARITY_PROOF_BITS): Int {
    require(maxBits >= 1) { "maxBits must be >= 1, was $maxBits" }
    var m = 0
    while (bitsForM(m + 1) <= maxBits) m += 1
    return m
}

/**
 * Bit lines through [maxBits], then one row per m-bucket.
 */
fun writeParityCoverageSummary(
    maxBits: Int = PARITY_PROOF_BITS,
    summary: Path = Path.of("data/classes/summary.txt"),
) {
    require(maxBits >= 1) { "maxBits must be >= 1, was $maxBits" }
    writeParityCoverageUntilHeap(Runtime.getRuntime().maxMemory(), summary, maxBits)
}

/**
 * One streaming pass of the parity-word count.
 *
 * Returns the last bit written. Stops at the smaller of [maxBits] and [PARITY_PROOF_BITS], or earlier
 * when a second copy of the active-word counters would exceed one third of [heapBytes]. The step
 * allocates that second copy before the first can be discarded, and the JVM needs the rest of the heap
 * to collect it.
 */
fun writeParityCoverageUntilHeap(
    heapBytes: Long,
    summary: Path = Path.of("data/classes/summary.txt"),
    maxBits: Int = Int.MAX_VALUE,
): Int {
    require(heapBytes >= 1) { "heapBytes must be >= 1, was $heapBytes" }
    require(maxBits >= 1) { "maxBits must be >= 1, was $maxBits" }
    val bitsLimit = minOf(maxBits, PARITY_PROOF_BITS)
    val stateBudget = heapBytes / 3
    val started = System.nanoTime()
    summary.parent.createDirectories()
    val partial = summary.resolveSibling("${summary.fileName}.partial")
    val buckets = summary.resolveSibling("${summary.fileName}.mbuckets")
    val active = ActiveParityWords()
    var m = 0
    var threeToNext = THREE
    var bucketM = 0
    var bucketStart = 1
    var previousOpen = BigInteger.ONE
    var lastBits = 0
    var peakStateBytes = active.estimatedStateBytes()
    var peakRssBytes = currentRssBytes()
    partial.bufferedWriter(Charsets.UTF_8, 1 shl 20).use { bitsOut ->
        buckets.bufferedWriter(Charsets.UTF_8, 1 shl 20).use { bucketOut ->
            bitsOut.write(SUMMARY_HEADER)
            val bucketRows = ArrayList<MBucketFields>()
            for (bits in 1..bitsLimit) {
                if (bits > 1 && active.estimatedStateBytes() > stateBudget) break
                if (threeToNext.bitLength() <= bits) {
                    m += 1
                    threeToNext = threeToNext.multiply(THREE)
                }
                active.advanceTo(bits, m)
                val open = active.open
                val modulus = BigInteger.ONE.shiftLeft(bits)
                val proved = modulus.subtract(open)
                bitsOut.write("2^$bits  ${formatPercent(proved, modulus)}  $proved/$modulus\n")
                if (m != bucketM) {
                    bucketRows += bucketFields(bucketM, bucketStart, bits - 1, previousOpen)
                    bucketM = m
                    bucketStart = bits
                }
                previousOpen = open
                lastBits = bits
                val stateBytes = active.estimatedStateBytes()
                if (stateBytes > peakStateBytes) peakStateBytes = stateBytes
                if (bits % 4096 == 0) {
                    val rssBytes = currentRssBytes()
                    if (rssBytes > peakRssBytes) peakRssBytes = rssBytes
                    println("2^$bits  state ${active.stateSize}  ${stateBytes shr 20}MiB")
                    System.out.flush()
                }
            }
            if (lastBits > 0) bucketRows += bucketFields(bucketM, bucketStart, lastBits, previousOpen)
            bucketOut.write(M_BUCKET_PROSE)
            bucketOut.write(alignMBucketTable(bucketRows))
        }
    }
    if (lastBits == 0) {
        partial.deleteIfExists()
        buckets.deleteIfExists()
        error("heap budget $heapBytes left no room for the first bit")
    }
    val endingRss = currentRssBytes()
    if (endingRss > peakRssBytes) peakRssBytes = endingRss
    val bodyBytes = Files.size(partial) + Files.size(buckets)
    val elapsedNanos = System.nanoTime() - started
    var fileBytes = bodyBytes
    var preface = ""
    for (attempt in 1..4) {
        preface = resourcePreface(
            heapBytes, bitsLimit, lastBits, elapsedNanos, peakStateBytes, peakRssBytes, fileBytes,
        )
        val nextBytes = bodyBytes + preface.toByteArray(Charsets.UTF_8).size
        if (nextBytes == fileBytes) break
        fileBytes = nextBytes
    }
    val assembled = summary.resolveSibling("${summary.fileName}.assembled")
    Files.newOutputStream(assembled).buffered(1 shl 20).use { out ->
        out.write(preface.toByteArray(Charsets.UTF_8))
        Files.copy(partial, out)
        Files.copy(buckets, out)
    }
    partial.deleteIfExists()
    buckets.deleteIfExists()
    Files.move(assembled, summary, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    return lastBits
}

/**
 * Opening lines of the share table.
 *
 * @param heapBytes Heap limit of the run. One third of it is the stop threshold printed when the pass ends early.
 * @param maxBits Bit cap the pass was allowed to reach.
 * @param lastBits Highest bit actually written. Below [maxBits], the text gives the heap stop. Otherwise it says
 * every bit through this one was written.
 * @param elapsedNanos Time spent on the parity-word pass, in nanoseconds.
 * @param peakStateBytes Largest estimated byte size of the live parity-word counters during the pass.
 * @param peakRssBytes Largest resident set of the process, in bytes. Zero omits that line.
 * @param fileBytes Size of the finished file in bytes, this preface included.
 */
private fun resourcePreface(
    heapBytes: Long,
    maxBits: Int,
    lastBits: Int,
    elapsedNanos: Long,
    peakStateBytes: Long,
    peakRssBytes: Long,
    fileBytes: Long,
): String {
    val lines = ArrayList<String>()
    if (lastBits < maxBits) {
        lines += "A step keeps a second copy of the parity-word counters."
        lines += "The run stops at 2^$lastBits, when that copy would pass ${formatBytes(heapBytes / 3)} of memory, " +
            "one third of the heap."
    } else {
        lines += "The run wrote every bit through 2^$lastBits."
    }
    lines += "Computed by one JVM on ${machineDescription()}, heap ${formatBytes(heapBytes)} of memory, one core, " +
        "in ${formatElapsed(elapsedNanos)}."
    lines += "The parity-word counters peaked at ${formatBytes(peakStateBytes)} of memory."
    if (peakRssBytes > 0) lines += "The process peaked at ${formatBytes(peakRssBytes)} of memory."
    lines += "This file is ${formatBytes(fileBytes)} of storage."
    return lines.joinToString("\n", postfix = "\n\n")
}

internal fun machineDescription(): String {
    val cpu = readProcess("sysctl", "-n", "machdep.cpu.brand_string")
    val memBytes = readProcess("sysctl", "-n", "hw.memsize").toLongOrNull()
    val cores = Runtime.getRuntime().availableProcessors()
    val memory = memBytes?.let { ", ${formatBytes(it)} of memory" } ?: ""
    return if (cpu.isEmpty()) {
        "${System.getProperty("os.name")}, $cores cores$memory"
    } else {
        "$cpu, $cores cores$memory"
    }
}

internal fun currentRssBytes(): Long {
    val pid = ProcessHandle.current().pid()
    val rssKb = readProcess("ps", "-o", "rss=", "-p", pid.toString()).trim().toLongOrNull() ?: return 0L
    return rssKb * 1024L
}

private fun readProcess(vararg command: String): String =
    try {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()
        text
    } catch (_: Exception) {
        ""
    }

internal fun formatElapsed(nanos: Long): String {
    val seconds = nanos / 1_000_000_000L
    val minutes = seconds / 60
    if (minutes > 0) return "$minutes min ${seconds % 60} s"
    val millis = (nanos % 1_000_000_000L) / 1_000_000L
    return "$seconds.${millis.toString().padStart(3, '0')} s"
}

internal fun formatBytes(bytes: Long): String = when {
    bytes >= (1L shl 30) && bytes % (1L shl 30) == 0L -> "${bytes shr 30} GB"
    bytes >= (1L shl 20) -> "${bytes / (1L shl 20)} MiB"
    else -> "$bytes B"
}

private const val M_BUCKET_PROSE =
    "\n" +
        "Grouped by m(b) = floor(log_3(2^b)), the largest m with 3^m <= 2^b.\n" +
        "Row m covers bits floor(m log_2 3)+1 through k(m)=floor((m+1) log_2 3); fraction is at k(m).\n" +
        "Open count at k(m) is the number of parity words that stay above 3^a > 2^j.\n" +
        "\n"

private data class MBucketFields(
    val m: String,
    val bits: String,
    val proved: String,
    val fraction: String,
)

private fun bucketFields(m: Int, firstBits: Int, lastBits: Int, open: BigInteger): MBucketFields {
    val modulus = BigInteger.ONE.shiftLeft(lastBits)
    val proved = modulus.subtract(open)
    val bitsLabel = if (firstBits == lastBits) "2^$firstBits" else "2^$firstBits..2^$lastBits"
    return MBucketFields(
        m.toString(),
        bitsLabel,
        formatPercent(proved, modulus),
        "$proved/$modulus",
    )
}

/** Right-align m and the bit range so the proved percent starts in one column. */
private fun alignMBucketTable(rows: List<MBucketFields>): String {
    val mWidth = rows.maxOfOrNull { it.m.length }?.coerceAtLeast("m".length) ?: "m".length
    val bitsWidth = rows.maxOfOrNull { it.bits.length }?.coerceAtLeast("bits".length) ?: "bits".length
    val provedWidth = rows.maxOfOrNull { it.proved.length }?.coerceAtLeast("proved".length) ?: "proved".length
    fun line(m: String, bits: String, proved: String, fraction: String) =
        listOf(
            m.padStart(mWidth),
            bits.padStart(bitsWidth),
            proved.padEnd(provedWidth),
            fraction,
        ).joinToString("  ")
    val header = line("m", "bits", "proved", "proved/modulus")
    if (rows.isEmpty()) return "$header\n"
    return rows.joinToString("\n", prefix = "$header\n", postfix = "\n") { row ->
        line(row.m, row.bits, row.proved, row.fraction)
    }
}

private fun formatBigMBucketSection(rows: List<MBucketShare>): String {
    val lines = ArrayList<String>()
    lines += ""
    lines += "Grouped by m(b) = floor(log_3(2^b)), the largest m with 3^m <= 2^b."
    lines += "Row m covers bits floor(m log_2 3)+1 through k(m)=floor((m+1) log_2 3); fraction is at k(m)."
    lines += "Open count at k(m) is the number of parity words that stay above 3^a > 2^j."
    lines += ""
    lines += alignMBucketTable(rows.map { it.toFields() }).trimEnd('\n')
    return lines.joinToString("\n", postfix = "\n")
}

private fun MBucketShare.toFields(): MBucketFields {
    val bitsLabel = if (firstBits == lastBits) "2^$firstBits" else "2^$firstBits..2^$lastBits"
    return MBucketFields(
        m.toString(),
        bitsLabel,
        formatPercent(proved, modulus),
        "$proved/$modulus",
    )
}

fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        "scan" -> {
            val maxBits = args.getOrNull(1)?.toIntOrNull() ?: 16
            coverageShares(maxBits) { level ->
                println(
                    "2^${level.bits}  ${formatPercent(level.proved, level.modulus)}  " +
                        "${level.proved}/${level.modulus}  open ${level.open}  ${level.millis}ms",
                )
                System.out.flush()
            }
        }
        "extend-summary" -> {
            if (args.getOrNull(1) == "heap") {
                val heapBytes = (args.getOrNull(2)?.toLongOrNull() ?: 1024L) * 1024L * 1024L
                val bits = writeParityCoverageUntilHeap(heapBytes)
                println("wrote parity-word share through 2^$bits within ${heapBytes shr 20}MiB")
            } else {
                val maxBits = minOf(
                    args.getOrNull(1)?.toIntOrNull() ?: PARITY_PROOF_BITS,
                    PARITY_PROOF_BITS,
                )
                writeParityCoverageSummary(maxBits)
                println("wrote parity-word share through 2^$maxBits, m 0..${largestProvedM(maxBits)}")
            }
        }
        "m" -> {
            val throughM = args.getOrNull(1)?.toIntOrNull()
                ?: error("usage: m <throughM>")
            for (row in mBucketShares(throughM)) {
                println(row.formatLine())
            }
        }
        "rewrite-summary" -> {
            val summary = Path.of("data/classes/summary.txt")
            writeCoverageSummary(parseSummaryRows(summary), summary)
        }
        else -> {
            val throughM = args.firstOrNull()?.toIntOrNull() ?: 40
            for (row in mBucketShares(throughM)) println(row.formatLine())
        }
    }
}

internal fun formatPercent(proved: Long, modulus: Long): String =
    formatPercent(BigInteger.valueOf(proved), BigInteger.valueOf(modulus))

// Two decimals, until that rounding would hide a nonzero remainder.
// Then eight significant digits of the gap, as more digits of the percent.
// A share that is not exact never prints 0% or 100%.
private const val PERCENT_SIG_DIGITS = 8
private val LOG10_2 = Math.log10(2.0)

internal fun formatPercent(proved: BigInteger, modulus: BigInteger): String {
    if (proved.signum() == 0) return "0%"
    if (proved == modulus) return "100%"
    val coarse = formatFixedPercent(proved, modulus, 2)
    if (coarse != "100%" && coarse != "0%") return coarse
    val open = modulus.subtract(proved)
    if (proved < open) return formatSignificantPercent(proved, modulus)
    val exp = floorLog10OfQuotient(open.multiply(BigInteger.valueOf(100)), modulus)
    val decimals = -exp + (PERCENT_SIG_DIGITS - 1)
    return formatFixedPercent(proved, modulus, decimals)
}

private fun formatFixedPercent(part: BigInteger, modulus: BigInteger, decimals: Int): String {
    val scale = BigInteger.TEN.pow(decimals)
    val scaled = part.multiply(BigInteger.valueOf(100)).multiply(scale)
    val division = scaled.divideAndRemainder(modulus)
    var rounded = division[0]
    if (division[1].shiftLeft(1) >= modulus) rounded = rounded.add(BigInteger.ONE)
    val whole = rounded.divide(scale)
    if (decimals == 0) return "$whole%"
    val frac = rounded.remainder(scale).toString().padStart(decimals, '0').trimEnd('0')
    if (frac.isEmpty()) return "$whole%"
    return "$whole.$frac%"
}

private fun formatSignificantPercent(part: BigInteger, modulus: BigInteger): String {
    val exp = floorLog10OfQuotient(part.multiply(BigInteger.valueOf(100)), modulus)
    val decimals = -exp + (PERCENT_SIG_DIGITS - 1)
    return formatFixedPercent(part, modulus, decimals)
}

private fun floorLog10OfQuotient(numer: BigInteger, denom: BigInteger): Int {
    var exp = floorLog10(numer) - floorLog10(denom)
    if (compareToPowerOfTen(numer, denom, exp) < 0) exp--
    return exp
}

private fun compareToPowerOfTen(numer: BigInteger, denom: BigInteger, exp: Int): Int =
    if (exp >= 0) numer.compareTo(denom.multiply(BigInteger.TEN.pow(exp)))
    else numer.multiply(BigInteger.TEN.pow(-exp)).compareTo(denom)

private fun floorLog10(value: BigInteger): Int {
    var exp = ((value.bitLength() - 1) * LOG10_2).toInt().coerceAtLeast(0)
    if (BigInteger.TEN.pow(exp) > value) {
        do exp-- while (BigInteger.TEN.pow(exp) > value)
    } else {
        while (BigInteger.TEN.pow(exp + 1) <= value) exp++
    }
    return exp
}
