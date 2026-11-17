package com.salihov.sensorlogger

import com.salihov.sensorlogger.data.analysis.SessionAnalyzer
import com.salihov.sensorlogger.data.export.AnalyticalExportEngine
import com.salihov.sensorlogger.domain.model.CsvDelimiter
import com.salihov.sensorlogger.domain.model.CsvLogDocument
import com.salihov.sensorlogger.domain.model.CsvLogRow
import com.salihov.sensorlogger.domain.model.TimestampFormat
import com.salihov.sensorlogger.domain.model.VerdictSeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.ZipInputStream

class SessionAnalyzerAndExportTest {

    @Test
    fun `analyzer produces deviation verdicts for gyro drift, accel shock, and normal sensors`() {
        val doc = CsvLogDocument(
            fileName = "test_session.csv",
            sizeBytes = 1024L,
            totalDataRows = 7,
            isTruncated = false,
            firstTimestampMillis = 1_700_000_000_000L,
            lastTimestampMillis = 1_700_000_002_000L,
            sensorNames = listOf("Acc", "Gyro", "Старт #1"),
            rows = listOf(
                CsvLogRow(1, 1_700_000_000_000L, 100L, "android.sensor.accelerometer", "Acc", listOf(0f, 0f, 25f)),
                CsvLogRow(2, 1_700_000_000_500L, 200L, "android.sensor.accelerometer", "Acc", listOf(0f, 0f, 9.81f)),
                CsvLogRow(3, 1_700_000_000_600L, 300L, "android.sensor.gyroscope", "Gyro", listOf(0.04f, 0f, 0f)),
                CsvLogRow(4, 1_700_000_001_000L, 400L, "android.sensor.gyroscope", "Gyro", listOf(0.04f, 0f, 0f)),
                CsvLogRow(5, 1_700_000_001_500L, 500L, "user.marker", "Старт #1", listOf(1f)),
                CsvLogRow(6, 1_700_000_001_800L, 600L, "android.location.gps", "GPS", listOf(55.7558f, 37.6173f, 150f, 5f, 90f, 4f))
            ),
            rawLines = emptyList()
        )

        val report = SessionAnalyzer.analyze(doc)
        assertEquals(1, report.markers.size)
        assertEquals(3, report.verdicts.size)

        val accVerdict = report.verdicts.first { it.sensorName == "Acc" }
        assertEquals(VerdictSeverity.ALERT, accVerdict.severity)
        assertTrue(accVerdict.verdictTitle.contains("перегрузка"))

        val gyroVerdict = report.verdicts.first { it.sensorName == "Gyro" }
        assertEquals(VerdictSeverity.WARN, gyroVerdict.severity)
        assertTrue(gyroVerdict.verdictTitle.contains("дрейф"))

        val wideCsv = AnalyticalExportEngine.buildWideCsvReport(doc, report, CsvDelimiter.SEMICOLON, TimestampFormat.RELATIVE_SEC)
        assertTrue(wideCsv.contains("ОТЧЁТ АНАЛИЗА СЕССИИ"))
        assertTrue(wideCsv.contains("Старт #1"))
        assertTrue(wideCsv.contains("СИНХРОНИЗИРОВАННАЯ ТАБЛИЦА"))

        val html = AnalyticalExportEngine.buildHtmlReport(doc, report, TimestampFormat.ISO_8601)
        assertTrue(html.contains("Сводная таблица и вердикты отклонений"))
        assertTrue(html.contains("ОТКЛОНЕНИЕ"))

        val kml = AnalyticalExportEngine.buildKmlTrack(doc, report)
        assertTrue(kml.contains("37.617"))
        assertTrue(kml.contains("55.755"))

        val zipBytes = AnalyticalExportEngine.buildZipBundleBytes(doc, report, CsvDelimiter.COMMA, TimestampFormat.EPOCH_MS, null)
        val entries = mutableListOf<String>()
        ZipInputStream(zipBytes.inputStream()).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                entries += e.name
                e = zis.nextEntry
            }
        }
        assertTrue(entries.any { it.endsWith(".html") })
        assertTrue(entries.any { it.endsWith(".json") })
        assertTrue(entries.any { it.endsWith(".kml") })
    }
}
