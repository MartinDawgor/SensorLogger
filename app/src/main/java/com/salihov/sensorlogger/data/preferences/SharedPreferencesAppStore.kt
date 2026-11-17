package com.salihov.sensorlogger.data.preferences

import android.content.Context
import com.salihov.sensorlogger.domain.model.CsvDelimiter
import com.salihov.sensorlogger.domain.model.RuleActionType
import com.salihov.sensorlogger.domain.model.RuleConditionType
import com.salihov.sensorlogger.domain.model.SamplingRate
import com.salihov.sensorlogger.domain.model.SensorCalibration
import com.salihov.sensorlogger.domain.model.SmartRule
import com.salihov.sensorlogger.domain.model.StreamingConfig
import com.salihov.sensorlogger.domain.model.StreamingProtocol
import com.salihov.sensorlogger.domain.model.TimestampFormat
import com.salihov.sensorlogger.domain.ports.AppPreferencesStore

class SharedPreferencesAppStore(context: Context) : AppPreferencesStore {

    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    private val calPrefs by lazy { context.getSharedPreferences(CAL_PREFS_NAME, Context.MODE_PRIVATE) }

    override fun getCsvDelimiter(): CsvDelimiter {
        val raw = prefs.getString(KEY_CSV_DELIMITER, CsvDelimiter.SEMICOLON.name)
        return CsvDelimiter.entries.find { it.name == raw } ?: CsvDelimiter.SEMICOLON
    }

    override fun setCsvDelimiter(delimiter: CsvDelimiter) {
        prefs.edit().putString(KEY_CSV_DELIMITER, delimiter.name).apply()
    }

    override fun getTimestampFormat(): TimestampFormat {
        val raw = prefs.getString(KEY_TIMESTAMP_FORMAT, TimestampFormat.ISO_8601.name)
        return TimestampFormat.entries.find { it.name == raw } ?: TimestampFormat.ISO_8601
    }

    override fun setTimestampFormat(format: TimestampFormat) {
        prefs.edit().putString(KEY_TIMESTAMP_FORMAT, format.name).apply()
    }

    override fun getCalibrations(): Map<String, SensorCalibration> {
        val result = LinkedHashMap<String, SensorCalibration>()
        for ((key, rawVal) in calPrefs.all) {
            val encoded = rawVal as? String ?: continue
            val parts = encoded.split('|', limit = 2)
            if (parts.size != 2) continue
            val ts = parts[0].toLongOrNull() ?: continue
            val offsets = parts[1].split(';').mapNotNull { it.toFloatOrNull() }
            if (offsets.isNotEmpty()) {
                result[key] = SensorCalibration(
                    sensorId = key,
                    offsets = offsets,
                    calibratedAtMillis = ts
                )
            }
        }
        return result
    }

    override fun saveCalibration(calibration: SensorCalibration) {
        val encoded = "${calibration.calibratedAtMillis}|${calibration.offsets.joinToString(";")}"
        calPrefs.edit().putString(calibration.sensorId, encoded).apply()
    }

    override fun removeCalibration(sensorId: String) {
        calPrefs.edit().remove(sensorId).apply()
    }

    override fun getStreamingConfig(): StreamingConfig {
        val proto = prefs.getString(KEY_STREAM_PROTO, StreamingProtocol.HTTP_POST.name)
            ?.let { name -> StreamingProtocol.entries.find { it.name == name } }
            ?: StreamingProtocol.HTTP_POST
        return StreamingConfig(
            enabled = prefs.getBoolean(KEY_STREAM_ENABLED, false),
            protocol = proto,
            httpUrl = prefs.getString(KEY_STREAM_HTTP_URL, "http://192.168.1.100:8080/ingest") ?: "http://192.168.1.100:8080/ingest",
            mqttHost = prefs.getString(KEY_STREAM_MQTT_HOST, "192.168.1.100") ?: "192.168.1.100",
            mqttPort = prefs.getInt(KEY_STREAM_MQTT_PORT, 1883),
            mqttTopic = prefs.getString(KEY_STREAM_MQTT_TOPIC, "sensorlogger/telemetry") ?: "sensorlogger/telemetry"
        )
    }

