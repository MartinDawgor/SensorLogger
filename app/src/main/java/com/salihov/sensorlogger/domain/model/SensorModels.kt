package com.salihov.sensorlogger.domain.model

import java.util.Locale

enum class CsvDelimiter(
    val symbol: Char,
    val label: String,
    val hint: String
) {
    COMMA(',', "Запятая (,)", "Стандартный RFC-4180 (Python, MATLAB, R)"),
    SEMICOLON(';', "Точка с запятой (;)", "Для русской локали Microsoft Excel (+ UTF-8 BOM)")
}

enum class TimestampFormat(val label: String) {
    EPOCH_MS("Unix ms"),
    ISO_8601("ISO-8601"),
    RELATIVE_SEC("От старта (сек)")
}

enum class ExportFormat(
    val title: String,
    val extension: String,
    val mimeType: String,
    val subtitle: String
) {
    HTML_REPORT(
        title = "HTML-отчёт с вердиктами",
        extension = "html",
        mimeType = "text/html",
        subtitle = "Сводная таблица отклонений и вердиктов"
    ),
    PDF_PRINT(
        title = "PDF-отчёт (Печать)",
        extension = "pdf",
        mimeType = "application/pdf",
        subtitle = "Сформировать PDF через системную печать Android"
    ),
    WIDE_CSV(
        title = "Таблица Excel (Wide CSV)",
        extension = "csv",
        mimeType = "text/csv",
        subtitle = "Сводка вердиктов + синхронизированные столбцы"
    ),
    MARKDOWN_REPORT(
        title = "Markdown-таблица (.md)",
        extension = "md",
        mimeType = "text/markdown",
        subtitle = "Табличный отчёт с вердиктами"
    ),
    JSON(
        title = "Структурированный JSON",
        extension = "json",
        mimeType = "application/json",
        subtitle = "Метаданные, вердикты, метки и замеры"
    ),
    SQLITE(
        title = "База данных SQLite (.db)",
        extension = "db",
        mimeType = "application/octet-stream",
        subtitle = "Таблицы session_meta, sensor_verdicts, markers, samples"
    ),
    KML(
        title = "GPS-трек KML (Google Earth)",
        extension = "kml",
        mimeType = "application/vnd.google-earth.kml+xml",
        subtitle = "Маршрут и метки по координатам GPS"
    ),
    ZIP_BUNDLE(
        title = "Пакет ZIP (CSV + HTML + JSON)",
        extension = "zip",
        mimeType = "application/zip",
        subtitle = "Архив со всеми форматами"
    ),
    RAW_CSV(
        title = "Исходный CSV (сырой)",
        extension = "csv",
        mimeType = "text/csv",
        subtitle = "Построчный сырой лог"
    )
}

enum class AppSection(val title: String, val subtitle: String) {
    HOME("AIDA SensorLogger", "Диагностика и логгер"),
    SENSORS("Датчики", "Каталог HAL, выбор и калибровка"),
    MONITOR("Живой монитор", "Мульти-осциллограф и Гц"),
    TOOLS("Инженерные приборы", "G-метр, БПФ-спектр, уровень, компас, дБ"),
    GPS("GPS / GNSS Трекер", "Координаты, скорость, высота, KML"),
    BLE("Bluetooth LE Сканер", "Маяки, RSSI, MAC и реклама"),
    RULES("Правила и Аннотации", "Триггеры, чёрный ящик и метки"),
    STREAMING("Стрим и Web-панель", "HTTP / MQTT / Wi-Fi дашборд"),
    DEVICE_SYSTEM("Система и CPU", "Модель, SoC, архитектура, частоты"),
    DEVICE_BATTERY("Батарея и Термо", "Питание, вольтаж, термозоны"),
    DEVICE_MEMORY("Память и Экран", "RAM, накопитель, дисплей"),
    DEVICE_NETWORK("Сеть и Система", "Wi-Fi, сигнал, яркость, HAL"),
    LOGS("Логи и Отчёты", "Таблицы, вердикты, A/B-сравнение и экспорт")
}

