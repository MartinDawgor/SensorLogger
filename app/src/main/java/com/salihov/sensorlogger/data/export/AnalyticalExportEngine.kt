package com.salihov.sensorlogger.data.export

import android.database.sqlite.SQLiteDatabase
import com.salihov.sensorlogger.data.csv.CsvFormatter
import com.salihov.sensorlogger.domain.model.CsvDelimiter
import com.salihov.sensorlogger.domain.model.CsvLogDocument
import com.salihov.sensorlogger.domain.model.CsvLogRow
import com.salihov.sensorlogger.domain.model.ExportFormat
import com.salihov.sensorlogger.domain.model.SessionAnalysisReport
import com.salihov.sensorlogger.domain.model.TimestampFormat
import com.salihov.sensorlogger.domain.model.VerdictSeverity
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object AnalyticalExportEngine {

    private data class ResampledBucket(
        val epochMillis: Long,
        val valuesByColumn: Map<String, Float>
    )

    fun exportToFile(
        document: CsvLogDocument,
        report: SessionAnalysisReport,
        format: ExportFormat,
        targetFile: File,
        delimiter: CsvDelimiter = CsvDelimiter.SEMICOLON,
        timestampFormat: TimestampFormat = TimestampFormat.ISO_8601,
        rawFile: File? = null
    ) {
        when (format) {
            ExportFormat.HTML_REPORT,
            ExportFormat.PDF_PRINT -> targetFile.writeText(
                buildHtmlReport(document, report, timestampFormat),
                StandardCharsets.UTF_8
            )
            ExportFormat.WIDE_CSV -> targetFile.writeText(
                buildWideCsvReport(document, report, delimiter, timestampFormat),
                StandardCharsets.UTF_8
            )
            ExportFormat.MARKDOWN_REPORT -> targetFile.writeText(
                buildMarkdownReport(document, report, timestampFormat),
                StandardCharsets.UTF_8
            )
            ExportFormat.JSON -> targetFile.writeText(
                buildJsonReport(document, report),
                StandardCharsets.UTF_8
            )
            ExportFormat.KML -> targetFile.writeText(
                buildKmlTrack(document, report),
                StandardCharsets.UTF_8
            )
            ExportFormat.SQLITE -> writeSqliteDatabase(document, report, targetFile)
            ExportFormat.ZIP_BUNDLE -> targetFile.writeBytes(
                buildZipBundleBytes(document, report, delimiter, timestampFormat, rawFile)
            )
            ExportFormat.RAW_CSV -> {
                if (rawFile != null && rawFile.exists()) {
                    rawFile.copyTo(targetFile, overwrite = true)
                } else {
                    targetFile.writeText(document.rawLines.joinToString("\n"), StandardCharsets.UTF_8)
                }
            }
        }
    }

    fun buildBatchZip(
        items: List<Triple<CsvLogDocument, SessionAnalysisReport, File?>>,
        targetFile: File,
        delimiter: CsvDelimiter,
        timestampFormat: TimestampFormat
    ) {
        ZipOutputStream(targetFile.outputStream().buffered()).use { zip ->
            for ((doc, report, rawFile) in items) {
                val base = doc.fileName.removeSuffix(".csv")
                if (rawFile != null && rawFile.exists()) {
                    zip.putNextEntry(ZipEntry("$base/${doc.fileName}"))
                    zip.write(rawFile.readBytes())
                    zip.closeEntry()
                }
                zip.putNextEntry(ZipEntry("$base/${base}_report.html"))
                zip.write(buildHtmlReport(doc, report, timestampFormat).toByteArray(StandardCharsets.UTF_8))
                zip.closeEntry()

                zip.putNextEntry(ZipEntry("$base/${base}_table.csv"))
                zip.write(buildWideCsvReport(doc, report, delimiter, timestampFormat).toByteArray(StandardCharsets.UTF_8))
                zip.closeEntry()
            }
        }
    }

    fun buildZipBundleBytes(
        document: CsvLogDocument,
        report: SessionAnalysisReport,
        delimiter: CsvDelimiter,
        timestampFormat: TimestampFormat,
        rawFile: File?
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        val base = document.fileName.removeSuffix(".csv")
        ZipOutputStream(baos).use { zip ->
            zip.putNextEntry(ZipEntry("${base}_raw.csv"))
            val rawBytes = if (rawFile != null && rawFile.exists()) {
                rawFile.readBytes()
            } else {
                document.rawLines.joinToString("\n").toByteArray(StandardCharsets.UTF_8)
            }
            zip.write(rawBytes)
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("${base}_report.html"))
            zip.write(buildHtmlReport(document, report, timestampFormat).toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("${base}_wide.csv"))
            zip.write(buildWideCsvReport(document, report, delimiter, timestampFormat).toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("${base}.json"))
            zip.write(buildJsonReport(document, report).toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()

            if (document.rows.any { it.sensorType == "android.location.gps" }) {
                zip.putNextEntry(ZipEntry("${base}.kml"))
                zip.write(buildKmlTrack(document, report).toByteArray(StandardCharsets.UTF_8))
                zip.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    fun buildWideCsvReport(
        document: CsvLogDocument,
        report: SessionAnalysisReport,
        delimiter: CsvDelimiter,
        timestampFormat: TimestampFormat,
        bucketMs: Long = 200L
    ): String {
        val d = delimiter.symbol
        val sb = StringBuilder()
        if (delimiter == CsvDelimiter.SEMICOLON) {
            sb.append('\uFEFF')
        }

        sb.appendLine("ОТЧЁТ АНАЛИЗА СЕССИИ${d}${CsvFormatter.escapeField(document.fileName, d)}")
        sb.appendLine("Всего замеров${d}${report.totalDataRows}${d}Длительность (с)${d}${String.format(Locale.US, "%.2f", report.durationMillis / 1000.0)}")
        sb.appendLine()
        sb.appendLine(listOf("Датчик", "Тип", "Замеров", "Частота (Гц)", "Мин v0", "Макс v0", "Среднее v0", "СКО (Откл.) v0", "Статус", "Вердикт", "Детали").joinToString(d.toString()))

        for (v in report.verdicts) {
            val s0 = v.channelStats.firstOrNull()
            sb.appendLine(
                listOf(
                    CsvFormatter.escapeField(v.sensorName, d),
                    CsvFormatter.escapeField(v.sensorType, d),
                    v.sampleCount.toString(),
                    String.format(Locale.US, "%.1f", v.effectiveHz),
                    s0?.let { String.format(Locale.US, "%.4f", it.min) }.orEmpty(),
                    s0?.let { String.format(Locale.US, "%.4f", it.max) }.orEmpty(),
                    s0?.let { String.format(Locale.US, "%.4f", it.mean) }.orEmpty(),
                    s0?.let { String.format(Locale.US, "%.4f", it.stdDev) }.orEmpty(),
                    v.severity.label,
                    CsvFormatter.escapeField(v.verdictTitle, d),
                    CsvFormatter.escapeField(v.verdictDetails, d)
                ).joinToString(d.toString())
            )
        }

        if (report.markers.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("МЕТКИ СОБЫТИЙ${d}Комментарий")
            for (m in report.markers) {
                sb.appendLine("${formatTs(m.epochMillis, report.firstTimestampMillis, timestampFormat)}${d}${CsvFormatter.escapeField(m.sensorName, d)}")
            }
        }

        val (columns, buckets) = resampleToBuckets(document.rows, bucketMs)
        if (columns.isNotEmpty() && buckets.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("СИНХРОНИЗИРОВАННАЯ ТАБЛИЦА (ШАГ ${bucketMs} МС)")
            sb.append("Время")
            for (col in columns) {
                sb.append(d).append(CsvFormatter.escapeField(col, d))
            }
            sb.appendLine()
            for (bucket in buckets) {
                sb.append(formatTs(bucket.epochMillis, report.firstTimestampMillis, timestampFormat))
                for (col in columns) {
                    sb.append(d)
                    bucket.valuesByColumn[col]?.let { v ->
                        sb.append(String.format(Locale.US, "%.5f", v))
                    }
                }
                sb.appendLine()
            }
        }

        return sb.toString()
    }

    fun buildHtmlReport(
        document: CsvLogDocument,
        report: SessionAnalysisReport,
        timestampFormat: TimestampFormat
    ): String {
        val (columns, buckets) = resampleToBuckets(document.rows, bucketMs = 250L, maxBuckets = 500)
        return buildString {
            appendLine("<!DOCTYPE html><html lang='ru'><head><meta charset='UTF-8'>")
            appendLine("<meta name='viewport' content='width=device-width,initial-scale=1'>")
            appendLine("<title>Отчёт ${escapeHtml(document.fileName)}</title>")
            appendLine(
                """
                <style>
                body{font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;background:#eef1f5;color:#111827;margin:16px;font-size:13px}
                h1,h2{margin:14px 0 8px;color:#0d47a1}
                .meta{display:flex;gap:12px;flex-wrap:wrap;margin-bottom:14px}
                .card{background:#fff;border:1px solid #cbd5e1;border-radius:4px;padding:10px 14px}
                table{width:100%;border-collapse:collapse;background:#fff;border:1px solid #cbd5e1;margin-bottom:16px}
                th,td{border:1px solid #e2e8f0;padding:6px 8px;text-align:left;font-variant-numeric:tabular-nums}
                th{background:#e2e8f0;font-size:12px;text-transform:uppercase}
                tr:nth-child(even) td{background:#f8fafc}
                .mono{font-family:ui-monospace,Consolas,monospace}
                .badge{padding:2px 6px;border-radius:3px;font-weight:700;font-size:11px}
                .OK{background:#dcfce7;color:#166534}.INFO{background:#dbeafe;color:#1e40af}
                .WARN{background:#fef3c7;color:#92400e}.ALERT{background:#fee2e2;color:#991b1b}
                </style></head><body>
                """.trimIndent()
            )
            appendLine("<h1>AIDA SensorLogger — Аналитический отчёт</h1>")
            appendLine("<div class='meta'>")
            appendLine("<div class='card'><b>Файл:</b> <span class='mono'>${escapeHtml(document.fileName)}</span></div>")
            appendLine("<div class='card'><b>Замеров:</b> ${report.totalDataRows}</div>")
            appendLine("<div class='card'><b>Длительность:</b> ${String.format(Locale.US, "%.2f с", report.durationMillis / 1000.0)}</div>")
            appendLine("<div class='card'><b>Датчиков:</b> ${report.verdicts.size}</div>")
            appendLine("</div>")

            appendLine("<h2>Сводная таблица и вердикты отклонений</h2>")
            appendLine("<table><tr><th>Датчик</th><th>Замеры / Гц</th><th>Среднее (v0)</th><th>СКО (Откл.)</th><th>Мин .. Макс</th><th>Вердикт</th><th>Диагноз</th></tr>")
            for (v in report.verdicts) {
                val s0 = v.channelStats.firstOrNull()
                val badgeClass = when (v.severity) {
                    VerdictSeverity.OK -> "OK"
                    VerdictSeverity.INFO -> "INFO"
                    VerdictSeverity.WARN -> "WARN"
                    VerdictSeverity.ALERT -> "ALERT"
                }
                appendLine("<tr>")
                appendLine("<td><b>${escapeHtml(v.sensorName)}</b><br><small class='mono'>${escapeHtml(v.sensorType)}</small></td>")
                appendLine("<td class='mono'>${v.sampleCount} / ${String.format(Locale.US, "%.1f", v.effectiveHz)} Гц</td>")
                appendLine("<td class='mono'>${s0?.let { String.format(Locale.US, "%.3f", it.mean) } ?: "—"}</td>")
                appendLine("<td class='mono'>${s0?.let { String.format(Locale.US, "%.3f", it.stdDev) } ?: "—"}</td>")
                appendLine("<td class='mono'>${s0?.let { "${String.format(Locale.US, "%.2f", it.min)} .. ${String.format(Locale.US, "%.2f", it.max)}" } ?: "—"}</td>")
                appendLine("<td><span class='badge $badgeClass'>${v.severity.label}</span></td>")
                appendLine("<td><b>${escapeHtml(v.verdictTitle)}</b><br><small>${escapeHtml(v.verdictDetails)}</small></td>")
                appendLine("</tr>")
            }
            appendLine("</table>")

            if (report.markers.isNotEmpty()) {
                appendLine("<h2>Метки и аннотации</h2><table><tr><th>Время</th><th>Комментарий</th></tr>")
                for (m in report.markers) {
                    appendLine("<tr><td class='mono'>${escapeHtml(formatTs(m.epochMillis, report.firstTimestampMillis, timestampFormat))}</td><td>${escapeHtml(m.sensorName)}</td></tr>")
                }
                appendLine("</table>")
            }

            if (columns.isNotEmpty() && buckets.isNotEmpty()) {
                appendLine("<h2>Таблица значений (шаг 250 мс, до 500 строк)</h2><table><tr><th>Время</th>")
                for (c in columns) appendLine("<th>${escapeHtml(c)}</th>")
                appendLine("</tr>")
                for (b in buckets) {
                    appendLine("<tr><td class='mono'>${escapeHtml(formatTs(b.epochMillis, report.firstTimestampMillis, timestampFormat))}</td>")
                    for (c in columns) {
                        val valStr = b.valuesByColumn[c]?.let { String.format(Locale.US, "%.3f", it) } ?: ""
                        appendLine("<td class='mono'>$valStr</td>")
                    }
                    appendLine("</tr>")
                }
                appendLine("</table>")
            }
            appendLine("</body></html>")
        }
    }

    fun buildMarkdownReport(
        document: CsvLogDocument,
        report: SessionAnalysisReport,
        timestampFormat: TimestampFormat
    ): String = buildString {
        appendLine("# Отчёт SensorLogger: ${document.fileName}")
        appendLine("- Замеров: **${report.totalDataRows}**")
        appendLine("- Длительность: **${String.format(Locale.US, "%.2f с", report.durationMillis / 1000.0)}**")
        appendLine()
        appendLine("## Вердикты по датчикам")
        appendLine("| Датчик | Замеры | Гц | Среднее v0 | СКО v0 | Статус | Вердикт |")
        appendLine("|---|---:|---:|---:|---:|---|---|")
        for (v in report.verdicts) {
            val s0 = v.channelStats.firstOrNull()
            appendLine(
                "| ${v.sensorName} | ${v.sampleCount} | ${String.format(Locale.US, "%.1f", v.effectiveHz)} | " +
                    "${s0?.let { String.format(Locale.US, "%.3f", it.mean) } ?: "—"} | " +
                    "${s0?.let { String.format(Locale.US, "%.3f", it.stdDev) } ?: "—"} | " +
                    "**${v.severity.label}** | ${v.verdictTitle} — ${v.verdictDetails} |"
            )
        }
        if (report.markers.isNotEmpty()) {
            appendLine()
            appendLine("## Метки событий")
            appendLine("| Время | Метка |")
            appendLine("|---|---|")
            for (m in report.markers) {
                appendLine("| ${formatTs(m.epochMillis, report.firstTimestampMillis, timestampFormat)} | ${m.sensorName} |")
            }
        }
    }

    fun buildJsonReport(document: CsvLogDocument, report: SessionAnalysisReport): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"fileName\": ${jsonStr(document.fileName)},\n")
        sb.append("  \"totalDataRows\": ${report.totalDataRows},\n")
        sb.append("  \"durationMillis\": ${report.durationMillis},\n")
        sb.append("  \"verdicts\": [\n")
        report.verdicts.forEachIndexed { idx, v ->
            sb.append("    {\n")
            sb.append("      \"sensorName\": ${jsonStr(v.sensorName)},\n")
            sb.append("      \"sensorType\": ${jsonStr(v.sensorType)},\n")
            sb.append("      \"sampleCount\": ${v.sampleCount},\n")
            sb.append("      \"effectiveHz\": ${String.format(Locale.US, "%.2f", v.effectiveHz)},\n")
            sb.append("      \"severity\": ${jsonStr(v.severity.label)},\n")
            sb.append("      \"verdictTitle\": ${jsonStr(v.verdictTitle)},\n")
            sb.append("      \"verdictDetails\": ${jsonStr(v.verdictDetails)}\n")
            sb.append("    }${if (idx < report.verdicts.lastIndex) "," else ""}\n")
        }
        sb.append("  ],\n")
        sb.append("  \"samples\": [\n")
        document.rows.forEachIndexed { idx, row ->
            val vals = row.values.joinToString(",") { String.format(Locale.US, "%.6f", it) }
            sb.append("    {\"t\":${row.epochMillis},\"type\":${jsonStr(row.sensorType)},\"name\":${jsonStr(row.sensorName)},\"values\":[$vals]}")
            if (idx < document.rows.lastIndex) sb.append(',')
            sb.append('\n')
        }
        sb.append("  ]\n")
        sb.append("}")
        return sb.toString()
    }

    fun buildKmlTrack(document: CsvLogDocument, report: SessionAnalysisReport): String {
        val gpsRows = document.rows.filter { it.sensorType == "android.location.gps" && it.values.size >= 2 }
        return buildString {
            appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            appendLine("<kml xmlns=\"http://www.opengis.net/kml/2.2\"><Document>")
            appendLine("<name>${escapeHtml(document.fileName)}</name>")
            if (gpsRows.isNotEmpty()) {
                appendLine("<Placemark><name>Трек</name><LineString><altitudeMode>clampToGround</altitudeMode><coordinates>")
                for (r in gpsRows) {
                    val lat = r.values[0]
                    val lon = r.values[1]
                    val alt = r.values.getOrNull(2) ?: 0f
                    appendLine(String.format(Locale.US, "%.6f,%.6f,%.2f", lon, lat, alt))
                }
                appendLine("</coordinates></LineString></Placemark>")
            }
            for (m in report.markers) {
                val nearest = gpsRows.minByOrNull { kotlin.math.abs(it.epochMillis - m.epochMillis) }
                if (nearest != null) {
                    appendLine("<Placemark><name>${escapeHtml(m.sensorName)}</name><Point><coordinates>")
                    appendLine(String.format(Locale.US, "%.6f,%.6f,%.2f", nearest.values[1], nearest.values[0], nearest.values.getOrNull(2) ?: 0f))
                    appendLine("</coordinates></Point></Placemark>")
                }
            }
            appendLine("</Document></kml>")
        }
    }

    private fun writeSqliteDatabase(
        document: CsvLogDocument,
        report: SessionAnalysisReport,
        targetFile: File
    ) {
        if (targetFile.exists()) targetFile.delete()
        val db = SQLiteDatabase.openOrCreateDatabase(targetFile, null)
        try {
            db.beginTransaction()
            db.execSQL("CREATE TABLE session_meta (key TEXT PRIMARY KEY, value TEXT)")
            db.execSQL("CREATE TABLE sensor_verdicts (sensor_name TEXT, sensor_type TEXT, sample_count INTEGER, effective_hz REAL, severity TEXT, verdict_title TEXT, verdict_details TEXT)")
            db.execSQL("CREATE TABLE markers (epoch_ms INTEGER, label TEXT)")
            db.execSQL("CREATE TABLE samples (row_num INTEGER PRIMARY KEY, epoch_ms INTEGER, elapsed_ns INTEGER, sensor_type TEXT, sensor_name TEXT, v0 REAL, v1 REAL, v2 REAL, v3 REAL, v4 REAL, v5 REAL)")

            db.execSQL("INSERT INTO session_meta VALUES ('file_name', ?)", arrayOf(document.fileName))
            db.execSQL("INSERT INTO session_meta VALUES ('total_rows', ?)", arrayOf(report.totalDataRows.toString()))
            db.execSQL("INSERT INTO session_meta VALUES ('duration_ms', ?)", arrayOf(report.durationMillis.toString()))

            for (v in report.verdicts) {
                db.execSQL(
                    "INSERT INTO sensor_verdicts VALUES (?, ?, ?, ?, ?, ?, ?)",
                    arrayOf<Any>(v.sensorName, v.sensorType, v.sampleCount, v.effectiveHz, v.severity.label, v.verdictTitle, v.verdictDetails)
                )
            }
            for (m in report.markers) {
                db.execSQL("INSERT INTO markers VALUES (?, ?)", arrayOf<Any>(m.epochMillis, m.sensorName))
            }
            val stmt = db.compileStatement("INSERT INTO samples VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
            for (r in document.rows) {
                stmt.clearBindings()
                stmt.bindLong(1, r.rowNumber.toLong())
                stmt.bindLong(2, r.epochMillis)
                stmt.bindLong(3, r.elapsedNanos)
                stmt.bindString(4, r.sensorType)
                stmt.bindString(5, r.sensorName)
                for (i in 0 until 6) {
                    val v = r.values.getOrNull(i)
                    if (v != null) stmt.bindDouble(6 + i, v.toDouble()) else stmt.bindNull(6 + i)
                }
                stmt.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            db.close()
        }
    }

    private fun resampleToBuckets(
        rows: List<CsvLogRow>,
        bucketMs: Long,
        maxBuckets: Int = 2_000
    ): Pair<List<String>, List<ResampledBucket>> {
        val sensorRows = rows.filter { it.sensorType != "user.marker" && it.epochMillis > 0L }
        if (sensorRows.isEmpty()) return emptyList<String>() to emptyList()

        val columns = linkedSetOf<String>()
        val bucketMap = linkedMapOf<Long, MutableMap<String, Float>>()
        val start = sensorRows.first().epochMillis

        for (r in sensorRows) {
            val idx = ((r.epochMillis - start) / bucketMs).coerceAtLeast(0L)
            if (idx >= maxBuckets) continue
            val bucketTime = start + idx * bucketMs
            val cell = bucketMap.getOrPut(bucketTime) { LinkedHashMap() }
            val shortName = r.sensorName.take(18)
            r.values.take(3).forEachIndexed { ch, v ->
                val colName = "${shortName}_v$ch"
                columns += colName
                cell[colName] = v
            }
        }
        return columns.toList() to bucketMap.entries.map { ResampledBucket(it.key, it.value) }
    }

    private fun formatTs(epochMs: Long, firstMs: Long?, format: TimestampFormat): String =
        when (format) {
            TimestampFormat.EPOCH_MS -> epochMs.toString()
            TimestampFormat.RELATIVE_SEC -> {
                val base = firstMs ?: epochMs
                String.format(Locale.US, "%.3f", (epochMs - base) / 1000.0)
            }
            TimestampFormat.ISO_8601 -> {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                sdf.format(Date(epochMs))
            }
        }

    private fun escapeHtml(raw: String): String =
        raw.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    private fun jsonStr(raw: String): String {
        val escaped = raw.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
        return "\"$escaped\""
    }
}
