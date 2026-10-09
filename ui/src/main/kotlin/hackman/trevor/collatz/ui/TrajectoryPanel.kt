package hackman.trevor.collatz.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hackman.trevor.collatz.StopReason
import hackman.trevor.collatz.Trajectory
import hackman.trevor.collatz.trajectoryOf
import java.math.BigInteger
import kotlin.math.ln
import kotlin.math.roundToInt

@Composable
fun TrajectoryPanel(modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf("27") }
    val start = text.toBigIntegerOrNull()?.takeIf { it > BigInteger.ZERO }
    val trajectory = remember(start) { start?.let(::trajectoryOf) }

    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit) },
                label = { Text("Start") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Text(
                trajectory?.summary() ?: "Enter a positive integer.",
                modifier = Modifier.weight(2f).padding(top = 8.dp),
            )
        }
        if (trajectory != null) {
            var hoverIndex by remember(trajectory) { mutableStateOf<Int?>(null) }
            val hoverValue = hoverIndex?.let { trajectory.values.getOrNull(it) }
            Text(
                if (hoverIndex != null && hoverValue != null) {
                    "step $hoverIndex, value $hoverValue"
                } else {
                    "x is the step, y is the value on a log scale. The mark is the first value below the start."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TrajectoryChart(
                values = trajectory.values,
                stoppingIndex = trajectory.stoppingTime?.steps,
                hoverIndex = hoverIndex,
                onHover = { hoverIndex = it },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TrajectoryChart(
    values: List<BigInteger>,
    stoppingIndex: Int?,
    hoverIndex: Int?,
    onHover: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val line = MaterialTheme.colorScheme.primary
    val mark = MaterialTheme.colorScheme.tertiary
    val axis = MaterialTheme.colorScheme.onSurface
    val grid = axis.copy(alpha = 0.18f)
    val labelStyle = TextStyle(color = axis, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    val textMeasurer = rememberTextMeasurer()
    val yTicks = remember(values) { displayedYTicks(values) }
    val xTicks = remember(values) { displayedXTicks(values.lastIndex) }
    val yLabels = yTicks.map { textMeasurer.measure(it.toString(), labelStyle) }
    val xLabels = xTicks.map { textMeasurer.measure(it.toString(), labelStyle) }
    val hoverText = hoverIndex?.let { index ->
        values.getOrNull(index)?.let { "step $index, value $it" }
    }
    val hoverLabel = hoverText?.let { textMeasurer.measure(it, labelStyle) }
    val yLabelWidth = yLabels.maxOfOrNull { it.size.width } ?: 0
    val leftPad: Float
    val bottomPad: Float
    val topPad: Float
    val rightPad: Float
    with(LocalDensity.current) {
        leftPad = yLabelWidth + 16.dp.toPx()
        bottomPad = 28.dp.toPx()
        topPad = 8.dp.toPx()
        rightPad = 12.dp.toPx()
    }

    Canvas(
        modifier
            .onPointerEvent(PointerEventType.Move) {
                val position = it.changes.firstOrNull()?.position
                val plotWidth = size.width - leftPad - rightPad
                val index = if (position != null && plotWidth > 0f && position.y in topPad..(size.height - bottomPad)) {
                    indexAt(position.x, leftPad, plotWidth, values.size)
                } else {
                    null
                }
                if (index != hoverIndex) onHover(index)
            }
            .onPointerEvent(PointerEventType.Exit) {
                if (hoverIndex != null) onHover(null)
            },
    ) {
        if (values.isEmpty()) return@Canvas
        val logs = DoubleArray(values.size) { logScale(values[it]) }
        var low = logs[0]
        var high = logs[0]
        for (y in logs) {
            if (y < low) low = y
            if (y > high) high = y
        }
        val span = (high - low).takeIf { it > 1e-9 } ?: 1.0
        val plotWidth = size.width - leftPad - rightPad
        val plotHeight = size.height - topPad - bottomPad
        if (plotWidth <= 0f || plotHeight <= 0f) return@Canvas
        val plotBottom = topPad + plotHeight

        fun point(index: Int): Offset {
            val xFraction = if (logs.size == 1) 0.5f else index.toFloat() / (logs.size - 1)
            val yFraction = 1.0 - (logs[index] - low) / span
            return Offset(leftPad + plotWidth * xFraction, topPad + plotHeight * yFraction.toFloat())
        }

        fun yOf(value: BigInteger): Float {
            val yFraction = 1.0 - (logScale(value) - low) / span
            return topPad + plotHeight * yFraction.toFloat()
        }

        val tick = 5.dp.toPx()
        yTicks.forEachIndexed { index, value ->
            val y = yOf(value)
            val measured = yLabels[index]
            drawLine(grid, Offset(leftPad, y), Offset(leftPad + plotWidth, y), strokeWidth = 1.dp.toPx())
            drawLine(axis, Offset(leftPad - tick, y), Offset(leftPad, y), strokeWidth = 1.dp.toPx())
            drawText(
                measured,
                topLeft = Offset(leftPad - tick - 6.dp.toPx() - measured.size.width, y - measured.size.height / 2f),
            )
        }
        xTicks.forEachIndexed { index, step ->
            val x = point(step).x
            val measured = xLabels[index]
            drawLine(axis, Offset(x, plotBottom), Offset(x, plotBottom + tick), strokeWidth = 1.dp.toPx())
            val maxLeft = (size.width - measured.size.width).coerceAtLeast(0f)
            val left = (x - measured.size.width / 2f).coerceIn(0f, maxLeft)
            drawText(measured, topLeft = Offset(left, plotBottom + tick + 2.dp.toPx()))
        }
        drawLine(axis, Offset(leftPad, topPad), Offset(leftPad, plotBottom), strokeWidth = 1.5.dp.toPx())
        drawLine(axis, Offset(leftPad, plotBottom), Offset(leftPad + plotWidth, plotBottom), strokeWidth = 1.5.dp.toPx())

        val path = Path()
        logs.indices.forEach { index ->
            val point = point(index)
            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        drawPath(
            path,
            color = line,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        if (stoppingIndex != null && stoppingIndex in logs.indices) {
            drawCircle(color = mark, radius = 4.dp.toPx(), center = point(stoppingIndex))
        }
        val hovered = hoverIndex
        if (hovered != null && hovered in logs.indices) {
            val at = point(hovered)
            drawLine(mark, Offset(at.x, topPad), Offset(at.x, plotBottom), strokeWidth = 1.dp.toPx())
            drawCircle(color = mark, radius = 4.dp.toPx(), center = at)
            val measured = hoverLabel
            if (measured == null) return@Canvas
            val padX = 6.dp.toPx()
            val padY = 4.dp.toPx()
            val boxWidth = measured.size.width + padX * 2
            val boxHeight = measured.size.height + padY * 2
            val boxLeft = if (at.x + 8.dp.toPx() + boxWidth <= size.width) at.x + 8.dp.toPx() else at.x - 8.dp.toPx() - boxWidth
            val boxTop = (at.y - boxHeight - 8.dp.toPx()).coerceAtLeast(0f)
            drawRoundRect(
                color = Color(0xF0141218),
                topLeft = Offset(boxLeft, boxTop),
                size = Size(boxWidth, boxHeight),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
            drawText(measured, topLeft = Offset(boxLeft + padX, boxTop + padY))
        }
    }
}

private fun indexAt(x: Float, leftPad: Float, plotWidth: Float, count: Int): Int? {
    if (count == 0 || x < leftPad || x > leftPad + plotWidth) return null
    if (count == 1) return 0
    val fraction = ((x - leftPad) / plotWidth).coerceIn(0f, 1f)
    return (fraction * (count - 1)).roundToInt()
}

/** Powers of ten inside the range, plus the minimum and maximum, so the log axis is labeled in actual values. */
private fun displayedYTicks(values: List<BigInteger>): List<BigInteger> {
    if (values.isEmpty()) return emptyList()
    val min = values.min()
    val max = values.max()
    if (min == max) return listOf(min)
    val ticks = mutableListOf(min, max)
    var power = BigInteger.ONE
    while (power < max) {
        if (power > min) ticks.add(power)
        if (max / BigInteger.TEN < power) break
        power = power.multiply(BigInteger.TEN)
    }
    return ticks.distinct().sorted()
}

private fun displayedXTicks(lastStep: Int): List<Int> {
    if (lastStep <= 0) return listOf(0)
    return (0..4).map { lastStep * it / 4 }.distinct()
}

private fun Trajectory.summary(): String {
    val stopping = stoppingTime?.let { "Drops below the start in ${it.steps} steps, to ${it.value}." }
        ?: "Does not drop below the start in this walk."
    val ending = when (reason) {
        StopReason.ReachedOne -> "Reaches 1 in ${values.lastIndex} steps."
        StopReason.Cycle -> "Repeats ${values.last()} before reaching 1."
        StopReason.StepLimit -> "Stopped at the step cap."
    }
    val peak = values.maxOrNull()
    return "$stopping $ending Peak $peak."
}

/** Natural log that stays finite for integers past the double range. 1 maps to 0. */
private fun logScale(n: BigInteger): Double {
    if (n <= BigInteger.ONE) return 0.0
    val shift = (n.bitLength() - 53).coerceAtLeast(0)
    val mantissa = n.shiftRight(shift).toDouble()
    return ln(mantissa) + shift * ln(2.0)
}
