package hackman.trevor.collatz.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import hackman.trevor.collatz.CollatzOperation
import hackman.trevor.collatz.CoverageLevel
import hackman.trevor.collatz.CoverageProbe
import hackman.trevor.collatz.MAX_PROBE_BITS
import hackman.trevor.collatz.ResidueAnalysis
import hackman.trevor.collatz.analyzeResidueClass
import java.math.BigInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun CoverageProbePanel(modifier: Modifier = Modifier) {
    val probe = remember { CoverageProbe() }
    var slider by remember { mutableFloatStateOf(12f) }
    var requested by remember { mutableIntStateOf(12) }
    var levels by remember { mutableStateOf<List<CoverageLevel>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var proving by remember { mutableStateOf<Int?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var selectedBits by remember { mutableIntStateOf(2) }
    var selected by remember { mutableStateOf<ResidueAnalysis?>(null) }
    val shownBits = slider.roundToInt().coerceIn(1, MAX_PROBE_BITS)
    val selectedLevel = levels.firstOrNull { it.bits == selectedBits.coerceAtMost(levels.size) }
    val shownAnalysis = selected ?: selectedLevel?.newlySettled?.firstOrNull()

    LaunchedEffect(requested) {
        val goal = requested
        if (probe.depth >= goal) {
            levels = probe.levels().filter { it.bits <= goal }
            loading = false
            proving = null
            return@LaunchedEffect
        }
        loading = true
        failure = null
        try {
            while (isActive && probe.depth < goal) {
                val next = probe.depth + 1
                proving = next
                withContext(Dispatchers.Default) { probe.advance() }
                if (!isActive) return@LaunchedEffect
                levels = probe.levels().filter { it.bits <= goal }
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            CrashLog.record(Thread.currentThread(), error)
            failure = error.message ?: error.javaClass.simpleName
        }
        loading = false
        proving = null
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Through 2^$shownBits", modifier = Modifier.width(140.dp))
            Slider(
                value = slider,
                onValueChange = { slider = it.coerceIn(1f, MAX_PROBE_BITS.toFloat()) },
                onValueChangeFinished = { requested = slider.roundToInt().coerceIn(1, MAX_PROBE_BITS) },
                valueRange = 1f..MAX_PROBE_BITS.toFloat(),
                steps = MAX_PROBE_BITS - 2,
                modifier = Modifier.weight(1f),
            )
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }
        val latest = levels.lastOrNull()
        Text(
            failure?.let { "Calculation failed ($it). The stack trace is in data/crashes." }
                ?: when (val bits = proving) {
                    null -> latest?.let { "Proved ${it.proved}/${it.modulus} (${it.percentLabel()}). Open classes: ${it.openResidues.size}." }
                        ?: "No level yet."
                    else -> "Settling classes modulo 2^$bits."
                },
        )
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LazyColumn(Modifier.width(LevelListWidth).fillMaxHeight()) {
                items(levels, key = { it.bits }) { level ->
                    val active = level.bits == selectedLevel?.bits
                    val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedBits = level.bits
                                selected = null
                            }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(LevelColumnGap),
                    ) {
                        LevelCell("2^${level.bits}", color, ExponentWidth)
                        LevelCell("${level.proved}/${level.modulus}", color, ShareWidth)
                        LevelCell(level.percentLabel(), color, PercentWidth)
                        LevelCell("+${level.newlySettled.size}", color, AddedWidth)
                    }
                }
            }
            if (selectedLevel == null) {
                Text("Advance the probe to see settled classes.")
            } else {
                LevelDetail(
                    level = selectedLevel,
                    shown = shownAnalysis,
                    onSelectSettled = { selected = it },
                    onSelectOpen = { residue ->
                        selected = analyzeResidueClass(residue, selectedLevel.modulus)
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun LevelDetail(
    level: CoverageLevel,
    shown: ResidueAnalysis?,
    onSelectSettled: (ResidueAnalysis.Descends) -> Unit,
    onSelectOpen: (BigInteger) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LazyColumn(Modifier.width(220.dp).fillMaxHeight()) {
            item {
                Text(
                    "New (${level.newlySettled.size})",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            if (level.newlySettled.isEmpty()) {
                item { Text("None. Share unchanged.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(level.newlySettled, key = { it.residue }) { settled ->
                ResidueLine(
                    label = "${settled.residue} mod ${settled.modulus}",
                    selected = shown?.residue == settled.residue && shown.modulus == settled.modulus,
                    onClick = { onSelectSettled(settled) },
                )
            }
            item {
                Text(
                    "Open (${level.openResidues.size})",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
            }
            items(level.openResidues, key = { it }) { residue ->
                ResidueLine(
                    label = "$residue mod ${level.modulus}",
                    selected = shown?.residue == residue && shown.modulus == level.modulus && shown !is ResidueAnalysis.Descends,
                    onClick = { onSelectOpen(residue) },
                )
            }
        }
        if (shown == null) {
            Text("This level settled nothing new.")
        } else {
            ClassTrace(shown, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun ResidueLine(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 2.dp),
    )
}

@Composable
private fun ClassTrace(analysis: ResidueAnalysis, modifier: Modifier = Modifier) {
    val verdict = when (analysis) {
        is ResidueAnalysis.Descends ->
            "Descends. ${analysis.image.formatPolynomial()} is strictly smaller than n for every n > 1 in the class."
        is ResidueAnalysis.Open ->
            "Open. The next parity depends on t. The shared prefix ends at ${analysis.image.formatPolynomial()}."
        is ResidueAnalysis.Loops ->
            "The determined prefix repeats ${analysis.image.formatPolynomial()} without falling below n."
        is ResidueAnalysis.Unresolved ->
            "Step cap hit while the prefix was still determined, at ${analysis.image.formatPolynomial()}."
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("n = ${analysis.modulus}t + ${analysis.residue}")
        Text(verdict, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        SelectionContainer {
            LazyColumn(Modifier.fillMaxSize()) {
                items(analysis.steps) { step ->
                    val name = when (step.operation) {
                        CollatzOperation.ThreeNPlusOne -> "3n+1"
                        CollatzOperation.Halve -> "/2   "
                    }
                    Text(
                        "$name   ${step.value.formatPolynomial()}",
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
private fun LevelCell(text: String, color: Color, width: Dp) {
    Text(
        text,
        fontFamily = FontFamily.Monospace,
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(width),
    )
}

// Separate columns so the share cannot run into the percent.
// Widths fit 2^32: a 21-character share, a two-decimal percent, and the new-class count.
private val ExponentWidth = 48.dp
private val ShareWidth = 224.dp
private val PercentWidth = 72.dp
private val AddedWidth = 96.dp
private val LevelColumnGap = 12.dp
private val LevelListWidth = ExponentWidth + ShareWidth + PercentWidth + AddedWidth + LevelColumnGap * 3

/** Percent of residues proved at this modulus, rounded half up to two decimal places. */
private fun CoverageLevel.percentLabel(): String {
    val scaled = proved.multiply(BigInteger.valueOf(10_000))
    val division = scaled.divideAndRemainder(modulus)
    val quotient = division[0]
    val remainder = division[1]
    var basisPoints = quotient
    if (remainder.shiftLeft(1) >= modulus) basisPoints += BigInteger.ONE
    val whole = basisPoints.divide(BigInteger.valueOf(100))
    val frac = basisPoints.remainder(BigInteger.valueOf(100)).toInt()
    if (frac == 0) return "$whole%"
    val fracText = frac.toString().padStart(2, '0').trimEnd('0')
    return "$whole.$fracText%"
}
