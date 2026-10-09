package hackman.trevor.collatz

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists

/**
 * Writes the probe casework under [root].
 *
 * `cycles/length-NN.txt` is the case analysis that no accelerated cycle of that length has an odd
 * member greater than 1. `classes/bits-NN.txt` is the residue-class casework modulo 2^NN, and
 * `classes/summary.txt` is the proved share at every modulus.
 */
fun logProofs(
    root: Path,
    cycleLengths: Int = MAX_CYCLE_LENGTH,
    classesBits: Int = 16,
) {
    require(cycleLengths in 1..MAX_CYCLE_LENGTH) {
        "cycleLengths must be in 1..$MAX_CYCLE_LENGTH, was $cycleLengths"
    }
    require(classesBits in 1..MAX_PROBE_BITS) {
        "classesBits must be in 1..$MAX_PROBE_BITS, was $classesBits"
    }
    val cycles = root.resolve("cycles")
    val classes = root.resolve("classes")
    cycles.createDirectories()
    classes.createDirectories()
    logCycles(cycles, cycleLengths)
    logClasses(classes, classesBits)
    logIndex(root, cycleLengths, classesBits)
}

fun main(args: Array<String>) {
    if (args.firstOrNull() == "cycles") {
        val lengths = args.getOrNull(1)?.toIntOrNull() ?: MAX_CYCLE_LENGTH
        logCycles(Path.of("data/cycles"), lengths)
    } else {
        logProofs(Path.of("data"))
    }
}

private fun logIndex(root: Path, cycleLengths: Int, classesBits: Int) {
    root.resolve("index.txt").bufferedWriter(StandardCharsets.UTF_8).use { out ->
        out.write("Proof log\n\n")
        out.write("cycles/explanation.txt\n")
        out.write("  What the cycle search proves, and why the exponent bounds make it finite.\n")
        out.write("classes/explanation.md\n")
        out.write("  What the residue-class argument proves, and why a drop for every n > 1 implies the conjecture.\n\n")
        val transcriptThrough = minOf(cycleLengths, FULL_CYCLE_TRANSCRIPT)
        out.write("cycles/length-01.txt through cycles/length-${"%02d".format(transcriptThrough)}.txt\n")
        out.write("  Each file rules out an accelerated cycle of that length for every odd x > 1.\n")
        out.write("  Length 1 has one positive integer solution: x = 1 with exponent k = 2.\n")
        if (cycleLengths > FULL_CYCLE_TRANSCRIPT) {
            out.write(
                "  Lengths ${FULL_CYCLE_TRANSCRIPT + 1} through $cycleLengths are searched the same way. " +
                    "Their branch counts are in cycles/summary.txt. A line-by-line transcript is not written; " +
                    "length $cycleLengths is billions of characters.\n",
            )
        }
        out.newLine()
        out.write("classes/summary.txt\n")
        out.write("  Proved share of positive integers at each modulus 2^1 through 2^$classesBits.\n")
        out.write("classes/bits-01.txt through classes/bits-${"%02d".format(classesBits)}.txt\n")
        out.write("  The classes examined at that modulus, with the forced steps and the inequality that shows descent.\n")
    }
}

/** Past this length a line-by-line transcript is multiple gigabytes, so only the branch count is logged. */
private const val FULL_CYCLE_TRANSCRIPT = 16

private fun logCycles(directory: Path, lengths: Int) {
    val started = System.nanoTime()
    val heapBytes = Runtime.getRuntime().maxMemory()
    var peakRssBytes = currentRssBytes()
    val body = directory.resolve("summary.txt.body")
    body.bufferedWriter(StandardCharsets.UTF_8).use { summary ->
        summary.write("Accelerated cycles. Length is the number of steps (3x+1)/2^k.\n")
        summary.write(
            "x = 1, k = 2 is the cycle 1 -> 4 -> 2 -> 1. No length in this log has another positive integer solution.\n\n",
        )
        for (length in 1..lengths) {
            val result = if (length <= FULL_CYCLE_TRANSCRIPT) {
                writeCycle(directory.resolve("length-%02d.txt".format(length)), length)
            } else {
                proveCycleLength(length)
            }
            val verdict = if (result.solutions.isEmpty()) "no x > 1" else "${result.solutions.size} solution(s) with x > 1"
            summary.write("length $length: $verdict")
            if (result.branches > 0) summary.write(" (${result.branches} branches)")
            summary.newLine()
            val rssBytes = currentRssBytes()
            if (rssBytes > peakRssBytes) peakRssBytes = rssBytes
        }
    }
    val endingRss = currentRssBytes()
    if (endingRss > peakRssBytes) peakRssBytes = endingRss
    val elapsedNanos = System.nanoTime() - started
    val bodyBytes = Files.size(body)
    var fileBytes = bodyBytes
    var preface = ""
    for (attempt in 1..4) {
        preface = cyclePreface(lengths, heapBytes, elapsedNanos, peakRssBytes, fileBytes)
        val nextBytes = bodyBytes + preface.toByteArray(Charsets.UTF_8).size
        if (nextBytes == fileBytes) break
        fileBytes = nextBytes
    }
    val assembled = directory.resolve("summary.txt.assembled")
    Files.newOutputStream(assembled).buffered().use { out ->
        out.write(preface.toByteArray(Charsets.UTF_8))
        Files.copy(body, out)
    }
    Files.move(
        assembled,
        directory.resolve("summary.txt"),
        StandardCopyOption.REPLACE_EXISTING,
        StandardCopyOption.ATOMIC_MOVE,
    )
    body.deleteIfExists()
}

