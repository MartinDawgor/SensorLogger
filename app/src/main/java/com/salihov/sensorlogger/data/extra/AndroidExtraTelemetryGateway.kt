package com.salihov.sensorlogger.data.extra

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.salihov.sensorlogger.domain.model.AudioLevelSnapshot
import com.salihov.sensorlogger.domain.model.BleBeaconInfo
import com.salihov.sensorlogger.domain.model.ExtraSource
import com.salihov.sensorlogger.domain.model.GpsSnapshot
import com.salihov.sensorlogger.domain.model.SensorSample
import com.salihov.sensorlogger.domain.ports.ExtraTelemetryGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.log10
import kotlin.math.sqrt

class AndroidExtraTelemetryGateway(
    private val context: Context
) : ExtraTelemetryGateway {

    override fun observeExtraSamples(sources: Set<ExtraSource>): Flow<SensorSample> {
        val flows = mutableListOf<Flow<SensorSample>>()

        if (ExtraSource.GPS in sources && hasLocationPermission()) {
            flows += observeGps().mapToSamples()
        }
        if (ExtraSource.AUDIO_DB in sources && hasAudioPermission()) {
            flows += observeAudioLevel().mapAudioToSamples()
        }
        if (ExtraSource.BLE_SCAN in sources && hasBlePermission()) {
            flows += observeBleBeacons().mapBleToSamples()
        }
        if (ExtraSource.SYSTEM_PROPS in sources) {
            flows += observeSystemPropsSamples()
        }

        return if (flows.isEmpty()) flow {} else flows.merge()
    }

    @SuppressLint("MissingPermission")
    override fun observeGps(): Flow<GpsSnapshot> = callbackFlow {
        if (!hasLocationPermission()) {
            close()
            return@callbackFlow
        }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (lm == null) {
            close()
            return@callbackFlow
        }

        fun emitLocation(loc: Location) {
            trySend(
                GpsSnapshot(
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    altitudeMeters = loc.altitude,
                    speedMps = loc.speed,
                    bearingDeg = loc.bearing,
                    accuracyMeters = loc.accuracy,
                    provider = loc.provider.orEmpty(),
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) = emitLocation(location)
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }

        runCatching {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let(::emitLocation)
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, listener, Looper.getMainLooper())
            }
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)?.let(::emitLocation)
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2_000L, 0f, listener, Looper.getMainLooper())
            }
        }

        awaitClose {
            runCatching { lm.removeUpdates(listener) }
        }
    }

    @SuppressLint("MissingPermission")
    override fun observeAudioLevel(): Flow<AudioLevelSnapshot> = callbackFlow {
        if (!hasAudioPermission()) {
            close()
            return@callbackFlow
        }

        val sampleRate = 8000
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(1024)

        val recorder = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2
            )
        }.getOrNull()

        if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder?.release()
            close()
            return@callbackFlow
        }

        val job = launch(Dispatchers.IO) {
            val buffer = ShortArray(minBuf)
            var peakDbfs = -90f
            runCatching { recorder.startRecording() }
            while (isActive) {
                val read = runCatching { recorder.read(buffer, 0, buffer.size) }.getOrDefault(-1)
                if (read > 0) {
                    var sumSq = 0.0
                    for (i in 0 until read) {
                        val norm = buffer[i] / 32768.0
                        sumSq += norm * norm
                    }
                    val rms = sqrt(sumSq / read).coerceAtLeast(1e-6)
                    val dbfs = (20.0 * log10(rms)).toFloat().coerceIn(-90f, 0f)
                    if (dbfs > peakDbfs) peakDbfs = dbfs
                    val approxSpl = (dbfs + 92f).coerceIn(15f, 115f)
                    trySend(
                        AudioLevelSnapshot(
                            dbfs = dbfs,
                            approxSplDb = approxSpl,
                            peakDbfs = peakDbfs,
                            updatedAtMillis = System.currentTimeMillis()
                        )
                    )
                }
                delay(180L)
            }
        }

        awaitClose {
            job.cancel()
            runCatching {
                recorder.stop()
                recorder.release()
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun observeBleBeacons(): Flow<List<BleBeaconInfo>> = callbackFlow {
        if (!hasBlePermission()) {
            close()
            return@callbackFlow
        }
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bm?.adapter
        val scanner = adapter?.bluetoothLeScanner
        if (adapter == null || !adapter.isEnabled || scanner == null) {
            close()
            return@callbackFlow
        }

        val beacons = LinkedHashMap<String, BleBeaconInfo>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val addr = result.device?.address ?: return
                val devName = runCatching { result.device?.name }.getOrNull()
                    ?: result.scanRecord?.deviceName
                    ?: "BLE Device"
                val bytes = result.scanRecord?.bytes
                val hex = bytes?.take(12)?.joinToString("") { "%02X".format(it) }.orEmpty()
                beacons[addr] = BleBeaconInfo(
                    address = addr,
                    name = devName,
                    rssiDbm = result.rssi,
                    lastSeenMillis = System.currentTimeMillis(),
                    payloadHex = hex
                )
                trySend(beacons.values.sortedByDescending { it.rssiDbm })
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        runCatching { scanner.startScan(null, settings, callback) }

        awaitClose {
            runCatching { scanner.stopScan(callback) }
        }
    }

    private fun observeSystemPropsSamples(): Flow<SensorSample> = flow {
        while (true) {
            val now = System.currentTimeMillis()
            val batteryIntent = runCatching {
                context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            }.getOrNull()
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val pct = if (level >= 0 && scale > 0) (level * 100f) / scale else -1f
            val voltageMv = (batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0).toFloat()
            val tempC = (batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
            val brightness = runCatching {
                Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, -1)
            }.getOrDefault(-1).toFloat()
            val wifiRssi = readWifiRssi().toFloat()

            emit(
                SensorSample(
                    sensorId = "__system_state__",
                    sensorName = "System State (Battery/Brightness/WiFi)",
                    sensorType = ExtraSource.SYSTEM_PROPS.sensorType,
                    epochMillis = now,
                    elapsedNanos = System.nanoTime(),
                    values = listOf(pct, voltageMv, tempC, brightness, wifiRssi)
                )
            )
            delay(2_000L)
        }
    }.flowOn(Dispatchers.IO)

    @Suppress("DEPRECATION")
    private fun readWifiRssi(): Int {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val caps = cm?.getNetworkCapabilities(cm.activeNetwork) ?: return -127
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return -127
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        return runCatching { wm?.connectionInfo?.rssi ?: -127 }.getOrDefault(-127)
    }

    private fun Flow<GpsSnapshot>.mapToSamples(): Flow<SensorSample> = flow {
        collect { gps ->
            emit(
                SensorSample(
                    sensorId = "__gps__",
                    sensorName = "GPS (${gps.provider})",
                    sensorType = ExtraSource.GPS.sensorType,
                    epochMillis = gps.updatedAtMillis,
                    elapsedNanos = System.nanoTime(),
                    values = listOf(
                        gps.latitude.toFloat(),
                        gps.longitude.toFloat(),
                        gps.altitudeMeters.toFloat(),
                        gps.speedMps,
                        gps.bearingDeg,
                        gps.accuracyMeters
                    )
                )
            )
        }
    }

    private fun Flow<AudioLevelSnapshot>.mapAudioToSamples(): Flow<SensorSample> = flow {
        collect { audio ->
            emit(
                SensorSample(
                    sensorId = "__audio__",
                    sensorName = "Microphone Sound Level",
                    sensorType = ExtraSource.AUDIO_DB.sensorType,
                    epochMillis = audio.updatedAtMillis,
                    elapsedNanos = System.nanoTime(),
                    values = listOf(audio.dbfs, audio.approxSplDb, audio.peakDbfs)
                )
            )
        }
    }

    private fun Flow<List<BleBeaconInfo>>.mapBleToSamples(): Flow<SensorSample> = flow {
        var lastEmit = 0L
        collect { list ->
            val now = System.currentTimeMillis()
            if (now - lastEmit < 1_500L) return@collect
            lastEmit = now
            val strongest = list.firstOrNull() ?: return@collect
            emit(
                SensorSample(
                    sensorId = "__ble__",
                    sensorName = "BLE ${strongest.name} (${strongest.address})",
                    sensorType = ExtraSource.BLE_SCAN.sensorType,
                    epochMillis = now,
                    elapsedNanos = System.nanoTime(),
                    values = listOf(strongest.rssiDbm.toFloat(), list.size.toFloat())
                )
            )
        }
    }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun hasBlePermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            hasLocationPermission()
        }
}
