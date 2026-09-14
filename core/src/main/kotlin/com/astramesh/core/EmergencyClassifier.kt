package com.astramesh.core

import java.util.regex.Pattern

/**
 * Multilingual Rule-Based Emergency & Distress Classifier.
 * Evaluates speech transcripts across 10 languages and identifies emergency priority (P0 vs P2).
 * Strictly zero-ML, on-device, sub-millisecond keyword matching engine.
 */
object EmergencyClassifier {

    data class ClassificationResult(
        val isEmergency: Boolean,
        val priority: String, // "P0" or "P2"
        val matchedKeywords: List<String>,
        val matchedEmergency: List<String>,
        val matchedLocation: List<String>,
        val matchedNumbers: List<String>,
        val needsReview: Boolean = false
    )

    private val BUILTIN_KEYWORDS: Map<Language, List<String>> = mapOf(
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

    private val BUILTIN_LOCATIONS: Map<Language, List<String>> = mapOf(
        Language.HINDI to listOf("मंजिल", "फ़्लोर", "इमारत", "भवन", "उत्तर", "दक्षिण", "पूर्व", "पश्चिम", "सीढ़ी", "कमरा", "ब्लॉक", "बेसमेंट", "छत", "गेट"),
        Language.ENGLISH to listOf("floor", "building", "north", "south", "east", "west", "basement", "roof", "staircase", "lobby", "gate", "room", "zone", "block")
    )

    fun isEmergency(text: String, language: Language = Language.ENGLISH): Boolean {
        if (text.isBlank()) return false
        val result = classifyDetailed(text, language.code)
        return result.isEmergency
    }

    fun classify(text: String, language: Language = Language.ENGLISH): MessageType {
        return if (isEmergency(text, language)) MessageType.ALERT else MessageType.NORMAL
    }

    fun classifyDetailed(text: String, languageCode: String = "en"): ClassificationResult {
        if (text.isBlank()) {
            return ClassificationResult(
                isEmergency = false,
                priority = "P2",
                matchedKeywords = emptyList(),
                matchedEmergency = emptyList(),
                matchedLocation = emptyList(),
                matchedNumbers = emptyList()
            )
        }

        val normalized = text.lowercase().trim()
        val lang = Language.fromCode(languageCode)
        val matchedEmergency = mutableListOf<String>()
        val matchedLocation = mutableListOf<String>()
        val matchedNumbers = mutableListOf<String>()

        // Split tokens supporting Latin and Indic Unicode scripts
        val tokenPattern = Pattern.compile("[\\w\\u0900-\\u0DFF]+")
        val matcher = tokenPattern.matcher(normalized)
        val tokens = mutableSetOf<String>()
        while (matcher.find()) {
            tokens.add(matcher.group())
        }

        fun matchesTerm(term: String): Boolean {
            val clean = term.trim().lowercase()
            if (clean.isEmpty()) return false
            if (clean.contains(" ")) {
                val escaped = Pattern.quote(clean)
                val phrasePattern = Pattern.compile("(?:\\b|\\s|^)" + escaped + "(?:\\b|\\s|$|[।,?!])")
                return phrasePattern.matcher(normalized).find()
            } else {
                return tokens.contains(clean) || normalized.split(Regex("\\s+")).contains(clean)
            }
        }

        // 1. Language-specific emergency terms
        val targetTerms = BUILTIN_KEYWORDS[lang] ?: BUILTIN_KEYWORDS[Language.ENGLISH] ?: emptyList()
        for (term in targetTerms) {
            if (matchesTerm(term)) {
                matchedEmergency.add(term)
            }
        }

        // 2. Cross-language distress terms (in case of code-switching / multilingual speech)
        for ((otherLang, terms) in BUILTIN_KEYWORDS) {
            if (otherLang == lang) continue
            for (term in terms) {
                if (matchesTerm(term)) {
                    matchedEmergency.add(term)
                }
            }
        }

        // 3. Location terms
        val locations = BUILTIN_LOCATIONS[lang] ?: BUILTIN_LOCATIONS[Language.ENGLISH] ?: emptyList()
        for (loc in locations) {
            if (matchesTerm(loc)) {
                matchedLocation.add(loc)
            }
        }

        val uniqueEmergency = matchedEmergency.distinct()
        val uniqueLocation = matchedLocation.distinct()
        val allMatched = (uniqueEmergency + uniqueLocation + matchedNumbers).distinct()
        val isEmergency = uniqueEmergency.isNotEmpty()
        val priority = if (isEmergency) "P0" else "P2"

        return ClassificationResult(
            isEmergency = isEmergency,
            priority = priority,
            matchedKeywords = allMatched,
            matchedEmergency = uniqueEmergency,
            matchedLocation = uniqueLocation,
            matchedNumbers = matchedNumbers
        )
    }
}
