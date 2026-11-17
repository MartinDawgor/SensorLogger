package com.salihov.sensorlogger.data.analysis

import com.salihov.sensorlogger.domain.model.CsvLogDocument
import com.salihov.sensorlogger.domain.model.SensorComparisonRow
import com.salihov.sensorlogger.domain.model.SessionComparisonReport
import com.salihov.sensorlogger.domain.model.SessionAnalysisReport

object SessionComparator {

    fun compare(
        docA: CsvLogDocument,
        reportA: SessionAnalysisReport,
        docB: CsvLogDocument,
        reportB: SessionAnalysisReport
    ): SessionComparisonReport {
        val mapA = reportA.verdicts.associateBy { it.sensorName }
        val mapB = reportB.verdicts.associateBy { it.sensorName }
        val allSensors = (mapA.keys + mapB.keys).toList()

        val rows = allSensors.map { name ->
            val vA = mapA[name]
            val vB = mapB[name]
            val stA = vA?.channelStats?.firstOrNull()
            val stB = vB?.channelStats?.firstOrNull()
            SensorComparisonRow(
                sensorName = name,
                meanA = stA?.mean,
                meanB = stB?.mean,
                stdA = stA?.stdDev,
                stdB = stB?.stdDev,
                verdictA = vA?.severity?.label ?: "—",
                verdictB = vB?.severity?.label ?: "—"
            )
        }

        val primarySensor = allSensors.firstOrNull { it in mapA && it in mapB } ?: allSensors.firstOrNull()
        val seriesA = primarySensor?.let { name ->
            downsampleValues(docA.rows.asSequence().filter { it.sensorName == name }.map { it.values }.toList())
        }.orEmpty()
        val seriesB = primarySensor?.let { name ->
            downsampleValues(docB.rows.asSequence().filter { it.sensorName == name }.map { it.values }.toList())
        }.orEmpty()

        return SessionComparisonReport(
            fileA = docA.fileName,
            fileB = docB.fileName,
            rowsA = docA.totalDataRows,
            rowsB = docB.totalDataRows,
            durationSecA = docA.durationMillis / 1000f,
            durationSecB = docB.durationMillis / 1000f,
            comparisons = rows,
            seriesA = seriesA,
            seriesB = seriesB,
            primarySensorName = primarySensor
        )
    }

    private fun downsampleValues(points: List<List<Float>>, maxPoints: Int = 120): List<List<Float>> {
        if (points.size <= maxPoints) return points
        val step = points.size.toFloat() / maxPoints
        return List(maxPoints) { i -> points[(i * step).toInt().coerceAtMost(points.lastIndex)] }
    }
}
