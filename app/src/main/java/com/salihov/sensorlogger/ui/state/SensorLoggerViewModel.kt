package com.salihov.sensorlogger.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.salihov.sensorlogger.data.analysis.SessionAnalyzer
import com.salihov.sensorlogger.data.analysis.SessionComparator
import com.salihov.sensorlogger.data.csv.CsvFormatter
import com.salihov.sensorlogger.data.export.AnalyticalExportEngine
import com.salihov.sensorlogger.data.recording.RecordingSessionCoordinator
import com.salihov.sensorlogger.data.sensor.SensorToolsMath
import com.salihov.sensorlogger.domain.model.AppSection
import com.salihov.sensorlogger.domain.model.CsvDelimiter
import com.salihov.sensorlogger.domain.model.ExtraSource
import com.salihov.sensorlogger.domain.model.RecordingScenario
import com.salihov.sensorlogger.domain.model.RecordingState
import com.salihov.sensorlogger.domain.model.RuleActionType
import com.salihov.sensorlogger.domain.model.RuleConditionType
import com.salihov.sensorlogger.domain.model.SamplingRate
import com.salihov.sensorlogger.domain.model.SensorCalibration
import com.salihov.sensorlogger.domain.model.SensorCategory
import com.salihov.sensorlogger.domain.model.SensorDescriptor
import com.salihov.sensorlogger.domain.model.SensorLoggerEffect
import com.salihov.sensorlogger.domain.model.SensorLoggerEvent
import com.salihov.sensorlogger.domain.model.SensorLoggerUiState
import com.salihov.sensorlogger.domain.model.SensorSample
import com.salihov.sensorlogger.domain.model.SmartRule
import com.salihov.sensorlogger.domain.model.StreamingConfig
import com.salihov.sensorlogger.domain.model.TimestampFormat
import com.salihov.sensorlogger.domain.model.defaultSmartRules
import com.salihov.sensorlogger.domain.ports.AppPreferencesStore
import com.salihov.sensorlogger.domain.ports.CsvSessionStore
import com.salihov.sensorlogger.domain.ports.DeviceDiagnosticsProvider
import com.salihov.sensorlogger.domain.ports.ExtraTelemetryGateway
import com.salihov.sensorlogger.domain.ports.PermissionChecker
import com.salihov.sensorlogger.domain.ports.SensorCatalog
import com.salihov.sensorlogger.domain.ports.SensorStream
import com.salihov.sensorlogger.domain.ports.TelemetryStreamer
import com.salihov.sensorlogger.domain.ports.WebDashboardServer
import com.salihov.sensorlogger.domain.usecase.BlackboxRingBuffer
import com.salihov.sensorlogger.domain.usecase.SmartRulesEvaluator
import com.salihov.sensorlogger.domain.usecase.TelemetryTracker
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SensorLoggerViewModel(
    private val sensorCatalog: SensorCatalog,
    private val sensorStream: SensorStream,
    private val sessionStore: CsvSessionStore,
    private val permissionChecker: PermissionChecker,
    private val appStore: AppPreferencesStore? = null,
    private val diagnosticsProvider: DeviceDiagnosticsProvider? = null,
    private val extraTelemetryGateway: ExtraTelemetryGateway? = null,
    private val telemetryStreamer: TelemetryStreamer? = null,
    private val webDashboardServer: WebDashboardServer? = null,
    private val onRecordingStateNotification: ((fileName: String?, sampleCount: Long, active: Boolean) -> Unit)? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clockMillis: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val _uiState = MutableStateFlow(SensorLoggerUiState())
    val uiState: StateFlow<SensorLoggerUiState> = _uiState.asStateFlow()

    private val _effects = Channel<SensorLoggerEffect>(Channel.BUFFERED)
    val effects: Flow<SensorLoggerEffect> = _effects.receiveAsFlow()

    private val telemetryTracker = TelemetryTracker()
    private val rulesEvaluator = SmartRulesEvaluator(clockMillis)
    private val blackboxBuffer = BlackboxRingBuffer()
    private val recorder = RecordingSessionCoordinator(sessionStore, ioDispatcher)

    private var streamJob: Job? = null
    private var extraStreamJob: Job? = null
    private var gpsMonitorJob: Job? = null
    private var audioMonitorJob: Job? = null
    private var bleMonitorJob: Job? = null
    private var uiFlushJob: Job? = null
    private var diagnosticsJob: Job? = null
    private var streamingJob: Job? = null
    private var webServerJob: Job? = null

    private val streamBatchLock = Any()
    private val streamBatchBuffer = ArrayList<SensorSample>(64)
    private val calibrationSamples = ArrayList<List<Float>>(CALIBRATION_SAMPLE_COUNT)
    private val calibrationLock = Any()

    private var isScreenVisible: Boolean = true
    private var isStoppingRecording: Boolean = false

    init {
        initializeState()
    }

    private fun initializeState() {
        val discovered = sensorCatalog.discoverAll()
        val missing = computeMissingPermissions(discovered)
        val initialSelected = discovered
            .asSequence()
            .filter { !it.isTriggerOnly && !it.isWakeUp && (it.requiredPermission == null || it.requiredPermission !in missing) }
            .map { it.id }
            .toSet()

        _uiState.value = SensorLoggerUiState(
            sensors = discovered,
            selectedSensorIds = initialSelected,
            pinnedSensorIds = pickDefaultPinnedSensors(discovered, missing),
            rateOverrides = appStore?.getRateOverrides().orEmpty(),
            sessions = sessionStore.listSessions(),
            missingPermissions = missing,
            csvDelimiter = appStore?.getCsvDelimiter() ?: CsvDelimiter.SEMICOLON,
            timestampFormat = appStore?.getTimestampFormat() ?: TimestampFormat.ISO_8601,
            calibrations = appStore?.getCalibrations().orEmpty(),
            streamingConfig = appStore?.getStreamingConfig() ?: StreamingConfig(),
            rules = appStore?.getRules() ?: defaultSmartRules(),
            annotationPresets = appStore?.getAnnotationPresets() ?: listOf("Старт", "Удар", "Поворот", "Тормоз", "Аномалия")
        )

        webDashboardServer?.let { server ->
            webServerJob = viewModelScope.launch {
                server.state.collect { ws -> _uiState.update { it.copy(webDashboard = ws) } }
            }
        }
        refreshDiagnosticsOnce()
        restartSensorObservationIfNeeded()
    }

    fun onEvent(event: SensorLoggerEvent) {
        when (event) {
            is SensorLoggerEvent.NavigateTo -> navigateTo(event.section)
            SensorLoggerEvent.NavigateHome -> navigateTo(AppSection.HOME)
            is SensorLoggerEvent.ToggleSensor -> toggleSensor(event.sensorId)
            is SensorLoggerEvent.TogglePinSensor -> togglePinSensor(event.sensorId)
            is SensorLoggerEvent.SetSensorRateOverride -> setSensorRateOverride(event.sensorId, event.rate)
            is SensorLoggerEvent.ToggleExtraSource -> toggleExtraSource(event.source)
            SensorLoggerEvent.SelectAllSensors -> selectAllSensors()
            SensorLoggerEvent.ClearSensorSelection -> clearSensorSelection()
            is SensorLoggerEvent.SelectOnlySensor -> selectOnlySensor(event.sensorId)
            is SensorLoggerEvent.ApplySensorPreset -> applySensorPreset(event.preset)
            is SensorLoggerEvent.ApplyScenario -> applyScenario(event.scenario)
            is SensorLoggerEvent.SelectSamplingRate -> selectSamplingRate(event.rate)
            is SensorLoggerEvent.SelectCsvDelimiter -> selectCsvDelimiter(event.delimiter)
            is SensorLoggerEvent.SelectTimestampFormat -> selectTimestampFormat(event.format)
            is SensorLoggerEvent.UpdateSessionNamePrefix -> _uiState.update { it.copy(sessionNamePrefix = event.prefix) }
            SensorLoggerEvent.StartRecording -> startRecording()
            SensorLoggerEvent.StopRecording -> stopRecording()
            is SensorLoggerEvent.InsertMarker -> insertMarker(event.label)
            is SensorLoggerEvent.AddAnnotationPreset -> addAnnotationPreset(event.label)
            is SensorLoggerEvent.RemoveAnnotationPreset -> removeAnnotationPreset(event.label)
            is SensorLoggerEvent.DeleteSession -> deleteSession(event.fileName)
            is SensorLoggerEvent.RenameSession -> renameSession(event.oldName, event.newName)
            is SensorLoggerEvent.ToggleLogSelection -> toggleLogSelection(event.fileName)
            SensorLoggerEvent.ClearLogSelection -> _uiState.update { it.copy(selectedLogFileNames = emptySet()) }
            SensorLoggerEvent.DeleteSelectedLogs -> deleteSelectedLogs()
            SensorLoggerEvent.CompareSelectedLogs -> compareSelectedLogs()
            SensorLoggerEvent.CloseComparison -> _uiState.update { it.copy(comparisonReport = null) }
            is SensorLoggerEvent.UpdateLogSearch -> _uiState.update { it.copy(logSearchQuery = event.query) }
            is SensorLoggerEvent.SelectLogSortOrder -> _uiState.update { it.copy(logSortOrder = event.order) }
            SensorLoggerEvent.RefreshPermissions -> refreshPermissions()
            is SensorLoggerEvent.UpdateSearchQuery -> _uiState.update { it.copy(searchQuery = event.query) }
            is SensorLoggerEvent.SelectCategory -> selectCategory(event.category)
            is SensorLoggerEvent.OpenSensorInspector -> openSensorInspector(event.sensorId)
            SensorLoggerEvent.CloseSensorInspector -> closeSensorInspector()
            is SensorLoggerEvent.OpenLogViewer -> openLogViewer(event.fileName)
            SensorLoggerEvent.CloseLogViewer -> _uiState.update {
                it.copy(openedLogDocument = null, openedLogReport = null, isLoadingLog = false)
            }
            SensorLoggerEvent.PrintOpenedLogPdf -> printOpenedLogPdf()
            SensorLoggerEvent.ResetPeakGForce -> resetPeakGForce()
            SensorLoggerEvent.ResetLiveStats -> resetLiveStats()
            SensorLoggerEvent.RefreshDeviceDiagnostics -> refreshDiagnosticsOnce()
            is SensorLoggerEvent.StartSensorCalibration -> startSensorCalibration(event.sensorId)
            is SensorLoggerEvent.ClearSensorCalibration -> clearSensorCalibration(event.sensorId)
            is SensorLoggerEvent.ToggleApplyCalibration -> _uiState.update { it.copy(applyCalibration = event.enabled) }
            SensorLoggerEvent.CalibrateInclinometerZero -> calibrateInclinometerZero()
            SensorLoggerEvent.ResetInclinometerZero -> _uiState.update {
                it.copy(inclinometerZeroPitch = 0f, inclinometerZeroRoll = 0f)
            }
            is SensorLoggerEvent.ToggleBleScan -> toggleBleScan(event.enabled)
            is SensorLoggerEvent.ToggleRule -> toggleRule(event.ruleId)
            is SensorLoggerEvent.AddRule -> addRule(event.rule)
            is SensorLoggerEvent.DeleteRule -> deleteRule(event.ruleId)
            is SensorLoggerEvent.UpdateStreamingConfig -> updateStreamingConfig(event.config)
            is SensorLoggerEvent.ToggleWebDashboard -> toggleWebDashboard(event.enabled)
            is SensorLoggerEvent.ToggleBlackbox -> toggleBlackbox(event.armed)
            SensorLoggerEvent.FlushBlackboxNow -> flushBlackboxNow()
            is SensorLoggerEvent.ToggleBackgroundService -> _uiState.update { it.copy(backgroundServiceEnabled = event.enabled) }
        }
    }

    fun setScreenVisible(visible: Boolean) {
        if (isScreenVisible == visible) return
        isScreenVisible = visible
        if (!visible) recorder.flush()
        restartSensorObservationIfNeeded()
        restartSectionMonitorsIfNeeded()
        restartDiagnosticsPollingIfNeeded()
    }

    private fun emitMessage(message: String) {
        _effects.trySend(SensorLoggerEffect.ShowMessage(message))
    }

    private fun navigateTo(section: AppSection) {
        if (_uiState.value.currentSection == section) return
        _uiState.update { it.copy(currentSection = section) }
        if (section.name.startsWith("DEVICE_")) refreshDiagnosticsOnce()
        restartDiagnosticsPollingIfNeeded()
        restartSectionMonitorsIfNeeded()
        if (!_uiState.value.isRecording) restartSensorObservationIfNeeded()
    }

    private fun selectCsvDelimiter(delimiter: CsvDelimiter) {
        if (_uiState.value.isRecording) return
        appStore?.setCsvDelimiter(delimiter)
        _uiState.update { it.copy(csvDelimiter = delimiter) }
    }

    private fun selectTimestampFormat(format: TimestampFormat) {
        appStore?.setTimestampFormat(format)
        _uiState.update { it.copy(timestampFormat = format) }
    }

    private fun toggleExtraSource(source: ExtraSource) {
        if (_uiState.value.isRecording) return
        _uiState.update { state ->
            val updated = if (source in state.enabledExtraSources) {
                state.enabledExtraSources - source
            } else {
                state.enabledExtraSources + source
            }
            state.copy(enabledExtraSources = updated)
        }
    }

    private fun setSensorRateOverride(sensorId: String, rate: SamplingRate?) {
        if (_uiState.value.isRecording) return
        _uiState.update { state ->
            val updated = if (rate == null) state.rateOverrides - sensorId else state.rateOverrides + (sensorId to rate)
            appStore?.saveRateOverrides(updated)
            state.copy(rateOverrides = updated)
        }
        restartSensorObservationIfNeeded()
    }

    private fun selectCategory(category: SensorCategory?) {
        _uiState.update { state ->
            state.copy(selectedCategory = if (state.selectedCategory == category) null else category)
        }
    }

    private fun applySensorPreset(preset: String) {
        if (_uiState.value.isRecording) return
        _uiState.update { state ->
            val permitted = state.sensors.asSequence().filter { isSensorStreamableAndPermitted(it) }
            val nextSelected = when (preset) {
                "motion" -> permitted.filter { !it.isWakeUp && it.category == SensorCategory.MOTION }.map { it.id }.toSet()
                "environment" -> permitted.filter { !it.isWakeUp && it.category == SensorCategory.ENVIRONMENT }.map { it.id }.toSet()
                else -> permitted.filter { !it.isWakeUp }.map { it.id }.toSet()
            }
            state.copy(selectedSensorIds = nextSelected)
        }
        emitMessage("Пресет применён: ${_uiState.value.selectedSensorIds.size} датч.")
        restartSensorObservationIfNeeded()
    }

    private fun applyScenario(scenario: RecordingScenario) {
        if (_uiState.value.isRecording) {
            emitMessage("Остановите текущую запись перед сменой сценария.")
            return
        }
        _uiState.update { state ->
            val matched = state.sensors
                .asSequence()
                .filter { !it.isWakeUp && it.type in scenario.sensorTypes && isSensorStreamableAndPermitted(it) }
                .map { it.id }
                .toSet()
            val extra = if (scenario.enableGps) state.enabledExtraSources + ExtraSource.GPS else state.enabledExtraSources
            state.copy(
                selectedSensorIds = matched.ifEmpty { state.selectedSensorIds },
                samplingRate = scenario.rate,
                enabledExtraSources = extra,
                sessionNamePrefix = scenario.filePrefix
            )
        }
        emitMessage("Сценарий «${scenario.title}» готов — нажмите «Запись CSV»")
        restartSensorObservationIfNeeded()
    }

    private fun togglePinSensor(sensorId: String) {
        val target = _uiState.value.sensors.find { it.id == sensorId } ?: return
        if (target.isTriggerOnly) {
            emitMessage("Триггерные датчики нельзя закрепить на осциллографе.")
            return
        }
        _uiState.update { state ->
            val updated = if (sensorId in state.pinnedSensorIds) {
                telemetryTracker.removeMonitorBuffer(sensorId)
                state.pinnedSensorIds - sensorId
            } else {
                state.pinnedSensorIds + sensorId
            }
            state.copy(pinnedSensorIds = updated)
        }
        if (!_uiState.value.isRecording) restartSensorObservationIfNeeded()
    }

    private fun openSensorInspector(sensorId: String) {
        telemetryTracker.clearInspector()
        _uiState.update { it.copy(inspectedSensorId = sensorId, inspectedHistory = emptyList()) }
        if (!_uiState.value.isRecording) restartSensorObservationIfNeeded()
    }

    private fun closeSensorInspector() {
        telemetryTracker.clearInspector()
        _uiState.update { it.copy(inspectedSensorId = null, inspectedHistory = emptyList()) }
        if (!_uiState.value.isRecording) restartSensorObservationIfNeeded()
    }

    private fun openLogViewer(fileName: String) {
        _uiState.update { it.copy(isLoadingLog = true) }
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching {
                    val doc = sessionStore.loadLogDocument(fileName)
                    doc to SessionAnalyzer.analyze(doc)
                }
            }
            result.onSuccess { (doc, report) ->
                _uiState.update { it.copy(openedLogDocument = doc, openedLogReport = report, isLoadingLog = false) }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoadingLog = false) }
                emitMessage("Не удалось открыть лог: ${err.message ?: "ошибка чтения"}")
            }
        }
    }

    private fun printOpenedLogPdf() {
        val doc = _uiState.value.openedLogDocument ?: return
        val report = _uiState.value.openedLogReport ?: return
        val tsFormat = _uiState.value.timestampFormat
        viewModelScope.launch {
            val html = withContext(ioDispatcher) {
                AnalyticalExportEngine.buildHtmlReport(doc, report, tsFormat)
            }
            _effects.trySend(SensorLoggerEffect.PrintHtmlReport(doc.fileName, html))
        }
    }

    private fun compareSelectedLogs() {
        val selected = _uiState.value.selectedLogFileNames.toList()
        if (selected.size != 2) {
            emitMessage("Выберите ровно 2 файла для A/B-сравнения.")
            return
        }
        _uiState.update { it.copy(isLoadingLog = true) }
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching {
                    val docA = sessionStore.loadLogDocument(selected[0])
                    val repA = SessionAnalyzer.analyze(docA)
                    val docB = sessionStore.loadLogDocument(selected[1])
                    val repB = SessionAnalyzer.analyze(docB)
                    SessionComparator.compare(docA, repA, docB, repB)
                }
            }
            result.onSuccess { cmp ->
                _uiState.update { it.copy(comparisonReport = cmp, isLoadingLog = false) }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoadingLog = false) }
                emitMessage("Ошибка сравнения: ${err.message}")
            }
        }
    }

    private fun resetPeakGForce() {
        telemetryTracker.resetPeakGForce()
        _uiState.update { it.copy(peakGForce = 0f) }
    }

    private fun startSensorCalibration(sensorId: String) {
        val sensor = _uiState.value.sensors.find { it.id == sensorId } ?: return
        if (sensor.isTriggerOnly) {
            emitMessage("Триггерные датчики не калибруются.")
            return
        }
        synchronized(calibrationLock) { calibrationSamples.clear() }
        _uiState.update { it.copy(calibratingSensorId = sensorId, calibrationProgress = 0f) }
        emitMessage("Держите устройство неподвижно ($CALIBRATION_SAMPLE_COUNT замеров)...")
        if (!_uiState.value.isRecording) restartSensorObservationIfNeeded()
    }

    private fun clearSensorCalibration(sensorId: String) {
        appStore?.removeCalibration(sensorId)
        telemetryTracker.clearSensor(sensorId)
        _uiState.update { state ->
            state.copy(
                calibrations = state.calibrations - sensorId,
                inspectedHistory = if (state.inspectedSensorId == sensorId) emptyList() else state.inspectedHistory
            )
        }
        emitMessage("Программная калибровка сброшена")
    }

    private fun calibrateInclinometerZero() {
        val state = _uiState.value
        val gravitySensor = state.sensors.firstOrNull { it.type == 9 } ?: state.sensors.firstOrNull { it.type == 1 }
        val pr = SensorToolsMath.pitchAndRollDegrees(gravitySensor?.let { state.liveReadings[it.id] }.orEmpty())
        if (pr == null) {
            emitMessage("Нет данных от акселерометра для тарирования уровня.")
            return
        }
        _uiState.update { it.copy(inclinometerZeroPitch = pr.first, inclinometerZeroRoll = pr.second) }
        emitMessage("Ноль уровня установлен")
    }

    private fun resetLiveStats() {
        telemetryTracker.resetAllStats()
        _uiState.update {
            it.copy(
                liveTelemetry = emptyMap(),
                monitorHistories = emptyMap(),
                inspectedHistory = emptyList(),
                peakGForce = 0f,
                fftSpectrum = com.salihov.sensorlogger.domain.model.FftSpectrumSnapshot()
            )
        }
    }

    private fun toggleSensor(sensorId: String) {
        if (_uiState.value.isRecording) return
        _uiState.update { state ->
            val target = state.sensors.find { it.id == sensorId } ?: return@update state
            if (target.isTriggerOnly) return@update state
            val updated = if (sensorId in state.selectedSensorIds) {
                state.selectedSensorIds - sensorId
            } else {
                state.selectedSensorIds + sensorId
            }
            state.copy(selectedSensorIds = updated)
        }
        restartSensorObservationIfNeeded()
    }

    private fun selectOnlySensor(sensorId: String) {
        if (_uiState.value.isRecording) return
        val target = _uiState.value.sensors.find { it.id == sensorId } ?: return
        if (target.isTriggerOnly) {
            emitMessage("Триггерные датчики не поддерживают потоковую запись.")
            return
        }
        if (target.requiredPermission != null && !permissionChecker.isGranted(target.requiredPermission)) {
            emitMessage("Сначала выдайте разрешение для этого датчика.")
            return
        }
        _uiState.update { it.copy(selectedSensorIds = setOf(sensorId)) }
        emitMessage("Для записи выбран только «${target.displayName}»")
        restartSensorObservationIfNeeded()
    }

    private fun selectAllSensors() {
        if (_uiState.value.isRecording) return
        _uiState.update { it.copy(selectedSensorIds = it.streamableSensorIds) }
        restartSensorObservationIfNeeded()
    }

    private fun clearSensorSelection() {
        if (_uiState.value.isRecording) return
        telemetryTracker.clearReadings()
        _uiState.update { it.copy(selectedSensorIds = emptySet(), liveReadings = emptyMap()) }
        restartSensorObservationIfNeeded()
    }

    private fun selectSamplingRate(rate: SamplingRate) {
        if (_uiState.value.isRecording) return
        _uiState.update { it.copy(samplingRate = rate) }
        restartSensorObservationIfNeeded()
    }

    private fun refreshPermissions() {
        val missing = computeMissingPermissions(_uiState.value.sensors)
        _uiState.update { it.copy(missingPermissions = missing) }
        restartSensorObservationIfNeeded()
        restartSectionMonitorsIfNeeded()
    }

    private fun buildCustomFileName(startedAt: Long, prefix: String): String {
        val cleanPrefix = prefix.trim().replace(Regex("[^a-zA-Z0-9_-]"), "_").trim('_').ifBlank { "sensors" }
        val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        return "${cleanPrefix}_${formatter.format(Date(startedAt))}.csv"
    }

    private fun startRecording() {
        val state = _uiState.value
        if (state.isRecording) return
        val recordableSensors = resolveRecordableSensors(state)
        if (recordableSensors.isEmpty() && state.enabledExtraSources.isEmpty()) {
            emitMessage("Выберите хотя бы один датчик или источник для записи.")
            return
        }

        val startedAt = clockMillis()
        val fileName = if (state.sessionNamePrefix == "sensors") {
            CsvFormatter.buildSessionFileName(startedAt)
        } else {
            buildCustomFileName(startedAt, state.sessionNamePrefix)
        }

        try {
            recorder.start(fileName, state.csvDelimiter, viewModelScope)
            rulesEvaluator.resetCooldowns()
            _uiState.update {
                it.copy(
                    recordingState = RecordingState.Recording(fileName, 0L, startedAt, 0),
                    sessions = sessionStore.listSessions()
                )
            }
            emitMessage("Запись начата: $fileName")
            if (state.backgroundServiceEnabled) {
                onRecordingStateNotification?.invoke(fileName, 0L, true)
            }
            startExtraRecordingStreamIfNeeded()
            startStreamingLoopIfNeeded()
            restartSensorObservationIfNeeded()
        } catch (e: Exception) {
            recorder.closeSafely()
            emitMessage("Не удалось начать запись: ${e.message ?: "ошибка файла"}")
        }
    }

    private fun insertMarker(label: String) {
        if (!_uiState.value.isRecording) return
        val nextIndex = recorder.nextMarkerIndex()
        val cleanLabel = label.trim().ifBlank { "MARKER" }
        val markerSample = SensorSample(
            sensorId = "__marker__",
            sensorName = "$cleanLabel #$nextIndex",
            sensorType = "user.marker",
            epochMillis = clockMillis(),
            elapsedNanos = System.nanoTime(),
            values = listOf(nextIndex.toFloat())
        )
        recorder.enqueue(markerSample)
        enqueueForRealtimeStream(markerSample)
        _uiState.update { state ->
            val currentRec = state.recordingState
            if (currentRec is RecordingState.Recording) {
                state.copy(recordingState = currentRec.copy(markerCount = nextIndex))
            } else {
                state
            }
        }
        emitMessage("Метка «$cleanLabel #$nextIndex» добавлена")
    }

    private fun addAnnotationPreset(label: String) {
        val clean = label.trim()
        if (clean.isEmpty()) return
        _uiState.update { state ->
            val updated = (state.annotationPresets + clean).distinct()
            appStore?.saveAnnotationPresets(updated)
            state.copy(annotationPresets = updated)
        }
    }

    private fun removeAnnotationPreset(label: String) {
        _uiState.update { state ->
            val updated = state.annotationPresets.filterNot { it == label }
            appStore?.saveAnnotationPresets(updated)
            state.copy(annotationPresets = updated)
        }
    }

    private fun stopRecording() {
        val current = _uiState.value.recordingState
        if (current !is RecordingState.Recording || isStoppingRecording) return
        isStoppingRecording = true

        extraStreamJob?.cancel()
        extraStreamJob = null
        streamingJob?.cancel()
        streamingJob = null

        viewModelScope.launch {
            try {
                val totalSamples = recorder.stop()
                val updatedSessions = withContext(ioDispatcher) { sessionStore.listSessions() }
                _uiState.update { it.copy(recordingState = RecordingState.Idle, sessions = updatedSessions) }
                onRecordingStateNotification?.invoke(null, totalSamples, false)
                emitMessage("Сохранено строк: $totalSamples (${current.sessionFileName})")
                restartSensorObservationIfNeeded()
            } finally {
                isStoppingRecording = false
            }
        }
    }

    private fun toggleBlackbox(armed: Boolean) {
        if (!armed) blackboxBuffer.clear()
        _uiState.update { it.copy(blackboxArmed = armed, blackboxSampleCount = blackboxBuffer.size()) }
        if (!_uiState.value.isRecording) restartSensorObservationIfNeeded()
    }

    private fun flushBlackboxNow() {
        val samples = blackboxBuffer.snapshotAndClear()
        if (samples.isEmpty()) {
            emitMessage("Буфер чёрного ящика пуст.")
            return
        }
        val fileName = buildCustomFileName(clockMillis(), "blackbox")
        val delimiter = _uiState.value.csvDelimiter
        viewModelScope.launch {
            val savedCount = withContext(ioDispatcher) {
                recorder.writeBlackboxSnapshot(fileName, delimiter, samples)
            }
            val updatedSessions = withContext(ioDispatcher) { sessionStore.listSessions() }
            _uiState.update { it.copy(sessions = updatedSessions, blackboxSampleCount = 0) }
            emitMessage("Чёрный ящик сохранён: $savedCount строк ($fileName)")
        }
    }

    private fun deleteSession(fileName: String) {
        viewModelScope.launch {
            val deleted = withContext(ioDispatcher) { sessionStore.deleteSession(fileName) }
            val updatedSessions = withContext(ioDispatcher) { sessionStore.listSessions() }
            _uiState.update { current ->
                val closeOpened = current.openedLogDocument?.fileName == fileName && deleted
                current.copy(
                    sessions = updatedSessions,
                    selectedLogFileNames = current.selectedLogFileNames - fileName,
                    openedLogDocument = if (closeOpened) null else current.openedLogDocument,
                    openedLogReport = if (closeOpened) null else current.openedLogReport
                )
            }
            emitMessage(if (deleted) "Файл $fileName удалён" else "Не удалось удалить $fileName")
        }
    }

    private fun renameSession(oldName: String, newName: String) {
        viewModelScope.launch {
            val renamed = withContext(ioDispatcher) { sessionStore.renameSession(oldName, newName) }
            val updatedSessions = withContext(ioDispatcher) { sessionStore.listSessions() }
            _uiState.update { it.copy(sessions = updatedSessions) }
            emitMessage(if (renamed) "Файл переименован" else "Не удалось переименовать файл")
        }
    }

    private fun toggleLogSelection(fileName: String) {
        _uiState.update { state ->
            val updated = if (fileName in state.selectedLogFileNames) {
                state.selectedLogFileNames - fileName
            } else {
                state.selectedLogFileNames + fileName
            }
            state.copy(selectedLogFileNames = updated)
        }
    }

    private fun deleteSelectedLogs() {
        val targets = _uiState.value.selectedLogFileNames
        if (targets.isEmpty()) return
        val activeName = (_uiState.value.recordingState as? RecordingState.Recording)?.sessionFileName
        viewModelScope.launch {
            var count = 0
            withContext(ioDispatcher) {
                targets.asSequence().filter { it != activeName }.forEach { name ->
                    if (sessionStore.deleteSession(name)) count++
                }
            }
            val updatedSessions = withContext(ioDispatcher) { sessionStore.listSessions() }
            _uiState.update { it.copy(sessions = updatedSessions, selectedLogFileNames = emptySet()) }
            emitMessage("Удалено файлов: $count")
        }
    }

    private fun toggleBleScan(enabled: Boolean) {
        _uiState.update { it.copy(isBleScanning = enabled) }
        restartSectionMonitorsIfNeeded()
    }

    private fun toggleRule(ruleId: String) {
        _uiState.update { state ->
            val updated = state.rules.map { if (it.id == ruleId) it.copy(enabled = !it.enabled) else it }
            appStore?.saveRules(updated)
            state.copy(rules = updated)
        }
    }

    private fun addRule(rule: SmartRule) {
        _uiState.update { state ->
            val updated = state.rules + rule
            appStore?.saveRules(updated)
            state.copy(rules = updated)
        }
        emitMessage("Правило «${rule.name}» добавлено")
    }

    private fun deleteRule(ruleId: String) {
        _uiState.update { state ->
            val updated = state.rules.filterNot { it.id == ruleId }
            appStore?.saveRules(updated)
            state.copy(rules = updated)
        }
    }

    private fun updateStreamingConfig(config: StreamingConfig) {
        appStore?.saveStreamingConfig(config)
        _uiState.update { it.copy(streamingConfig = config) }
        if (_uiState.value.isRecording) {
            streamingJob?.cancel()
            startStreamingLoopIfNeeded()
        }
    }

    private fun toggleWebDashboard(enabled: Boolean) {
        val server = webDashboardServer ?: return
        if (enabled) {
            server.start { telemetryTracker.snapshotReadings() }
        } else {
            server.stop()
        }
    }

    private fun startExtraRecordingStreamIfNeeded() {
        extraStreamJob?.cancel()
        val gateway = extraTelemetryGateway ?: return
        val sources = _uiState.value.enabledExtraSources
        if (sources.isEmpty()) return

        extraStreamJob = viewModelScope.launch {
            gateway.observeExtraSamples(sources).collect { sample ->
                if (_uiState.value.isRecording) {
                    recorder.enqueue(sample)
                    enqueueForRealtimeStream(sample)
                }
            }
        }
    }

    private fun startStreamingLoopIfNeeded() {
        streamingJob?.cancel()
        val streamer = telemetryStreamer ?: return
        if (!_uiState.value.streamingConfig.enabled) return

        streamingJob = viewModelScope.launch {
            while (true) {
                delay(1_000L)
                val currentCfg = _uiState.value.streamingConfig
                if (!currentCfg.enabled || !_uiState.value.isRecording) continue
                val batch = synchronized(streamBatchLock) {
                    if (streamBatchBuffer.isEmpty()) emptyList()
                    else streamBatchBuffer.toList().also { streamBatchBuffer.clear() }
                }
                if (batch.isNotEmpty()) {
                    val res = streamer.sendBatch(currentCfg, batch)
                    _uiState.update { st ->
                        val prev = st.streamingStats
                        st.copy(
                            streamingStats = if (res.isSuccess) {
                                prev.copy(sentBatches = prev.sentBatches + 1, sentSamples = prev.sentSamples + batch.size, lastError = null)
                            } else {
                                prev.copy(failedBatches = prev.failedBatches + 1, lastError = res.exceptionOrNull()?.message ?: "Ошибка сети")
                            }
                        )
                    }
                }
            }
        }
    }

    private fun enqueueForRealtimeStream(sample: SensorSample) {
        if (!_uiState.value.streamingConfig.enabled) return
        synchronized(streamBatchLock) {
            if (streamBatchBuffer.size >= 500) streamBatchBuffer.removeAt(0)
            streamBatchBuffer.add(sample)
        }
    }

    private fun restartSectionMonitorsIfNeeded() {
        val gateway = extraTelemetryGateway ?: return
        val section = _uiState.value.currentSection
        val shouldWatchGps = isScreenVisible && section == AppSection.GPS
        val shouldWatchAudio = isScreenVisible && section == AppSection.TOOLS
        val shouldWatchBle = isScreenVisible && section == AppSection.BLE && _uiState.value.isBleScanning

        if (shouldWatchGps && gpsMonitorJob?.isActive != true) {
            gpsMonitorJob = viewModelScope.launch {
                gateway.observeGps().collect { snap -> _uiState.update { it.copy(gpsSnapshot = snap) } }
            }
        } else if (!shouldWatchGps) {
            gpsMonitorJob?.cancel()
            gpsMonitorJob = null
        }

        if (shouldWatchAudio && audioMonitorJob?.isActive != true) {
            audioMonitorJob = viewModelScope.launch {
                gateway.observeAudioLevel().collect { audio ->
                    _uiState.update { it.copy(audioSnapshot = audio) }
                    applyTriggeredActions(rulesEvaluator.evaluate(_uiState.value.rules, RuleConditionType.SOUND_ABOVE, audio.approxSplDb))
                }
            }
        } else if (!shouldWatchAudio) {
            audioMonitorJob?.cancel()
            audioMonitorJob = null
        }

        if (shouldWatchBle && bleMonitorJob?.isActive != true) {
            bleMonitorJob = viewModelScope.launch {
                gateway.observeBleBeacons().collect { list -> _uiState.update { it.copy(bleBeacons = list) } }
            }
        } else if (!shouldWatchBle) {
            bleMonitorJob?.cancel()
            bleMonitorJob = null
        }
    }

    private fun restartDiagnosticsPollingIfNeeded() {
        diagnosticsJob?.cancel()
        val provider = diagnosticsProvider ?: return
        if (!isScreenVisible || !_uiState.value.currentSection.name.startsWith("DEVICE_")) return

        diagnosticsJob = viewModelScope.launch {
            while (true) {
                delay(DIAGNOSTICS_REFRESH_INTERVAL_MS)
                val snap = withContext(ioDispatcher) { provider.snapshot() }
                _uiState.update { it.copy(deviceDiagnostics = snap) }
            }
        }
    }

    private fun refreshDiagnosticsOnce() {
        val provider = diagnosticsProvider ?: return
        viewModelScope.launch {
            val snap = withContext(ioDispatcher) { provider.snapshot() }
            _uiState.update { it.copy(deviceDiagnostics = snap) }
        }
    }

    private fun restartSensorObservationIfNeeded() {
        streamJob?.cancel()
        uiFlushJob?.cancel()

        val state = _uiState.value
        if (!isScreenVisible && !state.isRecording && !state.blackboxArmed) return

        val recordableIds = state.selectedSensorIds
        val accelIds = state.sensors.asSequence().filter { it.type == 1 }.map { it.id }.toSet()
        val lightIds = state.sensors.asSequence().filter { it.type == 5 }.map { it.id }.toSet()
        val observedSensors = resolveObservedSensors(state)
        if (observedSensors.isEmpty()) return

        streamJob = viewModelScope.launch {
            sensorStream.observe(observedSensors, state.samplingRate, state.rateOverrides).collect { rawSample ->
                val curState = _uiState.value
                handleCalibrationSample(curState, rawSample)

                val cal = if (_uiState.value.applyCalibration) _uiState.value.calibrations[rawSample.sensorId] else null
                val sample = if (cal != null) {
                    rawSample.copy(values = SensorToolsMath.applyCalibrationOffsets(rawSample.values, cal.offsets))
                } else {
                    rawSample
                }

                val isAccel = sample.sensorId in accelIds
                val latestState = _uiState.value
                telemetryTracker.recordSample(sample, isAccel, latestState.inspectedSensorId, latestState.pinnedSensorIds)

                if (latestState.blackboxArmed && !latestState.isRecording) {
                    blackboxBuffer.push(sample)
                }
                if (isAccel) {
                    val g = SensorToolsMath.gForce(sample.values)
                    applyTriggeredActions(rulesEvaluator.evaluate(latestState.rules, RuleConditionType.G_FORCE_ABOVE, g))
                }
                if (sample.sensorId in lightIds) {
                    sample.values.firstOrNull()?.let { lux ->
                        applyTriggeredActions(rulesEvaluator.evaluate(latestState.rules, RuleConditionType.LIGHT_BELOW, lux))
                    }
                }
                if (latestState.isRecording && sample.sensorId in recordableIds) {
                    recorder.enqueue(sample)
                    enqueueForRealtimeStream(sample)
                }
            }
        }

        uiFlushJob = viewModelScope.launch {
            while (true) {
                val cur = _uiState.value
                val needsFastUi = cur.inspectedSensorId != null ||
                    cur.currentSection == AppSection.MONITOR ||
                    cur.currentSection == AppSection.TOOLS
                val intervalMs = when {
                    needsFastUi -> UI_REFRESH_FAST_MS
                    cur.currentSection == AppSection.SENSORS -> UI_REFRESH_LIST_MS
                    else -> UI_REFRESH_SLOW_MS
                }
                delay(intervalMs)

                val latest = _uiState.value
                val showSensorsData = latest.inspectedSensorId != null ||
                    latest.currentSection == AppSection.SENSORS ||
                    latest.currentSection == AppSection.MONITOR ||
                    latest.currentSection == AppSection.TOOLS

                val recordedCount = recorder.sampleCount
                if (!showSensorsData && !latest.isRecording && !latest.blackboxArmed) continue

                val snapshot = if (showSensorsData) telemetryTracker.snapshotReadings() else latest.liveReadings
                val telemetrySnapshot = if (latest.inspectedSensorId != null || latest.currentSection == AppSection.SENSORS || latest.currentSection == AppSection.MONITOR) {
                    telemetryTracker.snapshotTelemetry()
                } else {
                    latest.liveTelemetry
                }
                val historySnapshot = if (latest.inspectedSensorId != null) telemetryTracker.snapshotInspectedHistory() else emptyList()
                val monitorSnapshot = if (latest.currentSection == AppSection.MONITOR && latest.pinnedSensorIds.isNotEmpty()) {
                    telemetryTracker.snapshotMonitorHistories(latest.pinnedSensorIds)
                } else {
                    latest.monitorHistories
                }
                val fftSnapshot = if (latest.currentSection == AppSection.TOOLS) telemetryTracker.computeAccelFft() else latest.fftSpectrum
                val bbCount = if (latest.blackboxArmed) blackboxBuffer.size() else 0

                _uiState.update { current ->
                    val updatedRecording = when (val rec = current.recordingState) {
                        is RecordingState.Recording -> rec.copy(sampleCount = recordedCount)
                        RecordingState.Idle -> RecordingState.Idle
                    }
                    current.copy(
                        liveReadings = snapshot,
                        liveTelemetry = telemetrySnapshot,
                        recordingState = updatedRecording,
                        inspectedHistory = historySnapshot,
                        monitorHistories = monitorSnapshot,
                        peakGForce = telemetryTracker.peakGForce,
                        fftSpectrum = fftSnapshot,
                        blackboxSampleCount = bbCount
                    )
                }

                val recState = _uiState.value.recordingState
                if (recState is RecordingState.Recording) {
                    if (_uiState.value.backgroundServiceEnabled) {
                        onRecordingStateNotification?.invoke(recState.sessionFileName, recordedCount, true)
                    }
                    val elapsedSec = (clockMillis() - recState.startedAtMillis) / 1000f
                    applyTriggeredActions(rulesEvaluator.evaluate(_uiState.value.rules, RuleConditionType.AUTO_STOP_SECONDS, elapsedSec))
                }
            }
        }
    }

    private fun handleCalibrationSample(curState: SensorLoggerUiState, rawSample: SensorSample) {
        if (curState.calibratingSensorId != rawSample.sensorId) return
        var completedOffsets: List<Float>? = null
        var progress = 0f
        synchronized(calibrationLock) {
            calibrationSamples.add(rawSample.values)
            progress = (calibrationSamples.size.toFloat() / CALIBRATION_SAMPLE_COUNT).coerceIn(0f, 1f)
            if (calibrationSamples.size >= CALIBRATION_SAMPLE_COUNT) {
                val sensorType = curState.sensors.find { it.id == rawSample.sensorId }?.type ?: 0
                completedOffsets = SensorToolsMath.computeZeroOffsets(sensorType, calibrationSamples)
                calibrationSamples.clear()
            }
        }
        val offsets = completedOffsets
        if (offsets != null) {
            val cal = SensorCalibration(rawSample.sensorId, offsets, clockMillis())
            appStore?.saveCalibration(cal)
            telemetryTracker.clearSensor(rawSample.sensorId)
            _uiState.update { st ->
                st.copy(
                    calibrations = st.calibrations + (rawSample.sensorId to cal),
                    calibratingSensorId = null,
                    calibrationProgress = 0f,
                    applyCalibration = true,
                    inspectedHistory = if (st.inspectedSensorId == rawSample.sensorId) emptyList() else st.inspectedHistory
                )
            }
            emitMessage("Калибровка нуля сохранена")
        } else {
            _uiState.update { st ->
                if (st.calibratingSensorId == rawSample.sensorId) st.copy(calibrationProgress = progress) else st
            }
        }
    }

    private fun applyTriggeredActions(actions: List<RuleActionType>) {
        for (action in actions) {
            when (action) {
                RuleActionType.INSERT_MARKER -> if (_uiState.value.isRecording) insertMarker("RULE")
                RuleActionType.START_RECORDING -> if (!_uiState.value.isRecording) startRecording()
                RuleActionType.STOP_RECORDING -> if (_uiState.value.isRecording) stopRecording()
                RuleActionType.FLUSH_BLACKBOX -> flushBlackboxNow()
            }
        }
    }

    private fun resolveRecordableSensors(state: SensorLoggerUiState): List<SensorDescriptor> =
        state.sensors.filter { it.id in state.selectedSensorIds && isSensorStreamableAndPermitted(it) }

    private fun resolveObservedSensors(state: SensorLoggerUiState): List<SensorDescriptor> {
        if (state.isRecording || state.blackboxArmed) {
            return resolveRecordableSensors(state)
        }
        val activeSingleId = state.inspectedSensorId ?: state.calibratingSensorId
        if (activeSingleId != null) {
            return state.sensors.filter { it.id == activeSingleId && isSensorStreamableAndPermitted(it) }
        }
        val needsRuleSensors = state.rules.any { it.enabled && it.condition != RuleConditionType.AUTO_STOP_SECONDS }
        return when (state.currentSection) {
            AppSection.SENSORS -> state.sensors.filter { it.id in state.selectedSensorIds && isSensorStreamableAndPermitted(it) }
            AppSection.MONITOR -> state.sensors.filter { it.id in state.pinnedSensorIds && isSensorStreamableAndPermitted(it) }
            AppSection.TOOLS -> state.sensors.filter { it.type in TOOL_SENSOR_TYPES && isSensorStreamableAndPermitted(it) }
            else -> if (needsRuleSensors) {
                state.sensors.filter { it.type in TOOL_SENSOR_TYPES && isSensorStreamableAndPermitted(it) }
            } else {
                emptyList()
            }
        }
    }

    private fun pickDefaultPinnedSensors(
        sensors: List<SensorDescriptor>,
        missingPermissions: Set<String>
    ): Set<String> {
        val available = sensors.filter {
            !it.isTriggerOnly && !it.isWakeUp &&
                (it.requiredPermission == null || it.requiredPermission !in missingPermissions)
        }
        val picked = LinkedHashSet<String>()
        for (type in listOf(1, 4, 5)) {
            available.firstOrNull { it.type == type }?.let { picked += it.id }
            if (picked.size >= 2) break
        }
        if (picked.isEmpty()) available.take(2).forEach { picked += it.id }
        return picked
    }

    private fun isSensorStreamableAndPermitted(sensor: SensorDescriptor): Boolean =
        !sensor.isTriggerOnly &&
            (sensor.requiredPermission == null || permissionChecker.isGranted(sensor.requiredPermission))

    private fun computeMissingPermissions(sensors: List<SensorDescriptor>): Set<String> =
        sensors
            .asSequence()
            .mapNotNull { it.requiredPermission }
            .distinct()
            .filterNot { permissionChecker.isGranted(it) }
            .toSet()

    override fun onCleared() {
        streamJob?.cancel()
        extraStreamJob?.cancel()
        gpsMonitorJob?.cancel()
        audioMonitorJob?.cancel()
        bleMonitorJob?.cancel()
        uiFlushJob?.cancel()
        diagnosticsJob?.cancel()
        streamingJob?.cancel()
        webServerJob?.cancel()
        webDashboardServer?.stop()
        recorder.dispose()
        super.onCleared()
    }

    companion object {
        private const val UI_REFRESH_FAST_MS = 180L
        private const val UI_REFRESH_LIST_MS = 350L
        private const val UI_REFRESH_SLOW_MS = 500L
        private const val DIAGNOSTICS_REFRESH_INTERVAL_MS = 2_000L
        private const val CALIBRATION_SAMPLE_COUNT = 25
        private val TOOL_SENSOR_TYPES = setOf(1, 2, 5, 9, 10)

        fun factory(
            sensorCatalog: SensorCatalog,
            sensorStream: SensorStream,
            sessionStore: CsvSessionStore,
            permissionChecker: PermissionChecker,
            appStore: AppPreferencesStore,
            diagnosticsProvider: DeviceDiagnosticsProvider,
            extraTelemetryGateway: ExtraTelemetryGateway,
            telemetryStreamer: TelemetryStreamer,
            webDashboardServer: WebDashboardServer,
            onRecordingStateNotification: (fileName: String?, sampleCount: Long, active: Boolean) -> Unit
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SensorLoggerViewModel(
                    sensorCatalog = sensorCatalog,
                    sensorStream = sensorStream,
                    sessionStore = sessionStore,
                    permissionChecker = permissionChecker,
                    appStore = appStore,
                    diagnosticsProvider = diagnosticsProvider,
                    extraTelemetryGateway = extraTelemetryGateway,
                    telemetryStreamer = telemetryStreamer,
                    webDashboardServer = webDashboardServer,
                    onRecordingStateNotification = onRecordingStateNotification
                ) as T
        }
    }
}
