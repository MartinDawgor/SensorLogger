package com.salihov.sensorlogger.data.sensor

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sign
import kotlin.math.sqrt

object SensorToolsMath {

    private const val STANDARD_GRAVITY = 9.80665f

    fun computeZeroOffsets(sensorType: Int, samples: List<List<Float>>): List<Float> {
        if (samples.isEmpty()) return emptyList()
        val channelCount = samples.maxOf { it.size }
        if (channelCount == 0) return emptyList()

        val means = (0 until channelCount).map { ch ->
            val vals = samples.mapNotNull { it.getOrNull(ch) }.filter { it.isFinite() }
            if (vals.isEmpty()) 0f else (vals.sumOf { it.toDouble() } / vals.size).toFloat()
        }

        // For standard accelerometer, preserve 1g on the vertical resting axis.
        if (sensorType == 1 && means.size >= 3) {
            val dominantIdx = (0..2).maxByOrNull { abs(means[it]) } ?: 2
            val dominantVal = means[dominantIdx]
            if (abs(dominantVal) >= 7.5f) {
                val targetGravity = sign(dominantVal) * STANDARD_GRAVITY
                return means.mapIndexed { idx, mean ->
                    if (idx == dominantIdx) mean - targetGravity else mean
                }
            }
        }
        return means
    }

    fun applyCalibrationOffsets(values: List<Float>, offsets: List<Float>): List<Float> {
        if (offsets.isEmpty()) return values
        return values.mapIndexed { idx, v ->
            v - (offsets.getOrNull(idx) ?: 0f)
        }
    }

    fun vectorMagnitude(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        var sumSq = 0.0
        val count = minOf(3, values.size)
        for (i in 0 until count) {
            val v = values[i].toDouble()
            if (v.isFinite()) sumSq += v * v
        }
        return sqrt(sumSq).toFloat()
    }

    fun gForce(accelValues: List<Float>): Float =
        vectorMagnitude(accelValues) / STANDARD_GRAVITY

    /**
     * Returns pair (pitchDegrees, rollDegrees) from 3-axis accelerometer/gravity vector.
     */
    fun pitchAndRollDegrees(accel: List<Float>): Pair<Float, Float>? {
        if (accel.size < 3) return null
        val ax = accel[0].toDouble()
        val ay = accel[1].toDouble()
        val az = accel[2].toDouble()
        if (!ax.isFinite() || !ay.isFinite() || !az.isFinite()) return null

        val pitch = Math.toDegrees(atan2(-ay, sqrt(ax * ax + az * az))).toFloat()
        val roll = Math.toDegrees(atan2(ax, sqrt(ay * ay + az * az))).toFloat()
        return pitch to roll
    }

    /**
     * Computes tilt-compensated compass azimuth in degrees [0, 360) from gravity/accelerometer and magnetometer.
     */
    fun compassAzimuthDegrees(accel: List<Float>, magnet: List<Float>): Float? {
        if (accel.size < 3 || magnet.size < 3) return null
        val ax = accel[0].toDouble()
        val ay = accel[1].toDouble()
        val az = accel[2].toDouble()
        val mx = magnet[0].toDouble()
        val my = magnet[1].toDouble()
        val mz = magnet[2].toDouble()

        val normA = sqrt(ax * ax + ay * ay + az * az)
        if (normA < 1e-3) return null
        val gx = ax / normA
        val gy = ay / normA
        val gz = az / normA

        var ex = my * gz - mz * gy
        var ey = mz * gx - mx * gz
        var ez = mx * gy - my * gx
        val normE = sqrt(ex * ex + ey * ey + ez * ez)
        if (normE < 1e-3) return null
        ex /= normE
        ey /= normE
        ez /= normE

        val nx = gy * ez - gz * ey
        val ny = gz * ex - gx * ez
        val nz = gx * ey - gy * ex

        val azimuthRad = atan2(ey, ny)
        var deg = Math.toDegrees(azimuthRad).toFloat()
        if (deg < 0f) deg += 360f
        return deg
    }

    fun cardinalDirection(degrees: Float): String {
        val norm = ((degrees % 360f) + 360f) % 360f
        return when {
            norm < 22.5f || norm >= 337.5f -> "С"
            norm < 67.5f -> "СВ"
            norm < 112.5f -> "В"
            norm < 157.5f -> "ЮВ"
            norm < 202.5f -> "Ю"
            norm < 247.5f -> "ЮЗ"
            norm < 292.5f -> "З"
            else -> "СЗ"
        }
    }
}
