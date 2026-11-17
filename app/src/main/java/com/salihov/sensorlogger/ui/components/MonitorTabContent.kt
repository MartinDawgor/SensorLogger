package com.salihov.sensorlogger.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.salihov.sensorlogger.data.sensor.SensorToolsMath
import com.salihov.sensorlogger.domain.model.SensorDescriptor
import com.salihov.sensorlogger.domain.model.SensorSample
import com.salihov.sensorlogger.domain.model.SensorTelemetrySnapshot
import java.util.Locale

@Composable
fun MonitorTabContent(
    pinnedSensors: List<SensorDescriptor>,
    liveTelemetry: Map<String, SensorTelemetrySnapshot>,
    monitorHistories: Map<String, List<SensorSample>>,
    onUnpin: (String) -> Unit,
    onInspectSensor: (String) -> Unit,
    onResetStats: () -> Unit
) {
    val listState = rememberLazyListState()
    var frozenTelemetry by remember { mutableStateOf(liveTelemetry) }
    var frozenHistories by remember { mutableStateOf(monitorHistories) }

    // Pause chart & telemetry updates while user is actively scrolling
    LaunchedEffect(liveTelemetry, monitorHistories, listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            frozenTelemetry = liveTelemetry
            frozenHistories = monitorHistories
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp)
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "ОСЦИЛЛОГРАФЫ: ${pinnedSensors.size}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = onResetStats,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.RestartAlt,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Сброс min/max", style = MaterialTheme.typography.labelSmall)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (pinnedSensors.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Text(
                    text = "Нет закреплённых датчиков.\nНажмите на иконку булавки в разделе «Датчики», чтобы отслеживать графики здесь.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(
                    items = pinnedSensors,
                    key = { it.id },
                    contentType = { "monitor_chart_panel" }
                ) { sensor ->
                    val telemetry = frozenTelemetry[sensor.id]
                    val history = frozenHistories[sensor.id].orEmpty()
                    val chartPoints = remember(history) { history.map { it.values } }

                    DiagnosticSectionPanel(
                        title = sensor.displayName,
                        rightBadge = buildString {
                            if (telemetry != null && telemetry.measuredHz > 0f) {
                                append(String.format(Locale.US, "%.1f Гц", telemetry.measuredHz))
                            }
                            if (sensor.unit.isNotBlank()) {
                                if (isNotEmpty()) append(" • ")
                                append(sensor.unit)
                            }
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onInspectSensor(sensor.id) }
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (telemetry != null) {
                                        AccuracyBadge(telemetry.accuracy)
                                        Text(
                                            text = "сэмплов: ${telemetry.sampleCount}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (telemetry.values.size >= 3) {
                                            val mag = SensorToolsMath.vectorMagnitude(telemetry.values)
                                            Text(
                                                text = "|V|=${String.format(Locale.US, "%.2f", mag)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = "Ожидание потока...",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { onUnpin(sensor.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = "Открепить",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            if (telemetry != null && telemetry.values.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                telemetry.values.take(4).forEachIndexed { ch, value ->
                                    val min = telemetry.minValues.getOrNull(ch) ?: value
                                    val max = telemetry.maxValues.getOrNull(ch) ?: value
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = sensor.channelLabel(ch),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${String.format(Locale.US, "%+.3f", value)}  [${String.format(Locale.US, "%+.2f", min)}..${String.format(Locale.US, "%+.2f", max)}]",
                                            style = MaterialTheme.typography.labelMedium.merge(
                                                TextStyle(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontFeatureSettings = "tnum"
                                                )
                                            ),
                                            color = MaterialTheme.colorScheme.tertiary,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            TelemetryWaveformChart(
                                samples = chartPoints,
                                channelLabels = sensor.axisLabels,
                                unit = sensor.unit,
                                minScaleSpan = sensor.recommendedChartMinSpan
                            )
                        }
                    }
                }
            }
        }
    }
}
