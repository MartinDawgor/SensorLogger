package com.salihov.sensorlogger

import com.salihov.sensorlogger.data.sensor.SensorToolsMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SensorToolsMathTest {

    @Test
    fun `computes vector magnitude, g-force, and pitch-roll accurately`() {
        val restGravity = listOf(0f, 0f, 9.80665f)
        assertEquals(9.80665f, SensorToolsMath.vectorMagnitude(restGravity), 0.001f)
        assertEquals(1.0f, SensorToolsMath.gForce(restGravity), 0.001f)

        val flat = SensorToolsMath.pitchAndRollDegrees(restGravity)
        assertNotNull(flat)
        assertEquals(0f, flat!!.first, 0.1f)
        assertEquals(0f, flat.second, 0.1f)
    }

    @Test
    fun `computes cardinal directions from azimuth`() {
        assertEquals("С", SensorToolsMath.cardinalDirection(0f))
        assertEquals("В", SensorToolsMath.cardinalDirection(90f))
        assertEquals("Ю", SensorToolsMath.cardinalDirection(180f))
        assertEquals("З", SensorToolsMath.cardinalDirection(270f))
        assertEquals("С", SensorToolsMath.cardinalDirection(355f))
    }

    @Test
    fun `computes zero offsets for gyro and preserves 1g for accelerometer`() {
        val gyroSamples = List(10) { listOf(0.02f, -0.01f, 0.03f) }
        val gyroOffsets = SensorToolsMath.computeZeroOffsets(4, gyroSamples)
        assertEquals(0.02f, gyroOffsets[0], 0.0001f)
        assertEquals(-0.01f, gyroOffsets[1], 0.0001f)
        assertEquals(0.03f, gyroOffsets[2], 0.0001f)

        val calibratedGyro = SensorToolsMath.applyCalibrationOffsets(listOf(0.02f, -0.01f, 0.03f), gyroOffsets)
        assertEquals(0f, calibratedGyro[0], 0.0001f)
        assertEquals(0f, calibratedGyro[1], 0.0001f)
        assertEquals(0f, calibratedGyro[2], 0.0001f)

        val accelSamples = List(10) { listOf(0.15f, -0.10f, 9.95f) }
        val accelOffsets = SensorToolsMath.computeZeroOffsets(1, accelSamples)
        val calibratedAccel = SensorToolsMath.applyCalibrationOffsets(listOf(0.15f, -0.10f, 9.95f), accelOffsets)
        assertEquals(0f, calibratedAccel[0], 0.001f)
        assertEquals(0f, calibratedAccel[1], 0.001f)
        assertEquals(9.80665f, calibratedAccel[2], 0.001f)
    }
}
