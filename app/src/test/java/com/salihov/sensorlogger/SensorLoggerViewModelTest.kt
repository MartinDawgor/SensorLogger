package com.salihov.sensorlogger

import com.salihov.sensorlogger.domain.model.AppSection
import com.salihov.sensorlogger.domain.model.CsvDelimiter
import com.salihov.sensorlogger.domain.model.CsvLogDocument
import com.salihov.sensorlogger.domain.model.CsvLogRow
import com.salihov.sensorlogger.domain.model.CsvSessionInfo
import com.salihov.sensorlogger.domain.model.RecordingState
import com.salihov.sensorlogger.domain.model.SamplingRate
import com.salihov.sensorlogger.domain.model.SensorCalibration
import com.salihov.sensorlogger.domain.model.SensorCategory
import com.salihov.sensorlogger.domain.model.SensorDescriptor
import com.salihov.sensorlogger.domain.model.SensorLoggerEffect
import com.salihov.sensorlogger.domain.model.SensorLoggerEvent
import com.salihov.sensorlogger.domain.model.SensorSample
import com.salihov.sensorlogger.domain.ports.AppPreferencesStore
import com.salihov.sensorlogger.domain.ports.CsvSessionStore
import com.salihov.sensorlogger.domain.ports.CsvSessionWriter
import com.salihov.sensorlogger.domain.ports.PermissionChecker
import com.salihov.sensorlogger.domain.ports.SensorCatalog
import com.salihov.sensorlogger.domain.ports.SensorStream
import com.salihov.sensorlogger.ui.state.SensorLoggerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SensorLoggerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init discovers sensors and preselects only permitted streamable ones`() = runTest(testDispatcher) {
        val acc = descriptor("acc", isTriggerOnly = false, permission = null)
        val hr = descriptor("hr", isTriggerOnly = false, permission = "android.permission.BODY_SENSORS")
        val trig = descriptor("sig", isTriggerOnly = true, permission = null)

        val vm = SensorLoggerViewModel(
            sensorCatalog = FakeCatalog(listOf(acc, hr, trig)),
            sensorStream = FakeStream(),
            sessionStore = FakeStore(),
            permissionChecker = FakePermissions(granted = emptySet()),
            appStore = FakeAppStore(),
            ioDispatcher = testDispatcher
        )

        try {
            val state = vm.uiState.value
            assertEquals(3, state.sensors.size)
            assertEquals(setOf("acc"), state.selectedSensorIds)
            assertEquals(setOf("android.permission.BODY_SENSORS"), state.missingPermissions)
        } finally {
            vm.setScreenVisible(false)
        }
    }

    @Test
    fun `start and stop recording writes samples and saves session`() = runTest(testDispatcher) {
        val acc = descriptor("acc", isTriggerOnly = false, permission = null)
        val fakeStream = FakeStream()
        val fakeStore = FakeStore()

        val vm = SensorLoggerViewModel(
            sensorCatalog = FakeCatalog(listOf(acc)),
            sensorStream = fakeStream,
            sessionStore = fakeStore,
            permissionChecker = FakePermissions(granted = emptySet()),
            ioDispatcher = testDispatcher,
            clockMillis = { 1_700_000_000_000L }
        )

        try {
            vm.onEvent(SensorLoggerEvent.StartRecording)
            assertTrue(vm.uiState.value.isRecording)
            testDispatcher.scheduler.runCurrent()

            fakeStream.emit(
                SensorSample(
                    sensorId = "acc",
                    sensorName = "Acc",
                    sensorType = "android.sensor.accelerometer",
                    epochMillis = 1_700_000_000_100L,
                    elapsedNanos = 1000L,
                    values = listOf(0.1f, 0.2f, 9.8f)
                )
            )
            testDispatcher.scheduler.runCurrent()

            vm.onEvent(SensorLoggerEvent.StopRecording)
            testDispatcher.scheduler.runCurrent()

            assertFalse(vm.uiState.value.isRecording)
            assertEquals(RecordingState.Idle, vm.uiState.value.recordingState)
            assertEquals(1, fakeStore.sessions.size)
            assertTrue(fakeStore.lastWriter!!.headerWritten)
            assertEquals(1, fakeStore.lastWriter!!.rows.size)
            assertTrue(fakeStore.lastWriter!!.closed)
        } finally {
            vm.setScreenVisible(false)
        }
    }

    @Test
    fun `selection and rate changes are ignored during recording and applied when idle`() = runTest(testDispatcher) {
        val acc = descriptor("acc", isTriggerOnly = false, permission = null)
        val gyro = descriptor("gyro", isTriggerOnly = false, permission = null)
        val trig = descriptor("sig", isTriggerOnly = true, permission = null)

        val vm = SensorLoggerViewModel(
            sensorCatalog = FakeCatalog(listOf(acc, gyro, trig)),
            sensorStream = FakeStream(),
            sessionStore = FakeStore(),
            permissionChecker = FakePermissions(granted = emptySet()),
            ioDispatcher = testDispatcher,
            clockMillis = { 1_700_000_000_000L }
        )

        try {
            vm.onEvent(SensorLoggerEvent.ToggleSensor("gyro"))
            assertEquals(setOf("acc"), vm.uiState.value.selectedSensorIds)

            vm.onEvent(SensorLoggerEvent.ToggleSensor("sig"))
            assertEquals(setOf("acc"), vm.uiState.value.selectedSensorIds)

            vm.onEvent(SensorLoggerEvent.SelectSamplingRate(SamplingRate.HIGH))
            assertEquals(SamplingRate.HIGH, vm.uiState.value.samplingRate)

            vm.onEvent(SensorLoggerEvent.StartRecording)
            vm.onEvent(SensorLoggerEvent.ClearSensorSelection)
            vm.onEvent(SensorLoggerEvent.SelectSamplingRate(SamplingRate.LOW))
            assertEquals(setOf("acc"), vm.uiState.value.selectedSensorIds)
            assertEquals(SamplingRate.HIGH, vm.uiState.value.samplingRate)

            vm.onEvent(SensorLoggerEvent.StopRecording)
            testDispatcher.scheduler.runCurrent()

            vm.onEvent(SensorLoggerEvent.SelectAllSensors)
            assertEquals(setOf("acc", "gyro"), vm.uiState.value.selectedSensorIds)

            vm.onEvent(SensorLoggerEvent.SelectOnlySensor("gyro"))
            assertEquals(setOf("gyro"), vm.uiState.value.selectedSensorIds)

            vm.onEvent(SensorLoggerEvent.ClearSensorSelection)
            assertTrue(vm.uiState.value.selectedSensorIds.isEmpty())

            val effects = mutableListOf<SensorLoggerEffect>()
            val effectJob = launch { vm.effects.collect { effects += it } }
            vm.onEvent(SensorLoggerEvent.StartRecording)
            testDispatcher.scheduler.runCurrent()
            effectJob.cancel()
            assertFalse(vm.uiState.value.isRecording)
            assertTrue(effects.any { it is SensorLoggerEffect.ShowMessage })
        } finally {
            vm.setScreenVisible(false)
        }
    }

    @Test
    fun `delete session removes file and refresh permissions updates missing set`() = runTest(testDispatcher) {
        val hr = descriptor("hr", isTriggerOnly = false, permission = "android.permission.BODY_SENSORS")
        val permissions = MutableFakePermissions()
        val store = FakeStore().apply {
            sessions += CsvSessionInfo("sensors_old.csv", 256L, 100L)
        }

        val vm = SensorLoggerViewModel(
            sensorCatalog = FakeCatalog(listOf(hr)),
            sensorStream = FakeStream(),
            sessionStore = store,
            permissionChecker = permissions,
            ioDispatcher = testDispatcher
        )

        try {
            assertEquals(setOf("android.permission.BODY_SENSORS"), vm.uiState.value.missingPermissions)

            permissions.granted += "android.permission.BODY_SENSORS"
            vm.onEvent(SensorLoggerEvent.RefreshPermissions)
            assertTrue(vm.uiState.value.missingPermissions.isEmpty())

            vm.onEvent(SensorLoggerEvent.OpenLogViewer("sensors_old.csv"))
            testDispatcher.scheduler.runCurrent()
            assertEquals("sensors_old.csv", vm.uiState.value.openedLogDocument?.fileName)

            vm.onEvent(SensorLoggerEvent.DeleteSession("sensors_old.csv"))
            testDispatcher.scheduler.runCurrent()
            assertTrue(vm.uiState.value.sessions.isEmpty())
            assertNull(vm.uiState.value.openedLogDocument)
        } finally {
            vm.setScreenVisible(false)
        }
    }

    @Test
    fun `search filters, rate overrides, and sensor inspector work cleanly`() = runTest(testDispatcher) {
        val acc = descriptor("acc", isTriggerOnly = false, permission = null, category = SensorCategory.MOTION)
        val light = descriptor("light", isTriggerOnly = false, permission = null, category = SensorCategory.ENVIRONMENT)
        val fakeStream = FakeStream()
        val appStore = FakeAppStore()

        val vm = SensorLoggerViewModel(
            sensorCatalog = FakeCatalog(listOf(acc, light)),
            sensorStream = fakeStream,
            sessionStore = FakeStore(),
            permissionChecker = FakePermissions(granted = emptySet()),
            appStore = appStore,
            ioDispatcher = testDispatcher
        )

        try {
            vm.onEvent(SensorLoggerEvent.SetSensorRateOverride("acc", SamplingRate.FASTEST))
            assertEquals(SamplingRate.FASTEST, vm.uiState.value.rateOverrides["acc"])
            assertEquals(SamplingRate.FASTEST, appStore.getRateOverrides()["acc"])

            vm.onEvent(SensorLoggerEvent.SelectCategory(SensorCategory.ENVIRONMENT))
            assertEquals(listOf(light), vm.uiState.value.filteredSensors)

            vm.onEvent(SensorLoggerEvent.SelectCategory(SensorCategory.ENVIRONMENT))
            vm.onEvent(SensorLoggerEvent.UpdateSearchQuery("acc"))
            assertEquals(listOf(acc), vm.uiState.value.filteredSensors)

            vm.onEvent(SensorLoggerEvent.ClearSensorSelection)
            vm.onEvent(SensorLoggerEvent.OpenSensorInspector("light"))
            testDispatcher.scheduler.runCurrent()
            assertEquals("light", vm.uiState.value.inspectedSensorId)
            assertTrue(fakeStream.lastObservedSensors.any { it.id == "light" })

            vm.onEvent(SensorLoggerEvent.CloseSensorInspector)
            assertNull(vm.uiState.value.inspectedSensorId)
        } finally {
            vm.setScreenVisible(false)
        }
    }

    @Test
    fun `markers, delimiter selection, and pin toggle work as expected`() = runTest(testDispatcher) {
        val acc = descriptor("acc", isTriggerOnly = false, permission = null)
        val fakeStore = FakeStore()
        val appStore = FakeAppStore()

        val vm = SensorLoggerViewModel(
            sensorCatalog = FakeCatalog(listOf(acc)),
            sensorStream = FakeStream(),
            sessionStore = fakeStore,
            permissionChecker = FakePermissions(granted = emptySet()),
            appStore = appStore,
            ioDispatcher = testDispatcher,
            clockMillis = { 1_700_000_000_000L }
        )

        try {
            vm.onEvent(SensorLoggerEvent.SelectCsvDelimiter(CsvDelimiter.SEMICOLON))
            assertEquals(CsvDelimiter.SEMICOLON, vm.uiState.value.csvDelimiter)
            assertEquals(CsvDelimiter.SEMICOLON, appStore.getCsvDelimiter())

            vm.onEvent(SensorLoggerEvent.NavigateTo(AppSection.MONITOR))
            assertEquals(AppSection.MONITOR, vm.uiState.value.currentSection)

            vm.onEvent(SensorLoggerEvent.NavigateHome)
            assertEquals(AppSection.HOME, vm.uiState.value.currentSection)

            vm.onEvent(SensorLoggerEvent.StartRecording)
            vm.onEvent(SensorLoggerEvent.InsertMarker("SHOCK"))
            testDispatcher.scheduler.runCurrent()

            val recState = vm.uiState.value.recordingState as RecordingState.Recording
            assertEquals(1, recState.markerCount)

            vm.onEvent(SensorLoggerEvent.StopRecording)
            testDispatcher.scheduler.runCurrent()
            assertEquals("user.marker", fakeStore.lastWriter!!.rows.single().sensorType)
            assertEquals("SHOCK #1", fakeStore.lastWriter!!.rows.single().sensorName)
        } finally {
            vm.setScreenVisible(false)
        }
    }

    @Test
    fun `software sensor calibration collects samples, saves offsets, and applies them`() = runTest(testDispatcher) {
        val gyro = SensorDescriptor(
            id = "gyro",
            type = 4,
            stringType = "android.sensor.gyroscope",
            name = "Gyro",
            vendor = "TestVendor",
            isTriggerOnly = false,
            isWakeUp = false,
            requiredPermission = null,
            category = SensorCategory.MOTION
        )
        val fakeStream = FakeStream()
        val appStore = FakeAppStore()

        val vm = SensorLoggerViewModel(
            sensorCatalog = FakeCatalog(listOf(gyro)),
            sensorStream = fakeStream,
            sessionStore = FakeStore(),
            permissionChecker = FakePermissions(granted = emptySet()),
            appStore = appStore,
            ioDispatcher = testDispatcher,
            clockMillis = { 1_700_000_000_000L }
        )

        try {
            vm.onEvent(SensorLoggerEvent.OpenSensorInspector("gyro"))
            testDispatcher.scheduler.runCurrent()
            vm.onEvent(SensorLoggerEvent.StartSensorCalibration("gyro"))
            testDispatcher.scheduler.runCurrent()
            assertEquals("gyro", vm.uiState.value.calibratingSensorId)

            repeat(25) { idx ->
                fakeStream.emit(
                    SensorSample(
                        sensorId = "gyro",
                        sensorName = "Gyro",
                        sensorType = "android.sensor.gyroscope",
                        epochMillis = 1_700_000_000_000L + idx,
                        elapsedNanos = (idx + 1) * 20_000_000L,
                        values = listOf(0.05f, -0.03f, 0.02f)
                    )
                )
            }
            testDispatcher.scheduler.runCurrent()

            assertNull(vm.uiState.value.calibratingSensorId)
            val savedCal = vm.uiState.value.calibrations["gyro"]
            assertNotNull(savedCal)
            assertEquals(0.05f, savedCal!!.offsets[0], 0.001f)
            assertEquals(-0.03f, savedCal.offsets[1], 0.001f)
            assertEquals(0.02f, savedCal.offsets[2], 0.001f)

            // Next sample should be zeroed out when applyCalibration is true
            fakeStream.emit(
                SensorSample(
                    sensorId = "gyro",
                    sensorName = "Gyro",
                    sensorType = "android.sensor.gyroscope",
                    epochMillis = 1_700_000_001_000L,
                    elapsedNanos = 600_000_000L,
                    values = listOf(0.05f, -0.03f, 0.02f)
                )
            )
            testDispatcher.scheduler.runCurrent()
            testDispatcher.scheduler.advanceTimeBy(200L)
            testDispatcher.scheduler.runCurrent()

            val live = vm.uiState.value.liveReadings["gyro"]!!
            assertEquals(0f, live[0], 0.001f)
            assertEquals(0f, live[1], 0.001f)
            assertEquals(0f, live[2], 0.001f)

            vm.onEvent(SensorLoggerEvent.ClearSensorCalibration("gyro"))
            assertFalse("gyro" in vm.uiState.value.calibrations)
        } finally {
            vm.setScreenVisible(false)
        }
    }

    private fun descriptor(
        id: String,
        isTriggerOnly: Boolean,
        permission: String?,
        category: SensorCategory = SensorCategory.OTHER
    ) = SensorDescriptor(
        id = id,
        type = 1,
        stringType = "test.$id",
        name = "Sensor $id",
        vendor = "TestVendor",
        isTriggerOnly = isTriggerOnly,
        isWakeUp = false,
        requiredPermission = permission,
        category = category
    )

    private class FakeCatalog(private val list: List<SensorDescriptor>) : SensorCatalog {
        override fun discoverAll(): List<SensorDescriptor> = list
    }

    private class FakeStream : SensorStream {
        private val flow = MutableSharedFlow<SensorSample>(extraBufferCapacity = 64)
        var lastObservedSensors: List<SensorDescriptor> = emptyList()
        var lastRateOverrides: Map<String, SamplingRate> = emptyMap()
        override fun observe(
            sensors: List<SensorDescriptor>,
            rate: SamplingRate,
            rateOverrides: Map<String, SamplingRate>
        ): Flow<SensorSample> {
            lastObservedSensors = sensors
            lastRateOverrides = rateOverrides
            return flow
        }
        suspend fun emit(sample: SensorSample) = flow.emit(sample)
    }

    private class FakePermissions(private val granted: Set<String>) : PermissionChecker {
        override fun isGranted(permission: String): Boolean = permission in granted
    }

    private class MutableFakePermissions : PermissionChecker {
        val granted = mutableSetOf<String>()
        override fun isGranted(permission: String): Boolean = permission in granted
    }

    private class FakeAppStore(
        private var delimiter: CsvDelimiter = CsvDelimiter.COMMA
    ) : AppPreferencesStore {
        private val cals = linkedMapOf<String, SensorCalibration>()
        private var overrides = emptyMap<String, SamplingRate>()
        override fun getCsvDelimiter(): CsvDelimiter = delimiter
        override fun setCsvDelimiter(delimiter: CsvDelimiter) {
            this.delimiter = delimiter
        }
        override fun getCalibrations(): Map<String, SensorCalibration> = cals.toMap()
        override fun saveCalibration(calibration: SensorCalibration) {
            cals[calibration.sensorId] = calibration
        }
        override fun removeCalibration(sensorId: String) {
            cals.remove(sensorId)
        }
        override fun getRateOverrides(): Map<String, SamplingRate> = overrides
        override fun saveRateOverrides(overrides: Map<String, SamplingRate>) {
            this.overrides = overrides
        }
    }

    private class FakeWriter : CsvSessionWriter {
        var headerWritten = false
        var closed = false
        val rows = mutableListOf<SensorSample>()

        override fun writeHeader() {
            headerWritten = true
        }

        override fun append(sample: SensorSample) {
            rows += sample
        }

        override fun flush() = Unit

        override fun close() {
            closed = true
        }
    }

    private class FakeStore : CsvSessionStore {
        val sessions = mutableListOf<CsvSessionInfo>()
        var lastWriter: FakeWriter? = null

        override fun openWriter(fileName: String, delimiter: CsvDelimiter): CsvSessionWriter {
            val writer = FakeWriter()
            lastWriter = writer
            sessions += CsvSessionInfo(fileName = fileName, sizeBytes = 128L, createdAtMillis = 1L)
            return writer
        }

        override fun listSessions(): List<CsvSessionInfo> = sessions.toList()

        override fun deleteSession(fileName: String): Boolean =
            sessions.removeAll { it.fileName == fileName }

        override fun readSessionBytes(fileName: String): ByteArray = ByteArray(0)

        override fun loadLogDocument(fileName: String, maxRows: Int): CsvLogDocument =
            CsvLogDocument(
                fileName = fileName,
                sizeBytes = 128L,
                totalDataRows = 1,
                isTruncated = false,
                firstTimestampMillis = 1000L,
                lastTimestampMillis = 2000L,
                sensorNames = listOf("Acc"),
                rows = listOf(
                    CsvLogRow(
                        rowNumber = 1,
                        epochMillis = 1000L,
                        elapsedNanos = 100L,
                        sensorType = "test.acc",
                        sensorName = "Acc",
                        values = listOf(0.1f, 0.2f, 9.8f)
                    )
                ),
                rawLines = listOf("header", "1000,100,test.acc,Acc,0.1,0.2,9.8")
            )
    }
}
