package com.salihov.sensorlogger.data.analysis

import com.salihov.sensorlogger.domain.model.CsvLogDocument
import com.salihov.sensorlogger.domain.model.CsvLogRow
import com.salihov.sensorlogger.domain.model.SensorChannelStats
import com.salihov.sensorlogger.domain.model.SensorVerdict
import com.salihov.sensorlogger.domain.model.SessionAnalysisReport
import com.salihov.sensorlogger.domain.model.VerdictSeverity
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

object SessionAnalyzer {

    private const val STANDARD_GRAVITY = 9.80665f

    fun analyze(document: CsvLogDocument): SessionAnalysisReport {
        val markers = mutableListOf<CsvLogRow>()
        val grouped = LinkedHashMap<Pair<String, String>, MutableList<CsvLogRow>>()

        for (row in document.rows) {
            if (row.sensorType == "user.marker") {
                markers += row
            } else {
                val key = row.sensorName to row.sensorType
                grouped.getOrPut(key) { mutableListOf() }.add(row)
            }
        }

        val verdicts = grouped.map { (key, rows) ->
            analyzeSensorGroup(
                sensorName = key.first,
                sensorType = key.second,
                rows = rows
            )
        }

        return SessionAnalysisReport(
            fileName = document.fileName,
            totalDataRows = document.totalDataRows,
            durationMillis = document.durationMillis,
            firstTimestampMillis = document.firstTimestampMillis,
            lastTimestampMillis = document.lastTimestampMillis,
            verdicts = verdicts,
            markers = markers
        )
    }

    private fun analyzeSensorGroup(
        sensorName: String,
        sensorType: String,
        rows: List<CsvLogRow>
    ): SensorVerdict {
        val maxChannels = rows.maxOfOrNull { it.values.size } ?: 0
        val channelStats = (0 until maxChannels).mapNotNull { ch ->
            val vals = rows.mapNotNull { it.values.getOrNull(ch) }.filter { it.isFinite() }
            if (vals.isEmpty()) return@mapNotNull null
            val min = vals.min()
            val max = vals.max()
            val mean = (vals.sumOf { it.toDouble() } / vals.size).toFloat()
            val variance = vals.sumOf { ((it - mean) * (it - mean)).toDouble() } / vals.size
            val stdDev = sqrt(variance).toFloat()
            SensorChannelStats(
                channelLabel = "v$ch",
                min = min,
                max = max,
                mean = mean,
                stdDev = stdDev
            )
        }

        val firstTs = rows.firstOrNull { it.epochMillis > 0L }?.epochMillis
        val lastTs = rows.lastOrNull { it.epochMillis > 0L }?.epochMillis
        val effectiveHz = if (firstTs != null && lastTs != null && lastTs > firstTs && rows.size > 1) {
            (rows.size - 1) / ((lastTs - firstTs) / 1000f)
        } else {
            0f
        }

        val (severity, title, details) = evaluateVerdict(
            sensorType = sensorType,
            sensorName = sensorName,
            rows = rows,
            stats = channelStats
        )

        return SensorVerdict(
            sensorName = sensorName,
            sensorType = sensorType,
            sampleCount = rows.size,
            effectiveHz = effectiveHz,
            severity = severity,
            verdictTitle = title,
            verdictDetails = details,
            channelStats = channelStats
        )
    }