/**
 * Opening lines of the cycle summary.
 *
 * @param lengths Longest accelerated-cycle length this run searched. The search starts at length 1.
 * @param heapBytes Heap limit of the run.
 * @param elapsedNanos Time spent searching and writing the length files, in nanoseconds.
 * @param peakRssBytes Largest resident set of the process, in bytes. Zero omits that line.
 * @param fileBytes Size of the finished summary in bytes, this preface included.
 */
private fun cyclePreface(
    lengths: Int,
    heapBytes: Long,
    elapsedNanos: Long,
    peakRssBytes: Long,
    fileBytes: Long,
): String {
    val lines = ArrayList<String>()
    lines += "The run searched accelerated cycles of length 1 through $lengths."
    lines += "Computed by one JVM on ${machineDescription()}, heap ${formatBytes(heapBytes)} of memory, one core, " +
        "in ${formatElapsed(elapsedNanos)}."
    if (peakRssBytes > 0) lines += "The process peaked at ${formatBytes(peakRssBytes)} of memory."
    lines += "This file is ${formatBytes(fileBytes)} of storage."
    return lines.joinToString("\n", postfix = "\n\n")
}

private fun writeCycle(file: Path, length: Int): CycleLengthResult {
    file.bufferedWriter(StandardCharsets.UTF_8).use { out ->
        out.write("Length $length\n")
        out.write(nestedFormula(length))
        out.newLine()
        out.write(closedForm(length))
        out.newLine()
        if (length == 1) {
            val result = proveCycleLength(1)
            out.newLine()
            out.write(result.explanation)
            out.newLine()
            out.write("Trivial cycle: x = 1, k = 2.\n")
            out.write("Solutions with x > 1: 0\n")
            return result
        }
        val bounds = (0 until length).joinToString(" ") { maxExactHalvings(it).toString() }
        out.write("The least odd member x > 1 has first exponent 1: any larger power of 2 sends (3x+1)/2^k below x.\n")
        out.write("Later, exponent k at step j satisfies k < j*(log2(3)-1)+2, checked as 2^{k+j-2} < 3^j.\n")
        out.write("Maximum k at steps 0..${length - 1}: $bounds\n")
        out.write(
            "The total K = k1+...+k$length must satisfy 2^K > 3^$length, i.e. K >= ceil($length log2(3)). " +
                "A prefix that cannot reach that total, even at the maximum legal later exponents, is rejected once.\n",
        )
        out.write("A rejected prefix rules out every continuation. SOLUTION would be an odd cycle with x > 1.\n")
        out.write("x<=C < L: staying at or above the minimum forces x <= C, below the lower bound L.\n\n")
        val result = proveCycleLength(length) { line ->
            out.write(line)
            out.newLine()
        }
        out.newLine()
        out.write("Branches examined: ${result.branches}\n")
        out.write("Solutions with x > 1: ${result.solutions.size}\n")
        out.write(result.explanation)
        out.newLine()
        for (solution in result.solutions) {
            out.write("SOLUTION x = ${solution.start}, exponents ${solution.halvings.joinToString()}\n")
        }
        return result
    }
}

private fun logClasses(directory: Path, bits: Int) {
    val levels = CoverageProbe().apply { advanceTo(bits) }.levels()
    directory.resolve("summary.txt").bufferedWriter(StandardCharsets.UTF_8).use { summary ->
        summary.write("Positive integers forced strictly below their start by a shared Collatz prefix.\n")
        summary.write("The share is proved/modulus: every residue not still open was settled at this modulus or a coarser one.\n\n")
        for (level in levels) {
            writeClassesLevel(directory.resolve("bits-%02d.txt".format(level.bits)), level)
            summary.write(
                "2^${level.bits}  ${provedPercent(level.proved, level.modulus)}  ${level.proved}/${level.modulus}\n",
            )
        }
    }
}