    override fun saveStreamingConfig(config: StreamingConfig) {
        prefs.edit()
            .putBoolean(KEY_STREAM_ENABLED, config.enabled)
            .putString(KEY_STREAM_PROTO, config.protocol.name)
            .putString(KEY_STREAM_HTTP_URL, config.httpUrl)
            .putString(KEY_STREAM_MQTT_HOST, config.mqttHost)
            .putInt(KEY_STREAM_MQTT_PORT, config.mqttPort)
            .putString(KEY_STREAM_MQTT_TOPIC, config.mqttTopic)
            .apply()
    }

    override fun getRules(): List<SmartRule>? {
        val raw = prefs.getString(KEY_RULES, null) ?: return null
        if (raw.isBlank()) return emptyList()
        return raw.lines().mapNotNull { line ->
            val p = line.split('\t')
            if (p.size < 6) return@mapNotNull null
            val cond = RuleConditionType.entries.find { it.name == p[2] } ?: return@mapNotNull null
            val act = RuleActionType.entries.find { it.name == p[4] } ?: return@mapNotNull null
            SmartRule(
                id = p[0],
                name = p[1],
                condition = cond,
                threshold = p[3].toFloatOrNull() ?: return@mapNotNull null,
                action = act,
                enabled = p[5].toBooleanStrictOrNull() ?: false
            )
        }
    }

    override fun saveRules(rules: List<SmartRule>) {
        val encoded = rules.joinToString("\n") { r ->
            listOf(
                r.id,
                r.name.replace('\t', ' ').replace('\n', ' '),
                r.condition.name,
                r.threshold.toString(),
                r.action.name,
                r.enabled.toString()
            ).joinToString("\t")
        }
        prefs.edit().putString(KEY_RULES, encoded).apply()
    }

    override fun getAnnotationPresets(): List<String>? {
        val raw = prefs.getString(KEY_ANNOTATION_PRESETS, null) ?: return null
        return raw.split('\n').filter { it.isNotBlank() }
    }

    override fun saveAnnotationPresets(presets: List<String>) {
        prefs.edit().putString(KEY_ANNOTATION_PRESETS, presets.joinToString("\n")).apply()
    }

    override fun getRateOverrides(): Map<String, SamplingRate> {
        val raw = prefs.getString(KEY_RATE_OVERRIDES, null) ?: return emptyMap()
        return raw.lines()
            .asSequence()
            .mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size != 2) return@mapNotNull null
                val rate = SamplingRate.entries.find { it.name == parts[1] } ?: return@mapNotNull null
                parts[0] to rate
            }
            .toMap()
    }

    override fun saveRateOverrides(overrides: Map<String, SamplingRate>) {
        val encoded = overrides.entries.joinToString("\n") { (id, rate) -> "$id\t${rate.name}" }
        prefs.edit().putString(KEY_RATE_OVERRIDES, encoded).apply()
    }

    companion object {
        private const val PREFS_NAME = "sensor_logger_prefs"
        private const val CAL_PREFS_NAME = "sensor_logger_calibrations"
        private const val KEY_CSV_DELIMITER = "csv_delimiter"
        private const val KEY_TIMESTAMP_FORMAT = "timestamp_format"
        private const val KEY_STREAM_ENABLED = "stream_enabled"
        private const val KEY_STREAM_PROTO = "stream_proto"
        private const val KEY_STREAM_HTTP_URL = "stream_http_url"
        private const val KEY_STREAM_MQTT_HOST = "stream_mqtt_host"
        private const val KEY_STREAM_MQTT_PORT = "stream_mqtt_port"
        private const val KEY_STREAM_MQTT_TOPIC = "stream_mqtt_topic"
        private const val KEY_RULES = "smart_rules_v1"
        private const val KEY_ANNOTATION_PRESETS = "annotation_presets_v1"
        private const val KEY_RATE_OVERRIDES = "rate_overrides_v1"
    }
}
