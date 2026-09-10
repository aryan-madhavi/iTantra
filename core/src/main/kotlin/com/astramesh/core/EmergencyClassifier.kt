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

    fun isEmergency(text: String, language: Language = Language.ENGLISH): Boolean {
        if (text.isBlank()) return false
        val lowerText = text.lowercase().trim()

        // Universal check for SOS / HELP
        if (lowerText.contains("sos") || lowerText.contains("help") || lowerText.contains("emergency")) {
            return true
        }

        // Language specific keywords
        val terms = KEYWORDS[language] ?: KEYWORDS[Language.ENGLISH] ?: emptyList()
        for (term in terms) {
            if (lowerText.contains(term.lowercase())) {
                return true
            }
        }

        // Cross-check all languages in case speaker spoke mixed words (e.g. Hindi distress in English mode)
        for (list in KEYWORDS.values) {
            for (term in list) {
                if (lowerText.contains(term.lowercase())) {
                    return true
                }
            }
        }

        return false
    }

    fun classify(text: String, language: Language): MessageType {
        return if (isEmergency(text, language)) MessageType.ALERT else MessageType.NORMAL
    }
}
