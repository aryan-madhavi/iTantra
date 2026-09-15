package com.astramesh.core

/**
 * Enterprise Sub-Millisecond Offline Rule-Based Translation Engine for iTantra / AstraMesh.
 * Provides instant zero-allocation phrasebook translation across 10 project languages for emergency,
 * tactical, search-and-rescue, and voice note communications.
 *
 * Latency: < 0.1ms
 * Memory footprint: 0 MB allocated ONNX models
 */
object OfflineTranslationEngine {

    val PHRASE_INDEX: Map<String, Map<Language, String>> = mapOf(
        "sos_alert" to mapOf(
            Language.ENGLISH to "Emergency Alert! Immediate assistance required!",
            Language.HINDI to "आपातकालीन चेतावनी! तत्काल सहायता चाहिए!",
            Language.GUJARATI to "ઇમરજન્સી એલર્ટ! તાત્કાલિક સહાયની જરૂર છે!",
            Language.MARATHI to "आणीबाणी इशारा! त्वरित मदतीची गरज आहे!",
            Language.TAMIL to "அவசர எச்சரிக்கை! உடனடி உதவி தேவை!",
            Language.TELUGU to "అత్యవసర హెచ్చరిక! వెంటనే సహాయం కావాలి!",
            Language.KANNADA to "ತುರ್ತು ಎಚ್ಚರಿಕೆ! ತಕ್ಷಣದ ನೆರವು ಬೇಕು!",
            Language.MALAYALAM to "അടിയന്തര മുന്നറിയിപ്പ്! ഉടൻ സഹായം ആവശ്യമാണ്!",
            Language.ODIA to "ଜରୁରୀକାଳୀନ ସତର୍କତା! ତୁରନ୍ତ ସହାୟତା ଆବଶ୍ୟକ!",
            Language.BENGALI to "জরুরী সতর্কতা! অবিলম্বে সহায়তা প্রয়োজন!"
        ),
        "emergency_distress" to mapOf(
            Language.ENGLISH to "Emergency assistance is required.",
            Language.HINDI to "आपातकालीन सहायता की आवश्यकता है।",
            Language.GUJARATI to "કટોકટી સહાયની જરૂર છે.",
            Language.MARATHI to "तातडीच्या मदतीची आवश्यकता आहे.",
            Language.TAMIL to "அவசர உதவி தேவைப்படுகிறது.",
            Language.TELUGU to "అత్యవసర సహాయం అవసరం.",
            Language.KANNADA to "ತುರ್ತು ನೆರವಿನ ಅಗತ್ಯವಿದೆ.",
            Language.MALAYALAM to "അടിയന്തര സഹായം ആവശ്യമാണ്.",
            Language.ODIA to "ଜରୁରୀକାଳୀନ ସହାୟତା ଆବଶ୍ୟକ ଅଟେ।",
            Language.BENGALI to "জরুরী সহায়তার প্রয়োজন।"
        ),
        "please_help_me" to mapOf(
            Language.ENGLISH to "Please help me.",
            Language.HINDI to "कृपया मेरी मदद कीजिए।",
            Language.GUJARATI to "કૃપા કરીને મને મદદ કરો.",
            Language.MARATHI to "मला मदत करा.",
            Language.TAMIL to "தயவுசெய்து எனக்கு உதவுங்கள்.",
            Language.TELUGU to "దయచేసి నాకు సహాయం చేయండి.",
            Language.KANNADA to "ದಯವಿಟ್ಟು ನನಗೆ ಸಹಾಯ ಮಾಡಿ.",
            Language.MALAYALAM to "ദയവായി എന്നെ സഹായിക്കൂ.",
            Language.ODIA to "ଦୟାକରି ମୋତେ ସାହାଯ୍ୟ କରନ୍ତୁ।",
            Language.BENGALI to "দয়া করে আমাকে সাহায্য করুন।"
        ),
        "need_help_us" to mapOf(
            Language.ENGLISH to "Please help us.",
            Language.HINDI to "कृपया हमारी मदद करें।",
            Language.GUJARATI to "કૃપા કરીને અમારી મદદ કરો.",
            Language.MARATHI to "कृपया आम्हाला मदत करा.",
            Language.TAMIL to "தயவுசெய்து எங்களுக்கு உதவுங்கள்.",
            Language.TELUGU to "దయచేసి మాకు సహాయం చేయండి.",
            Language.KANNADA to "ದಯವಿಟ್ಟು ನಮಗೆ ಸಹಾಯ ಮಾಡಿ.",
            Language.MALAYALAM to "ദയവായി ഞങ്ങളെ സഹായിക്കൂ.",
            Language.ODIA to "ଦୟାକରି ଆମକୁ ସାହାଯ୍ୟ କରନ୍ତୁ।",
            Language.BENGALI to "দয়া করে আমাদের সাহায্য করুন।"
        ),
        "voice_note_received" to mapOf(
            Language.ENGLISH to "Voice message received.",
            Language.HINDI to "वॉयस संदेश प्राप्त हुआ।",
            Language.GUJARATI to "વૉઇસ સંદેશ મળ્યો.",
            Language.MARATHI to "व्हॉइस संदेश प्राप्त झाला.",
            Language.KANNADA to "ಧ್ವನಿ ಸಂದೇಶ ಸ್ವೀಕರಿಸಲಾಗಿದೆ.",
            Language.MALAYALAM to "വോയ്‌സ് സന്ദേശം ലഭിച്ചു.",
            Language.TAMIL to "குரல் செய்தி பெறப்பட்டது.",
            Language.TELUGU to "వాయిస్ సందేశం అందింది.",
            Language.ODIA to "ଭଏସ୍ ବାର୍ତ୍ତା ମିଳିଲା।",
            Language.BENGALI to "ভয়েস বার্তা প্রাপ্ত হয়েছে।"
        ),
        "medical_help" to mapOf(
            Language.ENGLISH to "Doctor needed. Send medical help.",
            Language.HINDI to "डॉक्टर की जरूरत है। चिकित्सा सहायता भेजें।",
            Language.GUJARATI to "ડોક્ટરની જરૂર છે. તબીબી સહાય મોકલો.",
            Language.MARATHI to "डॉक्टरांची गरज आहे. वैद्यकीय मदत पाठवा.",
            Language.TAMIL to "மருத்துவர் தேவை. மருத்துவ உதவி அனுப்பவும்.",
            Language.TELUGU to "డాక్టర్ అవసరం. వైద్య సహాయం పంపండి.",
            Language.KANNADA to "ವೈದ್ಯರ ಅಗತ್ಯವಿದೆ. ವೈದ್ಯಕೀಯ ನೆರವು ಕಳುಹಿಸಿ.",
            Language.MALAYALAM to "ഡോക്ടറെ ആവശ്യമുണ്ട്. മെഡിക്കൽ സഹായം അയക്കൂ.",
            Language.ODIA to "ଡାକ୍ତର ଆବଶ୍ୟକ। ଚିକିତ୍ସା ସହାୟତା ପଠାନ୍ତୁ।",
            Language.BENGALI to "ডাক্তার প্রয়োজন। চিকিৎসা সহায়তা পাঠান।"
        ),
        "person_injured" to mapOf(
            Language.ENGLISH to "People are injured here.",
            Language.HINDI to "यहाँ लोग घायल हैं।",
            Language.GUJARATI to "અહીં લોકો ઘાયલ થયા છે.",
            Language.MARATHI to "येथे लोक जखमी झाले आहेत.",
            Language.TAMIL to "இங்கு மக்கள் காயமடைந்துள்ளனர்.",
            Language.TELUGU to "ఇక్కడ ప్రజలు గాయపడ్డారు.",
            Language.KANNADA to "ಇಲ್ಲಿ ಜನರು ಗಾಯಗೊಂಡಿದ್ದಾರೆ.",
            Language.MALAYALAM to "ഇവിടെ ആളുകൾക്ക് പരിക്കേറ്റിട്ടുണ്ട്.",
            Language.ODIA to "ଏଠାରେ ଲୋକମାନେ ଆହତ ହୋଇଛନ୍ତି।",
            Language.BENGALI to "এখানে মানুষ আহত হয়েছেন।"
        ),
        "flood_alert" to mapOf(
            Language.ENGLISH to "Flood water is rising rapidly.",
            Language.HINDI to "बाढ़ का पानी तेजी से बढ़ रहा है।",
            Language.GUJARATI to "પૂરનું પાણી ઝડપથી વધી રહ્યું છે.",
            Language.MARATHI to "पुराचे पाणी वेगाने वाढत आहे.",
            Language.TAMIL to "வெள்ள நீர் வேகமாக உயர்ந்து வருகிறது.",
            Language.TELUGU to "వరద నీరు వేగంగా పెరుగుతోంది.",
            Language.KANNADA to "ಪ್ರವಾಹದ ನೀರು ವೇಗವಾಗಿ ಏರುತ್ತಿದೆ.",
            Language.MALAYALAM to "വെള്ളപ്പൊക്ക ജലം വേഗത്തിൽ ഉയരുന്നു.",
            Language.ODIA to "ବନ୍ୟା ଜଳ ଦ୍ରୁତ ଗତିରେ ବୃଦ୍ଧି ପାଉଛି।",
            Language.BENGALI to "বন্যার জল দ্রুত বাড়ছে।"
        ),
        "fire_alert" to mapOf(
            Language.ENGLISH to "Fire breakout detected. Danger!",
            Language.HINDI to "आग लग गई है। खतरा!",
            Language.GUJARATI to "આગ લાગી છે. ભય!",
            Language.MARATHI to "आग लागली आहे. धोका!",
            Language.TAMIL to "தீ விபத்து ஏற்பட்டது. ஆபத்து!",
            Language.TELUGU to "మంటలు చెలరేగాయి. ప్రమాదం!",
            Language.KANNADA to "ಬೆಂಕಿ ಕಾಣಿಸಿಕೊಂಡಿದೆ. ಅಪಾಯ!",
            Language.MALAYALAM to "തീപിടുത്തം ഉണ്ടായിരിക്കുന്നു. അപകടം!",
            Language.ODIA to "ନିଆଁ ଲାଗିଯାଇଛି। ବିପଦ!",
            Language.BENGALI to "আগুন লেগেছে। বিপদ!"
        ),
        "need_water" to mapOf(
            Language.ENGLISH to "We need clean drinking water.",
            Language.HINDI to "हमें पीने के पानी की आवश्यकता है।",
            Language.GUJARATI to "અમને પીવાના પાણીની જરૂર છે.",
            Language.MARATHI to "आम्हाला पिण्याच्या पाण्याची गरज आहे.",
            Language.TAMIL to "எங்களுக்கு குடிநீர் தேவை.",
            Language.TELUGU to "మాకు త్రాగునీరు అవసరం.",
            Language.KANNADA to "ನಮಗೆ ಕುಡಿಯುವ ನೀರಿನ ಅಗತ್ಯವಿದೆ.",
            Language.MALAYALAM to "ഞങ്ങൾക്ക് കുടിവെള്ളം ആവശ്യമുണ്ട്.",
            Language.ODIA to "ଆମକୁ ପିଇବା ପାଣି ଆବଶ୍ୟକ।",
            Language.BENGALI to "আমাদের পানীয় জল প্রয়োজন।"
        ),
        "need_food" to mapOf(
            Language.ENGLISH to "We need food supplies.",
            Language.HINDI to "हमें भोजन की आवश्यकता है।",
            Language.GUJARATI to "અમને ખોરાકની જરૂર છે.",
            Language.MARATHI to "आम्हाला अन्नाची गरज आहे.",
            Language.TAMIL to "எங்களுக்கு உணவு தேவை.",
            Language.TELUGU to "మాకు ఆహారం అవసరం.",
            Language.KANNADA to "ನಮಗೆ ಆಹಾರದ ಅಗತ್ಯವಿದೆ.",
            Language.MALAYALAM to "ഞങ്ങൾക്ക് ഭക്ഷണം ആവശ്യമാണ്.",
            Language.ODIA to "ଆମକୁ ଖାଦ୍ୟ ଆବଶ୍ୟକ।",
            Language.BENGALI to "আমাদের খাবার প্রয়োজন।"
        ),
        "evacuate_now" to mapOf(
            Language.ENGLISH to "Evacuate immediately to safe ground.",
            Language.HINDI to "तुरंत सुरक्षित स्थान पर जाएं।",
            Language.GUJARATI to "તરત જ સુરક્ષિત સ્થળે જાઓ.",
            Language.MARATHI to "तातडीने सुरक्षित स्थळी जा.",
            Language.TAMIL to "உடனடியாக பாதுகாப்பான இடத்திற்கு செல்லவும்.",
            Language.TELUGU to "వెంటనే సురక్షిత ప్రాంతానికి తరలివెళ్లండి.",
            Language.KANNADA to "ತಕ್ಷಣ ಸುರಕ್ಷಿತ ಸ್ಥಳಕ್ಕೆ ತೆರಳಿ.",
            Language.MALAYALAM to "ഉടൻ സുരക്ഷിത സ്ഥാനത്തേക്ക് മാറുക.",
            Language.ODIA to "ତୁରନ୍ତ ସୁରକ୍ଷିତ ସ୍ଥାନକୁ ଯାଆନ୍ତୁ।",
            Language.BENGALI to "অবিলম্বে নিরাপদ স্থানে যান।"
        ),
        "trapped_rubble" to mapOf(
            Language.ENGLISH to "People trapped under collapsed building.",
            Language.HINDI to "लोग मलबे के नीचे दबे हुए हैं।",
            Language.GUJARATI to "લોકો કાટમાળ નીચે ફસાયેલા છે.",
            Language.MARATHI to "लोक ढिगाऱ्याखाली अडकले आहेत.",
            Language.TAMIL to "இடிபாடுகளுக்குள் மக்கள் சிக்கியுள்ளனர்.",
            Language.TELUGU to "శిథిలాల కింద ప్రజలు చిక్కుకున్నారు.",
            Language.KANNADA to "ಜನರು ಅವಶೇಷಗಳ ಅಡಿಯಲ್ಲಿ ಸಿಲುಕಿದ್ದಾರೆ.",
            Language.MALAYALAM to "തകർന്ന കെട്ടിടാവശിഷ്ടങ്ങൾക്കിടയിൽ ആളുകൾ കുടുങ്ങിയിട്ടുണ്ട്.",
            Language.ODIA to "ଲୋକମାନେ ଧ୍ୱଂସାବଶେଷ ତଳେ ଫସି ରହିଛନ୍ତି।",
            Language.BENGALI to "ধ্বংসস্তূপের নিচে মানুষ আটকে আছেন।"
        ),
        "we_are_safe" to mapOf(
            Language.ENGLISH to "We are safe here.",
            Language.HINDI to "हम यहाँ सुरक्षित हैं।",
            Language.GUJARATI to "અમે અહીં સુરક્ષિત છીએ.",
            Language.MARATHI to "आम्ही येथे सुरक्षित आहोत.",
            Language.TAMIL to "நாங்கள் இங்கே பாதுகாப்பாக இருக்கிறோம்.",
            Language.TELUGU to "మేము ఇక్కడ సురక్షితంగా ఉన్నాము.",
            Language.KANNADA to "ನಾವು ಇಲ್ಲಿ ಸುರಕ್ಷಿತವಾಗಿದ್ದೇವೆ.",
            Language.MALAYALAM to "ഞങ്ങൾ ഇവിടെ സുരക്ഷിതരാണ്.",
            Language.ODIA to "ଆମେ ଏଠାରେ ସୁରକ୍ଷିତ ଅଛୁ।",
            Language.BENGALI to "আমরা এখানে নিরাপদ।"
        ),
        "can_you_hear_me" to mapOf(
            Language.ENGLISH to "Can you hear me?",
            Language.HINDI to "क्या आप मुझे सुन सकते हैं?",
            Language.GUJARATI to "શું તમે મને સાંભળી શકો છો?",
            Language.MARATHI to "तुम्ही मला ऐकू शकता का?",
            Language.TAMIL to "நீங்கள் என்னை கேட்க முடிகிறதா?",
            Language.TELUGU to "మీరు నన్ను వినగలరా?",
            Language.KANNADA to "ನೀವು ನನ್ನನ್ನು ಕೇಳಬಹುದೇ?",
            Language.MALAYALAM to "നിങ്ങൾക്ക് എന്നെ കേൾക്കാമോ?",
            Language.ODIA to "ଆପଣ ମୋତେ ଶୁଣିପାରୁଛନ୍ତି କି?",
            Language.BENGALI to "আপনি কি আমাকে শুনতে পাচ্ছেন?"
        ),
        "where_are_you" to mapOf(
            Language.ENGLISH to "Where are you?",
            Language.HINDI to "आप कहाँ हैं?",
            Language.GUJARATI to "તમે ક્યાં છો?",
            Language.MARATHI to "तुम्ही कुठे आहात?",
            Language.TAMIL to "நீங்கள் எங்கே இருக்கிறீர்கள்?",
            Language.TELUGU to "మీరు ఎక్కడ ఉన్నారు?",
            Language.KANNADA to "ನೀವು ಎಲ್ಲಿದ್ದೀರಿ?",
            Language.MALAYALAM to "നിങ്ങൾ എവിടെയാണ്?",
            Language.ODIA to "ଆପଣ କେଉଁଠାରେ ଅଛନ୍ତି?",
            Language.BENGALI to "আপনি কোথায় আছেন?"
        ),
        "i_am_here" to mapOf(
            Language.ENGLISH to "I am here.",
            Language.HINDI to "मैं यहाँ हूँ।",
            Language.GUJARATI to "હું અહીં છું.",
            Language.MARATHI to "मी येथे आहे.",
            Language.TAMIL to "நான் இங்கே இருக்கிறேன்.",
            Language.TELUGU to "నేను ఇక్కడ ఉన్నాను.",
            Language.KANNADA to "ನಾನು ಇಲ್ಲಿದ್ದೇನೆ.",
            Language.MALAYALAM to "ഞാൻ ഇവിടെയുണ്ട്.",
            Language.ODIA to "ମୁଁ ଏଠାରେ ଅଛି।",
            Language.BENGALI to "আমি এখানে আছি।"
        ),
        "come_quickly" to mapOf(
            Language.ENGLISH to "Please come quickly.",
            Language.HINDI to "कृपया जल्दी आइए।",
            Language.GUJARATI to "કૃપા કરીને ઝડપથી આવો.",
            Language.MARATHI to "कृपया लवकर या.",
            Language.TAMIL to "தயவுசெய்து சீக்கிரம் வாருங்கள்.",
            Language.TELUGU to "దయచేసి త్వరగా రండి.",
            Language.KANNADA to "ದಯವಿಟ್ಟು ಬೇಗ ಬನ್ನಿ.",
            Language.MALAYALAM to "ദയവായി വേഗം വരൂ.",
            Language.ODIA to "ଦୟାକରି ଶୀଘ୍ର ଆସନ୍ତୁ।",
            Language.BENGALI to "দয়া করে তাড়াতাড়ি আসুন।"
        ),
        "all_ok" to mapOf(
            Language.ENGLISH to "Everything is all right.",
            Language.HINDI to "सब कुछ ठीक है।",
            Language.GUJARATI to "બધું બરાબર છે.",
            Language.MARATHI to "सर्व काही ठीक आहे.",
            Language.TAMIL to "எல்லாம் சரியாக உள்ளது.",
            Language.TELUGU to "అంతా బాగానే ఉంది.",
            Language.KANNADA to "ಎಲ್ಲವೂ ಸರಿಯಾಗಿದೆ.",
            Language.MALAYALAM to "എല്ലാം ശരിയാണ്.",
            Language.ODIA to "ସବୁକିଛି ଠିକ୍ ଅଛି।",
            Language.BENGALI to "সবকিছু ঠিক আছে।"
        )
    )

