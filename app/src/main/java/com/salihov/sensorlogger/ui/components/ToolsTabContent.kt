package com.salihov.sensorlogger.ui.components

import android.Manifest
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.salihov.sensorlogger.data.sensor.SensorToolsMath
import com.salihov.sensorlogger.domain.model.AudioLevelSnapshot
import com.salihov.sensorlogger.domain.model.FftSpectrumSnapshot
import com.salihov.sensorlogger.domain.model.SensorDescriptor
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

@Composable
fun ToolsTabContent(
    sensors: List<SensorDescriptor>,
    liveReadings: Map<String, List<Float>>,
    peakGForce: Float,
    fftSpectrum: FftSpectrumSnapshot,
    inclinometerZeroPitch: Float,
    inclinometerZeroRoll: Float,
    audioSnapshot: AudioLevelSnapshot?,
    hasAudioPermission: Boolean,
    onResetPeakG: () -> Unit,
    onCalibrateInclinometerZero: () -> Unit,
    onResetInclinometerZero: () -> Unit,
    onRequestAudioPermission: (Set<String>) -> Unit
) {
    val accelSensor = sensors.firstOrNull { it.type == 1 }
    val gravitySensor = sensors.firstOrNull { it.type == 9 } ?: accelSensor
    val linearAccelSensor = sensors.firstOrNull { it.type == 10 }
    val magnetSensor = sensors.firstOrNull { it.type == 2 }
    val lightSensor = sensors.firstOrNull { it.type == 5 }
    val pressureSensor = sensors.firstOrNull { it.type == 6 }

    val accelVals = accelSensor?.let { liveReadings[it.id] }.orEmpty()
    val gravityVals = gravitySensor?.let { liveReadings[it.id] }.orEmpty()
    val linearVals = linearAccelSensor?.let { liveReadings[it.id] }.orEmpty()
    val magnetVals = magnetSensor?.let { liveReadings[it.id] }.orEmpty()
    val lightVal = lightSensor?.let { liveReadings[it.id]?.firstOrNull() }
    val pressureVal = pressureSensor?.let { liveReadings[it.id]?.firstOrNull() }

    val totalAccel = SensorToolsMath.vectorMagnitude(accelVals)
    val currentG = SensorToolsMath.gForce(accelVals)
    val linearMag = SensorToolsMath.vectorMagnitude(linearVals)
    val rawPitchRoll = SensorToolsMath.pitchAndRollDegrees(gravityVals)
    val pitchRoll = rawPitchRoll?.let { (p, r) ->
        (p - inclinometerZeroPitch) to (r - inclinometerZeroRoll)
    }
    val hasLevelZero = inclinometerZeroPitch != 0f || inclinometerZeroRoll != 0f
    val azimuth = SensorToolsMath.compassAzimuthDegrees(gravityVals, magnetVals)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            DiagnosticSectionPanel(
                title = "G-Метр и Виброметр",
                rightBadge = accelSensor?.name ?: "Нет акселерометра"
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = String.format(Locale.US, "%.3f g", currentG),
                                style = MaterialTheme.typography.headlineSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Пик: ${String.format(Locale.US, "%.3f g", peakGForce)} • |a| = ${String.format(Locale.US, "%.2f", totalAccel)} м/с²",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        OutlinedButton(
                            onClick = onResetPeakG,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.RestartAlt,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Сброс пика", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val primaryColor = MaterialTheme.colorScheme.primary
                    val peakColor = MaterialTheme.colorScheme.error
                    val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                    ) {
                        val maxScaleG = 3.0f
                        drawRect(color = trackColor, size = size)
                        val curFraction = (currentG / maxScaleG).coerceIn(0f, 1f)
                        drawRect(
                            color = primaryColor,
                            size = Size(size.width * curFraction, size.height)
                        )
                        val peakX = ((peakGForce / maxScaleG).coerceIn(0f, 1f)) * size.width
                        if (peakGForce > 0f) {
                            drawLine(
                                color = peakColor,
                                start = Offset(peakX, 0f),
                                end = Offset(peakX, size.height),
                                strokeWidth = 3.dp.toPx()
                            )
                        }
                    }

                    if (linearAccelSensor != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        DiagnosticKeyValueRow(
                            label = "Вибрация (без гравитации)",
                            value = "${String.format(Locale.US, "%.3f", linearMag)} м/с²",
                            showDivider = false
                        )
                    }
                }
            }
        }

        item {
            DiagnosticSectionPanel(
                title = "Спектр вибраций (FFT / БПФ)",
                rightBadge = if (fftSpectrum.dominantHz > 0f) {
                    "${String.format(Locale.US, "%.1f", fftSpectrum.dominantHz)} Гц"
                } else {
                    "Накопление"
                }
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Доминирующая: ${String.format(Locale.US, "%.1f", fftSpectrum.dominantHz)} Гц",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Пик ампл.: ${String.format(Locale.US, "%.3f", fftSpectrum.peakAmplitude)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    val barColor = MaterialTheme.colorScheme.primary
                    val bgColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                    ) {
                        drawRect(color = bgColor, size = size)
                        val bins = fftSpectrum.bins
                        if (bins.isNotEmpty()) {
                            val maxVal = bins.maxOrNull()?.coerceAtLeast(1e-4f) ?: 1f
                            val stepW = size.width / bins.size.toFloat()
                            bins.forEachIndexed { idx, amp ->
                                val h = (amp / maxVal).coerceIn(0f, 1f) * size.height
                                drawRect(
                                    color = barColor,
                                    topLeft = Offset(idx * stepW + 1f, size.height - h),
                                    size = Size((stepW - 2f).coerceAtLeast(1f), h)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            DiagnosticSectionPanel(
                title = "Инклинометр (Уровень) и Компас",
                rightBadge = if (azimuth != null) {
                    "${String.format(Locale.US, "%.0f°", azimuth)} ${SensorToolsMath.cardinalDirection(azimuth)}"
                } else {
                    "Наклон"
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val ringColor = MaterialTheme.colorScheme.outline
                    val crossColor = MaterialTheme.colorScheme.outlineVariant
                    val bubbleColor = MaterialTheme.colorScheme.tertiary
                    val northColor = MaterialTheme.colorScheme.error

                    Canvas(modifier = Modifier.size(116.dp)) {
                        val r = size.minDimension / 2f - 4.dp.toPx()
                        val center = Offset(size.width / 2f, size.height / 2f)

                        drawCircle(
                            color = ringColor,
                            radius = r,
                            center = center,
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                        drawCircle(
                            color = crossColor,
                            radius = r * 0.45f,
                            center = center,
                            style = Stroke(width = 1.dp.toPx())
                        )
                        drawLine(
                            color = crossColor,
                            start = Offset(center.x - r, center.y),
                            end = Offset(center.x + r, center.y),
                            strokeWidth = 1f
                        )
                        drawLine(
                            color = crossColor,
                            start = Offset(center.x, center.y - r),
                            end = Offset(center.x, center.y + r),
                            strokeWidth = 1f
                        )

                        if (azimuth != null) {
                            val angleRad = (-azimuth - 90f) * PI / 180.0
                            val tip = Offset(
                                x = center.x + (r * 0.85f * cos(angleRad)).toFloat(),
                                y = center.y + (r * 0.85f * sin(angleRad)).toFloat()
                            )
                            drawLine(
                                color = northColor,
                                start = center,
                                end = tip,
                                strokeWidth = 2.5.dp.toPx()
                            )
                            drawCircle(color = northColor, radius = 3.5.dp.toPx(), center = tip)
                        }

                        if (pitchRoll != null) {
                            val (pitch, roll) = pitchRoll
                            val dx = (roll / 45f).coerceIn(-1f, 1f) * (r * 0.78f)
                            val dy = (-pitch / 45f).coerceIn(-1f, 1f) * (r * 0.78f)
                            drawCircle(
                                color = bubbleColor,
                                radius = 7.dp.toPx(),
                                center = Offset(center.x + dx, center.y + dy)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        DiagnosticKeyValueRow(
                            label = "Тангаж (Pitch)",
                            value = pitchRoll?.let { String.format(Locale.US, "%+.1f°", it.first) } ?: "—",
                            index = 0
                        )
                        DiagnosticKeyValueRow(
                            label = "Крен (Roll)",
                            value = pitchRoll?.let { String.format(Locale.US, "%+.1f°", it.second) } ?: "—",
                            index = 1
                        )
                        DiagnosticKeyValueRow(
                            label = "Азимут",
                            value = azimuth?.let {
                                "${String.format(Locale.US, "%.1f°", it)} (${SensorToolsMath.cardinalDirection(it)})"
                            } ?: if (magnetSensor == null) "Нет магнитометра" else "Калибровка...",
                            index = 2,
                            showDivider = false
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = onCalibrateInclinometerZero,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Ноль уровня", style = MaterialTheme.typography.labelSmall)
                            }
                            if (hasLevelZero) {
                                OutlinedButton(
                                    onClick = onResetInclinometerZero,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("Сброс", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            DiagnosticSectionPanel(
                title = "Среда и Барометрический высотомер",
                rightBadge = "Live"
            ) {
                DiagnosticKeyValueRow(
                    label = "Освещённость",
                    value = lightVal?.let { String.format(Locale.US, "%.1f лк", it) }
                        ?: if (lightSensor == null) "Нет датчика" else "Ожидание...",
                    index = 0
                )
                DiagnosticKeyValueRow(
                    label = "Атм. давление",
                    value = pressureVal?.let { String.format(Locale.US, "%.2f гПа", it) }
                        ?: if (pressureSensor == null) "Нет барометра" else "Ожидание...",
                    index = 1
                )
                val baroAltitude = pressureVal?.takeIf { it > 0f }?.let { p ->
                    44330.0 * (1.0 - (p / 1013.25).pow(1.0 / 5.255))
                }
                DiagnosticKeyValueRow(
                    label = "Высота (по 1013.25 гПа)",
                    value = baroAltitude?.let { String.format(Locale.US, "%.1f м", it) } ?: "—",
                    index = 2
                )
                val mmHg = pressureVal?.let { it * 0.750062f }
                DiagnosticKeyValueRow(
                    label = "Давление (мм рт. ст.)",
                    value = mmHg?.let { String.format(Locale.US, "%.1f мм", it) } ?: "—",
                    index = 3,
                    showDivider = false
                )
            }
        }

        item {
            DiagnosticSectionPanel(
                title = "Шумомер (Микрофон)",
                rightBadge = audioSnapshot?.let { String.format(Locale.US, "%.1f дБ", it.approxSplDb) } ?: "Микрофон"
            ) {
                if (!hasAudioPermission) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Для измерения уровня шума (дБ / dBFS) необходимо разрешение на микрофон.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = { onRequestAudioPermission(setOf(Manifest.permission.RECORD_AUDIO)) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Разрешить микрофон")
                        }
                    }
                } else {
                    Column(modifier = Modifier.padding(10.dp)) {
                        val spl = audioSnapshot?.approxSplDb ?: 0f
                        val dbfs = audioSnapshot?.dbfs ?: -90f
                        val peak = audioSnapshot?.peakDbfs ?: -90f
                        Text(
                            text = String.format(Locale.US, "%.1f дБ SPL (%.1f dBFS)", spl, dbfs),
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        val barColor = if (spl >= 80f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                        val bgTrack = MaterialTheme.colorScheme.surfaceContainerHigh
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(16.dp)
                        ) {
                            drawRect(color = bgTrack, size = size)
                            val frac = ((spl - 15f) / 95f).coerceIn(0f, 1f)
                            drawRect(color = barColor, size = Size(size.width * frac, size.height))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Пик сигнала: ${String.format(Locale.US, "%.1f dBFS", peak)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
