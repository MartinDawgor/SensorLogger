package com.salihov.sensorlogger.domain.usecase

import com.salihov.sensorlogger.data.analysis.FftAnalyzer
import com.salihov.sensorlogger.data.sensor.SensorToolsMath
import com.salihov.sensorlogger.domain.model.FftSpectrumSnapshot
import com.salihov.sensorlogger.domain.model.SensorAccuracy
import com.salihov.sensorlogger.domain.model.SensorSample
import com.salihov.sensorlogger.domain.model.SensorTelemetrySnapshot
import java.util.concurrent.ConcurrentHashMap

class TelemetryTracker(
    private val inspectorHistoryLimit: Int = 80,
    private val monitorHistoryLimit: Int = 45,
    private val fftWindowSize: Int = 64
) {
    private class MutableTelemetryState {
        var values: List<Float> = emptyList()
        var accuracy: SensorAccuracy = SensorAccuracy.UNKNOWN
        var lastTimestampNanos: Long = 0L
        var emaHz: Float = 0f
        var sampleCount: Long = 0L
        val minValues = mutableListOf<Float>()
        val maxValues = mutableListOf<Float>()
    }

    private val latestReadings = ConcurrentHashMap<String, List<Float>>()
    private val telemetryMap = ConcurrentHashMap<String, MutableTelemetryState>()
    private val monitorBuffers = ConcurrentHashMap<String, ArrayDeque<SensorSample>>()
    private val inspectedBuffer = ArrayDeque<SensorSample>(inspectorHistoryLimit)
    private val accelMagWindow = ArrayDeque<Float>(fftWindowSize)
    private var accelSampleRateHz: Float = 20f
    private val lock = Any()

    @Volatile
    var peakGForce: Float = 0f
        private set

    fun recordSample(
        sample: SensorSample,
        isAccelerometer: Boolean,
        inspectedId: String?,
        pinnedIds: Set<String>
    ) {
        latestReadings[sample.sensorId] = sample.values
        val state = telemetryMap.getOrPut(sample.sensorId) { MutableTelemetryState() }
        synchronized(state) {
            state.values = sample.values
            if (sample.accuracy >= 0) {
                state.accuracy = SensorAccuracy.fromAndroid(sample.accuracy)
            }
            if (state.lastTimestampNanos > 0L && sample.elapsedNanos > state.lastTimestampNanos) {
                val dtSec = (sample.elapsedNanos - state.lastTimestampNanos) / 1_000_000_000.0
                if (dtSec in 0.0005..5.0) {
                    val instantHz = (1.0 / dtSec).toFloat()
                    state.emaHz = if (state.emaHz <= 0f) instantHz else state.emaHz * 0.8f + instantHz * 0.2f
                }
            }
            state.lastTimestampNanos = sample.elapsedNanos
            state.sampleCount++

            sample.values.forEachIndexed { idx, value ->
                if (!value.isFinite()) return@forEachIndexed
                if (idx >= state.minValues.size) {
                    state.minValues.add(value)
                    state.maxValues.add(value)
                } else {
                    if (value < state.minValues[idx]) state.minValues[idx] = value
                    if (value > state.maxValues[idx]) state.maxValues[idx] = value
                }
            }
            if (isAccelerometer && state.emaHz > 1f) {
                accelSampleRateHz = state.emaHz
            }
        }

        if (isAccelerometer) {
            val mag = SensorToolsMath.vectorMagnitude(sample.values)
            val g = mag / 9.80665f
            if (g > peakGForce) peakGForce = g
            synchronized(lock) {
                if (accelMagWindow.size >= fftWindowSize) accelMagWindow.removeFirst()
                accelMagWindow.addLast(mag)
            }
        }

        if (sample.sensorId == inspectedId) {
            synchronized(lock) {
                if (inspectedBuffer.size >= inspectorHistoryLimit) inspectedBuffer.removeFirst()
                inspectedBuffer.addLast(sample)
            }
        }
        if (sample.sensorId in pinnedIds) {
            synchronized(lock) {
                val queue = monitorBuffers.getOrPut(sample.sensorId) { ArrayDeque(monitorHistoryLimit) }
                if (queue.size >= monitorHistoryLimit) queue.removeFirst()
                queue.addLast(sample)
            }
        }
    }

    fun snapshotReadings(): Map<String, List<Float>> = latestReadings.toMap()

    fun snapshotTelemetry(): Map<String, SensorTelemetrySnapshot> =
        telemetryMap.mapValues { (_, st) ->
            synchronized(st) {
                SensorTelemetrySnapshot(
                    values = st.values,
                    accuracy = st.accuracy,
                    measuredHz = st.emaHz,
                    minValues = st.minValues.toList(),
                    maxValues = st.maxValues.toList(),
                    sampleCount = st.sampleCount
                )
            }
        }

    fun snapshotInspectedHistory(): List<SensorSample> =
        synchronized(lock) { inspectedBuffer.toList() }

    fun snapshotMonitorHistories(pinnedIds: Set<String>): Map<String, List<SensorSample>> =
        synchronized(lock) {
            pinnedIds.associateWith { id -> monitorBuffers[id]?.toList().orEmpty() }
        }

    fun computeAccelFft(): FftSpectrumSnapshot {
        val windowCopy = synchronized(lock) {
            if (accelMagWindow.size < 16) return FftSpectrumSnapshot()
            FloatArray(accelMagWindow.size).also { arr ->
                var idx = 0
                for (v in accelMagWindow) arr[idx++] = v
            }
        }
        return FftAnalyzer.computeMagnitudeSpectrum(windowCopy, accelSampleRateHz)
    }

    fun clearSensor(sensorId: String) {
        telemetryMap.remove(sensorId)
        monitorBuffers.remove(sensorId)
        synchronized(lock) { inspectedBuffer.clear() }
    }

    fun clearInspector() {
        synchronized(lock) { inspectedBuffer.clear() }
    }

    fun removeMonitorBuffer(sensorId: String) {
        monitorBuffers.remove(sensorId)
    }

    fun clearReadings() {
        latestReadings.clear()
    }

    fun resetPeakGForce() {
        peakGForce = 0f
    }

    fun resetAllStats() {
        telemetryMap.clear()
        monitorBuffers.clear()
        synchronized(lock) {
            inspectedBuffer.clear()
            accelMagWindow.clear()
        }
        peakGForce = 0f
    }
}
