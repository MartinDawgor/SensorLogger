package com.salihov.sensorlogger.data.streaming

import com.salihov.sensorlogger.domain.model.SensorSample
import com.salihov.sensorlogger.domain.model.StreamingConfig
import com.salihov.sensorlogger.domain.model.StreamingProtocol
import com.salihov.sensorlogger.domain.ports.TelemetryStreamer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale

class NetworkTelemetryStreamer : TelemetryStreamer {

    override suspend fun sendBatch(config: StreamingConfig, samples: List<SensorSample>): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (samples.isEmpty()) return@runCatching
                val payload = encodeSamplesJson(samples)
                when (config.protocol) {
                    StreamingProtocol.HTTP_POST -> postHttpJson(config.httpUrl, payload)
                    StreamingProtocol.MQTT_TCP -> publishMqtt(config.mqttHost, config.mqttPort, config.mqttTopic, payload)
                }
            }
        }

    private fun postHttpJson(urlStr: String, json: String) {
        val url = URL(urlStr)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 4_000
            readTimeout = 4_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        try {
            conn.outputStream.use { os ->
                os.write(json.toByteArray(StandardCharsets.UTF_8))
                os.flush()
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                error("HTTP $code")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun publishMqtt(host: String, port: Int, topic: String, payload: String) {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), 4_000)
            socket.soTimeout = 4_000
            val out = socket.getOutputStream()
            val inp = socket.getInputStream()

            writeMqttConnect(out, clientId = "aida_sensor_logger")
            val connack = ByteArray(4)
            val read = inp.read(connack)
            if (read < 4 || connack[0].toInt() != 0x20 || connack[3].toInt() != 0) {
                error("MQTT CONNACK error")
            }

            writeMqttPublish(out, topic, payload.toByteArray(StandardCharsets.UTF_8))
            out.write(byteArrayOf(0xE0.toByte(), 0x00))
            out.flush()
        }
    }

    private fun writeMqttConnect(out: OutputStream, clientId: String) {
        val idBytes = clientId.toByteArray(StandardCharsets.UTF_8)
        val body = ByteArrayOutputStream().apply {
            write(0x00); write(0x04)
            write("MQTT".toByteArray(StandardCharsets.UTF_8))
            write(0x04) // protocol level 4 (3.1.1)
            write(0x02) // clean session
            write(0x00); write(30) // keep alive 30s
            write((idBytes.size shr 8) and 0xFF); write(idBytes.size and 0xFF)
            write(idBytes)
        }.toByteArray()

        out.write(0x10)
        writeRemainingLength(out, body.size)
        out.write(body)
        out.flush()
    }

    private fun writeMqttPublish(out: OutputStream, topic: String, payload: ByteArray) {
        val topicBytes = topic.toByteArray(StandardCharsets.UTF_8)
        val remaining = 2 + topicBytes.size + payload.size
        out.write(0x30)
        writeRemainingLength(out, remaining)
        out.write((topicBytes.size shr 8) and 0xFF)
        out.write(topicBytes.size and 0xFF)
        out.write(topicBytes)
        out.write(payload)
        out.flush()
    }

    private fun writeRemainingLength(out: OutputStream, length: Int) {
        var x = length
        do {
            var digit = x % 128
            x /= 128
            if (x > 0) digit = digit or 0x80
            out.write(digit)
        } while (x > 0)
    }

    companion object {
        fun encodeSamplesJson(samples: List<SensorSample>): String {
            val sb = StringBuilder()
            sb.append("{\"samples\":[")
            samples.forEachIndexed { idx, s ->
                val vals = s.values.joinToString(",") { String.format(Locale.US, "%.5f", it) }
                val escapedName = s.sensorName.replace("\"", "\\\"")
                val escapedType = s.sensorType.replace("\"", "\\\"")
                sb.append("{\"t\":${s.epochMillis},\"type\":\"$escapedType\",\"name\":\"$escapedName\",\"values\":[$vals]}")
                if (idx < samples.lastIndex) sb.append(',')
            }
            sb.append("]}")
            return sb.toString()
        }
    }
}
