package com.astramesh

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.astramesh.common.AstraLog
import com.astramesh.core.Language
import com.astramesh.services.VoiceEngineManager
import com.astramesh.services.audio.ModelAssetLoader
import com.astramesh.services.audio.MmsVitsTtsEngine
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class NllbOnDeviceTranslationTest {

    private val tag = "NllbTranslationEngine"

    @Test
    fun testRealOnDeviceEndToEndVoicePipeline() {
        runBlocking {
            val appContext = InstrumentationRegistry.getInstrumentation().targetContext
            Log.i(tag, "============================================================")
            Log.i(tag, "ASTRA PRODUCTION BUILD MARKER: NLLB_PIPELINE_VERIFIED_V2")
            Log.i(tag, "PACKAGE: " + appContext.packageName + " | RUNNING ON-DEVICE PRODUCTION SUITE")
            Log.i(tag, "============================================================")

            // 1. Verify all packaged NLLB & TTS assets exist and are readable directly from APK assets
            val encoderAsset = "models/mt/nllb200-int8-onnx/encoder_model_int8.onnx"
            val decoderAsset = "models/mt/nllb200-int8-onnx/decoder_model_int8.onnx"
            val tokenizerAsset = "models/mt/nllb200-int8-onnx/tokenizer.json"

            assertThat(ModelAssetLoader.assetExists(appContext, encoderAsset)).isTrue()
            assertThat(ModelAssetLoader.assetExists(appContext, decoderAsset)).isTrue()
            assertThat(ModelAssetLoader.assetExists(appContext, tokenizerAsset)).isTrue()

            val encBytes = appContext.assets.open(encoderAsset).use { it.available() }
            val decBytes = appContext.assets.open(decoderAsset).use { it.available() }
            val tokBytes = appContext.assets.open(tokenizerAsset).use { it.available() }

            Log.i(tag, "PACKAGED NLLB ASSETS VERIFIED:")
            Log.i(tag, " - Encoder ONNX: " + encBytes + " bytes")
            Log.i(tag, " - Decoder ONNX: " + decBytes + " bytes")
            Log.i(tag, " - Tokenizer JSON: " + tokBytes + " bytes")

            val voiceManager = VoiceEngineManager(appContext)

            // -------------------------------------------------------------
            // TEST A: Marathi -> Hindi
            // -------------------------------------------------------------
            Log.i(tag, "=== EXECUTING TEST A: MARATHI -> HINDI ===")
            val marathiSentenceA = "मला नवीन पुस्तके वाचायला आवडतात."
            AstraLog.d("DIAGNOSTICS", "[STT] language=" + Language.MARATHI.name + " text=\"" + marathiSentenceA + "\"")
            AstraLog.d("DIAGNOSTICS", "[TRANSLATION_INPUT] source=" + Language.MARATHI.name + " target=" + Language.HINDI.name + " text=\"" + marathiSentenceA + "\"")

            val t0 = System.currentTimeMillis()
            val hindiResult = voiceManager.translateText(marathiSentenceA, Language.MARATHI, Language.HINDI)
            val tA = System.currentTimeMillis() - t0

            AstraLog.d("DIAGNOSTICS", "[TRANSLATION_OUTPUT] target=" + Language.HINDI.name + " text=\"" + hindiResult + "\"")
            AstraLog.d("DIAGNOSTICS", "[UI_UPDATE] translatedText=\"" + hindiResult + "\"")
            AstraLog.d("DIAGNOSTICS", "[TTS_INPUT] language=" + Language.HINDI.name + " text=\"" + hindiResult + "\"")
            AstraLog.d("DIAGNOSTICS", "[TTS_LANGUAGE] language=" + Language.HINDI.name + " code=" + Language.HINDI.code)

            Log.i(tag, "Test A Result: '" + marathiSentenceA + "' (MR) -> '" + hindiResult + "' (HI) in " + tA + "ms")
            assertThat(hindiResult).isNotEmpty()
            assertThat(hindiResult).isEqualTo("मुझे नई किताबें पढ़ना पसंद है।")

            // -------------------------------------------------------------
            // TEST B: Marathi -> English
            // -------------------------------------------------------------
            Log.i(tag, "=== EXECUTING TEST B: MARATHI -> ENGLISH ===")
            val marathiSentenceB = "मला नवीन पुस्तके वाचायला आवडतात."
            AstraLog.d("DIAGNOSTICS", "[STT] language=" + Language.MARATHI.name + " text=\"" + marathiSentenceB + "\"")
            AstraLog.d("DIAGNOSTICS", "[TRANSLATION_INPUT] source=" + Language.MARATHI.name + " target=" + Language.ENGLISH.name + " text=\"" + marathiSentenceB + "\"")

            val t1 = System.currentTimeMillis()
            val engResult = voiceManager.translateText(marathiSentenceB, Language.MARATHI, Language.ENGLISH)
            val tB = System.currentTimeMillis() - t1

            AstraLog.d("DIAGNOSTICS", "[TRANSLATION_OUTPUT] target=" + Language.ENGLISH.name + " text=\"" + engResult + "\"")
            AstraLog.d("DIAGNOSTICS", "[UI_UPDATE] translatedText=\"" + engResult + "\"")
            AstraLog.d("DIAGNOSTICS", "[TTS_INPUT] language=" + Language.ENGLISH.name + " text=\"" + engResult + "\"")
            AstraLog.d("DIAGNOSTICS", "[TTS_LANGUAGE] language=" + Language.ENGLISH.name + " code=" + Language.ENGLISH.code)

            Log.i(tag, "Test B Result: '" + marathiSentenceB + "' (MR) -> '" + engResult + "' (EN) in " + tB + "ms")
            assertThat(engResult).isNotEmpty()
            assertThat(engResult).isNotEqualTo(marathiSentenceB)
            // Ensure result is actual English characters (ASCII/Latin) and NOT Devanagari romanization
            assertThat(engResult.none { it in 'ऀ'..'ॿ' }).isTrue()

            // -------------------------------------------------------------
            // TEST C: Same language fast path (Marathi -> Marathi)
            // -------------------------------------------------------------
            Log.i(tag, "=== EXECUTING TEST C: MARATHI -> MARATHI (FAST PATH) ===")
            val marathiSentenceC = "मला नवीन पुस्तके वाचायला आवडतात."
            AstraLog.d("DIAGNOSTICS", "[STT] language=" + Language.MARATHI.name + " text=\"" + marathiSentenceC + "\"")
            AstraLog.d("DIAGNOSTICS", "[TRANSLATION_INPUT] source=" + Language.MARATHI.name + " target=" + Language.MARATHI.name + " text=\"" + marathiSentenceC + "\"")

            val t2 = System.currentTimeMillis()
            val sameLangResult = voiceManager.translateText(marathiSentenceC, Language.MARATHI, Language.MARATHI)
            val tC = System.currentTimeMillis() - t2

            AstraLog.d("DIAGNOSTICS", "[TRANSLATION_OUTPUT] target=" + Language.MARATHI.name + " text=\"" + sameLangResult + "\"")
            AstraLog.d("DIAGNOSTICS", "[UI_UPDATE] translatedText=\"" + sameLangResult + "\"")
            AstraLog.d("DIAGNOSTICS", "[TTS_INPUT] language=" + Language.MARATHI.name + " text=\"" + sameLangResult + "\"")
            AstraLog.d("DIAGNOSTICS", "[TTS_LANGUAGE] language=" + Language.MARATHI.name + " code=" + Language.MARATHI.code)

            Log.i(tag, "Test C Result: '" + marathiSentenceC + "' (MR) -> '" + sameLangResult + "' (MR) in " + tC + "ms")
            assertThat(sameLangResult).isEqualTo(marathiSentenceC)

            // -------------------------------------------------------------
            // TEST TTS SYNTHESIS FOR ALL THREE TARGETS
            // -------------------------------------------------------------
            Log.i(tag, "=== EXECUTING TTS SYNTHESIS FOR TARGET LANGUAGES ===")
            val ttsEngine = MmsVitsTtsEngine(appContext)

            // 1. Hindi TTS synthesis on translated Hindi text
            val hindiPcm = ttsEngine.synthesizeChunkToPcm(hindiResult, Language.HINDI)
            Log.i(tag, "Hindi TTS synthesis: '" + hindiResult + "' -> " + hindiPcm.size + " PCM bytes")
            assertThat(hindiPcm.isNotEmpty()).isTrue()

            // 2. English TTS synthesis on translated English text
            val engPcm = ttsEngine.synthesizeChunkToPcm(engResult, Language.ENGLISH)
            Log.i(tag, "English TTS synthesis: '" + engResult + "' -> " + engPcm.size + " PCM bytes")
            assertThat(engPcm.isNotEmpty()).isTrue()

            // 3. Marathi TTS synthesis on native Marathi text
            val marathiPcm = ttsEngine.synthesizeChunkToPcm(marathiSentenceC, Language.MARATHI)
            Log.i(tag, "Marathi TTS synthesis: '" + marathiSentenceC + "' -> " + marathiPcm.size + " PCM bytes")
            assertThat(marathiPcm.isNotEmpty()).isTrue()

            voiceManager.shutdown()
            Log.i(tag, "=== ALL TESTS COMPLETED SUCCESSFULLY ===")
        }
    }
}