enum class SensorCategory(val label: String) {
    MOTION("Движение"),
    ENVIRONMENT("Среда"),
    POSITION("Позиция"),
    BIOMETRIC("Биометрия"),
    OTHER("Прочие")
}

enum class SensorAccuracy(val code: Int, val shortLabel: String, val fullLabel: String) {
    UNRELIABLE(0, "UNREL", "Ненадёжно (нужна калибровка)"),
    LOW(1, "LOW", "Низкая точность"),
    MEDIUM(2, "MED", "Средняя точность"),
    HIGH(3, "HIGH", "Высокая точность"),
    UNKNOWN(-1, "N/A", "Нет данных");

    companion object {
        fun fromAndroid(code: Int): SensorAccuracy =
            entries.find { it.code == code } ?: UNKNOWN
    }
}

enum class SamplingRate(
    val label: String,
    val shortHz: String,
    val rateBadge: String,
    val userHint: String,
    val samplingPeriodUs: Int
) {
    LOW(
        label = "Экономная",
        shortHz = "~5 Гц",
        rateBadge = "~5 Гц (5/с)",
        userHint = "Медленная запись: свет, давление, температура.",
        samplingPeriodUs = 200_000
    ),
    MEDIUM(
        label = "Стандарт",
        shortHz = "~20 Гц",
        rateBadge = "~20 Гц (20/с)",
        userHint = "Баланс для ходьбы, наклонов и повседневных замеров.",
        samplingPeriodUs = 50_000
    ),
    HIGH(
        label = "Точная",
        shortHz = "~50 Гц",
        rateBadge = "~50 Гц (50/с)",
        userHint = "Детальная запись для жестов и динамики.",
        samplingPeriodUs = 20_000
    ),
    FASTEST(
        label = "Макс.",
        shortHz = "~100 Гц",
        rateBadge = "~100+ Гц (макс.)",
        userHint = "Максимальная частота для вибраций, ударов и БПФ-спектра.",
        samplingPeriodUs = 10_000
    )
}

enum class RecordingScenario(
    val title: String,
    val subtitle: String,
    val rate: SamplingRate,
    val sensorTypes: Set<Int>,
    val enableGps: Boolean,
    val filePrefix: String
) {
    CAR_DRIVE(
        title = "Поездка в авто",
        subtitle = "Акселерометр + Гироскоп + GPS (20 Гц)",
        rate = SamplingRate.MEDIUM,
        sensorTypes = setOf(1, 4, 10, 11),
        enableGps = true,
        filePrefix = "drive"
    ),
    VIBRATION_TEST(
        title = "Вибродиагностика",
        subtitle = "Акселерометр + Лин. ускорение (100 Гц)",
        rate = SamplingRate.FASTEST,
        sensorTypes = setOf(1, 10),
        enableGps = false,
        filePrefix = "vibro"
    ),
    WALKING(
        title = "Пешая прогулка",
        subtitle = "Шаги + Движение + Барометр (20 Гц)",
        rate = SamplingRate.MEDIUM,
        sensorTypes = setOf(1, 5, 6, 18, 19),
        enableGps = false,
        filePrefix = "walk"
    )
}

enum class ExtraSource(val label: String, val sensorType: String, val unit: String) {
    GPS("GPS / GNSS", "android.location.gps", "° / м/с"),
    AUDIO_DB("Шумомер (микрофон)", "android.media.sound_level", "dBFS"),
    BLE_SCAN("BLE маяки", "android.bluetooth.ble", "дБм"),
    SYSTEM_PROPS("Батарея / Яркость / Сеть", "device.system.state", "")
}

enum class LogSortOrder(val label: String) {
    NEWEST("Сначала новые"),
    OLDEST("Сначала старые"),
    LARGEST("По размеру"),
    NAME("По имени")
}

