package com.salihov.sensorlogger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.BluetoothSearching
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.salihov.sensorlogger.domain.model.AppSection
import com.salihov.sensorlogger.domain.model.RecordingScenario
import com.salihov.sensorlogger.domain.model.SensorLoggerUiState
import java.util.Locale

private data class AidaMenuItem(
    val section: AppSection,
    val title: String,
    val rightValue: String,
    val icon: ImageVector
)

@Composable
fun MainMenuHomeScreen(
    uiState: SensorLoggerUiState,
    onNavigate: (AppSection) -> Unit,
    onApplyScenario: (RecordingScenario) -> Unit
) {
    val diag = uiState.deviceDiagnostics
    val totalRamMb = diag.totalRamBytes / (1024 * 1024)

    val sensorItems = listOf(
        AidaMenuItem(
            section = AppSection.SENSORS,
            title = "Датчики",
            rightValue = "${uiState.selectedSensorIds.size}/${uiState.sensors.size}",
            icon = Icons.Outlined.Sensors
        ),
        AidaMenuItem(
            section = AppSection.MONITOR,
            title = "Живой монитор",
            rightValue = "${uiState.pinnedSensorIds.size} осц.",
            icon = Icons.AutoMirrored.Outlined.ShowChart
        ),
        AidaMenuItem(
            section = AppSection.TOOLS,
            title = "Приборы",
            rightValue = "G / Уровень / дБ",
            icon = Icons.Outlined.Explore
        ),
        AidaMenuItem(
            section = AppSection.LOGS,
            title = "Логи и Отчёты",
            rightValue = "${uiState.sessions.size} сессий",
            icon = Icons.Outlined.Assessment
        )
    )

    val extraItems = listOf(
        AidaMenuItem(
            section = AppSection.GPS,
            title = "GPS / GNSS",
            rightValue = uiState.gpsSnapshot?.let {
                String.format(Locale.US, "%.3f, %.3f", it.latitude, it.longitude)
            } ?: "KML",
            icon = Icons.Outlined.MyLocation
        ),
        AidaMenuItem(
            section = AppSection.BLE,
            title = "Bluetooth LE",
            rightValue = if (uiState.isBleScanning) "${uiState.bleBeacons.size} шт." else "Скан",
            icon = Icons.AutoMirrored.Outlined.BluetoothSearching
        ),
        AidaMenuItem(
            section = AppSection.RULES,
            title = "Правила и Аннотации",
            rightValue = "${uiState.rules.count { it.enabled }} вкл.",
            icon = Icons.AutoMirrored.Outlined.Rule
        ),
        AidaMenuItem(
            section = AppSection.STREAMING,
            title = "Стрим HTTP / MQTT",
            rightValue = if (uiState.streamingConfig.enabled) "Вкл." else "Выкл.",
            icon = Icons.Outlined.CloudUpload
        )
    )

    val hardwareItems = listOf(
        AidaMenuItem(
            section = AppSection.DEVICE_SYSTEM,
            title = "Система и ЦП",
            rightValue = "${diag.cpuCoresCount} ядер • API ${diag.apiLevel}",
            icon = Icons.Outlined.Memory
        ),
        AidaMenuItem(
            section = AppSection.DEVICE_BATTERY,
            title = "Батарея и Термо",
            rightValue = if (diag.batteryLevelPct >= 0) "${diag.batteryLevelPct}% • ${String.format(Locale.US, "%.1f°C", diag.batteryTempC)}" else "—",
            icon = Icons.Outlined.BatteryChargingFull
        ),
        AidaMenuItem(
            section = AppSection.DEVICE_MEMORY,
            title = "Память и Экран",
            rightValue = if (totalRamMb > 0) "$totalRamMb МБ" else diag.displayResolution,
            icon = Icons.Outlined.Storage
        ),
        AidaMenuItem(
            section = AppSection.DEVICE_NETWORK,
            title = "Сеть и HAL",
            rightValue = diag.networkType,
            icon = Icons.Outlined.Wifi
        )
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        item {
            AidaCategoryHeader("Сценарии (1-tap)")
        }
        items(RecordingScenario.entries.size) { idx ->
            val scenario = RecordingScenario.entries[idx]
            AidaScenarioRow(
                scenario = scenario,
                enabled = !uiState.isRecording,
                onClick = { onApplyScenario(scenario) }
            )
        }

        item {
            AidaCategoryHeader("Датчики и Запись")
        }
        items(sensorItems.size) { idx ->
            AidaMenuListRow(
                item = sensorItems[idx],
                onClick = { onNavigate(sensorItems[idx].section) }
            )
        }

        item {
            AidaCategoryHeader("Модули и Автоматизация")
        }
        items(extraItems.size) { idx ->
            AidaMenuListRow(
                item = extraItems[idx],
                onClick = { onNavigate(extraItems[idx].section) }
            )
        }

        item {
            AidaCategoryHeader("Оборудование (AIDA64)")
        }
        items(hardwareItems.size) { idx ->
            AidaMenuListRow(
                item = hardwareItems[idx],
                onClick = { onNavigate(hardwareItems[idx].section) }
            )
        }
    }
}

@Composable
private fun AidaCategoryHeader(title: String) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun AidaMenuListRow(
    item: AidaMenuItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.rightValue,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

@Composable
private fun AidaScenarioRow(
    scenario: RecordingScenario,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Outlined.PlayCircleOutline,
                    contentDescription = null,
                    tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = scenario.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = scenario.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = scenario.rate.shortHz,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary
            )
        }
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}
