package com.salihov.sensorlogger.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.salihov.sensorlogger.domain.model.CsvLogDocument
import com.salihov.sensorlogger.domain.model.ExportFormat
import com.salihov.sensorlogger.domain.model.SessionAnalysisReport
import com.salihov.sensorlogger.domain.model.SessionComparisonReport
import com.salihov.sensorlogger.domain.model.VerdictSeverity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class LogViewMode(val label: String) {
    VERDICTS("Вердикты"),
    TABLE("Таблица"),
    CHART("График"),
    RAW("CSV")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvLogViewerScreen(
    document: CsvLogDocument,
    report: SessionAnalysisReport?,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onExportFormatted: (ExportFormat) -> Unit
) {
    var viewMode by rememberSaveable { mutableStateOf(LogViewMode.VERDICTS) }
    var selectedSensor by rememberSaveable(document.fileName) { mutableStateOf<String?>(null) }
    var exportMenuOpen by remember { mutableStateOf(false) }

    val filteredRows = remember(document, selectedSensor) {
        if (selectedSensor == null) {
            document.rows
        } else {
            document.rows.filter { it.sensorName == selectedSensor }
        }
    }

    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = document.fileName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Анализатор отчёта и экспорт таблиц",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Закрыть просмотр"
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { exportMenuOpen = true }) {
                            Icon(Icons.Outlined.FileDownload, contentDescription = "Экспорт отчёта в формате")
                        }
                        DropdownMenu(
                            expanded = exportMenuOpen,
                            onDismissRequest = { exportMenuOpen = false }
                        ) {
                            ExportFormat.entries.forEach { fmt ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(fmt.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                            Text(fmt.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    onClick = {
                                        exportMenuOpen = false
                                        onExportFormatted(fmt)
                                    }
                                )
                            }
                        }
                    }
                    IconButton(onClick = onShare) {
                        Icon(Icons.Outlined.Share, contentDescription = "Поделиться CSV")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MetricTile("Замеров", document.totalDataRows.toString(), Modifier.weight(1f))
                MetricTile("Длит.", formatDurationShort(document.durationMillis), Modifier.weight(1f))
                MetricTile("Размер", formatFileBytes(document.sizeBytes), Modifier.weight(1f))
                MetricTile("Датчики", document.sensorNames.size.toString(), Modifier.weight(1f))
            }

            if (document.isTruncated) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Показаны первые ${document.rows.size} из ${document.totalDataRows} строк.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                LogViewMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = viewMode == mode,
                        onClick = { viewMode = mode },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = LogViewMode.entries.size
                        )
                    ) {
                        Text(mode.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }

            if (viewMode != LogViewMode.RAW && viewMode != LogViewMode.VERDICTS && document.sensorNames.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedSensor == null,
                            onClick = { selectedSensor = null },
                            label = { Text("Все (${document.rows.size})") }
                        )
                    }
                    items(document.sensorNames) { name ->
                        FilterChip(
                            selected = selectedSensor == name,
                            onClick = { selectedSensor = if (selectedSensor == name) null else name },
                            label = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (viewMode) {
                LogViewMode.VERDICTS -> {
                    if (report == null || (report.verdicts.isEmpty() && report.markers.isEmpty())) {
                        EmptyLogHint("Недостаточно данных для анализа.")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 14.dp)
                        ) {
                            if (report.markers.isNotEmpty()) {
                                item {
                                    DiagnosticSectionPanel(
                                        title = "Отмеченные события (Аннотации)",
                                        rightBadge = "${report.markers.size} шт."
                                    ) {
                                        report.markers.forEachIndexed { idx, m ->
                                            DiagnosticKeyValueRow(
                                                label = if (m.epochMillis > 0L) timeFormatter.format(Date(m.epochMillis)) else "#${m.rowNumber}",
                                                value = m.sensorName,
                                                index = idx,
                                                valueColor = MaterialTheme.colorScheme.primary,
                                                showDivider = idx < report.markers.lastIndex
                                            )
                                        }
                                    }
                                }
                            }

                            items(report.verdicts) { verdict ->
                                val badgeColor = when (verdict.severity) {
                                    VerdictSeverity.OK -> MaterialTheme.colorScheme.tertiary
                                    VerdictSeverity.INFO -> MaterialTheme.colorScheme.primary
                                    VerdictSeverity.WARN -> MaterialTheme.colorScheme.secondary
                                    VerdictSeverity.ALERT -> MaterialTheme.colorScheme.error
                                }
                                DiagnosticSectionPanel(
                                    title = verdict.sensorName,
                                    rightBadge = "${verdict.severity.label} • ${String.format(Locale.US, "%.1f Гц", verdict.effectiveHz)}"
                                ) {
                                    DiagnosticKeyValueRow(
                                        label = "Вердикт",
                                        value = verdict.verdictTitle,
                                        index = 0,
                                        valueColor = badgeColor
                                    )
                                    Text(
                                        text = verdict.verdictDetails,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                    verdict.channelStats.take(3).forEachIndexed { chIdx, st ->
                                        DiagnosticKeyValueRow(
                                            label = "${st.channelLabel}: ср. / СКО",
                                            value = "${String.format(Locale.US, "%+.3f", st.mean)} / σ=${String.format(Locale.US, "%.3f", st.stdDev)}",
                                            index = chIdx + 1,
                                            showDivider = chIdx < minOf(2, verdict.channelStats.lastIndex)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                LogViewMode.TABLE -> {
                    if (filteredRows.isEmpty()) {
                        EmptyLogHint("Записей в этом логе не найдено.")
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                items(filteredRows, key = { it.rowNumber }) { row ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 7.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = row.sensorName,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (row.epochMillis > 0L) {
                                                    timeFormatter.format(Date(row.epochMillis))
                                                } else {
                                                    "#${row.rowNumber}"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = row.values.joinToString("  |  ") {
                                                String.format(Locale.US, "%.4f", it)
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = if (row.sensorType == "user.marker") {
                                                MaterialTheme.colorScheme.error
                                            } else {
                                                MaterialTheme.colorScheme.primary
                                            }
                                        )
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }
                }

                LogViewMode.CHART -> {
                    val targetSensor = selectedSensor ?: document.sensorNames.firstOrNull { it != "MARKER" } ?: document.sensorNames.firstOrNull()
                    if (targetSensor == null) {
                        EmptyLogHint("В этом файле нет данных датчиков для построения графика.")
                    } else {
                        val sensorRows = remember(document, targetSensor) {
                            document.rows.filter { it.sensorName == targetSensor }
                        }
                        val downsampled = remember(sensorRows) {
                            if (sensorRows.size <= 180) {
                                sensorRows.map { it.values }
                            } else {
                                val step = sensorRows.size.toFloat() / 180f
                                List(180) { i -> sensorRows[(i * step).toInt().coerceAtMost(sensorRows.lastIndex)].values }
                            }
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            item {
                                Text(
                                    text = "Датчик: $targetSensor (${sensorRows.size} замеров)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            item {
                                TelemetryWaveformChart(
                                    samples = downsampled,
                                    channelLabels = listOf("v0", "v1", "v2", "v3", "v4", "v5"),
                                    unit = "",
                                    enableGestures = true
                                )
                            }
                            item {
                                SensorStatsSummaryCard(rows = sensorRows.map { it.values })
                            }
                        }
                    }
                }

                LogViewMode.RAW -> {
                    val hScroll = rememberScrollState()
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .horizontalScroll(hScroll),
                            contentPadding = PaddingValues(12.dp)
                        ) {
                            itemsIndexed(document.rawLines) { index, line ->
                                Text(
                                    text = String.format(Locale.US, "%4d | %s", index + 1, line),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SensorStatsSummaryCard(rows: List<List<Float>>) {
    val maxChannels = rows.maxOfOrNull { it.size } ?: 0
    if (maxChannels == 0) return

    DiagnosticSectionPanel(title = "Статистика по каналам") {
        for (ch in 0 until maxChannels) {
            val vals = rows.mapNotNull { it.getOrNull(ch) }.filter { it.isFinite() }
            if (vals.isEmpty()) continue
            val min = vals.min()
            val max = vals.max()
            val avg = vals.sum() / vals.size
            DiagnosticKeyValueRow(
                label = "v$ch",
                value = "min ${String.format(Locale.US, "%.2f", min)} • avg ${String.format(Locale.US, "%.2f", avg)} • max ${String.format(Locale.US, "%.2f", max)}",
                index = ch,
                showDivider = ch < maxChannels - 1
            )
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EmptyLogHint(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatDurationShort(durationMs: Long): String {
    val totalSec = (durationMs / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return String.format(Locale.US, "%02d:%02d", min, sec)
}

private fun formatFileBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f МБ", bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> String.format(Locale.US, "%.1f КБ", bytes / 1024.0)
    else -> "$bytes Б"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionComparisonScreen(
    report: SessionComparisonReport,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Сравнение A / B", maxLines = 1)
                        Text(
                            text = "${report.fileA} vs ${report.fileB}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Закрыть")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 10.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                DiagnosticSectionPanel(title = "Сводка сессий") {
                    DiagnosticKeyValueRow("Сессия A", "${report.fileA} (${report.rowsA} стр., ${String.format(Locale.US, "%.1f с", report.durationSecA)})", 0)
                    DiagnosticKeyValueRow("Сессия B", "${report.fileB} (${report.rowsB} стр., ${String.format(Locale.US, "%.1f с", report.durationSecB)})", 1, showDivider = false)
                }
            }

            if (report.seriesA.isNotEmpty() || report.seriesB.isNotEmpty()) {
                item {
                    val primary = report.primarySensorName ?: "Датчик"
                    DiagnosticSectionPanel(title = "Серия A: $primary") {
                        TelemetryWaveformChart(
                            samples = report.seriesA,
                            channelLabels = listOf("v0", "v1", "v2"),
                            unit = "",
                            enableGestures = true,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
                item {
                    val primary = report.primarySensorName ?: "Датчик"
                    DiagnosticSectionPanel(title = "Серия B: $primary") {
                        TelemetryWaveformChart(
                            samples = report.seriesB,
                            channelLabels = listOf("v0", "v1", "v2"),
                            unit = "",
                            enableGestures = true,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }

            items(report.comparisons) { row ->
                DiagnosticSectionPanel(title = row.sensorName) {
                    DiagnosticKeyValueRow(
                        label = "Сессия A (ср. / СКО)",
                        value = "${row.meanA?.let { String.format(Locale.US, "%+.3f", it) } ?: "—"} / σ=${row.stdA?.let { String.format(Locale.US, "%.3f", it) } ?: "—"} (${row.verdictA})",
                        index = 0
                    )
                    DiagnosticKeyValueRow(
                        label = "Сессия B (ср. / СКО)",
                        value = "${row.meanB?.let { String.format(Locale.US, "%+.3f", it) } ?: "—"} / σ=${row.stdB?.let { String.format(Locale.US, "%.3f", it) } ?: "—"} (${row.verdictB})",
                        index = 1,
                        showDivider = false
                    )
                }
            }
        }
    }
}
