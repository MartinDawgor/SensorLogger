package com.salihov.sensorlogger.ui.components

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.salihov.sensorlogger.domain.model.RuleActionType
import com.salihov.sensorlogger.domain.model.RuleConditionType
import com.salihov.sensorlogger.domain.model.SmartRule
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RulesAndMarkersScreen(
    rules: List<SmartRule>,
    annotationPresets: List<String>,
    blackboxArmed: Boolean,
    blackboxSampleCount: Int,
    onToggleBlackbox: (Boolean) -> Unit,
    onFlushBlackboxNow: () -> Unit,
    onToggleRule: (String) -> Unit,
    onAddRule: (SmartRule) -> Unit,
    onDeleteRule: (String) -> Unit,
    onAddPreset: (String) -> Unit,
    onRemovePreset: (String) -> Unit
) {
    var newPresetText by remember { mutableStateOf("") }
    var selectedCondition by remember { mutableStateOf(RuleConditionType.G_FORCE_ABOVE) }
    var selectedAction by remember { mutableStateOf(RuleActionType.INSERT_MARKER) }
    var thresholdText by remember { mutableStateOf("2.5") }
    var condExpanded by remember { mutableStateOf(false) }
    var actExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            DiagnosticSectionPanel(
                title = "Режим «Чёрный ящик» (30 сек буфер)",
                rightBadge = if (blackboxArmed) "$blackboxSampleCount сэмплов" else "Выключен"
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
                            Text("Кольцевой пред-аварийный буфер", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "Держит последние 30 сек в ОЗУ без износа диска",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = blackboxArmed,
                            onCheckedChange = onToggleBlackbox
                        )
                    }
                    Button(
                        onClick = onFlushBlackboxNow,
                        enabled = blackboxSampleCount > 0,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Сбросить буфер в CSV сейчас ($blackboxSampleCount)")
                    }
                }
            }
        }

        item {
            DiagnosticSectionPanel(
                title = "Пресеты аннотаций (Меток)",
                rightBadge = "${annotationPresets.size} шт."
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "Быстрые кнопки меток для моментальной вставки в лог во время записи.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        annotationPresets.forEach { preset ->
                            InputChip(
                                selected = false,
                                onClick = {},
                                label = { Text(preset) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = "Удалить",
                                        modifier = Modifier
                                            .size(16.dp)
                                            .let { mod ->
                                                mod
                                            }
                                    )
                                },
                                modifier = Modifier
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = newPresetText,
                            onValueChange = { newPresetText = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("Новый пресет метки...") }
                        )
                        Button(
                            onClick = {
                                onAddPreset(newPresetText)
                                newPresetText = ""
                            },
                            enabled = newPresetText.isNotBlank()
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null)
                        }
                    }
                    if (annotationPresets.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            annotationPresets.forEach { p ->
                                InputChip(
                                    selected = true,
                                    onClick = { onRemovePreset(p) },
                                    label = { Text("✕ $p", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            DiagnosticSectionPanel(
                title = "Добавить правило (Smart Rule)",
                rightBadge = "Триггеры"
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExposedDropdownMenuBox(
                        expanded = condExpanded,
                        onExpandedChange = { condExpanded = !condExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedCondition.label,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Условие") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(condExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = condExpanded,
                            onDismissRequest = { condExpanded = false }
                        ) {
                            RuleConditionType.entries.forEach { cond ->
                                DropdownMenuItem(
                                    text = { Text("${cond.label} (${cond.unit})") },
                                    onClick = {
                                        selectedCondition = cond
                                        condExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = thresholdText,
                        onValueChange = { thresholdText = it },
                        label = { Text("Порог (${selectedCondition.unit})") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    ExposedDropdownMenuBox(
                        expanded = actExpanded,
                        onExpandedChange = { actExpanded = !actExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedAction.label,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Действие") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(actExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = actExpanded,
                            onDismissRequest = { actExpanded = false }
                        ) {
                            RuleActionType.entries.forEach { act ->
                                DropdownMenuItem(
                                    text = { Text(act.label) },
                                    onClick = {
                                        selectedAction = act
                                        actExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            val valNum = thresholdText.replace(',', '.').toFloatOrNull() ?: return@Button
                            onAddRule(
                                SmartRule(
                                    id = UUID.randomUUID().toString(),
                                    name = "${selectedCondition.label} $valNum ${selectedCondition.unit} → ${selectedAction.label}",
                                    condition = selectedCondition,
                                    threshold = valNum,
                                    action = selectedAction,
                                    enabled = true
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Создать правило")
                    }
                }
            }
        }

        item {
            Text(
                text = "АКТИВНЫЕ И НАСТРОЕННЫЕ ПРАВИЛА (${rules.size})",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(rules, key = { it.id }) { rule ->
            DiagnosticSectionPanel(
                title = rule.condition.label,
                rightBadge = "${rule.threshold} ${rule.condition.unit}"
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = rule.name,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Действие: ${rule.action.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = rule.enabled,
                        onCheckedChange = { onToggleRule(rule.id) }
                    )
                    IconButton(onClick = { onDeleteRule(rule.id) }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Удалить правило")
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}
