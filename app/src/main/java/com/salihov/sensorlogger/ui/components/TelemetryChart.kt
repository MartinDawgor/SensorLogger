package com.salihov.sensorlogger.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val ChannelPalette = listOf(
    Color(0xFF1565C0),
    Color(0xFF0F766E),
    Color(0xFFD97706),
    Color(0xFF7C3AED),
    Color(0xFF0284C7),
    Color(0xFFB91C1C)
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TelemetryWaveformChart(
    samples: List<List<Float>>,
    channelLabels: List<String>,
    unit: String,
    modifier: Modifier = Modifier,
    minScaleSpan: Float = 1.0f,
    emptyText: String = "Ожидание данных с датчика...",
    enableGestures: Boolean = false
) {
    var autoZoom by remember { mutableStateOf(false) }
    var zoomFactor by remember { mutableFloatStateOf(1f) }
    var panFraction by remember { mutableFloatStateOf(1f) }

    val visibleSamples = remember(samples, enableGestures, zoomFactor, panFraction) {
        if (!enableGestures || zoomFactor <= 1.02f || samples.size < 4) {
            samples
        } else {
            val windowSize = (samples.size / zoomFactor).roundToInt().coerceIn(4, samples.size)
            val maxStart = (samples.size - windowSize).coerceAtLeast(0)
            val start = (maxStart * panFraction).roundToInt().coerceIn(0, maxStart)
            samples.subList(start, (start + windowSize).coerceAtMost(samples.size))
        }
    }

    val maxChannels = remember(visibleSamples) {
        (visibleSamples.maxOfOrNull { it.size } ?: 0).coerceAtMost(ChannelPalette.size)
    }
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val zeroLineColor = MaterialTheme.colorScheme.outline
    val tabularMono = MaterialTheme.typography.labelSmall.merge(
        TextStyle(fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum")
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            if (visibleSamples.isEmpty() || maxChannels == 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = emptyText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val (paddedMin, paddedMax) = remember(visibleSamples, maxChannels, autoZoom, minScaleSpan) {
                    var minVal = Float.POSITIVE_INFINITY
                    var maxVal = Float.NEGATIVE_INFINITY
                    for (row in visibleSamples) {
                        for (idx in 0 until maxChannels) {
                            val v = row.getOrNull(idx) ?: continue
                            if (v.isFinite()) {
                                if (v < minVal) minVal = v
                                if (v > maxVal) maxVal = v
                            }
                        }
                    }
                    if (!minVal.isFinite() || !maxVal.isFinite()) {
                        minVal = -0.5f
                        maxVal = 0.5f
                    }
                    val rawDiff = maxVal - minVal
                    val effectiveMinSpan = if (autoZoom) 1e-3f else minScaleSpan.coerceAtLeast(1e-3f)
                    if (rawDiff < effectiveMinSpan) {
                        val mid = (minVal + maxVal) / 2f
                        val center = if (!autoZoom && abs(mid) < effectiveMinSpan * 0.25f) 0f else mid
                        val half = effectiveMinSpan / 2f
                        (center - half) to (center + half)
                    } else {
                        (minVal - rawDiff * 0.08f) to (maxVal + rawDiff * 0.08f)
                    }
                }
                val paddedSpan = (paddedMax - paddedMin).coerceAtLeast(1e-4f)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val unitSuffix = if (unit.isNotBlank()) " $unit" else ""
                    Text(
                        text = "шкала ${String.format(Locale.US, "%.2f", paddedMin)} .. ${String.format(Locale.US, "%.2f", paddedMax)}$unitSuffix",
                        style = tabularMono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (enableGestures && zoomFactor > 1.02f) {
                            Surface(
                                modifier = Modifier.clickable {
                                    zoomFactor = 1f
                                    panFraction = 1f
                                },
                                shape = MaterialTheme.shapes.extraSmall,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "${String.format(Locale.US, "%.1fx", zoomFactor)} Сброс",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Surface(
                            modifier = Modifier.clickable { autoZoom = !autoZoom },
                            shape = MaterialTheme.shapes.extraSmall,
                            color = if (autoZoom) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            }
                        ) {
                            Text(
                                text = if (autoZoom) "Авто-зум" else "Стаб. шкала",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (autoZoom) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                val gestureModifier = if (enableGestures) {
                    Modifier.pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val nextZoom = (zoomFactor * zoom).coerceIn(1f, 8f)
                            zoomFactor = nextZoom
                            if (nextZoom > 1.02f && size.width > 0) {
                                panFraction = (panFraction - pan.x / size.width).coerceIn(0f, 1f)
                            } else {
                                panFraction = 1f
                            }
                        }
                    }
                } else {
                    Modifier
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clipToBounds()
                        .then(gestureModifier)
                        .drawWithCache {
                            val w = size.width
                            val h = size.height
                            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                            val strokeStyle = Stroke(
                                width = 2.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                            val pointCount = visibleSamples.size
                            val stepX = if (pointCount > 1) w / (pointCount - 1) else w
                            val paths = List(maxChannels) { ch ->
                                Path().apply {
                                    var started = false
                                    for (i in visibleSamples.indices) {
                                        val v = visibleSamples[i].getOrNull(ch) ?: continue
                                        if (!v.isFinite()) continue
                                        val x = if (pointCount == 1) w / 2f else i * stepX
                                        val norm = ((v - paddedMin) / paddedSpan).coerceIn(0f, 1f)
                                        val y = h - norm * h
                                        if (!started) {
                                            moveTo(x, y)
                                            started = true
                                        } else {
                                            lineTo(x, y)
                                        }
                                    }
                                }
                            }

                            onDrawBehind {
                                for (i in 0..4) {
                                    val y = h * i / 4f
                                    drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                                }
                                for (i in 0..4) {
                                    val x = w * i / 4f
                                    drawLine(gridColor.copy(alpha = 0.6f), Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
                                }
                                if (paddedMin <= 0f && paddedMax >= 0f) {
                                    val zeroY = h - ((0f - paddedMin) / paddedSpan) * h
                                    drawLine(
                                        color = zeroLineColor.copy(alpha = 0.75f),
                                        start = Offset(0f, zeroY),
                                        end = Offset(w, zeroY),
                                        strokeWidth = 1.5f,
                                        pathEffect = dashEffect
                                    )
                                }
                                paths.forEachIndexed { ch, path ->
                                    if (!path.isEmpty) {
                                        drawPath(
                                            path = path,
                                            color = ChannelPalette[ch % ChannelPalette.size],
                                            style = strokeStyle
                                        )
                                    }
                                }
                            }
                        }
                )

                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val lastRow = visibleSamples.lastOrNull().orEmpty()
                    for (ch in 0 until maxChannels) {
                        val color = ChannelPalette[ch % ChannelPalette.size]
                        val label = channelLabels.getOrNull(ch) ?: "v$ch"
                        val current = lastRow.getOrNull(ch)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(color, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (current != null) {
                                    "$label: ${String.format(Locale.US, "%+.3f", current)}"
                                } else {
                                    label
                                },
                                style = tabularMono
                            )
                        }
                    }
                }
            }
        }
    }
}
