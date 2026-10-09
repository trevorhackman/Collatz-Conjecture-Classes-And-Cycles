package hackman.trevor.collatz.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import hackman.trevor.collatz.CycleLengthResult
import hackman.trevor.collatz.CycleProbe
import hackman.trevor.collatz.MAX_CYCLE_LENGTH
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun CycleProbePanel(modifier: Modifier = Modifier) {
    val probe = remember { CycleProbe() }
    var slider by remember { mutableFloatStateOf(12f) }
    var requested by remember { mutableIntStateOf(12) }
    var results by remember { mutableStateOf<List<CycleLengthResult>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var proving by remember { mutableStateOf<Int?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var selectedLength by remember { mutableIntStateOf(1) }
    val shownLength = slider.roundToInt().coerceIn(1, MAX_CYCLE_LENGTH)
    val selected = results.firstOrNull { it.length == selectedLength.coerceAtMost(results.size) }

    LaunchedEffect(requested) {
        val goal = requested
        if (probe.length >= goal) {
            results = probe.results().filter { it.length <= goal }
            loading = false
            proving = null
            return@LaunchedEffect
        }
        loading = true
        failure = null
        try {
            while (isActive && probe.length < goal) {
                val next = probe.length + 1
                proving = next
                withContext(Dispatchers.Default) { probe.proveNext() }
                if (!isActive) return@LaunchedEffect
                results = probe.results().filter { it.length <= goal }
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
            Text("Through length $shownLength", modifier = Modifier.width(180.dp))
            Slider(
                value = slider,
                onValueChange = { slider = it.coerceIn(1f, MAX_CYCLE_LENGTH.toFloat()) },
                onValueChangeFinished = { requested = slider.roundToInt().coerceIn(1, MAX_CYCLE_LENGTH) },
                valueRange = 1f..MAX_CYCLE_LENGTH.toFloat(),
                steps = MAX_CYCLE_LENGTH - 2,
                modifier = Modifier.weight(1f),
            )
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }
        Text(
            failure?.let { "Calculation failed ($it). The stack trace is in data/crashes." }
                ?: when (val length = proving) {
                    null -> "Length counts accelerated steps (3x+1)/2^k. The only positive solution on this list is x = 1."
                    else -> "Proving length $length. Past 20 this takes minutes, and each further length costs several times more."
                },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LazyColumn(Modifier.width(420.dp).fillMaxHeight()) {
                items(results, key = { it.length }) { result ->
                    val active = result.length == selected?.length
                    Text(
                        result.rowLabel(),
                        fontFamily = FontFamily.Monospace,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedLength = result.length }
                            .padding(vertical = 3.dp),
                    )
                }
            }
            if (selected == null) {
                Text("Prove a length to see the equation.")
            } else {
                CycleDetail(selected, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CycleDetail(result: CycleLengthResult, modifier: Modifier = Modifier) {
    SelectionContainer {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Length ${result.length}", style = MaterialTheme.typography.titleMedium)
            Text(result.formula, fontFamily = FontFamily.Monospace)
            Text(result.closedForm, fontFamily = FontFamily.Monospace)
            Text(result.explanation)
            val trivial = result.trivial
            if (trivial != null) {
                Text("Trivial cycle: x = ${trivial.start}, exponents ${trivial.halvings.joinToString()}.")
            }
            if (result.solutions.isNotEmpty()) {
                Text("Solutions with x > 1:", color = MaterialTheme.colorScheme.error)
                for (cycle in result.solutions) {
                    Text("x = ${cycle.start}, exponents ${cycle.halvings.joinToString()}", fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

private fun CycleLengthResult.rowLabel(): String {
    val title = "n=$length".padEnd(6)
    val verdict = if (solutions.isEmpty()) "no x > 1" else "${solutions.size} cycle(s)"
    val work = if (branches == 0L) "solved directly" else "$branches tuples"
    return "$title${verdict.padEnd(16)}$work"
}