enum class VerdictSeverity(val label: String) {
    OK("НОРМА"),
    INFO("ИНФО"),
    WARN("ВНИМАНИЕ"),
    ALERT("ОТКЛОНЕНИЕ")
}

data class SensorChannelStats(
    val channelLabel: String,
    val min: Float,
    val max: Float,
    val mean: Float,
    val stdDev: Float
)

data class SensorVerdict(
    val sensorName: String,
    val sensorType: String,
    val sampleCount: Int,
    val effectiveHz: Float,
    val severity: VerdictSeverity,
    val verdictTitle: String,
    val verdictDetails: String,
    val channelStats: List<SensorChannelStats>
)

data class SessionAnalysisReport(
    val fileName: String,
    val totalDataRows: Int,
    val durationMillis: Long,
    val firstTimestampMillis: Long?,
    val lastTimestampMillis: Long?,
    val verdicts: List<SensorVerdict>,
    val markers: List<CsvLogRow>
)

data class SensorComparisonRow(
    val sensorName: String,
    val meanA: Float?,
    val meanB: Float?,
    val stdA: Float?,
    val stdB: Float?,
    val verdictA: String,
    val verdictB: String
)

data class SessionComparisonReport(
    val fileA: String,
    val fileB: String,
    val rowsA: Int,
    val rowsB: Int,
    val durationSecA: Float,
    val durationSecB: Float,
    val comparisons: List<SensorComparisonRow>,
    val seriesA: List<List<Float>>,
    val seriesB: List<List<Float>>,
    val primarySensorName: String?
)

data class FftSpectrumSnapshot(
    val dominantHz: Float = 0f,
    val peakAmplitude: Float = 0f,
    val bins: List<Float> = emptyList()
)

data class SensorDescriptor(
    val id: String,
    val type: Int,
    val stringType: String,
    val name: String,
    val friendlyName: String = "",
    val vendor: String,
    val isTriggerOnly: Boolean,
    val isWakeUp: Boolean,
    val requiredPermission: String?,
    val category: SensorCategory = SensorCategory.OTHER,
    val description: String = "Системный или аппаратный датчик устройства.",
    val unit: String = "",
    val axisLabels: List<String> = emptyList(),
    val resolution: Float = 0f,
    val maxRange: Float = 0f,
    val powerMa: Float = 0f,
    val minDelayUs: Int = 0
) {
    val displayName: String
        get() = friendlyName.ifBlank { name }

    val maxFrequencyHz: Float?
        get() = if (minDelayUs > 0) 1_000_000f / minDelayUs else null

    val recommendedChartMinSpan: Float
        get() = when (type) {
            1, 9, 10, 35 -> 2.0f
            4, 16, 37 -> 1.0f
            2, 14 -> 20.0f
            5 -> 10.0f
            6 -> 2.0f
            11, 15, 20 -> 0.5f
            else -> 1.0f
        }

    fun effectiveSamplingPeriodUs(defaultRate: SamplingRate, overrides: Map<String, SamplingRate>): Int =
        (overrides[id] ?: defaultRate).samplingPeriodUs

    fun channelLabel(index: Int): String =
        axisLabels.getOrNull(index) ?: "v$index"

    fun formatValueWithUnit(value: Float): String {
        val formatted = String.format(Locale.US, "%.3f", value)
        return if (unit.isNotBlank()) "$formatted $unit" else formatted
    }
}

data class SensorSample(
    val sensorId: String,
    val sensorName: String,
    val sensorType: String,
    val epochMillis: Long,
    val elapsedNanos: Long,
    val values: List<Float>,
    val accuracy: Int = -1
)

data class SensorTelemetrySnapshot(
    val values: List<Float>,
    val accuracy: SensorAccuracy = SensorAccuracy.UNKNOWN,
    val measuredHz: Float = 0f,
    val minValues: List<Float> = emptyList(),
    val maxValues: List<Float> = emptyList(),
    val sampleCount: Long = 0L
)

