package com.salihov.sensorlogger

import com.salihov.sensorlogger.data.sensor.SensorMetadataCatalog
import com.salihov.sensorlogger.domain.model.SensorCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorMetadataCatalogTest {

    @Test
    fun `resolves standard sensor types with units and explanations`() {
        val acc = SensorMetadataCatalog.resolve(1, "android.sensor.accelerometer", "BMI160 Accelerometer")
        assertEquals("Акселерометр", acc.friendlyName)
        assertEquals(SensorCategory.MOTION, acc.category)
        assertEquals("м/с²", acc.unit)
        assertEquals(listOf("X", "Y", "Z"), acc.axisLabels)
        assertTrue(acc.description.isNotBlank())

        val light = SensorMetadataCatalog.resolve(5, "android.sensor.light", "ALS", isWakeUp = true)
        assertEquals("Датчик освещённости (Wake-up)", light.friendlyName)
        assertEquals(SensorCategory.ENVIRONMENT, light.category)
        assertEquals("лк", light.unit)

        val aod = SensorMetadataCatalog.resolve(33171029, "xiaomi.sensor.aod", "Aod Non-wakeup")
        assertEquals("Датчик Always-On Display (AOD)", aod.friendlyName)

        val oem = SensorMetadataCatalog.resolve(65537, "com.vendor.custom_gyro", "Vendor Gyro")
        assertEquals(SensorCategory.MOTION, oem.category)
        assertEquals("рад/с", oem.unit)
    }
}
