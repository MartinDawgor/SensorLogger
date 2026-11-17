package com.salihov.sensorlogger.domain.usecase

import com.salihov.sensorlogger.domain.model.SensorSample

class BlackboxRingBuffer(
    private val windowMillis: Long = 30_000L,
    private val maxSamples: Int = 6_000
) {
    private val buffer = ArrayDeque<SensorSample>()
    private val lock = Any()

    fun push(sample: SensorSample) {
        synchronized(lock) {
            if (buffer.size >= maxSamples) {
                buffer.removeFirst()
            }
            buffer.addLast(sample)
            val cutoff = sample.epochMillis - windowMillis
            while (buffer.isNotEmpty() && buffer.first().epochMillis < cutoff) {
                buffer.removeFirst()
            }
        }
    }

    fun size(): Int = synchronized(lock) { buffer.size }

    fun snapshotAndClear(): List<SensorSample> = synchronized(lock) {
        val copy = buffer.toList()
        buffer.clear()
        copy
    }

    fun clear() {
        synchronized(lock) { buffer.clear() }
    }
}