data class SensorCalibration(
    val sensorId: String,
    val offsets: List<Float>,
    val calibratedAtMillis: Long
)

data class CpuCoreInfo(
    val coreIndex: Int,
    val currentFreqKhz: Long?,
    val maxFreqKhz: Long?
)

data class ThermalZoneInfo(
    val name: String,
    val tempCelsius: Float
)

data class DeviceDiagnostics(
    val manufacturer: String = "",
    val model: String = "",
    val deviceCode: String = "",
    val board: String = "",
    val hardware: String = "",
    val socModel: String = "",
    val androidVersion: String = "",
    val apiLevel: Int = 0,
    val securityPatch: String = "",
    val buildId: String = "",
    val abis: List<String> = emptyList(),
    val cpuCoresCount: Int = 0,
    val cpuCores: List<CpuCoreInfo> = emptyList(),
    val totalRamBytes: Long = 0L,
    val availableRamBytes: Long = 0L,
    val totalStorageBytes: Long = 0L,
    val freeStorageBytes: Long = 0L,
    val displayResolution: String = "",
    val displayDensityDpi: Int = 0,
    val displayRefreshHz: Float = 0f,
    val batteryLevelPct: Int = -1,
    val batteryTempC: Float = 0f,
    val batteryVoltageMv: Int = 0,
    val batteryStatus: String = "",
    val batteryHealth: String = "",
    val batteryPlugged: String = "",
    val batteryTechnology: String = "",
    val screenBrightness: Int = -1,
    val networkType: String = "—",
    val wifiSsid: String = "—",
    val wifiRssiDbm: Int? = null,
    val wifiLinkSpeedMbps: Int? = null,
    val thermalZones: List<ThermalZoneInfo> = emptyList()
)

data class GpsSnapshot(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitudeMeters: Double = 0.0,
    val speedMps: Float = 0f,
    val bearingDeg: Float = 0f,
    val accuracyMeters: Float = 0f,
    val provider: String = "",
    val updatedAtMillis: Long = 0L
)

data class AudioLevelSnapshot(
    val dbfs: Float = -90f,
    val approxSplDb: Float = 0f,
    val peakDbfs: Float = -90f,
    val updatedAtMillis: Long = 0L
)

data class BleBeaconInfo(
    val address: String,
    val name: String,
    val rssiDbm: Int,
    val lastSeenMillis: Long,
    val payloadHex: String = ""
)

enum class RuleConditionType(val label: String, val unit: String) {
    G_FORCE_ABOVE("Перегрузка (G) выше", "g"),
    LIGHT_BELOW("Освещённость ниже", "лк"),
    SOUND_ABOVE("Шум выше", "дБ"),
    AUTO_STOP_SECONDS("Автостоп записи через", "сек")
}

enum class RuleActionType(val label: String) {
    INSERT_MARKER("Поставить метку"),
    START_RECORDING("Начать запись"),
    STOP_RECORDING("Остановить запись"),
    FLUSH_BLACKBOX("Сохранить чёрный ящик")
}

data class SmartRule(
    val id: String,
    val name: String,
    val condition: RuleConditionType,
    val threshold: Float,
    val action: RuleActionType,
    val enabled: Boolean = true,
    val cooldownSec: Int = 5
)

enum class StreamingProtocol(val label: String) {
    HTTP_POST("HTTP POST (JSON)"),
    MQTT_TCP("MQTT 3.1.1 (TCP)")
}

data class StreamingConfig(
    val enabled: Boolean = false,
    val protocol: StreamingProtocol = StreamingProtocol.HTTP_POST,
    val httpUrl: String = "http://192.168.1.100:8080/ingest",
    val mqttHost: String = "192.168.1.100",
    val mqttPort: Int = 1883,
    val mqttTopic: String = "sensorlogger/telemetry"
)

