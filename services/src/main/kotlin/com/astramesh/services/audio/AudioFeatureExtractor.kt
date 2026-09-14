package com.astramesh.services.audio

import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * High-performance 80-bin Log-Mel Spectrogram Acoustic Feature Extractor for IndicConformer STT.
 * Matches NeMo / IndicConformer acoustic preprocessor contract:
 * - Sample rate: 16000 Hz
 * - Frame size (n_fft / win_length): 400 samples (25ms), 512 FFT points
 * - Hop size: 160 samples (10ms)
 * - 80 Mel filterbank bins (0 Hz to 8000 Hz)
 * - Per-feature mean & variance normalization across time steps
 */
object AudioFeatureExtractor {
    private const val SAMPLE_RATE = 16000
    private const val N_FFT = 512
    private const val WIN_LENGTH = 400
    private const val HOP_LENGTH = 160
    private const val N_MELS = 80
    private const val F_MIN = 0.0f
    private const val F_MAX = 8000.0f

    private val hannWindow = FloatArray(WIN_LENGTH)
    private val melFilters = Array(N_MELS) { FloatArray(N_FFT / 2 + 1) }

    init {
        // Precalculate Hann Window
        for (i in 0 until WIN_LENGTH) {
            hannWindow[i] = 0.5f * (1.0f - cos(2.0 * Math.PI * i / (WIN_LENGTH - 1)).toFloat())
        }

        // Precalculate 80 Mel Filterbank Matrix
        fun hzToMel(hz: Double): Double = 2595.0 * Math.log10(1.0 + hz / 700.0)
        fun melToHz(mel: Double): Double = 700.0 * (10.0.pow(mel / 2595.0) - 1.0)

        val minMel = hzToMel(F_MIN.toDouble())
        val maxMel = hzToMel(F_MAX.toDouble())
        val melPoints = DoubleArray(N_MELS + 2)
        for (i in 0 until N_MELS + 2) {
            melPoints[i] = minMel + i * (maxMel - minMel) / (N_MELS + 1)
        }

        val binPoints = IntArray(N_MELS + 2)
        for (i in 0 until N_MELS + 2) {
            val hz = melToHz(melPoints[i])
            binPoints[i] = floor((N_FFT + 1) * hz / SAMPLE_RATE).toInt()
        }

        val numFreqs = N_FFT / 2 + 1
        for (m in 1..N_MELS) {
            val fMinus = binPoints[m - 1]
            val f0 = binPoints[m]
            val fPlus = binPoints[m + 1]

            for (k in fMinus until f0) {
                if (k < numFreqs) {
                    melFilters[m - 1][k] = (k - fMinus).toFloat() / max(1, f0 - fMinus).toFloat()
                }
            }
            for (k in f0 until fPlus) {
                if (k < numFreqs) {
                    melFilters[m - 1][k] = (fPlus - k).toFloat() / max(1, fPlus - f0).toFloat()
                }
            }
        }
    }

    data class FeatureOutput(
        val features: FloatArray, // Flattened 1D array of shape [80, timeSteps]
        val timeSteps: Int
    )

    /**
     * Extracts normalized Log-Mel Spectrogram features [80, timeSteps] from 16kHz PCM float audio.
     */
    fun extractFeatures(samples: FloatArray): FeatureOutput {
        val paddedAudio = if (samples.size < WIN_LENGTH) {
            FloatArray(WIN_LENGTH) { i -> if (i < samples.size) samples[i] else 0.0f }
        } else {
            samples
        }

        val timeSteps = max(1, (paddedAudio.size - WIN_LENGTH) / HOP_LENGTH + 1)
        val numFreqs = N_FFT / 2 + 1
        val rawMel = Array(N_MELS) { FloatArray(timeSteps) }

        val real = FloatArray(N_FFT)
        val imag = FloatArray(N_FFT)
        val mag = FloatArray(numFreqs)

        for (t in 0 until timeSteps) {
            val offset = t * HOP_LENGTH
            for (n in 0 until N_FFT) {
                if (n < WIN_LENGTH && (offset + n) < paddedAudio.size) {
                    real[n] = paddedAudio[offset + n] * hannWindow[n]
                } else {
                    real[n] = 0.0f
                }
                imag[n] = 0.0f
            }

            // Radix-2 FFT / DFT for N_FFT = 512
            fft512(real, imag)

            // Compute magnitude spectrum
            for (k in 0 until numFreqs) {
                mag[k] = (real[k] * real[k] + imag[k] * imag[k])
            }

            // Apply 80 Mel filters
            for (m in 0 until N_MELS) {
                var sum = 0.0f
                val filter = melFilters[m]
                for (k in 0 until numFreqs) {
                    sum += filter[k] * mag[k]
                }
                rawMel[m][t] = ln(max(sum, 1e-5f))
            }
        }

        // Per-feature mean/variance normalization across time steps
        val flattened = FloatArray(N_MELS * timeSteps)
        var flatIdx = 0
        for (m in 0 until N_MELS) {
            var sum = 0.0
            for (t in 0 until timeSteps) {
                sum += rawMel[m][t]
            }
            val mean = (sum / timeSteps).toFloat()

            var varSum = 0.0
            for (t in 0 until timeSteps) {
                val diff = rawMel[m][t] - mean
                varSum += diff * diff
            }
            val std = sqrt(varSum / timeSteps + 1e-5).toFloat()

            for (t in 0 until timeSteps) {
                flattened[flatIdx++] = (rawMel[m][t] - mean) / std
            }
        }

        return FeatureOutput(flattened, timeSteps)
    }

    private fun fft512(real: FloatArray, imag: FloatArray) {
        val n = 512
        // Bit-reversal
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tempR = real[i]
                real[i] = real[j]
                real[j] = tempR

                val tempI = imag[i]
                imag[i] = imag[j]
                imag[j] = tempI
            }
            var k = n shr 1
            while (k <= j) {
                j -= k
                k = k shr 1
            }
            j += k
        }

        // Cooley-Tukey Radix-2 FFT
        var len = 2
        while (len <= n) {
            val halfLen = len shr 1
            val angle = -2.0 * Math.PI / len
            val wStepR = cos(angle).toFloat()
            val wStepI = sin(angle).toFloat()

            var i = 0
            while (i < n) {
                var wR = 1.0f
                var wI = 0.0f
                for (k in 0 until halfLen) {
                    val uR = real[i + k]
                    val uI = imag[i + k]

                    val tIdx = i + k + halfLen
                    val vR = real[tIdx] * wR - imag[tIdx] * wI
                    val vI = real[tIdx] * wI + imag[tIdx] * wR

                    real[i + k] = uR + vR
                    imag[i + k] = uI + vI
                    real[tIdx] = uR - vR
                    imag[tIdx] = uI - vI

                    val nextWR = wR * wStepR - wI * wStepI
                    val nextWI = wR * wStepI + wI * wStepR
                    wR = nextWR
                    wI = nextWI
                }
                i += len
            }
            len = len shl 1
        }
    }
}