private fun writeClassesLevel(file: Path, level: CoverageLevel) {
    file.bufferedWriter(StandardCharsets.UTF_8).use { out ->
        out.write("Modulus 2^${level.bits} = ${level.modulus}\n")
        out.write(
            "Proved ${level.proved}/${level.modulus} = ${provedPercent(level.proved, level.modulus)} " +
                "of positive integers.\n",
        )
        out.write(
            "Only the two children of each class still open at the previous modulus are examined here. " +
                "Every other residue is covered by a coarser proof.\n",
        )
        out.write("Still open: ${level.openResidues.size}\n\n")
        if (level.newlySettled.isEmpty()) {
            out.write("No class settled at this modulus.\n\n")
        }
        for (settled in level.newlySettled) {
            out.write("SETTLED ${settled.residue} mod ${settled.modulus}\n")
            out.write("n = ${settled.modulus}t + ${settled.residue}\n")
            writeSteps(out, settled)
            val check = descendsCheck(settled)
            check(check.holds) { "logged a class that does not descend: ${settled.residue} mod ${settled.modulus}" }
            out.write(check.text)
            out.newLine()
            out.newLine()
        }
        for (residue in level.openResidues) {
            when (val analysis = analyzeResidueClass(residue, level.modulus)) {
                is ResidueAnalysis.Descends ->
                    error("residue $residue mod ${level.modulus} was left open but the prefix descends")
                is ResidueAnalysis.Open -> {
                    out.write("OPEN ${analysis.residue} mod ${analysis.modulus}\n")
                    out.write("n = ${analysis.modulus}t + ${analysis.residue}\n")
                    writeSteps(out, analysis)
                    out.write(
                        "Leading coefficient ${analysis.image.coefficient} is odd, so the next parity depends on t. " +
                            "The shared prefix ends at ${analysis.image.formatPolynomial()} " +
                            "and is not strictly below n for every n > 1.\n\n",
                    )
                }
                is ResidueAnalysis.Loops -> {
                    out.write("OPEN ${analysis.residue} mod ${analysis.modulus}\n")
                    out.write("The determined prefix repeats ${analysis.image.formatPolynomial()} without falling below n.\n\n")
                }
                is ResidueAnalysis.Unresolved -> {
                    out.write("UNRESOLVED ${analysis.residue} mod ${analysis.modulus}\n")
                    out.write("Step cap hit at ${analysis.image.formatPolynomial()} while the prefix was still determined.\n\n")
                }
            }
        }
    }
}

private fun writeSteps(out: java.io.BufferedWriter, analysis: ResidueAnalysis) {
    for (step in analysis.steps) {
        val name = when (step.operation) {
            CollatzOperation.ThreeNPlusOne -> "3n+1"
            CollatzOperation.Halve -> "/2"
        }
        out.write("  $name  ${step.value.formatPolynomial()}")
        out.newLine()
    }
}

private data class DescendsCheck(val holds: Boolean, val text: String)

private fun descendsCheck(settled: ResidueAnalysis.Descends): DescendsCheck {
    val modulus = settled.modulus
    val residue = settled.residue
    val coefficient = settled.image.coefficient
    val constant = settled.image.constant
    val tMin = if (residue > BigInteger.ONE) BigInteger.ZERO else BigInteger.ONE
    val gap = modulus.subtract(coefficient)
    val slack = constant.subtract(residue)
    val compared = gap.multiply(tMin)
    val holds = when {
        coefficient < modulus -> compared > slack
        coefficient == modulus -> constant < residue
        else -> false
    }
    val comparison = if (coefficient < modulus) {
        "because ($modulus - $coefficient) * $tMin > ($constant - $residue), that is $compared > $slack."
    } else {
        "because the coefficients of t match and $constant < $residue."
    }
    val text = "Every n = ${modulus}t + $residue > 1 has t >= $tMin. " +
        "${settled.image.formatPolynomial()} < n for all such t $comparison"
    return DescendsCheck(holds, text)
}

private fun provedPercent(proved: BigInteger, modulus: BigInteger): String {
    val scaled = proved.multiply(BigInteger.valueOf(10_000))
    val division = scaled.divideAndRemainder(modulus)
    var basisPoints = division[0]
    if (division[1].shiftLeft(1) >= modulus) basisPoints += BigInteger.ONE
    val whole = basisPoints.divide(BigInteger.valueOf(100))
    val frac = basisPoints.remainder(BigInteger.valueOf(100)).toInt()
    if (frac == 0) return "$whole%"
    return "$whole.${frac.toString().padStart(2, '0').trimEnd('0')}%"
}