data class StreamingStats(
    val sentBatches: Long = 0L,
    val sentSamples: Long = 0L,
    val failedBatches: Long = 0L,
    val lastError: String? = null
)

data class WebDashboardState(
    val running: Boolean = false,
    val url: String? = null,
    val requestsServed: Long = 0L
)

data class CsvSessionInfo(
    val fileName: String,
    val sizeBytes: Long,
    val createdAtMillis: Long
)

data class CsvLogRow(
    val rowNumber: Int,
    val epochMillis: Long,
    val elapsedNanos: Long,
    val sensorType: String,
    val sensorName: String,
    val values: List<Float>
)

data class CsvLogDocument(
    val fileName: String,
    val sizeBytes: Long,
    val totalDataRows: Int,
    val isTruncated: Boolean,
    val firstTimestampMillis: Long?,
    val lastTimestampMillis: Long?,
    val sensorNames: List<String>,
    val rows: List<CsvLogRow>,
    val rawLines: List<String>
) {
    val durationMillis: Long
        get() = if (firstTimestampMillis != null && lastTimestampMillis != null && lastTimestampMillis >= firstTimestampMillis) {
            lastTimestampMillis - firstTimestampMillis
        } else {
            0L
        }
}

sealed interface RecordingState {
    data object Idle : RecordingState

    data class Recording(
        val sessionFileName: String,
        val sampleCount: Long,
        val startedAtMillis: Long,
        val markerCount: Int = 0
    ) : RecordingState
}

