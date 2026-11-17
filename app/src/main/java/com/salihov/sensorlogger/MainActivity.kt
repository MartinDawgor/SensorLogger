package com.salihov.sensorlogger

import android.content.Intent
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.salihov.sensorlogger.data.analysis.SessionAnalyzer
import com.salihov.sensorlogger.data.csv.CsvSessionManager
import com.salihov.sensorlogger.data.device.AndroidDeviceDiagnosticsProvider
import com.salihov.sensorlogger.data.export.AnalyticalExportEngine
import com.salihov.sensorlogger.data.extra.AndroidExtraTelemetryGateway
import com.salihov.sensorlogger.data.preferences.SharedPreferencesAppStore
import com.salihov.sensorlogger.data.sensor.AndroidSensorGateway
import com.salihov.sensorlogger.data.service.RecordingServiceBridge
import com.salihov.sensorlogger.data.service.SensorRecordingService
import com.salihov.sensorlogger.data.streaming.LocalWebDashboardServer
import com.salihov.sensorlogger.data.streaming.NetworkTelemetryStreamer
import com.salihov.sensorlogger.domain.model.ExportFormat
import com.salihov.sensorlogger.domain.model.SensorLoggerEvent
import com.salihov.sensorlogger.ui.SensorLoggerScreen
import com.salihov.sensorlogger.ui.state.SensorLoggerViewModel
import com.salihov.sensorlogger.ui.theme.SensorLoggerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

class MainActivity : ComponentActivity() {

    private val sensorGateway by lazy { AndroidSensorGateway(applicationContext) }
    private val sessionManager by lazy { CsvSessionManager(File(filesDir, "sessions")) }
    private val appStore by lazy { SharedPreferencesAppStore(applicationContext) }
    private val diagnosticsProvider by lazy { AndroidDeviceDiagnosticsProvider(applicationContext) }
    private val extraGateway by lazy { AndroidExtraTelemetryGateway(applicationContext) }
    private val telemetryStreamer by lazy { NetworkTelemetryStreamer() }
    private val webDashboardServer by lazy { LocalWebDashboardServer(sessionManager) }
    private var activePrintWebView: WebView? = null

    private val viewModel: SensorLoggerViewModel by viewModels {
        SensorLoggerViewModel.factory(
            sensorCatalog = sensorGateway,
            sensorStream = sensorGateway,
            sessionStore = sessionManager,
            permissionChecker = sensorGateway,
            appStore = appStore,
            diagnosticsProvider = diagnosticsProvider,
            extraTelemetryGateway = extraGateway,
            telemetryStreamer = telemetryStreamer,
            webDashboardServer = webDashboardServer,
            onRecordingStateNotification = { fileName, count, active ->
                if (active && fileName != null) {
                    runCatching { SensorRecordingService.startOrUpdate(applicationContext, fileName, count) }
                } else {
                    SensorRecordingService.stop(applicationContext)
                }
            }
        )
    }