    private fun evaluateVerdict(
        sensorType: String,
        sensorName: String,
        rows: List<CsvLogRow>,
        stats: List<SensorChannelStats>
    ): Triple<VerdictSeverity, String, String> {
        val t = "$sensorType $sensorName".lowercase()
        val magnitudes = rows.mapNotNull { r ->
            if (r.values.isEmpty()) null
            else {
                val c = minOf(3, r.values.size)
                var sum = 0.0
                for (i in 0 until c) sum += r.values[i] * r.values[i]
                sqrt(sum).toFloat()
            }
        }
        val meanMag = if (magnitudes.isNotEmpty()) magnitudes.average().toFloat() else 0f
        val maxMag = magnitudes.maxOrNull() ?: 0f
        val maxStdDev = stats.maxOfOrNull { it.stdDev } ?: 0f

        return when {
            "accelerometer" in t && "uncalibrated" !in t -> {
                val maxG = maxMag / STANDARD_GRAVITY
                val gravDiff = abs(meanMag - STANDARD_GRAVITY)
                when {
                    maxG >= 2.0f -> Triple(
                        VerdictSeverity.ALERT,
                        "Зафиксирована перегрузка (${String.format(Locale.US, "%.2f g", maxG)})",
                        "Пиковое ускорение ${String.format(Locale.US, "%.2f", maxMag)} м/с², СКО=${String.format(Locale.US, "%.2f", maxStdDev)}."
                    )
                    maxStdDev >= 0.35f -> Triple(
                        VerdictSeverity.WARN,
                        "Вибрация / активное движение",
                        "Среднее |a|=${String.format(Locale.US, "%.2f", meanMag)} м/с², разброс СКО=${String.format(Locale.US, "%.2f", maxStdDev)}."
                    )
                    gravDiff > 0.45f -> Triple(
                        VerdictSeverity.WARN,
                        "Отклонение калибровки (|g|=${String.format(Locale.US, "%.2f", meanMag)})",
                        "Отклонение от 9.81 м/с² составляет ${String.format(Locale.US, "%.2f", gravDiff)} м/с². Рекомендуется калибровка."
                    )
                    else -> Triple(
                        VerdictSeverity.OK,
                        "Норма (покой, 1g в допуске)",
                        "Среднее |a|=${String.format(Locale.US, "%.3f", meanMag)} м/с², шум СКО=${String.format(Locale.US, "%.3f", maxStdDev)}."
                    )
                }
            }

            "gyroscope" in t -> {
                when {
                    maxMag >= 0.15f -> Triple(
                        VerdictSeverity.INFO,
                        "Активное вращение (пик ${String.format(Locale.US, "%.2f", maxMag)} рад/с)",
                        "Средняя угловая скорость ${String.format(Locale.US, "%.3f", meanMag)} рад/с."
                    )
                    meanMag >= 0.025f -> Triple(
                        VerdictSeverity.WARN,
                        "Обнаружен дрейф нуля (${String.format(Locale.US, "%.3f", meanMag)} рад/с)",
                        "В покое есть постоянное смещение. Рекомендуется программная калибровка нуля."
                    )
                    else -> Triple(
                        VerdictSeverity.OK,
                        "Норма (покой, дрейф в допуске)",
                        "Средний дрейф ${String.format(Locale.US, "%.4f", meanMag)} рад/с, СКО=${String.format(Locale.US, "%.4f", maxStdDev)}."
                    )
                }
            }

            "magnetic" in t -> {
                when {
                    meanMag > 75f || meanMag < 15f -> Triple(
                        VerdictSeverity.ALERT,
                        "Магнитная аномалия (${String.format(Locale.US, "%.1f", meanMag)} мкТл)",
                        "Норма геомагнитного поля Земли ~25–65 мкТл. Возможна помеха от металла/магнита."
                    )
                    maxStdDev >= 6f -> Triple(
                        VerdictSeverity.WARN,
                        "Нестабильное магнитное поле",
                        "Колебания СКО=${String.format(Locale.US, "%.1f", maxStdDev)} мкТл, среднее ${String.format(Locale.US, "%.1f", meanMag)} мкТл."
                    )
                    else -> Triple(
                        VerdictSeverity.OK,
                        "Норма (поле ${String.format(Locale.US, "%.1f", meanMag)} мкТл)",
                        "Стабильное геомагнитное поле в пределах 20–70 мкТл."
                    )
                }
            }

            "light" in t -> {
                val mean = stats.firstOrNull()?.mean ?: 0f
                val env = when {
                    mean < 10f -> "Темнота"
                    mean < 300f -> "Помещение"
                    else -> "Яркое освещение"
                }
                val severity = if (maxStdDev > 100f) VerdictSeverity.INFO else VerdictSeverity.OK
                Triple(
                    severity,
                    "$env (${String.format(Locale.US, "%.1f", mean)} лк)",
                    "Диапазон ${String.format(Locale.US, "%.1f..%.1f", stats.firstOrNull()?.min ?: 0f, stats.firstOrNull()?.max ?: 0f)} лк."
                )
            }

            "pressure" in t -> {
                val mean = stats.firstOrNull()?.mean ?: 0f
                val severity = if (mean in 940f..1050f) VerdictSeverity.OK else VerdictSeverity.WARN
                Triple(
                    severity,
                    "Давление ${String.format(Locale.US, "%.2f", mean)} гПа",
                    "Разброс за сессию: ${String.format(Locale.US, "%.2f", (stats.firstOrNull()?.max ?: 0f) - (stats.firstOrNull()?.min ?: 0f))} гПа."
                )
            }

            "sound_level" in t -> {
                val maxDb = stats.getOrNull(1)?.max ?: stats.firstOrNull()?.max ?: 0f
                val meanDb = stats.getOrNull(1)?.mean ?: stats.firstOrNull()?.mean ?: 0f
                val severity = if (maxDb >= 80f) VerdictSeverity.WARN else VerdictSeverity.OK
                Triple(
                    severity,
                    if (maxDb >= 80f) "Повышенный шум (пик ${String.format(Locale.US, "%.0f", maxDb)} дБ)" else "Уровень шума ${String.format(Locale.US, "%.0f", meanDb)} дБ",
                    "Средний ~${String.format(Locale.US, "%.1f", meanDb)} дБ, максимум ~${String.format(Locale.US, "%.1f", maxDb)} дБ."
                )
            }

            "gps" in t -> {
                val maxSpeed = stats.getOrNull(3)?.max ?: 0f
                val avgAcc = stats.getOrNull(5)?.mean ?: 0f
                val severity = if (avgAcc > 30f) VerdictSeverity.WARN else VerdictSeverity.OK
                Triple(
                    severity,
                    "GPS трек (${rows.size} точек, точность ~${String.format(Locale.US, "%.1f", avgAcc)} м)",
                    "Макс. скорость ${String.format(Locale.US, "%.1f", maxSpeed * 3.6f)} км/ч."
                )
            }

            "system.state" in t -> {
                val maxTemp = stats.getOrNull(2)?.max ?: 0f
                val severity = if (maxTemp >= 42f) VerdictSeverity.ALERT else VerdictSeverity.OK
                Triple(
                    severity,
                    if (maxTemp >= 42f) "Нагрев батареи (${String.format(Locale.US, "%.1f", maxTemp)} °C)" else "Системные параметры в норме",
                    "Заряд ${String.format(Locale.US, "%.0f", stats.firstOrNull()?.mean ?: 0f)}%, макс. t=${String.format(Locale.US, "%.1f", maxTemp)} °C."
                )
            }

            else -> {
                val firstStats = stats.firstOrNull()
                Triple(
                    VerdictSeverity.INFO,
                    "Зафиксировано замеров: ${rows.size}",
                    if (firstStats != null) {
                        "v0: среднее=${String.format(Locale.US, "%.3f", firstStats.mean)}, СКО=${String.format(Locale.US, "%.3f", firstStats.stdDev)}."
                    } else {
                        "События без числовых каналов."
                    }
                )
            }
        }
    }
}
