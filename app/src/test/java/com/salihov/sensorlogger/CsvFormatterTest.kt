package com.salihov.sensorlogger

import com.salihov.sensorlogger.data.csv.CsvFormatter
import com.salihov.sensorlogger.domain.model.SensorSample
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class CsvFormatterTest {

    @Test
    fun `formatRow formats floats with dot separator even under Russian locale`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale("ru", "RU"))
            val sample = SensorSample(
                sensorId = "1:Acc",
                sensorName = "Accelerometer, Main",
                sensorType = "android.sensor.accelerometer",
                epochMillis = 1700000000000L,
                elapsedNanos = 123456789L,
                values = listOf(0.125f, -9.81f, 1.5f)
            )

            val row = CsvFormatter.formatRow(sample)

            assertEquals(
                "1700000000000,123456789,android.sensor.accelerometer,\"Accelerometer, Main\",0.125000,-9.810000,1.500000,,,",
                row
            )
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `escapeField escapes embedded quotes`() {
        assertEquals("\"Sensor \"\"Pro\"\"\"", CsvFormatter.escapeField("Sensor \"Pro\""))
        assertEquals("PlainSensor", CsvFormatter.escapeField("PlainSensor"))
    }
}
