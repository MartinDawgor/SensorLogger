package com.salihov.sensorlogger

import com.salihov.sensorlogger.data.analysis.FftAnalyzer
import com.salihov.sensorlogger.data.analysis.SessionAnalyzer
import com.salihov.sensorlogger.data.analysis.SessionComparator
import com.salihov.sensorlogger.domain.model.CsvLogDocument
import com.salihov.sensorlogger.domain.model.CsvLogRow
import com.salihov.sensorlogger.domain.model.RuleActionType
import com.salihov.sensorlogger.domain.model.RuleConditionType
import com.salihov.sensorlogger.domain.model.SensorSample
import com.salihov.sensorlogger.domain.model.SmartRule
import com.salihov.sensorlogger.domain.usecase.BlackboxRingBuffer
import com.salihov.sensorlogger.domain.usecase.SmartRulesEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class Block3FeaturesTest {

    @Test
    fun `fft analyzer detects dominant sine wave frequency`() {
        val sampleRateHz = 64f
        val targetHz = 10.0
        val signal = FloatArray(128) { i ->
            sin(2.0 * PI * targetHz * i / sampleRateHz).toFloat()
        }

        val spectrum = FftAnalyzer.computeMagnitudeSpectrum(signal, sampleRateHz)
        assertEquals(10f, spectrum.dominantHz, 0.8f)
        assertTrue(spectrum.peakAmplitude > 0.4f)
        assertTrue(spectrum.bins.isNotEmpty())
    }

    @Test
    fun `blackbox ring buffer evicts samples older than window`() {
        val buffer = BlackboxRingBuffer(windowMillis = 30_000L, maxSamples = 100)
        buffer.push(sampleAt(1_000L, 1f))
        buffer.push(sampleAt(15_000L, 2f))
        buffer.push(sampleAt(35_000L, 3f))

        assertEquals(2, buffer.size())
        val flushed = buffer.snapshotAndClear()
        assertEquals(listOf(15_000L, 35_000L), flushed.map { it.epochMillis })
        assertEquals(0, buffer.size())
    }

    @Test
    fun `smart rules evaluator respects condition and cooldown`() {
        var now = 10_000L
        val evaluator = SmartRulesEvaluator(clockMillis = { now })
        val rules = listOf(
            SmartRule(
                id = "r1",
                name = "Shock",
                condition = RuleConditionType.G_FORCE_ABOVE,
                threshold = 2.0f,
                action = RuleActionType.FLUSH_BLACKBOX,
                cooldownSec = 5,
                enabled = true
            )
        )

        assertEquals(emptyList<RuleActionType>(), evaluator.evaluate(rules, RuleConditionType.G_FORCE_ABOVE, 1.5f))
        assertEquals(listOf(RuleActionType.FLUSH_BLACKBOX), evaluator.evaluate(rules, RuleConditionType.G_FORCE_ABOVE, 2.5f))
        now += 2_000L
        assertEquals(emptyList<RuleActionType>(), evaluator.evaluate(rules, RuleConditionType.G_FORCE_ABOVE, 3.0f))
        now += 4_000L
        assertEquals(listOf(RuleActionType.FLUSH_BLACKBOX), evaluator.evaluate(rules, RuleConditionType.G_FORCE_ABOVE, 3.0f))
    }

    @Test
    fun `session comparator produces aligned comparison rows for shared sensors`() {
        val docA = doc("session_a.csv", listOf(0.1f, 0.2f, 9.8f))
        val docB = doc("session_b.csv", listOf(1.1f, 0.2f, 9.8f))
        val comparison = SessionComparator.compare(
            docA = docA,
            reportA = SessionAnalyzer.analyze(docA),
            docB = docB,
            reportB = SessionAnalyzer.analyze(docB)
        )

        assertEquals("session_a.csv", comparison.fileA)
        assertEquals("session_b.csv", comparison.fileB)
        assertEquals("Acc", comparison.primarySensorName)
        assertEquals(1, comparison.comparisons.size)
        assertEquals(0.1f, comparison.comparisons.single().meanA!!, 0.01f)
        assertEquals(1.1f, comparison.comparisons.single().meanB!!, 0.01f)
    }

    private fun sampleAt(epochMillis: Long, x: Float) = SensorSample(
        sensorId = "acc",
        sensorName = "Acc",
        sensorType = "android.sensor.accelerometer",
        epochMillis = epochMillis,
        elapsedNanos = epochMillis * 1_000_000L,
        values = listOf(x, 0f, 9.8f)
    )

    private fun doc(fileName: String, values: List<Float>): CsvLogDocument {
        val rows = (0 until 5).map { idx ->
            CsvLogRow(
                rowNumber = idx + 1,
                epochMillis = 1_700_000_000_000L + idx * 100L,
                elapsedNanos = (idx + 1) * 100_000_000L,
                sensorType = "android.sensor.accelerometer",
                sensorName = "Acc",
                values = values
            )
        }
        return CsvLogDocument(
            fileName = fileName,
            sizeBytes = 512L,
            totalDataRows = rows.size,
            isTruncated = false,
            firstTimestampMillis = rows.first().epochMillis,
            lastTimestampMillis = rows.last().epochMillis,
            sensorNames = listOf("Acc"),
            rows = rows,
            rawLines = emptyList()
        )
    }
}
