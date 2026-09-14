package com.astramesh.core

/**
 * Offline, Zero-ML Language Detector for the 10 canonical iTantra languages.
 * Uses Unicode script block frequency analysis and Devanagari lexical disambiguation (Hindi vs Marathi).
 * Sub-millisecond execution time, strictly offline.
 */
object OfflineLanguageDetector {

    data class DetectionResult(
        val language: Language,
        val confidence: Float,
        val scriptName: String,
        val isDisambiguated: Boolean = false
    )

    private val MARATHI_MARKERS = setOf(
        "आहे", "नाही", "आणि", "मी", "तुम्ही", "आम्ही", "काय", "होते", "झाले",
        "करा", "पाहिजे", "कसा", "कुठे", "कधी", "येथे", "तेथे", "घेऊन", "गेला",
        "आली", "झाला", "मदत", "वाचवा", "धोका", "आणीबाणी", "अडकलेला", "अडकलेली"
    )

    private val HINDI_MARKERS = setOf(
        "है", "हैं", "नहीं", "और", "मैं", "आप", "हम", "क्या", "था", "थी",
        "थे", "हुआ", "हुई", "हुए", "करो", "चाहिए", "कैसे", "कहाँ", "कब",
        "यहाँ", "वहाँ", "लेकर", "गया", "गई", "मदद", "सहायता", "बचाओ", "आपातकाल"
    )

    fun detect(text: String, hintLanguage: Language = Language.HINDI): DetectionResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return DetectionResult(hintLanguage, 0.0f, "UNKNOWN")
        }

        var devanagariCount = 0
        var bengaliCount = 0
        var gujaratiCount = 0
        var odiaCount = 0
        var tamilCount = 0
        var teluguCount = 0
        var kannadaCount = 0
        var malayalamCount = 0
        var latinCount = 0

        for (ch in trimmed) {
            val code = ch.code
            when (code) {
                in 0x0900..0x097F -> devanagariCount++
                in 0x0980..0x09FF -> bengaliCount++
                in 0x0A80..0x0AFF -> gujaratiCount++
                in 0x0B00..0x0B7F -> odiaCount++
                in 0x0B80..0x0BFF -> tamilCount++
                in 0x0C00..0x0C7F -> teluguCount++
                in 0x0C80..0x0CFF -> kannadaCount++
                in 0x0D00..0x0D7F -> malayalamCount++
                in 0x0041..0x005A, in 0x0061..0x007A -> latinCount++
            }
        }

        val totalScriptChars = devanagariCount + bengaliCount + gujaratiCount + odiaCount +
                tamilCount + teluguCount + kannadaCount + malayalamCount + latinCount

        if (totalScriptChars == 0) {
            return DetectionResult(hintLanguage, 0.5f, "UNKNOWN")
        }

        val scriptCounts = mapOf(
            "DEVANAGARI" to devanagariCount,
            "BENGALI" to bengaliCount,
            "GUJARATI" to gujaratiCount,
            "ODIA" to odiaCount,
            "TAMIL" to tamilCount,
            "TELUGU" to teluguCount,
            "KANNADA" to kannadaCount,
            "MALAYALAM" to malayalamCount,
            "LATIN" to latinCount
        )

        val dominant = scriptCounts.maxByOrNull { it.value } ?: return DetectionResult(hintLanguage, 0.5f, "UNKNOWN")
        val confidence = dominant.value.toFloat() / totalScriptChars.toFloat()

        return when (dominant.key) {
            "BENGALI" -> DetectionResult(Language.BENGALI, confidence, dominant.key)
            "GUJARATI" -> DetectionResult(Language.GUJARATI, confidence, dominant.key)
            "ODIA" -> DetectionResult(Language.ODIA, confidence, dominant.key)
            "TAMIL" -> DetectionResult(Language.TAMIL, confidence, dominant.key)
            "TELUGU" -> DetectionResult(Language.TELUGU, confidence, dominant.key)
            "KANNADA" -> DetectionResult(Language.KANNADA, confidence, dominant.key)
            "MALAYALAM" -> DetectionResult(Language.MALAYALAM, confidence, dominant.key)
            "LATIN" -> DetectionResult(Language.ENGLISH, confidence, dominant.key)
            "DEVANAGARI" -> disambiguateDevanagari(trimmed, confidence, hintLanguage)
            else -> DetectionResult(hintLanguage, confidence, "UNKNOWN")
        }
    }

    private fun disambiguateDevanagari(
        text: String,
        scriptConfidence: Float,
        hintLanguage: Language
    ): DetectionResult {
        val words = text.split(Regex("[\\s,।!?.]+")).filter { it.isNotBlank() }
        var marathiScore = 0
        var hindiScore = 0

        for (w in words) {
            if (MARATHI_MARKERS.contains(w)) marathiScore++
            if (HINDI_MARKERS.contains(w)) hindiScore++
        }

        return when {
            marathiScore > hindiScore -> DetectionResult(Language.MARATHI, scriptConfidence, "DEVANAGARI", isDisambiguated = true)
            hindiScore > marathiScore -> DetectionResult(Language.HINDI, scriptConfidence, "DEVANAGARI", isDisambiguated = true)
            hintLanguage == Language.MARATHI -> DetectionResult(Language.MARATHI, scriptConfidence, "DEVANAGARI", isDisambiguated = false)
            else -> DetectionResult(Language.HINDI, scriptConfidence, "DEVANAGARI", isDisambiguated = false)
        }
    }
}
