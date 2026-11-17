package com.salihov.sensorlogger.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.salihov.sensorlogger.domain.model.AppSection
import com.salihov.sensorlogger.domain.model.ExportFormat
import com.salihov.sensorlogger.domain.model.ExtraSource
import com.salihov.sensorlogger.domain.model.RecordingState
import com.salihov.sensorlogger.domain.model.SensorLoggerEffect
import com.salihov.sensorlogger.domain.model.SensorLoggerEvent
import com.salihov.sensorlogger.domain.model.SensorLoggerUiState
import com.salihov.sensorlogger.ui.components.BleScannerScreen
import com.salihov.sensorlogger.ui.components.CompactRecordingDock
import com.salihov.sensorlogger.ui.components.CsvLogViewerScreen
import com.salihov.sensorlogger.ui.components.DeviceTabContent
import com.salihov.sensorlogger.ui.components.GpsTrackerScreen
import com.salihov.sensorlogger.ui.components.LogsTabContent
import com.salihov.sensorlogger.ui.components.MainMenuHomeScreen
import com.salihov.sensorlogger.ui.components.MonitorTabContent
import com.salihov.sensorlogger.ui.components.RulesAndMarkersScreen
import com.salihov.sensorlogger.ui.components.SensorInspectorScreen
import com.salihov.sensorlogger.ui.components.SensorsTabContent
import com.salihov.sensorlogger.ui.components.SessionComparisonScreen
import com.salihov.sensorlogger.ui.components.StreamingScreen
import com.salihov.sensorlogger.ui.components.ToolsTabContent
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorLoggerScreen(
    uiState: SensorLoggerUiState,
    effects: Flow<SensorLoggerEffect>,
    hasLocationPermission: Boolean,
    hasAudioPermission: Boolean,
    hasBlePermission: Boolean,
    onEvent: (SensorLoggerEvent) -> Unit,
    onRequestPermissions: (Set<String>) -> Unit,
    onShareSession: (String) -> Unit,
    onSaveSessionToDevice: (String) -> Unit,
    onExportFormatted: (String, ExportFormat) -> Unit,
    onExportBatchZip: (List<String>) -> Unit,
    onPrintHtml: (String, String) -> Unit
) {
    val openedDoc = uiState.openedLogDocument
    val inspectedSensor = uiState.inspectedSensor
    val comparisonReport = uiState.comparisonReport

    BackHandler(
        enabled = comparisonReport != null || openedDoc != null || inspectedSensor != null || uiState.currentSection != AppSection.HOME
    ) {
        when {
            comparisonReport != null -> onEvent(SensorLoggerEvent.CloseComparison)
            openedDoc != null -> onEvent(SensorLoggerEvent.CloseLogViewer)
            inspectedSensor != null -> onEvent(SensorLoggerEvent.CloseSensorInspector)
            uiState.currentSection != AppSection.HOME -> onEvent(SensorLoggerEvent.NavigateHome)
        }
    }

    if (comparisonReport != null) {
        SessionComparisonScreen(
            report = comparisonReport,
            onBack = { onEvent(SensorLoggerEvent.CloseComparison) }
        )
        return
    }

    if (openedDoc != null) {
        CsvLogViewerScreen(
            document = openedDoc,
            report = uiState.openedLogReport,
            onBack = { onEvent(SensorLoggerEvent.CloseLogViewer) },
            onShare = { onShareSession(openedDoc.fileName) },
            onExportFormatted = { fmt -> onExportFormatted(openedDoc.fileName, fmt) }
        )
        return
    }

    if (inspectedSensor != null) {
        SensorInspectorScreen(
            sensor = inspectedSensor,
            isSelectedForRecording = inspectedSensor.id in uiState.selectedSensorIds,
            isPinnedToMonitor = inspectedSensor.id in uiState.pinnedSensorIds,
            isRecording = uiState.isRecording,
            missingPermissions = uiState.missingPermissions,
            samplingRate = uiState.samplingRate,
            rateOverride = uiState.rateOverrides[inspectedSensor.id],
            telemetry = uiState.liveTelemetry[inspectedSensor.id],
            liveValues = uiState.liveReadings[inspectedSensor.id],
            history = uiState.inspectedHistory,
            calibration = uiState.calibrations[inspectedSensor.id],
            applyCalibration = uiState.applyCalibration,
            isCalibrating = uiState.calibratingSensorId == inspectedSensor.id,
            calibrationProgress = uiState.calibrationProgress,
            onBack = { onEvent(SensorLoggerEvent.CloseSensorInspector) },
            onToggleRecordingSelection = { onEvent(SensorLoggerEvent.ToggleSensor(inspectedSensor.id)) },
            onTogglePin = { onEvent(SensorLoggerEvent.TogglePinSensor(inspectedSensor.id)) },
            onSelectOnlyThisSensor = { onEvent(SensorLoggerEvent.SelectOnlySensor(inspectedSensor.id)) },
            onSetRateOverride = { rate -> onEvent(SensorLoggerEvent.SetSensorRateOverride(inspectedSensor.id, rate)) },
            onStartCalibration = { onEvent(SensorLoggerEvent.StartSensorCalibration(inspectedSensor.id)) },
            onClearCalibration = { onEvent(SensorLoggerEvent.ClearSensorCalibration(inspectedSensor.id)) },
            onToggleApplyCalibration = { onEvent(SensorLoggerEvent.ToggleApplyCalibration(it)) },
            onRequestPermissions = onRequestPermissions
        )
        return
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is SensorLoggerEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
                is SensorLoggerEffect.PrintHtmlReport -> onPrintHtml(effect.title, effect.html)
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    navigationIcon = {
                        if (uiState.currentSection != AppSection.HOME) {
                            IconButton(onClick = { onEvent(SensorLoggerEvent.NavigateHome) }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = "Главное меню"
                                )
                            }
                        }
                    },
                    title = {
                        Column {
                            Text(
                                text = uiState.currentSection.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (uiState.currentSection == AppSection.HOME) {
                                    "${uiState.deviceDiagnostics.manufacturer} ${uiState.deviceDiagnostics.model} • Android ${uiState.deviceDiagnostics.androidVersion}".trim()
                                } else {
                                    uiState.currentSection.subtitle
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    actions = {
                        Text(
                            text = "by @chumzes",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    }
                )
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
        bottomBar = {
            CompactRecordingDock(
                recordingState = uiState.recordingState,
                samplingRate = uiState.samplingRate,
                selectedCount = uiState.selectedSensorIds.size,
                annotationPresets = uiState.annotationPresets,
                onSelectRate = { onEvent(SensorLoggerEvent.SelectSamplingRate(it)) },
                onStartRecording = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && uiState.backgroundServiceEnabled) {
                        onRequestPermissions(setOf(Manifest.permission.POST_NOTIFICATIONS))
                    }
                    onEvent(SensorLoggerEvent.StartRecording)
                },
                onStopRecording = { onEvent(SensorLoggerEvent.StopRecording) },
                onInsertMarker = { label -> onEvent(SensorLoggerEvent.InsertMarker(label)) }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.isLoadingLog) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            when (uiState.currentSection) {
                AppSection.HOME -> MainMenuHomeScreen(
                    uiState = uiState,
                    onNavigate = { section -> onEvent(SensorLoggerEvent.NavigateTo(section)) },
                    onApplyScenario = { scenario -> onEvent(SensorLoggerEvent.ApplyScenario(scenario)) }
                )

                AppSection.SENSORS -> SensorsTabContent(
                    sensors = uiState.filteredSensors,
                    totalCount = uiState.sensors.size,
                    selectedSensorIds = uiState.selectedSensorIds,
                    pinnedSensorIds = uiState.pinnedSensorIds,
                    enabledExtraSources = uiState.enabledExtraSources,
                    liveReadings = uiState.liveReadings,
                    liveTelemetry = uiState.liveTelemetry,
                    missingPermissions = uiState.missingPermissions,
                    searchQuery = uiState.searchQuery,
                    selectedCategory = uiState.selectedCategory,
                    isRecording = uiState.isRecording,
                    onSearchQueryChange = { onEvent(SensorLoggerEvent.UpdateSearchQuery(it)) },
                    onSelectCategory = { onEvent(SensorLoggerEvent.SelectCategory(it)) },
                    onApplyPreset = { onEvent(SensorLoggerEvent.ApplySensorPreset(it)) },
                    onToggleExtraSource = { onEvent(SensorLoggerEvent.ToggleExtraSource(it)) },
                    onSelectAll = { onEvent(SensorLoggerEvent.SelectAllSensors) },
                    onClearAll = { onEvent(SensorLoggerEvent.ClearSensorSelection) },
                    onToggleSensor = { onEvent(SensorLoggerEvent.ToggleSensor(it)) },
                    onTogglePin = { onEvent(SensorLoggerEvent.TogglePinSensor(it)) },
                    onInspectSensor = { onEvent(SensorLoggerEvent.OpenSensorInspector(it)) },
                    onRequestPermissions = onRequestPermissions
                )

                AppSection.MONITOR -> MonitorTabContent(
                    pinnedSensors = uiState.pinnedSensors,
                    liveTelemetry = uiState.liveTelemetry,
                    monitorHistories = uiState.monitorHistories,
                    onUnpin = { onEvent(SensorLoggerEvent.TogglePinSensor(it)) },
                    onInspectSensor = { onEvent(SensorLoggerEvent.OpenSensorInspector(it)) },
                    onResetStats = { onEvent(SensorLoggerEvent.ResetLiveStats) }
                )

                AppSection.TOOLS -> ToolsTabContent(
                    sensors = uiState.sensors,
                    liveReadings = uiState.liveReadings,
                    peakGForce = uiState.peakGForce,
                    fftSpectrum = uiState.fftSpectrum,
                    inclinometerZeroPitch = uiState.inclinometerZeroPitch,
                    inclinometerZeroRoll = uiState.inclinometerZeroRoll,
                    audioSnapshot = uiState.audioSnapshot,
                    hasAudioPermission = hasAudioPermission,
                    onResetPeakG = { onEvent(SensorLoggerEvent.ResetPeakGForce) },
                    onCalibrateInclinometerZero = { onEvent(SensorLoggerEvent.CalibrateInclinometerZero) },
                    onResetInclinometerZero = { onEvent(SensorLoggerEvent.ResetInclinometerZero) },
                    onRequestAudioPermission = onRequestPermissions
                )

                AppSection.GPS -> GpsTrackerScreen(
                    gps = uiState.gpsSnapshot,
                    isRecordingEnabled = ExtraSource.GPS in uiState.enabledExtraSources,
                    hasLocationPermission = hasLocationPermission,
                    onToggleRecordGps = { onEvent(SensorLoggerEvent.ToggleExtraSource(ExtraSource.GPS)) },
                    onRequestLocationPermission = { onRequestPermissions(setOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }
                )

                AppSection.BLE -> BleScannerScreen(
                    beacons = uiState.bleBeacons,
                    isScanning = uiState.isBleScanning,
                    isRecordingEnabled = ExtraSource.BLE_SCAN in uiState.enabledExtraSources,
                    hasBlePermission = hasBlePermission,
                    onToggleScan = { onEvent(SensorLoggerEvent.ToggleBleScan(it)) },
                    onToggleRecordBle = { onEvent(SensorLoggerEvent.ToggleExtraSource(ExtraSource.BLE_SCAN)) },
                    onRequestBlePermissions = onRequestPermissions
                )

                AppSection.RULES -> RulesAndMarkersScreen(
                    rules = uiState.rules,
                    annotationPresets = uiState.annotationPresets,
                    blackboxArmed = uiState.blackboxArmed,
                    blackboxSampleCount = uiState.blackboxSampleCount,
                    onToggleBlackbox = { onEvent(SensorLoggerEvent.ToggleBlackbox(it)) },
                    onFlushBlackboxNow = { onEvent(SensorLoggerEvent.FlushBlackboxNow) },
                    onToggleRule = { onEvent(SensorLoggerEvent.ToggleRule(it)) },
                    onAddRule = { onEvent(SensorLoggerEvent.AddRule(it)) },
                    onDeleteRule = { onEvent(SensorLoggerEvent.DeleteRule(it)) },
                    onAddPreset = { onEvent(SensorLoggerEvent.AddAnnotationPreset(it)) },
                    onRemovePreset = { onEvent(SensorLoggerEvent.RemoveAnnotationPreset(it)) }
                )

                AppSection.STREAMING -> StreamingScreen(
                    config = uiState.streamingConfig,
                    stats = uiState.streamingStats,
                    webDashboard = uiState.webDashboard,
                    onSaveConfig = { onEvent(SensorLoggerEvent.UpdateStreamingConfig(it)) },
                    onToggleWebDashboard = { onEvent(SensorLoggerEvent.ToggleWebDashboard(it)) }
                )

                AppSection.DEVICE_SYSTEM,
                AppSection.DEVICE_BATTERY,
                AppSection.DEVICE_MEMORY,
                AppSection.DEVICE_NETWORK -> DeviceTabContent(
                    section = uiState.currentSection,
                    diagnostics = uiState.deviceDiagnostics,
                    sensors = uiState.sensors,
                    onRefresh = { onEvent(SensorLoggerEvent.RefreshDeviceDiagnostics) }
                )

                AppSection.LOGS -> {
                    val activeFileName = (uiState.recordingState as? RecordingState.Recording)?.sessionFileName
                    LogsTabContent(
                        sessions = uiState.filteredSessions,
                        selectedFileNames = uiState.selectedLogFileNames,
                        activeRecordingFileName = activeFileName,
                        csvDelimiter = uiState.csvDelimiter,
                        timestampFormat = uiState.timestampFormat,
                        sessionNamePrefix = uiState.sessionNamePrefix,
                        logSearchQuery = uiState.logSearchQuery,
                        logSortOrder = uiState.logSortOrder,
                        backgroundServiceEnabled = uiState.backgroundServiceEnabled,
                        isRecording = uiState.isRecording,
                        onSelectDelimiter = { onEvent(SensorLoggerEvent.SelectCsvDelimiter(it)) },
                        onSelectTimestampFormat = { onEvent(SensorLoggerEvent.SelectTimestampFormat(it)) },
                        onUpdateSessionPrefix = { onEvent(SensorLoggerEvent.UpdateSessionNamePrefix(it)) },
                        onToggleBackgroundService = { onEvent(SensorLoggerEvent.ToggleBackgroundService(it)) },
                        onUpdateSearch = { onEvent(SensorLoggerEvent.UpdateLogSearch(it)) },
                        onSelectSortOrder = { onEvent(SensorLoggerEvent.SelectLogSortOrder(it)) },
                        onToggleSelectLog = { onEvent(SensorLoggerEvent.ToggleLogSelection(it)) },
                        onClearSelection = { onEvent(SensorLoggerEvent.ClearLogSelection) },
                        onCompareSelected = { onEvent(SensorLoggerEvent.CompareSelectedLogs) },
                        onDeleteSelected = { onEvent(SensorLoggerEvent.DeleteSelectedLogs) },
                        onExportSelectedZip = onExportBatchZip,
                        onOpenLog = { onEvent(SensorLoggerEvent.OpenLogViewer(it)) },
                        onRenameLog = { oldName, newName -> onEvent(SensorLoggerEvent.RenameSession(oldName, newName)) },
                        onShareLog = onShareSession,
                        onSaveLogToDevice = onSaveSessionToDevice,
                        onDeleteLog = { onEvent(SensorLoggerEvent.DeleteSession(it)) }
                    )
                }
            }
        }
    }
}
