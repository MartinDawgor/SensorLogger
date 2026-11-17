package com.salihov.sensorlogger.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.salihov.sensorlogger.domain.model.StreamingConfig
import com.salihov.sensorlogger.domain.model.StreamingProtocol
import com.salihov.sensorlogger.domain.model.StreamingStats
import com.salihov.sensorlogger.domain.model.WebDashboardState

@Composable
fun StreamingScreen(
    config: StreamingConfig,
    stats: StreamingStats,
    webDashboard: WebDashboardState,
    onSaveConfig: (StreamingConfig) -> Unit,
    onToggleWebDashboard: (Boolean) -> Unit
) {
    var enabled by remember(config) { mutableStateOf(config.enabled) }
    var protocol by remember(config) { mutableStateOf(config.protocol) }
    var httpUrl by remember(config) { mutableStateOf(config.httpUrl) }
    var mqttHost by remember(config) { mutableStateOf(config.mqttHost) }
    var mqttPort by remember(config) { mutableStateOf(config.mqttPort.toString()) }
    var mqttTopic by remember(config) { mutableStateOf(config.mqttTopic) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            DiagnosticSectionPanel(
                title = "Локальный Wi-Fi Web-дашборд",
                rightBadge = if (webDashboard.running) "Активен" else "Выключен"
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Встроить локальный HTTP-сервер", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "Live-график и скачивание CSV с браузера в той же сети",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = webDashboard.running,
                            onCheckedChange = onToggleWebDashboard
                        )
                    }
                    if (webDashboard.running) {
                        DiagnosticKeyValueRow(
                            label = "Адрес в сети",
                            value = webDashboard.url ?: "http://127.0.0.1:8090/",
                            index = 0
                        )
                        DiagnosticKeyValueRow(
                            label = "Обработано запросов",
                            value = webDashboard.requestsServed.toString(),
                            index = 1,
                            showDivider = false
                        )
                    }
                }
            }
        }

        item {
            DiagnosticSectionPanel(
                title = "Потоковая передача (Real-Time)",
                rightBadge = if (config.enabled) "Активна" else "Отключена"
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Отправлять данные во время записи", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "Пакетная отправка каждую секунду в JSON",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = {
                                enabled = it
                                onSaveConfig(
                                    StreamingConfig(
                                        enabled = enabled,
                                        protocol = protocol,
                                        httpUrl = httpUrl.trim(),
                                        mqttHost = mqttHost.trim(),
                                        mqttPort = mqttPort.toIntOrNull() ?: 1883,
                                        mqttTopic = mqttTopic.trim()
                                    )
                                )
                            }
                        )
                    }

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        StreamingProtocol.entries.forEachIndexed { idx, p ->
                            SegmentedButton(
                                selected = protocol == p,
                                onClick = { protocol = p },
                                shape = SegmentedButtonDefaults.itemShape(idx, StreamingProtocol.entries.size)
                            ) {
                                Text(p.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                        }
                    }

                    when (protocol) {
                        StreamingProtocol.HTTP_POST -> {
                            OutlinedTextField(
                                value = httpUrl,
                                onValueChange = { httpUrl = it },
                                label = { Text("URL сервера (HTTP POST JSON)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        StreamingProtocol.MQTT_TCP -> {
                            OutlinedTextField(
                                value = mqttHost,
                                onValueChange = { mqttHost = it },
                                label = { Text("MQTT Broker Host / IP") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = mqttPort,
                                onValueChange = { mqttPort = it },
                                label = { Text("Порт (обычно 1883)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = mqttTopic,
                                onValueChange = { mqttTopic = it },
                                label = { Text("Топик MQTT") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    Button(
                        onClick = {
                            onSaveConfig(
                                StreamingConfig(
                                    enabled = enabled,
                                    protocol = protocol,
                                    httpUrl = httpUrl.trim(),
                                    mqttHost = mqttHost.trim(),
                                    mqttPort = mqttPort.toIntOrNull() ?: 1883,
                                    mqttTopic = mqttTopic.trim()
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Сохранить настройки стриминга")
                    }
                }
            }
        }

        item {
            DiagnosticSectionPanel(title = "Статистика отправки") {
                DiagnosticKeyValueRow("Отправлено пакетов", stats.sentBatches.toString(), 0)
                DiagnosticKeyValueRow("Отправлено сэмплов", stats.sentSamples.toString(), 1)
                DiagnosticKeyValueRow("Ошибок доставки", stats.failedBatches.toString(), 2)
                DiagnosticKeyValueRow("Последняя ошибка", stats.lastError ?: "Нет", 3, showDivider = false)
            }
        }
    }
}