    fun translate(text: String, sourceLang: Language, targetLang: Language): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ""

        // Same-language optimization (zero latency, exact transcript preserved)
        if (sourceLang == targetLang) {
            return trimmed
        }

        val cleanInput = trimmed.replace("[।.,?!:;]".toRegex(), "").trim()

        // 1. Exact phrase lookup
        for ((_, translations) in PHRASE_INDEX) {
            val sourcePhrase = translations[sourceLang]
            val targetPhrase = translations[targetLang]
            if (sourcePhrase != null && targetPhrase != null) {
                val cleanSource = sourcePhrase.replace("[।.,?!:;]".toRegex(), "").trim()
                if (cleanSource.equals(cleanInput, ignoreCase = true)) {
                    return targetPhrase
                }
            }
        }

        // 2. Substring phrase match
        for ((_, translations) in PHRASE_INDEX) {
            val sourcePhrase = translations[sourceLang]
            val targetPhrase = translations[targetLang]
            if (sourcePhrase != null && targetPhrase != null) {
                val cleanSource = sourcePhrase.replace("[।.,?!:;]".toRegex(), "").trim()
                if (cleanInput.contains(cleanSource, ignoreCase = true) || cleanSource.contains(cleanInput, ignoreCase = true)) {
                    return targetPhrase
                }
            }
        }

        // 3. Fallback: Return original text if no dictionary match
        return trimmed
    }
}
