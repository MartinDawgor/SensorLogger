package com.salihov.sensorlogger.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.salihov.sensorlogger.domain.model.AppSection
import com.salihov.sensorlogger.domain.model.DeviceDiagnostics
import com.salihov.sensorlogger.domain.model.SensorDescriptor
import java.util.Locale

@Composable
fun DeviceTabContent(
    section: AppSection,
    diagnostics: DeviceDiagnostics,
    sensors: List<SensorDescriptor>,
    onRefresh: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = section.subtitle.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = onRefresh,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Обновить", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        if (section == AppSection.DEVICE_SYSTEM) {
            item {
                DiagnosticSectionPanel(
                    title = "Система и Платформа",
                    rightBadge = "API ${diagnostics.apiLevel}"
                ) {
                    DiagnosticKeyValueRow("Модель", "${diagnostics.manufacturer} ${diagnostics.model}".trim(), 0)
                    DiagnosticKeyValueRow("Устройство / Код", diagnostics.deviceCode.ifBlank { "—" }, 1)
                    DiagnosticKeyValueRow("SoC / Чипсет", diagnostics.socModel.ifBlank { "—" }, 2)
                    DiagnosticKeyValueRow("Плата / Hardware", "${diagnostics.board} / ${diagnostics.hardware}", 3)
                    DiagnosticKeyValueRow("Android ОС", "${diagnostics.androidVersion} (API ${diagnostics.apiLevel})", 4)
                    DiagnosticKeyValueRow("Патч безопасности", diagnostics.securityPatch.ifBlank { "—" }, 5)
                    DiagnosticKeyValueRow("Сборка", diagnostics.buildId.ifBlank { "—" }, 6, showDivider = false)
                }
            }

            item {
                DiagnosticSectionPanel(
                    title = "Процессор (CPU)",
                    rightBadge = "${diagnostics.cpuCoresCount} ядер"
                ) {
                    DiagnosticKeyValueRow(
                        "Архитектура (ABI)",
                        diagnostics.abis.joinToString(", ").ifBlank { "—" },
                        0
                    )
                    diagnostics.cpuCores.forEachIndexed { idx, core ->
                        val freqText = when {
                            core.currentFreqKhz != null && core.maxFreqKhz != null ->
                                "${core.currentFreqKhz / 1000} МГц (макс ${core.maxFreqKhz / 1000} МГц)"
                            core.currentFreqKhz != null -> "${core.currentFreqKhz / 1000} МГц"
                            core.maxFreqKhz != null -> "макс ${core.maxFreqKhz / 1000} МГц"
                            else -> "Ограничено SELinux"
                        }
                        DiagnosticKeyValueRow(
                            label = "Ядро CPU #${core.coreIndex}",
                            value = freqText,
                            index = idx + 1,
                            showDivider = idx < diagnostics.cpuCores.lastIndex
                        )
                    }
                }
            }
        }

        if (section == AppSection.DEVICE_BATTERY) {
            item {
                DiagnosticSectionPanel(
                    title = "Батарея и Питание",
                    rightBadge = if (diagnostics.batteryLevelPct >= 0) "${diagnostics.batteryLevelPct}%" else "—"
                ) {
                    DiagnosticKeyValueRow(
                        "Уровень заряда",
                        if (diagnostics.batteryLevelPct >= 0) "${diagnostics.batteryLevelPct}%" else "—",
                        0,
                        valueColor = MaterialTheme.colorScheme.tertiary
                    )
                    DiagnosticKeyValueRow("Статус", diagnostics.batteryStatus.ifBlank { "—" }, 1)
                    DiagnosticKeyValueRow("Источник питания", diagnostics.batteryPlugged.ifBlank { "—" }, 2)
                    DiagnosticKeyValueRow(
                        "Температура",
                        if (diagnostics.batteryTempC > 0f) String.format(Locale.US, "%.1f °C", diagnostics.batteryTempC) else "—",
                        3
                    )
                    DiagnosticKeyValueRow(
                        "Напряжение",
                        if (diagnostics.batteryVoltageMv > 0) "${diagnostics.batteryVoltageMv} мВ" else "—",
                        4
                    )
                    DiagnosticKeyValueRow("Состояние / Тип", "${diagnostics.batteryHealth} • ${diagnostics.batteryTechnology}".trim(' ', '•'), 5, showDivider = false)
                }
            }

            item {
                DiagnosticSectionPanel(
                    title = "Термодатчики (Thermal Zones)",
                    rightBadge = "${diagnostics.thermalZones.size} зон"
                ) {
                    if (diagnostics.thermalZones.isEmpty()) {
                        DiagnosticKeyValueRow(
                            "Системные термозоны",
                            "Доступ к /sys/class/thermal ограничен политикой SELinux",
                            0,
                            showDivider = false
                        )
                    } else {
                        diagnostics.thermalZones.forEachIndexed { idx, tz ->
                            DiagnosticKeyValueRow(
                                label = tz.name,
                                value = String.format(Locale.US, "%.1f °C", tz.tempCelsius),
                                index = idx,
                                valueColor = if (tz.tempCelsius >= 45f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                                showDivider = idx < diagnostics.thermalZones.lastIndex
                            )
                        }
                    }
                }
            }
        }

        if (section == AppSection.DEVICE_MEMORY) {
            item {
                val usedRam = (diagnostics.totalRamBytes - diagnostics.availableRamBytes).coerceAtLeast(0L)
                val usedStorage = (diagnostics.totalStorageBytes - diagnostics.freeStorageBytes).coerceAtLeast(0L)
                DiagnosticSectionPanel(
                    title = "Память (RAM и Флеш)",
                    rightBadge = formatMbGb(diagnostics.totalRamBytes)
                ) {
                    DiagnosticKeyValueRow("ОЗУ (RAM) всего", formatMbGb(diagnostics.totalRamBytes), 0)
                    DiagnosticKeyValueRow("ОЗУ занято", formatMbGb(usedRam), 1)
                    DiagnosticKeyValueRow("ОЗУ свободно", formatMbGb(diagnostics.availableRamBytes), 2)
                    DiagnosticKeyValueRow("Накопитель всего", formatMbGb(diagnostics.totalStorageBytes), 3)
                    DiagnosticKeyValueRow("Накопитель занято", formatMbGb(usedStorage), 4)
                    DiagnosticKeyValueRow("Накопитель свободно", formatMbGb(diagnostics.freeStorageBytes), 5, showDivider = false)
                }
            }

            item {
                DiagnosticSectionPanel(
                    title = "Дисплей и Яркость",
                    rightBadge = diagnostics.displayResolution
                ) {
                    DiagnosticKeyValueRow("Разрешение", diagnostics.displayResolution.ifBlank { "—" }, 0)
                    DiagnosticKeyValueRow("Плотность (DPI)", "${diagnostics.displayDensityDpi} dpi", 1)
                    DiagnosticKeyValueRow("Частота обновления", "${String.format(Locale.US, "%.1f", diagnostics.displayRefreshHz)} Гц", 2)
                    DiagnosticKeyValueRow(
                        "Яркость экрана (0–255)",
                        if (diagnostics.screenBrightness >= 0) "${diagnostics.screenBrightness} (${(diagnostics.screenBrightness * 100) / 255}%)" else "—",
                        3,
                        showDivider = false
                    )
                }
            }
        }

        if (section == AppSection.DEVICE_NETWORK) {
            item {
                DiagnosticSectionPanel(
                    title = "Сеть и Радиомодуль",
                    rightBadge = diagnostics.networkType
                ) {
                    DiagnosticKeyValueRow("Активный транспорт", diagnostics.networkType, 0)
                    DiagnosticKeyValueRow("Wi-Fi SSID", diagnostics.wifiSsid, 1)
                    DiagnosticKeyValueRow(
                        "Уровень сигнала Wi-Fi",
                        diagnostics.wifiRssiDbm?.let { "$it дБм" } ?: "—",
                        2
                    )
                    DiagnosticKeyValueRow(
                        "Скорость линка Wi-Fi",
                        diagnostics.wifiLinkSpeedMbps?.let { "$it Мбит/с" } ?: "—",
                        3,
                        showDivider = false
                    )
                }
            }

            item {
                val wakeUpCount = sensors.count { it.isWakeUp }
                val triggerCount = sensors.count { it.isTriggerOnly }
                val streamCount = sensors.size - triggerCount
                DiagnosticSectionPanel(
                    title = "Подсистема датчиков (HAL)",
                    rightBadge = "${sensors.size} всего"
                ) {
                    DiagnosticKeyValueRow("Всего датчиков в HAL", sensors.size.toString(), 0)
                    DiagnosticKeyValueRow("Потоковых датчиков", streamCount.toString(), 1)
                    DiagnosticKeyValueRow("Триггерных (one-shot)", triggerCount.toString(), 2)
                    DiagnosticKeyValueRow("Wake-up датчиков", wakeUpCount.toString(), 3, showDivider = false)
                }
            }
        }
    }
}

private fun formatMbGb(bytes: Long): String {
    if (bytes <= 0L) return "—"
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    return if (gb >= 1.0) {
        String.format(Locale.US, "%.2f ГБ", gb)
    } else {
        String.format(Locale.US, "%.0f МБ", bytes / (1024.0 * 1024.0))
    }
}
