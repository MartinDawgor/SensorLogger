package com.salihov.sensorlogger.data.sensor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import androidx.core.content.ContextCompat
import com.salihov.sensorlogger.domain.model.SamplingRate
import com.salihov.sensorlogger.domain.model.SensorDescriptor
import com.salihov.sensorlogger.domain.model.SensorSample
import com.salihov.sensorlogger.domain.ports.PermissionChecker
import com.salihov.sensorlogger.domain.ports.SensorCatalog
import com.salihov.sensorlogger.domain.ports.SensorStream
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class AndroidSensorGateway(
    private val context: Context
) : SensorCatalog, SensorStream, PermissionChecker {

    private val sensorManager: SensorManager by lazy {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    override fun discoverAll(): List<SensorDescriptor> {
        val rawSensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
        val seenIds = mutableMapOf<String, Int>()

        return rawSensors
            .map { sensor ->
                val baseId = sensor.baseKey()
                val count = seenIds.getOrDefault(baseId, 0) + 1
                seenIds[baseId] = count
                val uniqueId = if (count == 1) baseId else "$baseId#$count"
                val stringType = sensor.stringType.orEmpty().ifBlank { "type_${sensor.type}" }
                val name = sensor.name.orEmpty().ifBlank { "Sensor #${sensor.type}" }
                val metadata = SensorMetadataCatalog.resolve(sensor.type, stringType, name, sensor.isWakeUpSensor)

                SensorDescriptor(
                    id = uniqueId,
                    type = sensor.type,
                    stringType = stringType,
                    name = name,
                    friendlyName = metadata.friendlyName,
                    vendor = sensor.vendor.orEmpty(),
                    isTriggerOnly = sensor.reportingMode == Sensor.REPORTING_MODE_ONE_SHOT,
                    isWakeUp = sensor.isWakeUpSensor,
                    requiredPermission = requiredPermissionFor(sensor.type),
                    category = metadata.category,
                    description = metadata.description,
                    unit = metadata.unit,
                    axisLabels = metadata.axisLabels,
                    resolution = sensor.resolution,
                    maxRange = sensor.maximumRange,
                    powerMa = sensor.power,
                    minDelayUs = sensor.minDelay
                )
            }
            .sortedWith(compareBy<SensorDescriptor> { it.isTriggerOnly }.thenBy { it.displayName.lowercase() })
    }

    override fun observe(
        sensors: List<SensorDescriptor>,
        rate: SamplingRate,
        rateOverrides: Map<String, SamplingRate>
    ): Flow<SensorSample> = callbackFlow {
        if (sensors.isEmpty()) {
            awaitClose { }
            return@callbackFlow
        }

        val descriptorByHardware = resolveHardwareMap(sensors)
        if (descriptorByHardware.isEmpty()) {
            awaitClose { }
            return@callbackFlow
        }

        val handlerThread = HandlerThread("SensorLoggerThread").apply { start() }
        val handler = Handler(handlerThread.looper)
        val accuracyMap = HashMap<Sensor, Int>()

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val descriptor = descriptorByHardware[event.sensor] ?: return
                trySend(
                    SensorSample(
                        sensorId = descriptor.id,
                        sensorName = descriptor.name,
                        sensorType = descriptor.stringType,
                        epochMillis = System.currentTimeMillis(),
                        elapsedNanos = event.timestamp,
                        values = event.values.toList(),
                        accuracy = accuracyMap[event.sensor] ?: event.accuracy
                    )
                )
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                accuracyMap[sensor] = accuracy
            }
        }

        descriptorByHardware.forEach { (hardwareSensor, descriptor) ->
            sensorManager.registerListener(
                listener,
                hardwareSensor,
                descriptor.effectiveSamplingPeriodUs(rate, rateOverrides),
                handler
            )
        }

        awaitClose {
            sensorManager.unregisterListener(listener)
            handlerThread.quitSafely()
        }
    }

    override fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun resolveHardwareMap(targets: List<SensorDescriptor>): Map<Sensor, SensorDescriptor> {
        val rawSensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
        val seenIds = mutableMapOf<String, Int>()
        val targetById = targets.associateBy { it.id }

        val result = LinkedHashMap<Sensor, SensorDescriptor>()
        for (sensor in rawSensors) {
            val baseId = sensor.baseKey()
            val count = seenIds.getOrDefault(baseId, 0) + 1
            seenIds[baseId] = count
            val uniqueId = if (count == 1) baseId else "$baseId#$count"

            val descriptor = targetById[uniqueId] ?: continue
            if (descriptor.isTriggerOnly) continue
            val perm = descriptor.requiredPermission
            if (perm != null && !isGranted(perm)) continue

            result[sensor] = descriptor
        }
        return result
    }

    private fun Sensor.baseKey(): String =
        "${type}:${name}:${vendor}:${if (isWakeUpSensor) "w" else "nw"}"

    private fun requiredPermissionFor(sensorType: Int): String? = when (sensorType) {
        Sensor.TYPE_HEART_RATE -> Manifest.permission.BODY_SENSORS
        Sensor.TYPE_STEP_COUNTER,
        Sensor.TYPE_STEP_DETECTOR ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Manifest.permission.ACTIVITY_RECOGNITION
            } else {
                null
            }
        else -> null
    }
}
