package com.salihov.sensorlogger.data.recording

import com.salihov.sensorlogger.domain.model.CsvDelimiter
import com.salihov.sensorlogger.domain.model.SensorSample
import com.salihov.sensorlogger.domain.ports.CsvSessionStore
import com.salihov.sensorlogger.domain.ports.CsvSessionWriter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class RecordingSessionCoordinator(
    private val sessionStore: CsvSessionStore,
    private val ioDispatcher: CoroutineDispatcher
) {
    private var activeWriter: CsvSessionWriter? = null
    private var writerChannel: Channel<SensorSample>? = null
    private var writerJob: Job? = null

    private val samplesCounter = AtomicLong(0L)
    private val markersCounter = AtomicInteger(0)

    val sampleCount: Long
        get() = samplesCounter.get()

    fun start(
        fileName: String,
        delimiter: CsvDelimiter,
        scope: CoroutineScope
    ) {
        val writer = sessionStore.openWriter(fileName, delimiter)
        writer.writeHeader()
        writer.flush()

        val channel = Channel<SensorSample>(capacity = Channel.UNLIMITED)
        activeWriter = writer
        writerChannel = channel
        samplesCounter.set(0L)
        markersCounter.set(0)

        writerJob = scope.launch(ioDispatcher) {
            var unsaved = 0
            for (sample in channel) {
                writer.append(sample)
                samplesCounter.incrementAndGet()
                unsaved++
                if (unsaved >= FLUSH_EVERY_N_SAMPLES) {
                    writer.flush()
                    unsaved = 0
                }
            }
        }
    }

    fun writeBlackboxSnapshot(
        fileName: String,
        delimiter: CsvDelimiter,
        samples: List<SensorSample>
    ): Int {
        sessionStore.openWriter(fileName, delimiter).use { writer ->
            writer.writeHeader()
            samples.forEach { writer.append(it) }
            writer.flush()
        }
        return samples.size
    }

    fun enqueue(sample: SensorSample) {
        writerChannel?.trySend(sample)
    }

    fun nextMarkerIndex(): Int = markersCounter.incrementAndGet()

    fun flush() {
        runCatching { activeWriter?.flush() }
    }

    suspend fun stop(): Long {
        writerChannel?.close()
        writerJob?.join()
        writerJob = null
        writerChannel = null

        withContext(ioDispatcher) {
            closeSafely()
        }
        return samplesCounter.get()
    }

    fun closeSafely() {
        runCatching { activeWriter?.close() }
        activeWriter = null
    }

    fun dispose() {
        writerChannel?.close()
        writerJob?.cancel()
        writerJob = null
        writerChannel = null
        closeSafely()
    }

    companion object {
        private const val FLUSH_EVERY_N_SAMPLES = 50
    }
}
