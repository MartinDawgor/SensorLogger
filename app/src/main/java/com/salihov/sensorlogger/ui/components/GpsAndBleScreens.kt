package com.salihov.sensorlogger.ui.components

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.BluetoothSearching
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.salihov.sensorlogger.domain.model.BleBeaconInfo
import com.salihov.sensorlogger.domain.model.ExtraSource
import com.salihov.sensorlogger.domain.model.GpsSnapshot
import java.util.Locale

@Composable
fun GpsTrackerScreen(
    gps: GpsSnapshot?,
    isRecordingEnabled: Boolean,
    hasLocationPermission: Boolean,
    onToggleRecordGps: () -> Unit,
    onRequestLocationPermission: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!hasLocationPermission) {
            item {
                DiagnosticSectionPanel(title = "Требуется разрешение геолокации") {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Для получения координат GPS/GNSS, скорости, высоты и экспорта треков KML необходимо разрешение местоположения.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onRequestLocationPermission, modifier = Modifier.fillMaxWidth()) {
                            Text("Разрешить доступ к GPS")
                        }
                    }
                }
            }
        }

        item {
            DiagnosticSectionPanel(
                title = "Настройки записи GPS",
                rightBadge = ExtraSource.GPS.sensorType
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Писать GPS в сессию и KML", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "Добавляет координаты в лог для построения маршрута KML",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = isRecordingEnabled, onCheckedChange = { onToggleRecordGps() })
                }
            }
        }

        item {
            DiagnosticSectionPanel(
                title = "Телеметрия GPS / GNSS",
                rightBadge = gps?.provider?.uppercase()?.ifBlank { "Ожидание" } ?: "Нет фикса"
            ) {
                DiagnosticKeyValueRow("Широта (Lat)", gps?.let { String.format(Locale.US, "%.6f°", it.latitude) } ?: "—", 0)
                DiagnosticKeyValueRow("Долгота (Lon)", gps?.let { String.format(Locale.US, "%.6f°", it.longitude) } ?: "—", 1)
                DiagnosticKeyValueRow("Высота над уровнем моря", gps?.let { String.format(Locale.US, "%.1f м", it.altitudeMeters) } ?: "—", 2)
                DiagnosticKeyValueRow("Скорость", gps?.let { String.format(Locale.US, "%.1f км/ч (%.2f м/с)", it.speedMps * 3.6f, it.speedMps) } ?: "—", 3)
                DiagnosticKeyValueRow("Курс (Bearing)", gps?.let { String.format(Locale.US, "%.1f°", it.bearingDeg) } ?: "—", 4)
                DiagnosticKeyValueRow("Точность (CEP)", gps?.let { String.format(Locale.US, "±%.1f м", it.accuracyMeters) } ?: "—", 5, showDivider = false)
            }
        }
    }
}

@Composable
fun BleScannerScreen(
    beacons: List<BleBeaconInfo>,
    isScanning: Boolean,
    isRecordingEnabled: Boolean,
    hasBlePermission: Boolean,
    onToggleScan: (Boolean) -> Unit,
    onToggleRecordBle: () -> Unit,
    onRequestBlePermissions: (Set<String>) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        if (!hasBlePermission) {
            DiagnosticSectionPanel(title = "Нужны разрешения Bluetooth") {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "Для сканирования ближайших Bluetooth LE маяков и уровня RSSI требуется разрешение Bluetooth.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                setOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
                            } else {
                                setOf(Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                            onRequestBlePermissions(perms)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Разрешить сканирование BLE")
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        DiagnosticSectionPanel(
            title = "Управление BLE сканером",
            rightBadge = if (isScanning) "Сканирует..." else "Остановлен"
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Записывать ближайший BLE маяк в лог", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = isRecordingEnabled, onCheckedChange = { onToggleRecordBle() })
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (isScanning) {
                    OutlinedButton(
                        onClick = { onToggleScan(false) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Outlined.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Остановить BLE скан")
                    }
                } else {
                    Button(
                        onClick = { onToggleScan(true) },
                        enabled = hasBlePermission,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.BluetoothSearching, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Запустить поиск маяков BLE")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        DiagnosticSectionPanel(
            title = "Обнаруженные устройства BLE",
            rightBadge = "${beacons.size} шт."
        ) {
            if (beacons.isEmpty()) {
                Text(
                    text = if (isScanning) "Поиск BLE пакетов..." else "Нажмите кнопку выше для поиска ближайших устройств.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(beacons, key = { _, b -> b.address }) { idx, beacon ->
                        DiagnosticKeyValueRow(
                            label = "${beacon.name} (${beacon.address})",
                            value = "${beacon.rssiDbm} дБм${if (beacon.payloadHex.isNotEmpty()) " • ${beacon.payloadHex.take(10)}" else ""}",
                            index = idx,
                            valueColor = MaterialTheme.colorScheme.tertiary,
                            showDivider = idx < beacons.lastIndex
                        )
                    }
                }
            }
        }
    }
}
