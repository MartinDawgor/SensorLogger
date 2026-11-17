package com.salihov.sensorlogger.data.streaming

import com.salihov.sensorlogger.domain.model.WebDashboardState
import com.salihov.sensorlogger.domain.ports.CsvSessionStore
import com.salihov.sensorlogger.domain.ports.WebDashboardServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

class LocalWebDashboardServer(
    private val sessionStore: CsvSessionStore,
    private val port: Int = 8090
) : WebDashboardServer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(WebDashboardState())
    override val state: Flow<WebDashboardState> = _state.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private val hits = AtomicLong(0L)

    override fun start(liveProvider: () -> Map<String, List<Float>>) {
        if (acceptJob?.isActive == true) return
        runCatching {
            val ss = ServerSocket(port)
            serverSocket = ss
            val hostIp = findLocalIpv4() ?: "127.0.0.1"
            val url = "http://$hostIp:$port"
            _state.value = WebDashboardState(running = true, url = url, requestsServed = hits.get())

            acceptJob = scope.launch {
                while (isActive && !ss.isClosed) {
                    val client = runCatching { ss.accept() }.getOrNull() ?: break
                    launch { handleClient(client, liveProvider) }
                }
            }
        }.onFailure {
            _state.value = WebDashboardState(running = false, url = null, requestsServed = hits.get())
        }
    }

    override fun stop() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        acceptJob?.cancel()
        acceptJob = null
        _state.value = _state.value.copy(running = false, url = null)
    }

    private fun handleClient(client: Socket, liveProvider: () -> Map<String, List<Float>>) {
        client.use { socket ->
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val requestLine = reader.readLine() ?: return
            val path = requestLine.split(' ').getOrNull(1)?.substringBefore('?') ?: "/"

            val count = hits.incrementAndGet()
            val cur = _state.value
            if (cur.running) _state.value = cur.copy(requestsServed = count)

            val out = socket.getOutputStream()
            when {
                path == "/" -> {
                    val html = buildDashboardHtml()
                    writeHttp(out, "200 OK", "text/html; charset=utf-8", html.toByteArray(StandardCharsets.UTF_8))
                }
                path == "/api/live" -> {
                    val json = buildLiveJson(liveProvider())
                    writeHttp(out, "200 OK", "application/json; charset=utf-8", json.toByteArray(StandardCharsets.UTF_8))
                }
                path.startsWith("/download/") -> {
                    val name = URLDecoder.decode(path.removePrefix("/download/"), "UTF-8")
                    val bytes = runCatching { sessionStore.readSessionBytes(name) }.getOrNull()
                    if (bytes != null) {
                        writeHttp(out, "200 OK", "text/csv; charset=utf-8", bytes, downloadName = name)
                    } else {
                        writeHttp(out, "404 Not Found", "text/plain", "Not Found".toByteArray())
                    }
                }
                else -> writeHttp(out, "404 Not Found", "text/plain", "Not Found".toByteArray())
            }
        }
    }

    private fun writeHttp(
        out: java.io.OutputStream,
        status: String,
        contentType: String,
        body: ByteArray,
        downloadName: String? = null
    ) {
        val headers = buildString {
            append("HTTP/1.1 $status\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${body.size}\r\n")
            if (downloadName != null) {
                append("Content-Disposition: attachment; filename=\"$downloadName\"\r\n")
            }
            append("Connection: close\r\n\r\n")
        }
        out.write(headers.toByteArray(StandardCharsets.US_ASCII))
        out.write(body)
        out.flush()
    }

    private fun buildLiveJson(readings: Map<String, List<Float>>): String {
        val sessions = sessionStore.listSessions()
        return buildString {
            append("{\"readings\":{")
            readings.entries.forEachIndexed { idx, (k, vals) ->
                val escapedKey = k.replace("\"", "\\\"")
                val numList = vals.joinToString(",") { String.format(Locale.US, "%.4f", it) }
                append("\"$escapedKey\":[$numList]")
                if (idx < readings.size - 1) append(',')
            }
            append("},\"sessions\":[")
            sessions.forEachIndexed { idx, s ->
                val escapedName = s.fileName.replace("\"", "\\\"")
                append("{\"name\":\"$escapedName\",\"size\":${s.sizeBytes}}")
                if (idx < sessions.size - 1) append(',')
            }
            append("]}")
        }
    }

    private fun buildDashboardHtml(): String = """
        <!DOCTYPE html><html lang='ru'><head><meta charset='UTF-8'>
        <title>AIDA SensorLogger Web Dashboard</title>
        <style>
        body{font-family:system-ui,sans-serif;background:#f2f4f7;color:#111827;margin:20px;font-size:13px}
        h1{color:#1565c0;margin-bottom:8px}
        table{width:100%;border-collapse:collapse;background:#fff;border:1px solid #dce1e8;margin-bottom:18px}
        th,td{border:1px solid #dce1e8;padding:6px 10px;text-align:left;font-family:ui-monospace,monospace}
        th{background:#eceff3}
        </style></head><body>
        <h1>AIDA SensorLogger — Wi-Fi Telemetry Dashboard</h1>
        <p>Автообновление каждые 500 мс</p>
        <table><thead><tr><th>Датчик (ID)</th><th>Каналы</th></tr></thead><tbody id='tb'></tbody></table>
        <h2>Сохранённые сессии CSV</h2>
        <ul id='files'></ul>
        <script>
        async function tick(){
          try{
            const r=await fetch('/api/live');const d=await r.json();
            const tb=document.getElementById('tb');tb.innerHTML='';
            for(const [k,v] of Object.entries(d.readings)){
              const tr=document.createElement('tr');
              tr.innerHTML='<td>'+k+'</td><td>'+v.map(x=>x.toFixed(3)).join(' | ')+'</td>';
              tb.appendChild(tr);
            }
            const ul=document.getElementById('files');ul.innerHTML='';
            for(const f of d.sessions){
              const li=document.createElement('li');
              li.innerHTML='<a href="/download/'+encodeURIComponent(f.name)+'">'+f.name+'</a> ('+f.size+' B)';
              ul.appendChild(li);
            }
          }catch(e){}
        }
        setInterval(tick,500);tick();
        </script></body></html>
    """.trimIndent()

    private fun findLocalIpv4(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList().asSequence() }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
    }.getOrNull()
}
