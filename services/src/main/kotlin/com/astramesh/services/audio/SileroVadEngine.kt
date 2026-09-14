package com.astramesh.services.audio

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import com.astramesh.common.AstraLog
import com.astramesh.core.SttConfig
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.util.ArrayDeque
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class VADEventType {
    SILENCE,
    SPEECH_START,
    SPEECH_ACTIVE,
    SPEECH_END
}

data class VADEvent(
    val eventType: VADEventType,
    val probability: Float,
    val isSpeech: Boolean,
    val segmentAudio: FloatArray? = null,
    val timestampMs: Long = 0L
)

/**
 * On-Device Silero VAD v5 Engine using ONNX Runtime Mobile.
 * Operates on 16kHz mono PCM frames (512 samples) with 64-sample rolling context (576 samples total).
 * Strictly zero network calls, sub-millisecond per-frame inference.
 */
class SileroVadEngine(
    private val context: Context,
    private val modelAssetPath: String = "models/silero_vad.onnx"
) {
    private val tag = "ASTRA_VOICE"
    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null

    // Hidden state: [2, 1, 128] float32 tensor
    private var hiddenState: Array<Array<FloatArray>> = Array(2) { Array(1) { FloatArray(128) } }
    private var contextBuffer = FloatArray(SttConfig.CONTEXT_SIZE_SAMPLES)

    // Streaming state machine
    private var isTriggered = false
    private var consecutiveSpeechFrames = 0
    private var silenceSamples = 0
    private val preBuffer = ArrayDeque<FloatArray>()
    private val currentSpeechFrames = mutableListOf<FloatArray>()
    private var processedSampleCount = 0L

    val isInitialized: Boolean
        get() = ortSession != null

    init {
        initializeSession()
    }

    private fun initializeSession() {
        try {
            ortEnv = OrtEnvironment.getEnvironment()
            val modelFile = ModelAssetLoader.getOrExtractAssetFile(context, modelAssetPath)
            val sessionOptions = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
            }
            ortSession = ortEnv?.createSession(modelFile.absolutePath, sessionOptions)
            AstraLog.i(tag, "VAD initialized: Silero VAD v5 ONNX session loaded from '${modelFile.absolutePath}'")
        } catch (e: Exception) {
            AstraLog.w(tag, "VAD initialization failed (${e.message}). Will use energy heuristic.")
        }
    }

    fun resetState() {
        hiddenState = Array(2) { Array(1) { FloatArray(128) } }
        contextBuffer.fill(0f)
        isTriggered = false
        consecutiveSpeechFrames = 0
        silenceSamples = 0
        preBuffer.clear()
        currentSpeechFrames.clear()
        processedSampleCount = 0L
    }

    /**
     * Infer speech probability for a single 512-sample float32 frame.
     */
    fun processFrame(frame: FloatArray): Float {
        val window = FloatArray(SttConfig.FRAME_SIZE_SAMPLES)
        val copyLen = min(frame.size, SttConfig.FRAME_SIZE_SAMPLES)
        System.arraycopy(frame, 0, window, 0, copyLen)

        val session = ortSession
        val env = ortEnv
        if (session != null && env != null) {
            try {
                // Construct input tensor [1, 576] = 64 context + 512 frame
                val inputCombined = FloatArray(SttConfig.TOTAL_INPUT_SAMPLES)
                System.arraycopy(contextBuffer, 0, inputCombined, 0, SttConfig.CONTEXT_SIZE_SAMPLES)
                System.arraycopy(window, 0, inputCombined, SttConfig.CONTEXT_SIZE_SAMPLES, SttConfig.FRAME_SIZE_SAMPLES)

                // Update rolling context to the last 64 samples of current frame
                System.arraycopy(window, SttConfig.FRAME_SIZE_SAMPLES - SttConfig.CONTEXT_SIZE_SAMPLES, contextBuffer, 0, SttConfig.CONTEXT_SIZE_SAMPLES)

                val inputTensor = OnnxTensor.createTensor(
                    env,
                    FloatBuffer.wrap(inputCombined),
                    longArrayOf(1, SttConfig.TOTAL_INPUT_SAMPLES.toLong())
                )

                // Hidden state tensor: [2, 1, 128]
                val flatState = FloatArray(2 * 1 * 128)
                var idx = 0
                for (i in 0 until 2) {
                    for (j in 0 until 1) {
                        for (k in 0 until 128) {
                            flatState[idx++] = hiddenState[i][j][k]
                        }
                    }
                }
                val stateTensor = OnnxTensor.createTensor(
                    env,
                    FloatBuffer.wrap(flatState),
                    longArrayOf(2, 1, 128)
                )

                val srTensor = OnnxTensor.createTensor(
                    env,
                    LongBuffer.wrap(longArrayOf(SttConfig.SAMPLE_RATE.toLong())),
                    longArrayOf(1)
                )

                val inputs = mapOf(
                    "input" to inputTensor,
                    "state" to stateTensor,
                    "sr" to srTensor
                )

                val results = session.run(inputs)
                var prob = 0.0f

                val outputTensor = results.get(0) as? OnnxTensor
                if (outputTensor != null) {
                    val floatBuffer = outputTensor.floatBuffer
                    if (floatBuffer.hasRemaining()) {
                        prob = floatBuffer.get(0)
                    }
                }

                if (results.size() > 1) {
                    val nextStateTensor = results.get(1) as? OnnxTensor
                    if (nextStateTensor != null) {
                        val stateBuf = nextStateTensor.floatBuffer
                        var sIdx = 0
                        for (i in 0 until 2) {
                            for (j in 0 until 1) {
                                for (k in 0 until 128) {
                                    if (stateBuf.hasRemaining()) {
                                        hiddenState[i][j][k] = stateBuf.get(sIdx++)
                                    }
                                }
                            }
                        }
                    }
                }

                inputTensor.close()
                stateTensor.close()
                srTensor.close()
                results.close()

                return prob.coerceIn(0.0f, 1.0f)
            } catch (e: Exception) {
                AstraLog.d(tag, "VAD inference fallback: ${e.message}")
            }
        }

        // Energy heuristic fallback
        var sumSquares = 0.0
        for (s in window) {
            sumSquares += (s * s)
        }
        val rms = sqrt(sumSquares / window.size + 1e-10)
        return min(1.0f, (rms / 0.04f).toFloat())
    }

    /**
     * Stream frame through state machine (SILENCE -> SPEECH_START -> SPEECH_ACTIVE -> SPEECH_END).
     */
    fun processStreamFrame(
        frame: FloatArray,
        threshold: Float = SttConfig.VAD_THRESHOLD,
        minSpeechMs: Long = SttConfig.VAD_MIN_SPEECH_MS,
        minSilenceMs: Long = SttConfig.VAD_MIN_SILENCE_MS,
        speechPadMs: Long = SttConfig.VAD_SPEECH_PAD_MS
    ): VADEvent {
        val prob = processFrame(frame)
        val timestampMs = (processedSampleCount * 1000L) / SttConfig.SAMPLE_RATE
        processedSampleCount += frame.size

        val padFramesCount = max(1, (speechPadMs * SttConfig.SAMPLE_RATE / 1000L) / SttConfig.FRAME_SIZE_SAMPLES).toInt()
        val minSilenceSamples = (minSilenceMs * SttConfig.SAMPLE_RATE / 1000L).toInt()
        val minSpeechSamples = (minSpeechMs * SttConfig.SAMPLE_RATE / 1000L).toInt()

        if (!isTriggered) {
            preBuffer.addLast(frame.copyOf())
            if (preBuffer.size > padFramesCount) {
                preBuffer.removeFirst()
            }
        }

        var eventType = VADEventType.SILENCE
        var segmentAudio: FloatArray? = null
        val wasTriggered = isTriggered

        if (!isTriggered) {
            if (prob >= threshold) {
                consecutiveSpeechFrames++
                if (consecutiveSpeechFrames >= 2) {
                    isTriggered = true
                    eventType = VADEventType.SPEECH_START
                    currentSpeechFrames.clear()
                    currentSpeechFrames.addAll(preBuffer)
                    currentSpeechFrames.add(frame.copyOf())
                    preBuffer.clear()
                    segmentAudio = concatenateFrames(currentSpeechFrames)
                    AstraLog.d(tag, "ASTRA_VOICE: speech detected (prob=$prob)")
                }
            } else {
                consecutiveSpeechFrames = 0
            }
        } else {
            currentSpeechFrames.add(frame.copyOf())
            if (prob >= threshold) {
                silenceSamples = 0
                eventType = VADEventType.SPEECH_ACTIVE
            } else {
                silenceSamples += frame.size
                if (silenceSamples >= minSilenceSamples) {
                    val totalAudio = concatenateFrames(currentSpeechFrames)
                    if (totalAudio.size >= minSpeechSamples) {
                        eventType = VADEventType.SPEECH_END
                        segmentAudio = totalAudio
                        AstraLog.d(tag, "ASTRA_VOICE: speech ended (durationMs=${(totalAudio.size * 1000L) / SttConfig.SAMPLE_RATE})")
                    } else {
                        eventType = VADEventType.SILENCE
                    }
                    isTriggered = false
                    consecutiveSpeechFrames = 0
                    silenceSamples = 0
                    currentSpeechFrames.clear()
                } else {
                    eventType = VADEventType.SPEECH_ACTIVE
                }
            }
        }

        return VADEvent(
            eventType = eventType,
            probability = prob,
            isSpeech = wasTriggered || isTriggered || eventType == VADEventType.SPEECH_START || eventType == VADEventType.SPEECH_END,
            segmentAudio = segmentAudio,
            timestampMs = timestampMs
        )
    }

    private fun concatenateFrames(frames: List<FloatArray>): FloatArray {
        val totalLen = frames.sumOf { it.size }
        val result = FloatArray(totalLen)
        var offset = 0
        for (f in frames) {
            System.arraycopy(f, 0, result, offset, f.size)
            offset += f.size
        }
        return result
    }

    fun release() {
        try {
            ortSession?.close()
            ortEnv?.close()
        } catch (_: Exception) {}
        ortSession = null
        ortEnv = null
    }
}
