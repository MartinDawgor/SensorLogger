package com.salihov.sensorlogger.data.csv

import com.salihov.sensorlogger.domain.model.CsvDelimiter
import com.salihov.sensorlogger.domain.model.CsvLogDocument
import com.salihov.sensorlogger.domain.model.CsvSessionInfo
import com.salihov.sensorlogger.domain.model.SensorSample
import com.salihov.sensorlogger.domain.ports.CsvSessionStore
import com.salihov.sensorlogger.domain.ports.CsvSessionWriter
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

class CsvSessionManager(
    private val sessionsDir: File
) : CsvSessionStore {

    init {
        if (!sessionsDir.exists()) {
            sessionsDir.mkdirs()
        }
    }

    override fun openWriter(fileName: String, delimiter: CsvDelimiter): CsvSessionWriter {
        val file = resolveSafeFile(fileName)
        val writer = BufferedWriter(
            OutputStreamWriter(FileOutputStream(file, false), StandardCharsets.UTF_8)
        )
        return BufferedCsvSessionWriter(writer, delimiter)
    }

    override fun listSessions(): List<CsvSessionInfo> =
        sessionsDir.listFiles { file -> file.isFile && file.extension.equals("csv", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?.map { file ->
                CsvSessionInfo(
                    fileName = file.name,
                    sizeBytes = file.length(),
                    createdAtMillis = file.lastModified()
                )
            }
            ?: emptyList()

    override fun deleteSession(fileName: String): Boolean {
        val file = resolveSafeFile(fileName)
        return file.exists() && file.delete()
    }

    override fun renameSession(oldName: String, newName: String): Boolean {
        val source = resolveSafeFile(oldName)
        if (!source.exists()) return false
        val safeBase = File(newName).name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val targetName = if (safeBase.endsWith(".csv", ignoreCase = true)) safeBase else "$safeBase.csv"
        if (targetName.isBlank() || targetName == ".csv") return false
        val target = resolveSafeFile(targetName)
        if (target.exists()) return false
        return source.renameTo(target)
    }

    override fun readSessionBytes(fileName: String): ByteArray {
        val file = resolveSafeFile(fileName)
        return file.readBytes()
    }

    override fun loadLogDocument(fileName: String, maxRows: Int): CsvLogDocument {
        val file = resolveSafeFile(fileName)
        require(file.exists()) { "Файл $fileName не найден" }
        return CsvLogParser.parseFile(file, maxRows = maxRows)
    }

    fun getSessionFile(fileName: String): File = resolveSafeFile(fileName)

    private fun resolveSafeFile(fileName: String): File {
        val cleanName = File(fileName).name
        return File(sessionsDir, cleanName)
    }

    private class BufferedCsvSessionWriter(
        private val writer: BufferedWriter,
        private val delimiter: CsvDelimiter
    ) : CsvSessionWriter {

        override fun writeHeader() {
            if (delimiter == CsvDelimiter.SEMICOLON) {
                writer.write('\uFEFF'.code)
            }
            writer.write(CsvFormatter.buildHeader(delimiter.symbol))
            writer.newLine()
        }

        override fun append(sample: SensorSample) {
            writer.write(CsvFormatter.formatRow(sample, delimiter.symbol))
            writer.newLine()
        }

        override fun flush() {
            writer.flush()
        }

        override fun close() {
            writer.flush()
            writer.close()
        }
    }
}