    private var pendingExportFileName: String? = null
    private var pendingExportFormat: ExportFormat = ExportFormat.RAW_CSV
    private var pendingBatchZipFiles: List<String>? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.onEvent(SensorLoggerEvent.RefreshPermissions)
    }

    private val saveDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { targetUri ->
        val fileName = pendingExportFileName
        val format = pendingExportFormat
        val batchFiles = pendingBatchZipFiles
        pendingExportFileName = null
        pendingBatchZipFiles = null
        if (targetUri == null) return@registerForActivityResult

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val state = viewModel.uiState.value
                    val tempOut = File(cacheDir, "export_tmp_${System.currentTimeMillis()}")
                    try {
                        if (batchFiles != null) {
                            val items = batchFiles.map { name ->
                                val doc = sessionManager.loadLogDocument(name, maxRows = 10_000)
                                val report = SessionAnalyzer.analyze(doc)
                                Triple(doc, report, sessionManager.getSessionFile(name))
                            }
                            AnalyticalExportEngine.buildBatchZip(
                                items = items,
                                targetFile = tempOut,
                                delimiter = state.csvDelimiter,
                                timestampFormat = state.timestampFormat
                            )
                        } else if (fileName != null) {
                            val rawFile = sessionManager.getSessionFile(fileName)
                            if (format == ExportFormat.RAW_CSV) {
                                if (!rawFile.exists()) error("Файл не найден")
                                rawFile.copyTo(tempOut, overwrite = true)
                            } else {
                                val doc = sessionManager.loadLogDocument(fileName, maxRows = 10_000)
                                val report = SessionAnalyzer.analyze(doc)
                                AnalyticalExportEngine.exportToFile(
                                    document = doc,
                                    report = report,
                                    format = format,
                                    targetFile = tempOut,
                                    delimiter = state.csvDelimiter,
                                    timestampFormat = state.timestampFormat,
                                    rawFile = rawFile
                                )
                            }
                        } else {
                            error("Не указан файл экспорта")
                        }

                        FileInputStream(tempOut).use { input ->
                            contentResolver.openOutputStream(targetUri)?.use { output ->
                                input.copyTo(output)
                                output.flush()
                            } ?: error("Не удалось открыть поток для записи")
                        }
                    } finally {
                        tempOut.delete()
                    }
                }
            }
            result.onSuccess {
                Toast.makeText(this@MainActivity, "Экспорт завершён", Toast.LENGTH_SHORT).show()
            }.onFailure { err ->
                Toast.makeText(this@MainActivity, "Ошибка экспорта: ${err.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            RecordingServiceBridge.commands.collect { cmd ->
                when (cmd) {
                    RecordingServiceBridge.ServiceCommand.StopRecording ->
                        viewModel.onEvent(SensorLoggerEvent.StopRecording)
                    RecordingServiceBridge.ServiceCommand.InsertMarker ->
                        viewModel.onEvent(SensorLoggerEvent.InsertMarker("NOTIF"))
                }
            }
        }

        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.setScreenVisible(true)
                Lifecycle.Event.ON_STOP -> viewModel.setScreenVisible(false)
                else -> Unit
            }
        })

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            SensorLoggerTheme {
                SensorLoggerScreen(
                    uiState = uiState,
                    effects = viewModel.effects,
                    hasLocationPermission = extraGateway.hasLocationPermission(),
                    hasAudioPermission = extraGateway.hasAudioPermission(),
                    hasBlePermission = extraGateway.hasBlePermission(),
                    onEvent = viewModel::onEvent,
                    onRequestPermissions = { permissions ->
                        permissionLauncher.launch(permissions.toTypedArray())
                    },
                    onShareSession = ::shareCsvFile,
                    onSaveSessionToDevice = { fileName -> exportFormatted(fileName, ExportFormat.WIDE_CSV) },
                    onExportFormatted = ::exportFormatted,
                    onExportBatchZip = ::exportBatchZip,
                    onPrintHtml = ::printHtmlReport
                )
            }
        }
    }

    private fun shareCsvFile(fileName: String) {
        val file = sessionManager.getSessionFile(fileName)
        if (!file.exists()) {
            Toast.makeText(this, "Файл не найден", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            startActivity(Intent.createChooser(shareIntent, "Экспорт CSV"))
        }.onFailure { err ->
            Toast.makeText(this, "Не удалось открыть меню отправки: ${err.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun exportFormatted(fileName: String, format: ExportFormat) {
        if (format == ExportFormat.PDF_PRINT) {
            viewModel.onEvent(SensorLoggerEvent.PrintOpenedLogPdf)
            return
        }
        val base = fileName.removeSuffix(".csv")
        val suffix = when (format) {
            ExportFormat.HTML_REPORT -> "_report.html"
            ExportFormat.PDF_PRINT -> ".pdf"
            ExportFormat.WIDE_CSV -> "_table.csv"
            ExportFormat.MARKDOWN_REPORT -> "_report.md"
            ExportFormat.JSON -> ".json"
            ExportFormat.SQLITE -> ".db"
            ExportFormat.KML -> ".kml"
            ExportFormat.ZIP_BUNDLE -> "_bundle.zip"
            ExportFormat.RAW_CSV -> ".csv"
        }
        pendingExportFileName = fileName
        pendingExportFormat = format
        pendingBatchZipFiles = null
        saveDocumentLauncher.launch("$base$suffix")
    }

    private fun printHtmlReport(title: String, html: String) {
        val webView = WebView(this)
        activePrintWebView = webView
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                val printManager = getSystemService(PrintManager::class.java)
                val adapter = view.createPrintDocumentAdapter(title)
                printManager?.print(
                    "SensorLogger_$title",
                    adapter,
                    PrintAttributes.Builder().build()
                )
            }
        }
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    override fun onDestroy() {
        activePrintWebView?.destroy()
        activePrintWebView = null
        super.onDestroy()
    }

    private fun exportBatchZip(files: List<String>) {
        if (files.isEmpty()) return
        pendingExportFileName = null
        pendingBatchZipFiles = files
        pendingExportFormat = ExportFormat.ZIP_BUNDLE
        saveDocumentLauncher.launch("sensor_sessions_${System.currentTimeMillis()}.zip")
    }
}
