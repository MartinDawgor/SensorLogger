package com.salihov.sensorlogger.domain.ports

import com.salihov.sensorlogger.domain.model.AudioLevelSnapshot
import com.salihov.sensorlogger.domain.model.BleBeaconInfo
import com.salihov.sensorlogger.domain.model.CsvDelimiter
import com.salihov.sensorlogger.domain.model.CsvLogDocument
import com.salihov.sensorlogger.domain.model.CsvSessionInfo
import com.salihov.sensorlogger.domain.model.DeviceDiagnostics
import com.salihov.sensorlogger.domain.model.ExtraSource
import com.salihov.sensorlogger.domain.model.GpsSnapshot
import com.salihov.sensorlogger.domain.model.SamplingRate
import com.salihov.sensorlogger.domain.model.SensorCalibration
import com.salihov.sensorlogger.domain.model.SensorDescriptor
import com.salihov.sensorlogger.domain.model.SensorSample
import com.salihov.sensorlogger.domain.model.SmartRule
import com.salihov.sensorlogger.domain.model.StreamingConfig
import com.salihov.sensorlogger.domain.model.TimestampFormat
import com.salihov.sensorlogger.domain.model.WebDashboardState
import kotlinx.coroutines.flow.Flow
import java.io.Closeable

interface SensorCatalog {
    fun discoverAll(): List<SensorDescriptor>
}

interface SensorStream {
    fun observe(
        sensors: List<SensorDescriptor>,
        rate: SamplingRate,
        rateOverrides: Map<String, SamplingRate> = emptyMap()
    ): Flow<SensorSample>
}

interface CsvSessionWriter : Closeable {
    fun writeHeader()
    fun append(sample: SensorSample)
    fun flush()
}

interface CsvSessionStore {
    fun openWriter(fileName: String, delimiter: CsvDelimiter = CsvDelimiter.COMMA): CsvSessionWriter
    fun listSessions(): List<CsvSessionInfo>
    fun deleteSession(fileName: String): Boolean
    fun renameSession(oldName: String, newName: String): Boolean = false
    fun readSessionBytes(fileName: String): ByteArray
    fun loadLogDocument(fileName: String, maxRows: Int = 5_000): CsvLogDocument
}

interface PermissionChecker {
    fun isGranted(permission: String): Boolean
}

interface AppPreferencesStore {
    fun getCsvDelimiter(): CsvDelimiter = CsvDelimiter.SEMICOLON
    fun setCsvDelimiter(delimiter: CsvDelimiter) {}
    fun getTimestampFormat(): TimestampFormat = TimestampFormat.ISO_8601
    fun setTimestampFormat(format: TimestampFormat) {}
    fun getCalibrations(): Map<String, SensorCalibration> = emptyMap()
    fun saveCalibration(calibration: SensorCalibration) {}
    fun removeCalibration(sensorId: String) {}
    fun getStreamingConfig(): StreamingConfig = StreamingConfig()
    fun saveStreamingConfig(config: StreamingConfig) {}
    fun getRules(): List<SmartRule>? = null
    fun saveRules(rules: List<SmartRule>) {}
    fun getAnnotationPresets(): List<String>? = null
    fun saveAnnotationPresets(presets: List<String>) {}
    fun getRateOverrides(): Map<String, SamplingRate> = emptyMap()
    fun saveRateOverrides(overrides: Map<String, SamplingRate>) {}
}

interface DeviceDiagnosticsProvider {
    fun snapshot(): DeviceDiagnostics
}

interface ExtraTelemetryGateway {
    fun observeExtraSamples(sources: Set<ExtraSource>): Flow<SensorSample>
    fun observeGps(): Flow<GpsSnapshot>
    fun observeAudioLevel(): Flow<AudioLevelSnapshot>
    fun observeBleBeacons(): Flow<List<BleBeaconInfo>>
}

interface TelemetryStreamer {
    suspend fun sendBatch(config: StreamingConfig, samples: List<SensorSample>): Result<Unit>
}

interface WebDashboardServer {
    val state: Flow<WebDashboardState>
    fun start(liveProvider: () -> Map<String, List<Float>>)
    fun stop()
}