data class SensorLoggerUiState(
    val currentSection: AppSection = AppSection.HOME,
    val sensors: List<SensorDescriptor> = emptyList(),
    val selectedSensorIds: Set<String> = emptySet(),
    val pinnedSensorIds: Set<String> = emptySet(),
    val rateOverrides: Map<String, SamplingRate> = emptyMap(),
    val enabledExtraSources: Set<ExtraSource> = emptySet(),
    val liveReadings: Map<String, List<Float>> = emptyMap(),
    val liveTelemetry: Map<String, SensorTelemetrySnapshot> = emptyMap(),
    val monitorHistories: Map<String, List<SensorSample>> = emptyMap(),
    val samplingRate: SamplingRate = SamplingRate.MEDIUM,
    val csvDelimiter: CsvDelimiter = CsvDelimiter.SEMICOLON,
    val timestampFormat: TimestampFormat = TimestampFormat.ISO_8601,
    val sessionNamePrefix: String = "sensors",
    val recordingState: RecordingState = RecordingState.Idle,
    val sessions: List<CsvSessionInfo> = emptyList(),
    val selectedLogFileNames: Set<String> = emptySet(),
    val logSearchQuery: String = "",
    val logSortOrder: LogSortOrder = LogSortOrder.NEWEST,
    val missingPermissions: Set<String> = emptySet(),
    val searchQuery: String = "",
    val selectedCategory: SensorCategory? = null,
    val inspectedSensorId: String? = null,
    val inspectedHistory: List<SensorSample> = emptyList(),
    val openedLogDocument: CsvLogDocument? = null,
    val openedLogReport: SessionAnalysisReport? = null,
    val comparisonReport: SessionComparisonReport? = null,
    val isLoadingLog: Boolean = false,
    val peakGForce: Float = 0f,
    val fftSpectrum: FftSpectrumSnapshot = FftSpectrumSnapshot(),
    val deviceDiagnostics: DeviceDiagnostics = DeviceDiagnostics(),
    val calibrations: Map<String, SensorCalibration> = emptyMap(),
    val applyCalibration: Boolean = true,
    val calibratingSensorId: String? = null,
    val calibrationProgress: Float = 0f,
    val inclinometerZeroPitch: Float = 0f,
    val inclinometerZeroRoll: Float = 0f,
    val gpsSnapshot: GpsSnapshot? = null,
    val audioSnapshot: AudioLevelSnapshot? = null,
    val bleBeacons: List<BleBeaconInfo> = emptyList(),
    val isBleScanning: Boolean = false,
    val annotationPresets: List<String> = listOf("Старт", "Удар", "Поворот", "Тормоз", "Аномалия"),
    val rules: List<SmartRule> = defaultSmartRules(),
    val streamingConfig: StreamingConfig = StreamingConfig(),
    val streamingStats: StreamingStats = StreamingStats(),
    val webDashboard: WebDashboardState = WebDashboardState(),
    val blackboxArmed: Boolean = false,
    val blackboxSampleCount: Int = 0,
    val backgroundServiceEnabled: Boolean = true
) {
    val isRecording: Boolean
        get() = recordingState is RecordingState.Recording

    val streamableSensorIds: Set<String>
        get() = sensors
            .asSequence()
            .filter { !it.isTriggerOnly }
            .map { it.id }
            .toSet()

    val inspectedSensor: SensorDescriptor?
        get() = inspectedSensorId?.let { id -> sensors.find { it.id == id } }

    val pinnedSensors: List<SensorDescriptor>
        get() = sensors.filter { it.id in pinnedSensorIds }

    val filteredSensors: List<SensorDescriptor>
        get() {
            val query = searchQuery.trim().lowercase()
            return sensors.filter { sensor ->
                val matchesCategory = selectedCategory == null || sensor.category == selectedCategory
                val matchesQuery = query.isEmpty() ||
                    sensor.displayName.lowercase().contains(query) ||
                    sensor.name.lowercase().contains(query) ||
                    sensor.stringType.lowercase().contains(query) ||
                    sensor.description.lowercase().contains(query) ||
                    sensor.vendor.lowercase().contains(query)
                matchesCategory && matchesQuery
            }
        }

    val filteredSessions: List<CsvSessionInfo>
        get() {
            val q = logSearchQuery.trim().lowercase()
            val filtered = if (q.isEmpty()) sessions else sessions.filter { it.fileName.lowercase().contains(q) }
            return when (logSortOrder) {
                LogSortOrder.NEWEST -> filtered.sortedByDescending { it.createdAtMillis }
                LogSortOrder.OLDEST -> filtered.sortedBy { it.createdAtMillis }
                LogSortOrder.LARGEST -> filtered.sortedByDescending { it.sizeBytes }
                LogSortOrder.NAME -> filtered.sortedBy { it.fileName.lowercase() }
            }
        }
}

fun defaultSmartRules(): List<SmartRule> = listOf(
    SmartRule(
        id = "rule_shock",
        name = "Авто-метка при ударе (> 2.2 g)",
        condition = RuleConditionType.G_FORCE_ABOVE,
        threshold = 2.2f,
        action = RuleActionType.INSERT_MARKER,
        enabled = false
    ),
    SmartRule(
        id = "rule_dark",
        name = "Метка при резкой темноте (< 10 лк)",
        condition = RuleConditionType.LIGHT_BELOW,
        threshold = 10f,
        action = RuleActionType.INSERT_MARKER,
        enabled = false
    ),
    SmartRule(
        id = "rule_timer",
        name = "Таймер автостопа (60 сек)",
        condition = RuleConditionType.AUTO_STOP_SECONDS,
        threshold = 60f,
        action = RuleActionType.STOP_RECORDING,
        enabled = false
    )
)

sealed interface SensorLoggerEffect {
    data class ShowMessage(val message: String) : SensorLoggerEffect
    data class PrintHtmlReport(val title: String, val html: String) : SensorLoggerEffect
}

