package com.astramesh.core

/**
 * Multilingual Rule-Based Emergency & Distress Classifier.
 * Evaluates speech transcripts across 10 languages and identifies emergency priority.
 */
object EmergencyClassifier {

    private val KEYWORDS: Map<Language, List<String>> = mapOf(
        Language.HINDI to listOf(
            "मदद", "सहायता", "बचाओ", "आग", "खतरा", "आपातकाल", "फंसा", "फंसी", "फंसे",
            "चिकित्सा", "एम्बुलेंस", "धुआं", "धमाका", "गैस लीक", "मलबे", "पुलिस", "घायल",
            "sos", "help", "fire", "danger"
        ),
        Language.ENGLISH to listOf(
            "help", "trapped", "fire", "medical", "sos", "danger", "evacuate",
            "emergency", "blast", "gas leak", "smoke", "collapse", "injured",
            "ambulance", "police", "attack", "casualty"
        ),
        Language.GUJARATI to listOf(
            "મદદ", "સહાય", "બચાવો", "આગ", "જોખમ", "કટોકટી", "ફસાયેલા", "ઈજાગ્રસ્ત",
            "એમ્બ્યુલન્સ", "દવાખાનું", "ધૂમાડો", "ધડાકો", "પોલીસ", "sos", "help"
        ),
        Language.MARATHI to listOf(
            "मदत", "वाचवा", "आग", "धोका", "आणीबाणी", "अडकलेला", "अडकलेली", "जखमी",
            "रुग्णवाहिका", "डॉक्टर", "अपघात", "धूर", "स्फोट", "पोलीस", "sos", "help"
        ),
        Language.TAMIL to listOf(
            "உதவி", "காப்பாற்றுங்கள்", "தீ", "ஆபத்து", "அவசரம்", "விபத்து", "காயம்",
            "ஆம்புலன்ஸ்", "மருத்துவர்", "புகை", "வெடிப்பு", "போலீஸ்", "sos", "help"
        ),
        Language.TELUGU to listOf(
            "సహాయం", "కాపాడండి", "నిప్పు", "ప్రమాదం", "అత్యవసరం", "చిక్కుకున్నారు",
            "గాయపడ్డారు", "అంబులెన్స్", "డాక్టర్", "పొగ", "పేలుడు", "పోలీసు", "sos", "help"
        ),
        Language.KANNADA to listOf(
            "ಸಹಾಯ", "ಉಳಿಸಿ", "ಬೆಂಕಿ", "ಅಪಾಯ", "ತುರ್ತು", "ಸಿಲುಕಿದ್ದಾರೆ", "ಗಾಯಗೊಂಡಿದ್ದಾರೆ",
            "ಆಂಬ್ಯುಲೆನ್ಸ್", "ವೈದ್ಯ", "ಹೊಗೆ", "ಸ್ಫೋಟ", "ಪೊಲೀಸ್", "sos", "help"
        ),
        Language.MALAYALAM to listOf(
            "സഹായം", "രക്ഷിക്കൂ", "തീ", "അപകടം", "അടിയന്തരാവസ്ഥ", "കുടുങ്ങി",
            "പരിക്കേറ്റു", "ആംബുലൻസ്", "ഡോക്ടർ", "പുക", "പോലീസ്", "sos", "help"
        ),
        Language.ODIA to listOf(
            "ସାହାଯ୍ୟ", "ବଞ୍ଚାଅ", "ନିଆଁ", "ବିପଦ", "ଜରୁରୀ", "ଫସିଯାଇଛି", "ଆହତ",
            "ଆମ୍ବୁଲାନ୍ସ", "ଡାକ୍ତର", "ଧୂଆଁ", "ବିସ୍ଫୋରଣ", "ପୋଲିସ", "sos", "help"
        ),
        Language.BENGALI to listOf(
            "সাহায্য", "বাঁচাও", "আগুন", "বিপদ", "জরুরী", "আটকে", "আহত",
            "অ্যাম্বুলেন্স", "ডাক্তার", "পুলিশ", "ধোঁয়া", "বিস্ফোরণ", "sos", "help"
        )
    )

    private val DELIMITERS_REGEX = Regex("[\\s।,?!;:.\"/()\\[\\]{}]+")

    /**
     * Checks if text contains an emergency or distress keyword.
     * Enforces strict word-boundary tokenization to eliminate false triggers
     * (e.g., prevents "lessons" or "espresso" from triggering "sos").
     */
    fun isEmergency(text: String, language: Language = Language.ENGLISH): Boolean {
        return findTriggerKeyword(text, language) != null
    }

    /**
     * Finds and returns the first matching emergency keyword if present, or null.
     * Safe against partial-word false triggers.
     */
    fun findTriggerKeyword(text: String, language: Language = Language.ENGLISH): String? {
        val trimmed = text.trim()
        if (trimmed.length < 2) return null

        val lowerText = trimmed.lowercase()
        val tokens = lowerText.split(DELIMITERS_REGEX).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null

        // 1. Language-specific keywords first
        val targetKeywords = KEYWORDS[language] ?: KEYWORDS[Language.ENGLISH] ?: emptyList()
        matchKeywordInTokens(lowerText, tokens, targetKeywords)?.let { return it }

        // 2. Universal distress check (English/Latin SOS, HELP, EMERGENCY)
        val universalKeywords = listOf("sos", "help", "emergency", "mayday")
        matchKeywordInTokens(lowerText, tokens, universalKeywords)?.let { return it }

        // 3. Cross-language check across all other languages
        for ((lang, terms) in KEYWORDS) {
            if (lang != language) {
                matchKeywordInTokens(lowerText, tokens, terms)?.let { return it }
            }
        }

        return null
    }

    private fun matchKeywordInTokens(
        lowerText: String,
        tokens: List<String>,
        keywords: List<String>
    ): String? {
        for (keyword in keywords) {
            val lowerKw = keyword.lowercase().trim()
            if (lowerKw.isBlank()) continue

            // If keyword is multi-word (e.g., "gas leak", "गैस लीक")
            if (lowerKw.contains(" ")) {
                val phraseRegex = Regex("(?:^|[\\s।,?!;:.\"/()\\[\\]{}])" + Regex.escape(lowerKw) + "(?:$|[\\s।,?!;:.\"/()\\[\\]{}])")
                if (phraseRegex.containsMatchIn(lowerText)) {
                    return keyword
                }
            } else {
                // Single-word: check if any token exactly matches
                if (tokens.any { it == lowerKw }) {
                    return keyword
                }
                // For latin single words, double check with regex word boundary
                if (lowerKw.all { it in 'a'..'z' || it in '0'..'9' }) {
                    val wordRegex = Regex("\\b" + Regex.escape(lowerKw) + "\\b")
                    if (wordRegex.containsMatchIn(lowerText)) {
                        return keyword
                    }
                }
            }
        }
        return null
    }

    fun classify(text: String, language: Language): MessageType {
        return if (isEmergency(text, language)) MessageType.ALERT else MessageType.NORMAL
    }
}

