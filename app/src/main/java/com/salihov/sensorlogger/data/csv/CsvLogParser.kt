package com.salihov.sensorlogger.data.csv

import com.salihov.sensorlogger.domain.model.CsvLogDocument
import com.salihov.sensorlogger.domain.model.CsvLogRow
import java.io.File
import java.nio.charset.StandardCharsets

object CsvLogParser {

    fun parseFile(file: File, maxRows: Int = 5_000): CsvLogDocument {
        val rawLines = mutableListOf<String>()
        val rows = mutableListOf<CsvLogRow>()
        val sensorNames = linkedSetOf<String>()
        var totalDataRows = 0
        var firstTimestamp: Long? = null
        var lastTimestamp: Long? = null
        var isTruncated = false
        var detectedDelimiter = ','

        file.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
            lines.forEachIndexed { lineIndex, rawLine ->
                val line = if (lineIndex == 0) rawLine.removePrefix("\uFEFF") else rawLine
                if (lineIndex == 0 && line.startsWith("timestamp_epoch_ms")) {
                    detectedDelimiter = if (line.contains(';')) ';' else ','
                    if (rawLines.size <= maxRows) {
                        rawLines += line
                    }
                    return@forEachIndexed
                }
                if (line.isBlank()) return@forEachIndexed

                if (lineIndex == 0 && line.contains(';') && !line.contains(',')) {
                    detectedDelimiter = ';'
                }

                totalDataRows++
                val fields = parseCsvLine(line, detectedDelimiter)
                val epochMillis = fields.getOrNull(0)?.toLongOrNull() ?: 0L
                val elapsedNanos = fields.getOrNull(1)?.toLongOrNull() ?: 0L
                val sensorType = fields.getOrNull(2).orEmpty()
                val sensorName = fields.getOrNull(3).orEmpty().ifBlank { sensorType }

                if (epochMillis > 0L) {
                    if (firstTimestamp == null || epochMillis < firstTimestamp!!) {
                        firstTimestamp = epochMillis
                    }
                    if (lastTimestamp == null || epochMillis > lastTimestamp!!) {
                        lastTimestamp = epochMillis
                    }
                }
                if (sensorName.isNotBlank()) {
                    sensorNames += sensorName
                }

                if (rows.size < maxRows) {
                    val values = fields
                        .drop(4)
                        .mapNotNull { token -> token.takeIf { it.isNotBlank() }?.toFloatOrNull() }
                    rows += CsvLogRow(
                        rowNumber = totalDataRows,
                        epochMillis = epochMillis,
                        elapsedNanos = elapsedNanos,
                        sensorType = sensorType,
                        sensorName = sensorName,
                        values = values
                    )
                    rawLines += line
                } else {
                    isTruncated = true
                }
            }
        }

        return CsvLogDocument(
            fileName = file.name,
            sizeBytes = file.length(),
            totalDataRows = totalDataRows,
            isTruncated = isTruncated,
            firstTimestampMillis = firstTimestamp,
            lastTimestampMillis = lastTimestamp,
            sensorNames = sensorNames.toList(),
            rows = rows,
            rawLines = rawLines
        )
    }

    fun parseCsvLine(line: String, delimiter: Char = ','): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0

        while (index < line.length) {
            val ch = line[index]
            when {
                inQuotes && ch == '"' -> {
                    val next = line.getOrNull(index + 1)
                    if (next == '"') {
                        current.append('"')
                        index++
                    } else {
                        inQuotes = false
                    }
                }
                ch == '"' -> inQuotes = true
                ch == delimiter && !inQuotes -> {
                    result += current.toString()
                    current.setLength(0)
                }
                else -> current.append(ch)
            }
            index++
        }
        result += current.toString()
        return result
    }
}
