package com.salihov.sensorlogger.data.csv

import com.salihov.sensorlogger.domain.model.SensorSample
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvFormatter {
    const val MAX_VALUE_COLUMNS = 6

    const val HEADER =
        "timestamp_epoch_ms,elapsed_ns,sensor_type,sensor_name,v0,v1,v2,v3,v4,v5"

    fun buildHeader(delimiter: Char = ','): String =
        if (delimiter == ',') HEADER else HEADER.replace(',', delimiter)

    fun buildSessionFileName(epochMillis: Long): String {
        val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        return "sensors_${formatter.format(Date(epochMillis))}.csv"
    }

    fun formatRow(sample: SensorSample, delimiter: Char = ','): String = buildString {
        append(sample.epochMillis)
        append(delimiter)
        append(sample.elapsedNanos)
        append(delimiter)
        append(escapeField(sample.sensorType, delimiter))
        append(delimiter)
        append(escapeField(sample.sensorName, delimiter))

        for (index in 0 until MAX_VALUE_COLUMNS) {
            append(delimiter)
            val value = sample.values.getOrNull(index)
            if (value != null) {
                append(String.format(Locale.US, "%.6f", value))
            }
        }
    }

    fun escapeField(raw: String, delimiter: Char = ','): String {
        val needsQuotes = raw.any { it == delimiter || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuotes) return raw
        val escaped = raw.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}
