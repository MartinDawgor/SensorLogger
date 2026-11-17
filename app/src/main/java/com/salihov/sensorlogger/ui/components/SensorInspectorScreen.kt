package com.salihov.sensorlogger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.salihov.sensorlogger.data.sensor.SensorToolsMath
import com.salihov.sensorlogger.domain.model.SamplingRate
import com.salihov.sensorlogger.domain.model.SensorCalibration
import com.salihov.sensorlogger.domain.model.SensorDescriptor
import com.salihov.sensorlogger.domain.model.SensorSample
import com.salihov.sensorlogger.domain.model.SensorTelemetrySnapshot
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SensorInspectorScreen(
    sensor: SensorDescriptor,
    isSelectedForRecording: Boolean,
    isPinnedToMonitor: Boolean,
    isRecording: Boolean,
    missingPermissions: Set<String>,
    samplingRate: SamplingRate,
    rateOverride: SamplingRate?,
    telemetry: SensorTelemetrySnapshot?,
    liveValues: List<Float>?,
    history: List<SensorSample>,
    calibration: SensorCalibration?,
    applyCalibration: Boolean,
    isCalibrating: Boolean,
    calibrationProgress: Float,
    onBack: () -> Unit,
    onToggleRecordingSelection: () -> Unit,
    onTogglePin: () -> Unit,
    onSelectOnlyThisSensor: () -> Unit,
    onSetRateOverride: (SamplingRate?) -> Unit,
    onStartCalibration: () -> Unit,
    onClearCalibration: () -> Unit,
    onToggleApplyCalibration: (Boolean) -> Unit,
    onRequestPermissions: (Set<String>) -> Unit
) {
    val needsPermission = sensor.requiredPermission != null && sensor.requiredPermission in missingPermissions
    val effectiveRate = rateOverride ?: samplingRate

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = sensor.displayName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${sensor.name} • ${effectiveRate.shortHz}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                },
                actions = {
                    if (!sensor.isTriggerOnly) {
                        IconButton(onClick = onTogglePin) {
                            Icon(
                                imageVector = Icons.Outlined.PushPin,
                                contentDescription = if (isPinnedToMonitor) "Убрать с монитора" else "Закрепить на мониторе",
                                tint = if (isPinnedToMonitor) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                DiagnosticSectionPanel(
                    title = "Назначение и Единицы",
                    rightBadge = sensor.category.label
                ) {
                    Text(
                        text = sensor.description,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            if (needsPermission) {
                item {
                    PermissionBannerStrip(
                        missingPermissions = setOfNotNull(sensor.requiredPermission),
                        onRequestPermissions = {
                            sensor.requiredPermission?.let { onRequestPermissions(setOf(it)) }
                        }
                    )
                }
            }

            if (sensor.isTriggerOnly) {
                item {
                    DiagnosticSectionPanel(title = "Режим триггера") {
                        Text(
                            text = "Триггерный датчик (one-shot): срабатывает однократно при событии и не выдаёт непрерывный поток значений.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            } else {
                item {
                    val hzBadge = telemetry?.measuredHz?.takeIf { it > 0f }
                        ?.let { String.format(Locale.US, "%.1f Гц", it) }
                        ?: "Ожидание"
                    val calTag = if (calibration != null && applyCalibration) " [CAL]" else ""
                    DiagnosticSectionPanel(
                        title = "Живая телеметрия$calTag",
                        rightBadge = hzBadge
                    ) {
                        val values = liveValues.orEmpty()
                        if (values.isEmpty()) {
                            Text(
                                text = if (isRecording && !isSelectedForRecording) {
                                    "Идёт запись других датчиков — этот датчик не включён в текущую сессию."
                                } else {
                                    "Ожидание первого значения от датчика..."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(10.dp)
                            )
                        } else {
                            values.forEachIndexed { index, value ->
                                val channelMin = history.mapNotNull { it.values.getOrNull(index) }.minOrNull()
                                val channelMax = history.mapNotNull { it.values.getOrNull(index) }.maxOrNull()
                                FixedTelemetryChannelRow(
                                    channelName = sensor.channelLabel(index),
                                    formattedValue = formatSignedValue(value, sensor.unit),
                                    minMaxRange = if (channelMin != null && channelMax != null) {
                                        "${String.format(Locale.US, "%+.2f", channelMin)} .. ${String.format(Locale.US, "%+.2f", channelMax)}"
                                    } else {
                                        "—"
                                    },
                                    index = index,
                                    showDivider = index < values.lastIndex || values.size >= 3 || telemetry != null
                                )
                            }
                            if (values.size >= 3) {
                                val mag = SensorToolsMath.vectorMagnitude(values)
                                DiagnosticKeyValueRow(
                                    label = "Модуль вектора |V|",
                                    value = sensor.formatValueWithUnit(mag),
                                    index = values.size,
                                    valueColor = MaterialTheme.colorScheme.primary,
                                    showDivider = telemetry != null
                                )
                            }
                            if (telemetry != null) {
                                DiagnosticKeyValueRow(
                                    label = "Точность калибровки",
                                    value = telemetry.accuracy.fullLabel,
                                    index = values.size + 1,
                                    showDivider = false
                                )
                            }
                        }
                    }
                }

                item {
                    DiagnosticSectionPanel(
                        title = "Осциллограмма (Real-Time)",
                        rightBadge = "${history.size} точек"
                    ) {
                        TelemetryWaveformChart(
                            samples = history.map { it.values },
                            channelLabels = sensor.axisLabels,
                            unit = sensor.unit,
                            minScaleSpan = sensor.recommendedChartMinSpan,
                            enableGestures = true,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                item {
                    DiagnosticSectionPanel(
                        title = "Частота опроса (Override)",
                        rightBadge = rateOverride?.shortHz ?: "Общая (${samplingRate.shortHz})"
                    ) {
                        FlowRow(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            FilterChip(
                                selected = rateOverride == null,
                                onClick = { onSetRateOverride(null) },
                                enabled = !isRecording,
                                label = { Text("Общая", style = MaterialTheme.typography.labelSmall) }
                            )
                            SamplingRate.entries.forEach { rate ->
                                FilterChip(
                                    selected = rateOverride == rate,
                                    onClick = { onSetRateOverride(rate) },
                                    enabled = !isRecording,
                                    label = { Text(rate.shortHz, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }

                item {
                    DiagnosticSectionPanel(
                        title = "Программная калибровка (без root)",
                        rightBadge = if (calibration != null) "Активна" else "Не настроена"
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = when (sensor.type) {
                                    4, 16, 37 -> "Системную заводскую калибровку в HAL без root изменить нельзя, но можно откалибровать дрейф нуля гироскопа внутри приложения: положите телефон неподвижно и нажмите кнопку ниже."
                                    2, 14 -> "Для системной калибровки магнитометра покрутите телефон «восьмёркой». Дополнительно можно зафиксировать локальное смещение нуля."
                                    1 -> "Положите телефон горизонтально на ровный стол: выровняет X и Y к 0, а вертикальную ось к 9.81 м/с² внутри SensorLogger."
                                    else -> "Вычисляет среднее смещение в покое и вычитает его из показаний внутри приложения (системный HAL без root не изменяется)."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (isCalibrating) {
                                LinearProgressIndicator(
                                    progress = { calibrationProgress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    text = "Сбор замеров в покое: ${(calibrationProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            if (calibration != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Применять смещение",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = calibration.offsets.joinToString("  ") {
                                                String.format(Locale.US, "%+.3f", it)
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Switch(
                                        checked = applyCalibration,
                                        onCheckedChange = onToggleApplyCalibration
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onStartCalibration,
                                    enabled = !isCalibrating && !needsPermission,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Outlined.CenterFocusStrong, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isCalibrating) "Калибровка..." else "Калибровать ноль")
                                }

                                if (calibration != null) {
                                    OutlinedButton(
                                        onClick = onClearCalibration,
                                        enabled = !isCalibrating
                                    ) {
                                        Icon(Icons.Outlined.RestartAlt, contentDescription = null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Сброс")
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isSelectedForRecording) {
                            FilledTonalButton(
                                onClick = onToggleRecordingSelection,
                                enabled = !isRecording,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("В записи CSV")
                            }
                        } else {
                            OutlinedButton(
                                onClick = onToggleRecordingSelection,
                                enabled = !isRecording && !needsPermission,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.RadioButtonUnchecked, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Добавить в CSV")
                            }
                        }

                        Button(
                            onClick = onSelectOnlyThisSensor,
                            enabled = !isRecording && !needsPermission,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.TrackChanges, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Только этот")
                        }
                    }
                }
            }

            item {
                DiagnosticSectionPanel(
                    title = "Паспорт оборудования",
                    rightBadge = "Type #${sensor.type}"
                ) {
                    DiagnosticKeyValueRow("Аппаратное имя", sensor.name, 0)
                    DiagnosticKeyValueRow("Системный тип", sensor.stringType, 1)
                    DiagnosticKeyValueRow("Производитель", sensor.vendor.ifBlank { "Не указан" }, 2)
                    DiagnosticKeyValueRow("Единица измерения", sensor.unit.ifBlank { "Безразмерная" }, 3)
                    DiagnosticKeyValueRow(
                        "Макс. диапазон",
                        if (sensor.maxRange > 0f) "${String.format(Locale.US, "%.3f", sensor.maxRange)} ${sensor.unit}".trim() else "—",
                        4
                    )
                    DiagnosticKeyValueRow(
                        "Разрешение",
                        if (sensor.resolution > 0f) String.format(Locale.US, "%.5f", sensor.resolution) else "—",
                        5
                    )
                    DiagnosticKeyValueRow(
                        "Макс. частота",
                        sensor.maxFrequencyHz?.let { String.format(Locale.US, "%.1f Гц", it) } ?: "По событию",
                        6
                    )
                    DiagnosticKeyValueRow(
                        "Энергопотребление",
                        if (sensor.powerMa > 0f) String.format(Locale.US, "%.2f мА", sensor.powerMa) else "—",
                        7
                    )
                    DiagnosticKeyValueRow("Режим работы", if (sensor.isTriggerOnly) "Триггерный (one-shot)" else "Потоковый", 8)
                    DiagnosticKeyValueRow("Wake-up датчик", if (sensor.isWakeUp) "Да (будит CPU)" else "Нет", 9, showDivider = false)
                }
            }
        }
    }
}

@Composable
private fun FixedTelemetryChannelRow(
    channelName: String,
    formattedValue: String,
    minMaxRange: String,
    index: Int,
    showDivider: Boolean
) {
    val bg = if (index % 2 == 1) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.surface
    }
    val tabularStyle = MaterialTheme.typography.bodySmall.merge(
        TextStyle(fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum")
    )
    val rangeStyle = MaterialTheme.typography.labelSmall.merge(
        TextStyle(fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum")
    )

    Column(modifier = Modifier.background(bg)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 42.dp)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = channelName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(0.32f)
            )
            Column(
                modifier = Modifier.weight(0.68f),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = formattedValue,
                    style = tabularStyle,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.tertiary,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = minMaxRange,
                    style = rangeStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
            )
        }
    }
}

private fun formatSignedValue(value: Float, unit: String): String {
    val num = String.format(Locale.US, "%+.3f", value)
    return if (unit.isNotBlank()) "$num $unit" else num
}
