package com.salihov.sensorlogger.data.device

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.view.Display
import com.salihov.sensorlogger.domain.model.CpuCoreInfo
import com.salihov.sensorlogger.domain.model.DeviceDiagnostics
import com.salihov.sensorlogger.domain.model.ThermalZoneInfo
import com.salihov.sensorlogger.domain.ports.DeviceDiagnosticsProvider
import java.io.File

class AndroidDeviceDiagnosticsProvider(
    private val context: Context
) : DeviceDiagnosticsProvider {

    override fun snapshot(): DeviceDiagnostics {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)

        val statFs = runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()
        val totalStorage = statFs?.totalBytes ?: 0L
        val freeStorage = statFs?.availableBytes ?: 0L

        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val display = dm?.getDisplay(Display.DEFAULT_DISPLAY)
        val metrics = context.resources.displayMetrics
        val resolution = "${metrics.widthPixels} × ${metrics.heightPixels} px"
        val refreshRate = display?.refreshRate ?: 60f

        val batteryIntent = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()

        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) (level * 100) / scale else -1
        val tempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val voltage = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val statusCode = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val healthCode = batteryIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val pluggedCode = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val tech = batteryIntent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY).orEmpty()

        val coreCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val cores = (0 until coreCount).map { idx ->
            CpuCoreInfo(
                coreIndex = idx,
                currentFreqKhz = readSysLong("/sys/devices/system/cpu/cpu$idx/cpufreq/scaling_cur_freq"),
                maxFreqKhz = readSysLong("/sys/devices/system/cpu/cpu$idx/cpufreq/cpuinfo_max_freq")
            )
        }

        val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOfNotNull(
                Build.SOC_MANUFACTURER.takeIf { it != Build.UNKNOWN && it.isNotBlank() },
                Build.SOC_MODEL.takeIf { it != Build.UNKNOWN && it.isNotBlank() }
            ).joinToString(" ")
        } else {
            ""
        }

        val brightness = runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, -1)
        }.getOrDefault(-1)

        val (netType, wifiSsid, wifiRssi, wifiSpeed) = readNetworkSnapshot()
        val thermalZones = readThermalZones()

        return DeviceDiagnostics(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            deviceCode = "${Build.DEVICE} (${Build.PRODUCT})",
            board = Build.BOARD.orEmpty(),
            hardware = Build.HARDWARE.orEmpty(),
            socModel = socModel.ifBlank { Build.HARDWARE.orEmpty() },
            androidVersion = Build.VERSION.RELEASE.orEmpty(),
            apiLevel = Build.VERSION.SDK_INT,
            securityPatch = Build.VERSION.SECURITY_PATCH.orEmpty(),
            buildId = Build.DISPLAY.orEmpty(),
            abis = Build.SUPPORTED_ABIS?.toList().orEmpty(),
            cpuCoresCount = coreCount,
            cpuCores = cores,
            totalRamBytes = memInfo.totalMem,
            availableRamBytes = memInfo.availMem,
            totalStorageBytes = totalStorage,
            freeStorageBytes = freeStorage,
            displayResolution = resolution,
            displayDensityDpi = metrics.densityDpi,
            displayRefreshHz = refreshRate,
            batteryLevelPct = pct,
            batteryTempC = tempTenths / 10f,
            batteryVoltageMv = voltage,
            batteryStatus = batteryStatusLabel(statusCode),
            batteryHealth = batteryHealthLabel(healthCode),
            batteryPlugged = batteryPluggedLabel(pluggedCode),
            batteryTechnology = tech,
            screenBrightness = brightness,
            networkType = netType,
            wifiSsid = wifiSsid,
            wifiRssiDbm = wifiRssi,
            wifiLinkSpeedMbps = wifiSpeed,
            thermalZones = thermalZones
        )
    }

    private data class NetInfo(val type: String, val ssid: String, val rssi: Int?, val linkMbps: Int?)

    @Suppress("DEPRECATION")
    private fun readNetworkSnapshot(): NetInfo {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val caps = cm?.getNetworkCapabilities(cm.activeNetwork) ?: return NetInfo("Нет сети", "—", null, null)
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val info = runCatching { wm?.connectionInfo }.getOrNull()
                val ssid = info?.ssid?.trim('"')?.takeIf { it.isNotBlank() && it != "<unknown ssid>" } ?: "Wi‑Fi"
                val rssi = info?.rssi?.takeIf { it in -120..-1 }
                val speed = info?.linkSpeed?.takeIf { it > 0 }
                NetInfo("Wi‑Fi", ssid, rssi, speed)
            }
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetInfo("Мобильная сеть", "—", null, null)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetInfo("Ethernet", "—", null, null)
            else -> NetInfo("Подключено", "—", null, null)
        }
    }

    private fun readThermalZones(): List<ThermalZoneInfo> = runCatching {
        val dir = File("/sys/class/thermal")
        if (!dir.exists() || !dir.isDirectory) return@runCatching emptyList()
        dir.listFiles { f -> f.name.startsWith("thermal_zone") }
            ?.sortedBy { it.name.removePrefix("thermal_zone").toIntOrNull() ?: 999 }
            ?.mapNotNull { zoneDir ->
                val type = File(zoneDir, "type").takeIf { it.canRead() }?.readText()?.trim() ?: return@mapNotNull null
                val rawTemp = File(zoneDir, "temp").takeIf { it.canRead() }?.readText()?.trim()?.toFloatOrNull() ?: return@mapNotNull null
                val tempC = if (rawTemp > 1000f) rawTemp / 1000f else rawTemp
                if (tempC in -40f..150f) ThermalZoneInfo(type, tempC) else null
            }
            ?.take(12)
            .orEmpty()
    }.getOrDefault(emptyList())

    private fun readSysLong(path: String): Long? = runCatching {
        val file = File(path)
        if (!file.exists() || !file.canRead()) return@runCatching null
        file.readText().trim().toLongOrNull()
    }.getOrNull()

    private fun batteryStatusLabel(code: Int): String = when (code) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "Заряжается"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "Разряжается"
        BatteryManager.BATTERY_STATUS_FULL -> "Заряжен"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Не заряжается"
        else -> "Неизвестно"
    }

    private fun batteryHealthLabel(code: Int): String = when (code) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Хорошее"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Перегрев"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Изношена"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Перенапряжение"
        BatteryManager.BATTERY_HEALTH_COLD -> "Переохлаждение"
        else -> "—"
    }

    private fun batteryPluggedLabel(code: Int): String = when (code) {
        BatteryManager.BATTERY_PLUGGED_AC -> "Сеть (AC)"
        BatteryManager.BATTERY_PLUGGED_USB -> "USB"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Беспроводная"
        else -> "От батареи"
    }
}
