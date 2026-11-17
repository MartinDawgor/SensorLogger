package com.salihov.sensorlogger

import com.salihov.sensorlogger.data.csv.CsvFormatter
import com.salihov.sensorlogger.data.csv.CsvLogParser
import com.salihov.sensorlogger.data.csv.CsvSessionManager
import com.salihov.sensorlogger.domain.model.CsvDelimiter
import com.salihov.sensorlogger.domain.model.SensorSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.charset.StandardCharsets

class CsvSessionManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `writes csv header and rows, loads log document, and deletes sessions safely`() {
        val sessionsDir = File(tempFolder.root, "sessions")
        val manager = CsvSessionManager(sessionsDir)

        manager.openWriter("sensors_20261117_230000.csv").use { writer ->
            writer.writeHeader()
            writer.append(
                SensorSample(
                    sensorId = "acc",
                    sensorName = "Accelerometer, Main",
                    sensorType = "android.sensor.accelerometer",
                    epochMillis = 1_700_000_000_000L,
                    elapsedNanos = 42L,
                    values = listOf(1.0f, 2.0f, 3.0f)
                )
            )
            writer.append(
                SensorSample(
                    sensorId = "light",
                    sensorName = "Light \"Pro\"",
                    sensorType = "android.sensor.light",
                    epochMillis = 1_700_000_001_500L,
                    elapsedNanos = 99L,
                    values = listOf(250.5f)
                )
            )
        }

        val sessions = manager.listSessions()
        assertEquals(1, sessions.size)
        assertEquals("sensors_20261117_230000.csv", sessions.first().fileName)
        assertTrue(sessions.first().sizeBytes > 0L)

        val content = manager.readSessionBytes("sensors_20261117_230000.csv")
            .toString(StandardCharsets.UTF_8)
            .lines()
            .filter { it.isNotBlank() }

        assertEquals(3, content.size)
        assertEquals(CsvFormatter.HEADER, content[0])

        val doc = manager.loadLogDocument("sensors_20261117_230000.csv")
        assertEquals(2, doc.totalDataRows)
        assertEquals(1_500L, doc.durationMillis)
        assertEquals(listOf("Accelerometer, Main", "Light \"Pro\""), doc.sensorNames)
        assertEquals(listOf(1.0f, 2.0f, 3.0f), doc.rows[0].values)
        assertEquals(listOf(250.5f), doc.rows[1].values)

        val parsed = CsvLogParser.parseCsvLine("1,2,type,\"Sensor, \"\"X\"\"\",1.5,,")
        assertEquals("Sensor, \"X\"", parsed[3])

        val resolved = manager.getSessionFile("../../etc/passwd")
        assertEquals(sessionsDir.canonicalPath, resolved.parentFile?.canonicalPath)

        assertTrue(manager.deleteSession("sensors_20261117_230000.csv"))
        assertFalse(manager.deleteSession("sensors_20261117_230000.csv"))
        assertTrue(manager.listSessions().isEmpty())
    }

    @Test
    fun `writes semicolon excel csv with UTF-8 BOM and parses back cleanly`() {
        val sessionsDir = File(tempFolder.root, "sessions_semi")
        val manager = CsvSessionManager(sessionsDir)

        manager.openWriter("excel.csv", CsvDelimiter.SEMICOLON).use { writer ->
            writer.writeHeader()
            writer.append(
                SensorSample(
                    sensorId = "acc",
                    sensorName = "Датчик; Тест",
                    sensorType = "android.sensor.accelerometer",
                    epochMillis = 1_700_000_000_000L,
                    elapsedNanos = 10L,
                    values = listOf(1.5f, -2.5f, 9.8f)
                )
            )
        }

        val bytes = manager.readSessionBytes("excel.csv")
        assertEquals(0xEF.toByte(), bytes[0])
        assertEquals(0xBB.toByte(), bytes[1])
        assertEquals(0xBF.toByte(), bytes[2])

        val doc = manager.loadLogDocument("excel.csv")
        assertEquals(1, doc.totalDataRows)
        assertEquals(listOf("Датчик; Тест"), doc.sensorNames)
        assertEquals(listOf(1.5f, -2.5f, 9.8f), doc.rows.first().values)
    }
}
