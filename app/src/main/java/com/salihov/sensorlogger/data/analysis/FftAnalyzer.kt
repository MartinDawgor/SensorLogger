package com.salihov.sensorlogger.data.analysis

import com.salihov.sensorlogger.domain.model.FftSpectrumSnapshot
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object FftAnalyzer {

    fun computeMagnitudeSpectrum(
        magnitudes: FloatArray,
        sampleRateHz: Float
    ): FftSpectrumSnapshot {
        val n = highestPowerOfTwo(magnitudes.size).coerceAtMost(128)
        if (n < 16 || sampleRateHz <= 0f) return FftSpectrumSnapshot()

        val real = DoubleArray(n)
        val imag = DoubleArray(n)
        var mean = 0.0
        val offset = magnitudes.size - n
        for (i in 0 until n) {
            mean += magnitudes[offset + i].toDouble()
        }
        mean /= n

        for (i in 0 until n) {
            val hann = 0.5 * (1.0 - cos(2.0 * PI * i / (n - 1)))
            real[i] = (magnitudes[offset + i] - mean) * hann
        }

        fftInPlace(real, imag)

        val half = n / 2
        val amps = FloatArray(half)
        var peakIdx = 1
        var peakAmp = 0f
        for (k in 1 until half) {
            val amp = (2.0 * sqrt(real[k] * real[k] + imag[k] * imag[k]) / n).toFloat()
            amps[k] = amp
            if (amp > peakAmp) {
                peakAmp = amp
                peakIdx = k
            }
        }

        val dominantHz = peakIdx * (sampleRateHz / n)
        return FftSpectrumSnapshot(
            dominantHz = dominantHz,
            peakAmplitude = peakAmp,
            bins = amps.drop(1)
        )
    }

    private fun highestPowerOfTwo(size: Int): Int {
        var p = 1
        while (p * 2 <= size) p *= 2
        return p
    }

    private fun fftInPlace(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
        }

        var len = 2
        while (len <= n) {
            val angle = -2.0 * PI / len
            val wLenRe = cos(angle)
            val wLenIm = sin(angle)
            var i = 0
            while (i < n) {
                var wRe = 1.0
                var wIm = 0.0
                val halfLen = len shr 1
                for (k in 0 until halfLen) {
                    val uRe = real[i + k]
                    val uIm = imag[i + k]
                    val idx = i + k + halfLen
                    val vRe = real[idx] * wRe - imag[idx] * wIm
                    val vIm = real[idx] * wIm + imag[idx] * wRe
                    real[i + k] = uRe + vRe
                    imag[i + k] = uIm + vIm
                    real[idx] = uRe - vRe
                    imag[idx] = uIm - vIm
                    val nextRe = wRe * wLenRe - wIm * wLenIm
                    wIm = wRe * wLenIm + wIm * wLenRe
                    wRe = nextRe
                }
                i += len
            }
            len = len shl 1
        }
    }
}
