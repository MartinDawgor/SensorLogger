package com.salihov.sensorlogger.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.salihov.sensorlogger.domain.model.CsvDelimiter
import com.salihov.sensorlogger.domain.model.CsvSessionInfo
import com.salihov.sensorlogger.domain.model.LogSortOrder
import com.salihov.sensorlogger.domain.model.TimestampFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogsTabContent(
    sessions: List<CsvSessionInfo>,
    selectedFileNames: Set<String>,
    activeRecordingFileName: String?,
    csvDelimiter: CsvDelimiter,
    timestampFormat: TimestampFormat,
    sessionNamePrefix: String,
    logSearchQuery: String,
    logSortOrder: LogSortOrder,
    backgroundServiceEnabled: Boolean,
    isRecording: Boolean,
    onSelectDelimiter: (CsvDelimiter) -> Unit,
    onSelectTimestampFormat: (TimestampFormat) -> Unit,
    onUpdateSessionPrefix: (String) -> Unit,
    onToggleBackgroundService: (Boolean) -> Unit,
    onUpdateSearch: (String) -> Unit,
    onSelectSortOrder: (LogSortOrder) -> Unit,
    onToggleSelectLog: (String) -> Unit,
    onClearSelection: () -> Unit,
    onCompareSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onExportSelectedZip: (List<String>) -> Unit,
    onOpenLog: (String) -> Unit,
    onRenameLog: (String, String) -> Unit,
    onShareLog: (String) -> Unit,
    onSaveLogToDevice: (String) -> Unit,
    onDeleteLog: (String) -> Unit
) {
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    var showExportPrefs by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }

    if (showSortDialog) {
        AlertDialog(
            onDismissRequest = { showSortDialog = false },
            title = { Text("Сортировка логов") },
            text = {
                Column {
                    LogSortOrder.entries.forEach { order ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectSortOrder(order)
                                    showSortDialog = false
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = logSortOrder == order,
                                onClick = {
                                    onSelectSortOrder(order)
                                    showSortDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(order.label, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSortDialog = false }) { Text("Закрыть") }
            }
        )
    }

    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Переименовать сессию") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Имя файла (.csv)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    val target = renameTarget
                    if (target != null && renameText.isNotBlank()) {
                        onRenameLog(target, renameText.trim())
                    }
                    renameTarget = null
                }) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Отмена")
                }
            }
        )
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
                text = "СЕССИИ (${sessions.size}) — НАЖМИТЕ ДЛЯ ВЕРДИКТОВ",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = { showExportPrefs = !showExportPrefs },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(if (showExportPrefs) "Скрыть параметры" else "Параметры CSV", style = MaterialTheme.typography.labelSmall)
            }
        }

        if (showExportPrefs) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        CsvDelimiter.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = mode == csvDelimiter,
                                enabled = !isRecording,
                                onClick = { onSelectDelimiter(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index, CsvDelimiter.entries.size)
                            ) {
                                Text(mode.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                        }
                    }

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        TimestampFormat.entries.forEachIndexed { index, tf ->
                            SegmentedButton(
                                selected = tf == timestampFormat,
                                onClick = { onSelectTimestampFormat(tf) },
                                shape = SegmentedButtonDefaults.itemShape(index, TimestampFormat.entries.size)
                            ) {
                                Text(tf.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = sessionNamePrefix,
                        onValueChange = onUpdateSessionPrefix,
                        label = { Text("Шаблон префикса новых файлов") },
                        singleLine = true,
                        enabled = !isRecording,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Фоновая запись (Foreground Service)", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "Не прерывать запись при выключении экрана",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = backgroundServiceEnabled,
                            onCheckedChange = onToggleBackgroundService
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CompactSearchField(
                value = logSearchQuery,
                onValueChange = onUpdateSearch,
                placeholder = "Поиск файла...",
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = { showSortDialog = true },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Outlined.FilterList, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Фильтры", style = MaterialTheme.typography.labelSmall)
            }
        }

        if (selectedFileNames.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onExportSelectedZip(selectedFileNames.toList()) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.Archive, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ZIP (${selectedFileNames.size})", style = MaterialTheme.typography.labelSmall)
                }
                if (selectedFileNames.size == 2) {
                    FilledTonalButton(
                        onClick = onCompareSelected,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.CompareArrows, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("A/B", style = MaterialTheme.typography.labelSmall)
                    }
                }
                OutlinedButton(
                    onClick = onDeleteSelected,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Удалить", style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = onClearSelection) {
                    Text("Снять", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (sessions.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Text(
                    text = "Сохранённых логов нет.\nНажмите «Запись CSV» на нижней панели.",
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
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(sessions, key = { _, item -> item.fileName }) { index, session ->
                        val isActive = session.fileName == activeRecordingFileName
                        val isChecked = session.fileName in selectedFileNames
                        val rowBg = if (index % 2 == 1) {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        } else {
                            MaterialTheme.colorScheme.surface
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(rowBg)
                                .clickable(enabled = !isActive) { onOpenLog(session.fileName) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 2.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { onToggleSelectLog(session.fileName) },
                                    enabled = !isActive
                                )
                                Icon(
                                    imageVector = Icons.Outlined.Description,
                                    contentDescription = null,
                                    tint = if (isActive) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = session.fileName,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (isActive) {
                                            "ИДЁТ ЗАПИСЬ • ${formatBytesCompact(session.sizeBytes)}"
                                        } else {
                                            "${formatDateCompact(session.createdAtMillis)} • ${formatBytesCompact(session.sizeBytes)}"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (isActive) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        renameTarget = session.fileName
                                        renameText = session.fileName.removeSuffix(".csv")
                                    },
                                    enabled = !isActive,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Outlined.Edit, contentDescription = "Переименовать", modifier = Modifier.size(16.dp))
                                }
                                IconButton(
                                    onClick = { onSaveLogToDevice(session.fileName) },
                                    enabled = !isActive,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Outlined.Download, contentDescription = "Сохранить", modifier = Modifier.size(16.dp))
                                }
                                IconButton(
                                    onClick = { onShareLog(session.fileName) },
                                    enabled = !isActive,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Outlined.Share, contentDescription = "Поделиться", modifier = Modifier.size(16.dp))
                                }
                                IconButton(
                                    onClick = { onDeleteLog(session.fileName) },
                                    enabled = !isActive,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "Удалить", modifier = Modifier.size(16.dp))
                                }
                            }
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatBytesCompact(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.US, "%.2f МБ", bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> String.format(Locale.US, "%.1f КБ", bytes / 1024.0)
    else -> "$bytes Б"
}

private fun formatDateCompact(epochMillis: Long): String {
    val formatter = SimpleDateFormat("dd.MM.yy HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(epochMillis))
}