sealed interface SensorLoggerEvent {
    data class NavigateTo(val section: AppSection) : SensorLoggerEvent
    data object NavigateHome : SensorLoggerEvent
    data class ToggleSensor(val sensorId: String) : SensorLoggerEvent
    data class TogglePinSensor(val sensorId: String) : SensorLoggerEvent
    data class SetSensorRateOverride(val sensorId: String, val rate: SamplingRate?) : SensorLoggerEvent
    data class ToggleExtraSource(val source: ExtraSource) : SensorLoggerEvent
    data object SelectAllSensors : SensorLoggerEvent
    data object ClearSensorSelection : SensorLoggerEvent
    data class SelectOnlySensor(val sensorId: String) : SensorLoggerEvent
    data class ApplySensorPreset(val preset: String) : SensorLoggerEvent
    data class ApplyScenario(val scenario: RecordingScenario) : SensorLoggerEvent
    data class SelectSamplingRate(val rate: SamplingRate) : SensorLoggerEvent
    data class SelectCsvDelimiter(val delimiter: CsvDelimiter) : SensorLoggerEvent
    data class SelectTimestampFormat(val format: TimestampFormat) : SensorLoggerEvent
    data class UpdateSessionNamePrefix(val prefix: String) : SensorLoggerEvent
    data object StartRecording : SensorLoggerEvent
    data object StopRecording : SensorLoggerEvent
    data class InsertMarker(val label: String = "MARKER") : SensorLoggerEvent
    data class AddAnnotationPreset(val label: String) : SensorLoggerEvent
    data class RemoveAnnotationPreset(val label: String) : SensorLoggerEvent
    data class DeleteSession(val fileName: String) : SensorLoggerEvent
    data class RenameSession(val oldName: String, val newName: String) : SensorLoggerEvent
    data class ToggleLogSelection(val fileName: String) : SensorLoggerEvent
    data object ClearLogSelection : SensorLoggerEvent
    data object DeleteSelectedLogs : SensorLoggerEvent
    data object CompareSelectedLogs : SensorLoggerEvent
    data object CloseComparison : SensorLoggerEvent
    data class UpdateLogSearch(val query: String) : SensorLoggerEvent
    data class SelectLogSortOrder(val order: LogSortOrder) : SensorLoggerEvent
    data object RefreshPermissions : SensorLoggerEvent
    data class UpdateSearchQuery(val query: String) : SensorLoggerEvent
    data class SelectCategory(val category: SensorCategory?) : SensorLoggerEvent
    data class OpenSensorInspector(val sensorId: String) : SensorLoggerEvent
    data object CloseSensorInspector : SensorLoggerEvent
    data class OpenLogViewer(val fileName: String) : SensorLoggerEvent
    data object CloseLogViewer : SensorLoggerEvent
    data object PrintOpenedLogPdf : SensorLoggerEvent
    data object ResetPeakGForce : SensorLoggerEvent
    data object ResetLiveStats : SensorLoggerEvent
    data object RefreshDeviceDiagnostics : SensorLoggerEvent
    data class StartSensorCalibration(val sensorId: String) : SensorLoggerEvent
    data class ClearSensorCalibration(val sensorId: String) : SensorLoggerEvent
    data class ToggleApplyCalibration(val enabled: Boolean) : SensorLoggerEvent
    data object CalibrateInclinometerZero : SensorLoggerEvent
    data object ResetInclinometerZero : SensorLoggerEvent
    data class ToggleBleScan(val enabled: Boolean) : SensorLoggerEvent
    data class ToggleRule(val ruleId: String) : SensorLoggerEvent
    data class AddRule(val rule: SmartRule) : SensorLoggerEvent
    data class DeleteRule(val ruleId: String) : SensorLoggerEvent
    data class UpdateStreamingConfig(val config: StreamingConfig) : SensorLoggerEvent
    data class ToggleWebDashboard(val enabled: Boolean) : SensorLoggerEvent
    data class ToggleBlackbox(val armed: Boolean) : SensorLoggerEvent
    data object FlushBlackboxNow : SensorLoggerEvent
    data class ToggleBackgroundService(val enabled: Boolean) : SensorLoggerEvent
}
