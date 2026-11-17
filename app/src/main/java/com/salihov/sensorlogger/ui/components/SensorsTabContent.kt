package com.salihov.sensorlogger.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.salihov.sensorlogger.domain.model.ExtraSource
import com.salihov.sensorlogger.domain.model.SensorAccuracy
import com.salihov.sensorlogger.domain.model.SensorCategory
import com.salihov.sensorlogger.domain.model.SensorDescriptor
import com.salihov.sensorlogger.domain.model.SensorTelemetrySnapshot
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SensorsTabContent(
    sensors: List<SensorDescriptor>,
    totalCount: Int,
    selectedSensorIds: Set<String>,
    pinnedSensorIds: Set<String>,
    enabledExtraSources: Set<ExtraSource>,
    liveReadings: Map<String, List<Float>>,
    liveTelemetry: Map<String, SensorTelemetrySnapshot>,
    missingPermissions: Set<String>,
    searchQuery: String,
    selectedCategory: SensorCategory?,
    isRecording: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSelectCategory: (SensorCategory?) -> Unit,
    onApplyPreset: (String) -> Unit,
    onToggleExtraSource: (ExtraSource) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
    onToggleSensor: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    onInspectSensor: (String) -> Unit,
    onRequestPermissions: (Set<String>) -> Unit
) {
    val listState = rememberLazyListState()
    var frozenReadings by remember { mutableStateOf(liveReadings) }
    var frozenTelemetry by remember { mutableStateOf(liveTelemetry) }
    var filtersDialogOpen by remember { mutableStateOf(false) }

    // Pause live telemetry text updates during scroll so LazyColumn stays 60/120 FPS
    LaunchedEffect(liveReadings, liveTelemetry, listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            frozenReadings = liveReadings
            frozenTelemetry = liveTelemetry
        }
    }

    if (filtersDialogOpen) {
        AlertDialog(
            onDismissRequest = { filtersDialogOpen = false },
            title = { Text("Фильтры и Источники") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Категория датчиков:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { onSelectCategory(null) },
                            label = { Text("Все ($totalCount)", style = MaterialTheme.typography.labelSmall) }
                        )
                        SensorCategory.entries.forEach { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = { onSelectCategory(category) },
                                label = { Text(category.label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    HorizontalDivider()
                    Text("Быстрые пресеты выбора:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterChip(
                            selected = false,
                            enabled = !isRecording,
                            onClick = { onApplyPreset("no_wakeup") },
                            label = { Text("Без wake-up", style = MaterialTheme.typography.labelSmall) }
                        )
                        FilterChip(
                            selected = false,
                            enabled = !isRecording,
                            onClick = { onApplyPreset("motion") },
                            label = { Text("Движение", style = MaterialTheme.typography.labelSmall) }
                        )
                        FilterChip(
                            selected = false,
                            enabled = !isRecording,
                            onClick = { onApplyPreset("environment") },
                            label = { Text("Среда", style = MaterialTheme.typography.labelSmall) }
                        )
                    }

                    HorizontalDivider()
                    Text("Дополнительные источники в лог:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ExtraSource.entries.forEach { src ->
                            FilterChip(
                                selected = src in enabledExtraSources,
                                enabled = !isRecording,
                                onClick = { onToggleExtraSource(src) },
                                label = { Text(src.label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { filtersDialogOpen = false }) {
                    Text("Готово")
                }
            }
        )
    }

    val activeFilterCount = (if (selectedCategory != null) 1 else 0) + enabledExtraSources.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp)
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        if (missingPermissions.isNotEmpty()) {
            PermissionBannerStrip(
                missingPermissions = missingPermissions,
                onRequestPermissions = { onRequestPermissions(missingPermissions) }
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CompactSearchField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = "Поиск...",
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = { filtersDialogOpen = true },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.FilterList,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (activeFilterCount > 0) "Фильтры ($activeFilterCount)" else "Фильтры",
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = buildString {
                    append("${sensors.size}/$totalCount • В ЗАПИСИ ${selectedSensorIds.size}")
                    if (selectedCategory != null) append(" • ${selectedCategory.label}")
                },
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Row {
                TextButton(
                    onClick = onSelectAll,
                    enabled = !isRecording,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("Все", style = MaterialTheme.typography.labelMedium)
                }
                TextButton(
                    onClick = onClearAll,
                    enabled = !isRecording,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("Сброс", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        if (sensors.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Text(
                    text = if (totalCount == 0) {
                        "Датчики на устройстве не обнаружены"
                    } else {
                        "По фильтру ничего не найдено"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 8.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(
                        items = sensors,
                        key = { _, item -> item.id },
                        contentType = { _, _ -> "sensor_row" }
                    ) { index, sensor ->
                        val liveVals = frozenReadings[sensor.id]
                        val tel = frozenTelemetry[sensor.id]
                        val readingStr = remember(sensor.id, liveVals) {
                            if (liveVals.isNullOrEmpty()) null else formatCompactReadings(sensor, liveVals)
                        }
                        val hzStr = remember(tel?.measuredHz) {
                            tel?.measuredHz?.takeIf { it > 0f }?.let { String.format(Locale.US, "%.1f Гц", it) }
                        }
                        val metaStr = remember(sensor.id, missingPermissions) {
                            val needsPerm = sensor.requiredPermission != null && sensor.requiredPermission in missingPermissions
                            buildMetaLine(sensor, needsPerm)
                        }

                        DiagnosticSensorRow(
                            title = sensor.displayName,
                            metaLine = metaStr,
                            readingLine = readingStr,
                            hzLine = hzStr,
                            accuracy = tel?.accuracy ?: SensorAccuracy.UNKNOWN,
                            index = index,
                            isSelected = sensor.id in selectedSensorIds,
                            isPinned = sensor.id in pinnedSensorIds,
                            canToggle = !isRecording && !sensor.isTriggerOnly,
                            canPin = !sensor.isTriggerOnly,
                            onToggle = { onToggleSensor(sensor.id) },
                            onTogglePin = { onTogglePin(sensor.id) },
                            onInspect = { onInspectSensor(sensor.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticSensorRow(
    title: String,
    metaLine: String,
    readingLine: String?,
    hzLine: String?,
    accuracy: SensorAccuracy,
    index: Int,
    isSelected: Boolean,
    isPinned: Boolean,
    canToggle: Boolean,
    canPin: Boolean,
    onToggle: () -> Unit,
    onTogglePin: () -> Unit,
    onInspect: () -> Unit
) {
    val rowBg = if (index % 2 == 1) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.surface
    }
    val monoStyle = MaterialTheme.typography.labelSmall.merge(
        TextStyle(fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum")
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .clickable(onClick = onInspect)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 2.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { if (canToggle) onToggle() },
                enabled = canToggle
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (hzLine != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = hzLine,
                            style = monoStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    if (accuracy != SensorAccuracy.UNKNOWN) {
                        Spacer(modifier = Modifier.width(4.dp))
                        AccuracyBadge(accuracy)
                    }
                }

                Text(
                    text = metaLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (readingLine != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = readingLine,
                        style = monoStyle,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (canPin) {
                IconButton(
                    onClick = onTogglePin,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PushPin,
                        contentDescription = if (isPinned) "Убрать с монитора" else "Закрепить на мониторе",
                        tint = if (isPinned) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        },
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
        )
    }
}

private fun buildMetaLine(sensor: SensorDescriptor, needsPermission: Boolean): String =
    buildList {
        add(sensor.name)
        if (sensor.vendor.isNotBlank()) add(sensor.vendor)
        if (sensor.unit.isNotBlank()) add(sensor.unit)
        if (sensor.isTriggerOnly) add("триггер")
        if (needsPermission) add("НЕТ ДОСТУПА")
    }.joinToString(" • ")

private fun formatCompactReadings(sensor: SensorDescriptor, values: List<Float>): String {
    val joined = values.take(3).mapIndexed { idx, v ->
        "${sensor.channelLabel(idx)}:${String.format(Locale.US, "%+.2f", v)}"
    }.joinToString("  ")
    return if (sensor.unit.isNotBlank()) "$joined ${sensor.unit}" else joined
}
