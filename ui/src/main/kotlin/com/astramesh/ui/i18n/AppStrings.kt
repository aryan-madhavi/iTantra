package com.astramesh.ui.i18n

import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.neverEqualPolicy
import java.util.Locale

/**
 * Immutable model containing localized UI strings for iTantra.
 */
data class AppStrings(
    val languageCode: String,

    // Navigation
    val navDiscovery: String,
    val navComms: String,
    val navHistory: String,
    val navStats: String,

    // Common & Header
    val appName: String,
    val offlineCommsMesh: String,
    val meshActiveReady: String,
    val meshActive: String,
    val emergencyBroadcast: String,

    // Splash & Onboarding
    val initializeSystem: String,
    val enterTacticalMesh: String,
    val selectPrimaryLanguage: String,
    val selectLanguageSubtitle: String,
    val continueButton: String,
    val requiredPermissions: String,
    val permissionsSubtitle: String,
    val permMicTitle: String,
    val permMicDesc: String,
    val permBtTitle: String,
    val permBtDesc: String,
    val permWifiTitle: String,
    val permWifiDesc: String,
    val grantAndStart: String,

    // Device Discovery
    val scanningMesh: String,
    val searchingLocalNodes: String,
    val availableNodes: String,
    val signalStrength: String,
    val leader: String,
    val connected: String,
    val connecting: String,
    val offline: String,

    // Settings
    val settingsTitle: String,
    val settingsSubtitle: String,
    val userProfileTitle: String,
    val operatorName: String,
    val enterNamePlaceholder: String,
    val tacticalCallsign: String,
    val callsignPlaceholder: String,
    val saveProfile: String,
    val savedToSystem: String,
    val languageSettingsTitle: String,
    val appInterfaceLanguage: String,
    val speechInputLanguage: String,
    val receiverOutputLanguage: String,
    val receiverTranslationTitle: String,
    val receiverTranslationDesc: String,
    val pttToggleTitle: String,
    val pttToggleDesc: String,
    val statePersisted: String,
    val statePending: String,
    val compactRepresentation: String,
    val resetOnboarding: String,

    // Walkie Talkie & PTT
    val holdToBroadcast: String,
    val pushToTalk: String,
    val secureMesh: String,
    val recordingAudio: String,
    val transmitting: String,

    // Alert Mode
    val emergencyAlertTitle: String,
    val emergencyAlertDesc: String,
    val broadcastAlertNow: String,
    val cancel: String,

    // Transcripts & Diagnostics
    val transcriptsTitle: String,
    val searchTranscripts: String,
    val noTranscripts: String,
    val clearHistory: String,
    val diagnosticsTitle: String,
    val meshLatency: String,
    val connectionSettings: String,
    val devMode: String
) {
    val peersActive: String
        get() = when (languageCode) {
            "hi" -> "सक्रिय साथी"
            "gu" -> "સક્રિય પીઅર્સ"
            "mr" -> "सक्रिय सहकारी"
            "kn" -> "ಸಕ್ರಿಯ ಸಹಪಾಠಿಗಳು"
            "ml" -> "സജീവ പിയർമാർ"
            "ta" -> "செயலில் உள்ளவர்கள்"
            "te" -> "క్రియాశీల సహచరులు"
            "or" -> "ସକ୍ରିୟ ସାଥୀ"
            "bn" -> "সক্রিয় সহকর্মী"
            else -> "peers active"
        }

    val contacts: String
        get() = when (languageCode) {
            "hi" -> "संपर्क"
            "gu" -> "સંપર્કો"
            "mr" -> "संपर्क"
            "kn" -> "ಸಂಪರ್ಕಗಳು"
            "ml" -> "ബന്ധങ്ങൾ"
            "ta" -> "தொடர்புகள்"
            "te" -> "పరిచయాలు"
            "or" -> "ଯୋଗାଯୋଗ"
            "bn" -> "পরিচিতি"
            else -> "Contacts"
        }

    val meshBroadcast: String
        get() = when (languageCode) {
            "hi" -> "मेश प्रसारण"
            "gu" -> "મેશ પ્રસારણ"
            "mr" -> "मेश प्रसारण"
            "kn" -> "ಮೆಶ್ ಪ್ರಸಾರ"
            "ml" -> "മെഷ് പ്രക്ഷേപണം"
            "ta" -> "மெஷ் ஒலிபரப்பு"
            "te" -> "మెష్ ప్రసారం"
            "or" -> "ମେଶ୍ ପ୍ରସାରଣ"
            "bn" -> "মেশ সম্প্রচার"
            else -> "Mesh Broadcast"
        }

    val directPeer: String
        get() = when (languageCode) {
            "hi" -> "पीयर"
            "gu" -> "પીઅર"
            "mr" -> "पीअर"
            "kn" -> "ಸಹಪಾಠಿ"
            "ml" -> "പിയർ"
            "ta" -> "பியர்"
            "te" -> "పీర్"
            "or" -> "ସାଥୀ"
            "bn" -> "সহকর্মী"
            else -> "Peer"
        }

    val allMesh: String
        get() = when (languageCode) {
            "hi" -> "संपूर्ण मेश"
            "gu" -> "આખું મેશ"
            "mr" -> "संपूर्ण मेश"
            "kn" -> "ಸಂಪೂರ್ಣ ಮೆಶ್"
            "ml" -> "മുഴുവൻ മെഷ്"
            "ta" -> "முழு மெஷ்"
            "te" -> "మొత్తం మెష్"
            "or" -> "ସମ୍ପୂର୍ଣ୍ଣ ମେଶ୍"
            "bn" -> "সমগ্র মেশ"
            else -> "All Mesh"
        }

    val replay: String
        get() = when (languageCode) {
            "hi" -> "फिर चलाएं"
            "gu" -> "ફરી ચલાવો"
            "mr" -> "पुन्हा वाजवा"
            "kn" -> "ಮರುಚಲಾಯಿಸಿ"
            "ml" -> "വീണ്ടും പ്ലേ ചെയ്യുക"
            "ta" -> "மீண்டும் இயக்கு"
            "te" -> "మళ్లీ ప్లే చేయండి"
            "or" -> "ପୁନଃ ଚଲାନ୍ତୁ"
            "bn" -> "পুনরায় চালান"
            else -> "Replay"
        }

    val sent: String
        get() = when (languageCode) {
            "hi" -> "भेजा गया"
            "gu" -> "મોકલેલ"
            "mr" -> "पाठवले"
            "kn" -> "ಕಳುಹಿಸಲಾಗಿದೆ"
            "ml" -> "അയച്ചു"
            "ta" -> "அனுப்பப்பட்டது"
            "te" -> "పంపబడింది"
            "or" -> "ପଠାଯାଇଛି"
            "bn" -> "পাঠানো হয়েছে"
            else -> "Sent"
        }

    val received: String
        get() = when (languageCode) {
            "hi" -> "प्राप्त"
            "gu" -> "પ્રાપ્ત"
            "mr" -> "प्राप्त"
            "kn" -> "ಸ್ವೀಕರಿಸಲಾಗಿದೆ"
            "ml" -> "ലഭിച്ചു"
            "ta" -> "பெறப்பட்டது"
            "te" -> "స్వీకరించబడింది"
            "or" -> "ପ୍ରାପ୍ତ"
            "bn" -> "প্রাপ্ত"
            else -> "Received"
        }

    private fun localized(
        hindi: String,
        gujarati: String,
        marathi: String,
        kannada: String,
        malayalam: String,
        tamil: String,
        telugu: String,
        odia: String,
        bengali: String,
        english: String
    ): String = when (languageCode) {
        "hi" -> hindi
        "gu" -> gujarati
        "mr" -> marathi
        "kn" -> kannada
        "ml" -> malayalam
        "ta" -> tamil
        "te" -> telugu
        "or" -> odia
        "bn" -> bengali
        else -> english
    }

    val peerCountLabel: String
        get() = localized("सक्रिय साथी", "સક્રિય પીઅર્સ", "सक्रिय सहकारी", "ಸಕ್ರಿಯ ಸಹಪಾಠಿಗಳು", "സജീവ പിയർമാർ", "செயலில் உள்ளவர்கள்", "క్రియాశీల సహచరులు", "ସକ୍ରିୟ ସାଥୀ", "সক্রিয় সহকর্মী", "peers")

    val assignDeviceNamePrompt: String
        get() = localized("डिवाइस के लिए परिचित नाम दें:", "ઉપકરણ માટે ઓળખી શકાય તેવું નામ આપો:", "डिव्हाइससाठी ओळखीचे नाव द्या:", "ಸಾಧನಕ್ಕೆ ಸುಲಭವಾದ ಹೆಸರನ್ನು ನೀಡಿ:", "ഉപകരണത്തിന് പരിചിതമായ പേര് നൽകുക:", "சாதனத்திற்கு ஒரு பெயரை வழங்கவும்:", "పరికరానికి గుర్తుండే పేరు ఇవ్వండి:", "ଉପକରଣ ପାଇଁ ଏକ ସହଜ ନାମ ଦିଅନ୍ତୁ:", "ডিভাইসের জন্য একটি পরিচিত নাম দিন:", "Assign a friendly name for device:")

    val deviceLabel: String
        get() = localized("डिवाइस", "ઉપકરણ", "डिव्हाइस", "ಸಾಧನ", "ഉപകരണം", "சாதனம்", "పరికరం", "ଉପକରଣ", "ডিভাইস", "Device")

    val p2pMeshLink: String
        get() = localized("P2P मेश लिंक", "P2P મેશ લિંક", "P2P मेश लिंक", "P2P ಮೆಶ್ ಲಿಂಕ್", "P2P മെഷ് ലിങ്ക്", "P2P மெஷ் இணைப்பு", "P2P మెష్ లింక్", "P2P ମେଶ୍ ଲିଙ୍କ୍", "P2P মেশ লিংক", "P2P Mesh Link")

    val sttOn: String
        get() = localized("STT: चालू", "STT: ચાલુ", "STT: सुरू", "STT: ಆನ್", "STT: ഓൺ", "STT: ஆன்", "STT: ఆన్", "STT: ଅନ୍", "STT: চালু", "STT: ON")

    val sttOff: String
        get() = localized("STT: बंद", "STT: બંધ", "STT: बंद", "STT: ಆಫ್", "STT: ഓഫ്", "STT: ஆஃப்", "STT: ఆఫ్", "STT: ଅଫ୍", "STT: বন্ধ", "STT: OFF")

    val recordingHoldToTalk: String
        get() = localized("रिकॉर्ड हो रहा है (बोलने के लिए दबाए रखें)", "રેકોર્ડ થઈ રહ્યું છે (બોલવા માટે દબાવી રાખો)", "रेकॉर्डिंग सुरू (बोलण्यासाठी दाबून ठेवा)", "ರೆಕಾರ್ಡಿಂಗ್ (ಮಾತನಾಡಲು ಒತ್ತಿಹಿಡಿಯಿರಿ)", "റെക്കോർഡിംഗ് (സംസാരിക്കാൻ അമർത്തിപ്പിടിക്കുക)", "பதிவு செய்யப்படுகிறது (பேச அழுத்திப் பிடிக்கவும்)", "రికార్డింగ్ (మాట్లాడటానికి నొక్కి పట్టుకోండి)", "ରେକର୍ଡ ହେଉଛି (କହିବା ପାଇଁ ଧରି ରଖନ୍ତୁ)", "রেকর্ড হচ্ছে (কথা বলতে চেপে ধরে রাখুন)", "RECORDING (HOLD TO TALK)")

    val pushToTalkMode: String
        get() = localized("पुश-टू-टॉक मोड", "પુશ-ટુ-ટોક મોડ", "पुश-टू-टॉक मोड", "ಪುಶ್-ಟು-ಟಾಕ್ ಮೋಡ್", "പുഷ്-ടു-ടോക്ക് മോഡ്", "புஷ்-டு-டாக் பயன்முறை", "పుష్-టు-టాక్ మోడ్", "ପୁଶ୍-ଟୁ-ଟକ୍ ମୋଡ୍", "পুশ-টু-টক মোড", "PUSH-TO-TALK MODE")

    fun releaseToSend(durationSeconds: Int): String = localized("भेजने के लिए छोड़ें (${durationSeconds} सेकंड)", "મોકલવા માટે છોડો (${durationSeconds} સેકન્ડ)", "पाठवण्यासाठी सोडा (${durationSeconds} सेकंद)", "ಕಳುಹಿಸಲು ಬಿಡಿ (${durationSeconds} ಸೆಕೆಂಡ್)", "അയയ്ക്കാൻ വിടുക (${durationSeconds} സെക്കൻഡ്)", "அனுப்ப விடுங்கள் (${durationSeconds} வினாடி)", "పంపడానికి వదలండి (${durationSeconds} సెకన్లు)", "ପଠାଇବା ପାଇଁ ଛାଡ଼ନ୍ତୁ (${durationSeconds} ସେକେଣ୍ଡ)", "পাঠাতে ছেড়ে দিন (${durationSeconds} সেকেন্ড)", "Release to Send (${durationSeconds}s)")

    val holdToTalk: String
        get() = localized("बोलने के लिए दबाए रखें", "બોલવા માટે દબાવી રાખો", "बोलण्यासाठी दाबून ठेवा", "ಮಾತನಾಡಲು ಒತ್ತಿಹಿಡಿಯಿರಿ", "സംസാരിക്കാൻ അമർത്തിപ്പിടിക്കുക", "பேச அழுத்திப் பிடிக்கவும்", "మాట్లాడటానికి నొక్కి పట్టుకోండి", "କହିବା ପାଇଁ ଧରି ରଖନ୍ତୁ", "কথা বলতে চেপে ধরে রাখুন", "Hold to Talk")

    val peerTransmittingBusy: String
        get() = localized("साथी प्रसारित कर रहा है (व्यस्त)", "પીઅર પ્રસારણ કરી રહ્યું છે (વ્યસ્ત)", "सहकारी प्रसारित करत आहे (व्यस्त)", "ಸಹಪಾಠಿ ಪ್ರಸಾರದಲ್ಲಿದೆ (ವ್ಯಸ್ತ)", "പിയർ പ്രക്ഷേപണം ചെയ്യുന്നു (തിരക്കിലാണ്)", "பியர் ஒலிபரப்புகிறது (பிஸி)", "పీర్ ప్రసారం చేస్తోంది (బిజీ)", "ସାଥୀ ପ୍ରସାରଣ କରୁଛି (ବ୍ୟସ୍ତ)", "সহকর্মী সম্প্রচার করছে (ব্যস্ত)", "PEER TRANSMITTING (BUSY)")

    val walkieTalkieHalfDuplex: String
        get() = localized("वॉकी-टॉकी (हाफ-डुप्लेक्स)", "વૉકી-ટૉકી (હાફ-ડુપ્લેક્સ)", "वॉकी-टॉकी (हाफ-डुप्लेक्स)", "ವಾಕಿ-ಟಾಕಿ (ಹಾಫ್-ಡ್ಯುಪ್ಲೆಕ್ಸ್)", "വാക്കി-ടോക്കി (ഹാഫ്-ഡ്യൂപ്ലെക്സ്)", "வாக்கி-டாக்கி (அரை-இருவழி)", "వాకీ-టాకీ (హాఫ్-డ్యూప్లెక్స్)", "ୱାକି-ଟକି (ହାଫ୍-ଡୁପ୍ଲେକ୍ସ)", "ওয়াকি-টকি (হাফ-ডুপ্লেক্স)", "WALKIE-TALKIE (HALF-DUPLEX)")

    val holdToTransmit: String
        get() = localized("प्रसारित करने के लिए दबाए रखें", "પ્રસારણ માટે દબાવી રાખો", "प्रसारित करण्यासाठी दाबून ठेवा", "ಪ್ರಸಾರ ಮಾಡಲು ಒತ್ತಿಹಿಡಿಯಿರಿ", "പ്രക്ഷേപണം ചെയ്യാൻ അമർത്തിപ്പിടിക്കുക", "ஒலிபரப்ப அழுத்திப் பிடிக்கவும்", "ప్రసారం చేయడానికి నొక్కి పట్టుకోండి", "ପ୍ରସାରଣ ପାଇଁ ଧରି ରଖନ୍ତୁ", "সম্প্রচার করতে চেপে ধরে রাখুন", "Hold to Transmit")

    val emergencyVoiceBroadcast: String
        get() = localized("आपातकालीन SOS वॉइस प्रसारण", "કટોકટી SOS વૉઇસ પ્રસારણ", "तातडीचे SOS व्हॉइस प्रसारण", "ತುರ್ತು SOS ಧ್ವನಿ ಪ್ರಸಾರ", "അടിയന്തര SOS വോയ്സ് പ്രക്ഷേപണം", "அவசர SOS குரல் ஒலிபரப்பு", "అత్యవసర SOS వాయిస్ ప్రసారం", "ଜରୁରୀକାଳୀନ SOS ଭଏସ୍ ପ୍ରସାରଣ", "জরুরি SOS ভয়েস সম্প্রচার", "EMERGENCY SOS VOICE BROADCAST")

    val emergencyPriorityDescription: String
        get() = localized("मल्टी-हॉप फ्लड डिलीवरी के साथ सर्वोच्च मेश पैकेट प्राथमिकता", "મલ્ટી-હોપ ફ્લડ ડિલિવરી સાથે સર્વોચ્ચ મેશ પેકેટ પ્રાથમિકતા", "मल्टी-हॉप फ्लड डिलिव्हरीसह सर्वोच्च मेश पॅकेट प्राधान्य", "ಮಲ್ಟಿ-ಹಾಪ್ ಫ್ಲಡ್ ವಿತರಣೆಯೊಂದಿಗೆ ಅತ್ಯುನ್ನತ ಮೆಶ್ ಪ್ಯಾಕೆಟ್ ಆದ್ಯತೆ", "മൾട്ടി-ഹോപ്പ് ഫ്ലഡ് ഡെലിവറിയോടെയുള്ള പരമാവധി മെഷ് പാക്കറ്റ് മുൻഗണന", "பல-தாவல் வெள்ள விநியோகத்துடன் அதிகபட்ச மெஷ் பாக்கெட் முன்னுரிமை", "మల్టీ-హాప్ ఫ్లడ్ డెలివరీతో అత్యధిక మెష్ ప్యాకెట్ ప్రాధాన్యత", "ମଲ୍ଟି-ହପ୍ ଫ୍ଲଡ୍ ଡେଲିଭରୀ ସହ ସର୍ବୋଚ୍ଚ ମେଶ୍ ପ୍ୟାକେଟ୍ ପ୍ରାଥମିକତା", "মাল্টি-হপ ফ্লাড ডেলিভারিসহ সর্বোচ্চ মেশ প্যাকেট অগ্রাধিকার", "Highest mesh packet priority with multi-hop flood delivery")

    val holdSosVoice: String
        get() = localized("SOS वॉइस के लिए दबाए रखें", "SOS વૉઇસ માટે દબાવી રાખો", "SOS व्हॉइससाठी दाबून ठेवा", "SOS ಧ್ವನಿಗಾಗಿ ಒತ್ತಿಹಿಡಿಯಿರಿ", "SOS വോയ്സിനായി അമർത്തിപ്പിടിക്കുക", "SOS குரலுக்கு அழுத்திப் பிடிக்கவும்", "SOS వాయిస్ కోసం నొక్కి పట్టుకోండి", "SOS ଭଏସ୍ ପାଇଁ ଧରି ରଖନ୍ତୁ", "SOS ভয়েসের জন্য চেপে ধরে রাখুন", "Hold SOS Voice")

    val sending: String
        get() = localized("भेजा जा रहा है", "મોકલાઈ રહ્યું છે", "पाठवत आहे", "ಕಳುಹಿಸಲಾಗುತ್ತಿದೆ", "അയയ്ക്കുന്നു", "அனுப்பப்படுகிறது", "పంపుతోంది", "ପଠାଯାଉଛି", "পাঠানো হচ্ছে", "Sending")

    val handsFreeContinuousSpeech: String
        get() = localized("हैंड्स-फ्री निरंतर भाषण (STT/TTS)", "હેન્ડ્સ-ફ્રી સતત સ્પીચ (STT/TTS)", "हँड्स-फ्री सतत भाषण (STT/TTS)", "ಹ್ಯಾಂಡ್ಸ್-ಫ್ರೀ ನಿರಂತರ ಧ್ವನಿ (STT/TTS)", "ഹാൻഡ്സ്-ഫ്രീ തുടർച്ചയായ സംഭാഷണം (STT/TTS)", "கைகளற்ற தொடர்ச்சியான பேச்சு (STT/TTS)", "హ్యాండ్స్-ఫ్రీ నిరంతర ప్రసంగం (STT/TTS)", "ହ୍ୟାଣ୍ଡସ୍-ଫ୍ରି ନିରନ୍ତର ଭାଷଣ (STT/TTS)", "হ্যান্ডস-ফ্রি ধারাবাহিক কথা (STT/TTS)", "HANDS-FREE CONTINUOUS SPEECH (STT/TTS)")

    val transcriptLabel: String
        get() = localized("प्रतिलेख", "ટ્રાન્સક્રિપ્ટ", "प्रतिलेख", "ಪ್ರತಿಲಿಪಿ", "ട്രാൻസ്ക്രിപ്റ്റ്", "படியெடுத்தல்", "ట్రాన్స్‌క్రిప్ట్", "ପ୍ରତିଲିପି", "প্রতিলিপি", "Transcript")

    val initializing: String
        get() = localized("प्रारंभ हो रहा है...", "પ્રારંભ થઈ રહ્યું છે...", "आरंभ होत आहे...", "ಪ್ರಾರಂಭಿಸಲಾಗುತ್ತಿದೆ...", "ആരംഭിക്കുന്നു...", "தொடங்குகிறது...", "ప్రారంభిస్తోంది...", "ଆରମ୍ଭ ହେଉଛି...", "শুরু হচ্ছে...", "Initializing...")

    val packetsSent: String
        get() = localized("भेजे गए पैकेट", "મોકલાયેલા પેકેટ્સ", "पाठवलेली पॅकेट्स", "ಕಳುಹಿಸಿದ ಪ್ಯಾಕೆಟ್‌ಗಳು", "അയച്ച പാക്കറ്റുകൾ", "அனுப்பப்பட்ட தொகுப்புகள்", "పంపిన ప్యాకెట్లు", "ପଠାଯାଇଥିବା ପ୍ୟାକେଟ୍", "পাঠানো প্যাকেট", "Packets Sent")

    val packetsRelayed: String
        get() = localized("रिले किए गए पैकेट", "રિલે થયેલા પેકેટ્સ", "रिले केलेली पॅकेट्स", "ರಿಲೇ ಮಾಡಿದ ಪ್ಯಾಕೆಟ್‌ಗಳು", "റിലേ ചെയ്ത പാക്കറ്റുകൾ", "ரிலே செய்யப்பட்ட தொகுப்புகள்", "రిలే చేసిన ప్యాకెట్లు", "ରିଲେ ହୋଇଥିବା ପ୍ୟାକେଟ୍", "রিলে করা প্যাকেট", "Packets Relayed")

    val packetsReceived: String
        get() = localized("प्राप्त पैकेट", "મેળવાયેલા પેકેટ્સ", "प्राप्त पॅकेट्स", "ಸ್ವೀಕರಿಸಿದ ಪ್ಯಾಕೆಟ್‌ಗಳು", "ലഭിച്ച പാക്കറ്റുകൾ", "பெறப்பட்ட தொகுப்புகள்", "అందుకున్న ప్యాకెట్లు", "ପ୍ରାପ୍ତ ପ୍ୟାକେଟ୍", "পাওয়া প্যাকেট", "Packets Received")

    val activeBleLinks: String
        get() = localized("सक्रिय BLE लिंक", "સક્રિય BLE લિંક્સ", "सक्रिय BLE लिंक्स", "ಸಕ್ರಿಯ BLE ಲಿಂಕ್‌ಗಳು", "സജീവ BLE ലിങ്കുകൾ", "செயலில் உள்ள BLE இணைப்புகள்", "యాక్టివ్ BLE లింకులు", "ସକ୍ରିୟ BLE ଲିଙ୍କ୍", "সক্রিয় BLE লিঙ্ক", "Active BLE Links")

    val broadcastingEmergency: String
        get() = localized("आपातकालीन SOS प्रसारित हो रहा है...", "કટોકટી SOS પ્રસારિત થઈ રહ્યું છે...", "तातडीचे SOS प्रसारित होत आहे...", "ತುರ್ತು SOS ಪ್ರಸಾರವಾಗುತ್ತಿದೆ...", "അടിയന്തര SOS പ്രക്ഷേപണം ചെയ്യുന്നു...", "அவசர SOS ஒலிபரப்பப்படுகிறது...", "అత్యవసర SOS ప్రసారం అవుతోంది...", "ଜରୁରୀକାଳୀନ SOS ପ୍ରସାରଣ ହେଉଛି...", "জরুরি SOS সম্প্রচার হচ্ছে...", "Broadcasting Emergency SOS...")

    val emergencyDispatched: String
        get() = localized("आपातकालीन SOS प्रसारण भेज दिया गया!", "કટોકટી SOS પ્રસારણ મોકલવામાં આવ્યું!", "तातडीचे SOS प्रसारण पाठवले!", "ತುರ್ತು SOS ಪ್ರಸಾರ ಕಳುಹಿಸಲಾಗಿದೆ!", "അടിയന്തര SOS പ്രക്ഷേപണം അയച്ചു!", "அவசர SOS ஒலிபரப்பு அனுப்பப்பட்டது!", "అత్యవసర SOS ప్రసారం పంపబడింది!", "ଜରୁରୀକାଳୀନ SOS ପ୍ରସାରଣ ପଠାଯାଇଛି!", "জরুরি SOS সম্প্রচার পাঠানো হয়েছে!", "Emergency SOS Broadcast Dispatched!")

    val youLabel: String
        get() = localized("आप", "તમે", "तुम्ही", "ನೀವು", "നിങ്ങൾ", "நீங்கள்", "మీరు", "ଆପଣ", "আপনি", "You")

    val verifiedIdentity: String
        get() = localized("सत्यापित क्रिप्टोग्राफ़िक पहचान", "ચકાસાયેલ ક્રિપ્ટોગ્રાફિક ઓળખ", "सत्यापित क्रिप्टोग्राफिक ओळख", "ಪರಿಶೀಲಿಸಿದ ಕ್ರಿಪ್ಟೋಗ್ರಾಫಿಕ್ ಗುರುತು", "പരിശോധിച്ച ക്രിപ്റ്റോഗ്രാഫിക് ഐഡന്റിറ്റി", "சரிபார்க்கப்பட்ட கிரிப்டோகிராஃபிக் அடையாளம்", "ధృవీకరించబడిన క్రిప్టోగ్రాఫిక్ గుర్తింపు", "ଯାଞ୍ଚ ହୋଇଥିବା କ୍ରିପ୍ଟୋଗ୍ରାଫିକ୍ ପରିଚୟ", "যাচাইকৃত ক্রিপ্টোগ্রাফিক পরিচয়", "Verified Cryptographic Identity")

    val voiceNoteLabel: String
        get() = localized("वॉइस नोट", "વૉઇસ નોટ", "व्हॉइस नोट", "ಧ್ವನಿ ಟಿಪ್ಪಣಿ", "വോയ്സ് നോട്ട്", "குரல் குறிப்பு", "వాయిస్ నోట్", "ଭଏସ୍ ନୋଟ୍", "ভয়েস নোট", "Voice Note")

    val idleStatus: String
        get() = localized("निष्क्रिय", "નિષ્ક્રિય", "निष्क्रिय", "ನಿಷ್ಕ್ರಿಯ", "നിഷ്‌ക്രിയം", "செயலற்றது", "నిష్క్రియ", "ନିଷ୍କ୍ରିୟ", "নিষ্ক্রিয়", "IDLE")

    val listeningStatus: String
        get() = localized("सुन रहा है", "સાંભળી રહ્યું છે", "ऐकत आहे", "ಆಲಿಸುತ್ತಿದೆ", "കേൾക്കുന്നു", "கேட்கிறது", "వింటోంది", "ଶୁଣୁଛି", "শুনছে", "LISTENING")

    val speechDetectedStatus: String
        get() = localized("भाषण मिला", "સ્પીચ મળી", "भाषण आढळले", "ಧ್ವನಿ ಪತ್ತೆಯಾಗಿದೆ", "സംഭാഷണം കണ്ടെത്തി", "பேச்சு கண்டறியப்பட்டது", "ప్రసంగం గుర్తించబడింది", "ଭାଷଣ ଚିହ୍ନଟ ହେଲା", "কথা শনাক্ত হয়েছে", "SPEECH DETECTED")

    val transcribingStatus: String
        get() = localized("लिखित रूपांतरण", "ટ્રાન્સક્રાઇબ થઈ રહ્યું છે", "लिप्यंतरण", "ಪಠ್ಯಕ್ಕೆ ಪರಿವರ್ತಿಸಲಾಗುತ್ತಿದೆ", "ട്രാൻസ്ക്രൈബ് ചെയ്യുന്നു", "படியெடுக்கப்படுகிறது", "ట్రాన్స్‌క్రైబ్ చేస్తోంది", "ଟ୍ରାନ୍ସକ୍ରାଇବ୍ ହେଉଛି", "ট্রান্সক্রাইব হচ্ছে", "TRANSCRIBING")

    val thinkingStatus: String
        get() = localized("सोच रहा है", "વિચારી રહ્યું છે", "विचार करत आहे", "ಯೋಚಿಸುತ್ತಿದೆ", "ചിന്തിക്കുന്നു", "சிந்திக்கிறது", "ఆలోచిస్తోంది", "ଚିନ୍ତା କରୁଛି", "ভাবছে", "THINKING")

    val speakingStatus: String
        get() = localized("बोल रहा है", "બોલી રહ્યું છે", "बोलत आहे", "ಮಾತನಾಡುತ್ತಿದೆ", "സംസാരിക്കുന്നു", "பேசுகிறது", "మాట్లాడుతోంది", "କହୁଛି", "কথা বলছে", "SPEAKING")

    val connectedStatus: String
        get() = localized("जुड़ा हुआ", "જોડાયેલ", "जोडलेले", "ಸಂಪರ್ಕಿಸಲಾಗಿದೆ", "ബന്ധിപ്പിച്ചു", "இணைக்கப்பட்டது", "కనెక్ట్ చేయబడింది", "ସଂଯୁକ୍ତ", "সংযুক্ত", "CONNECTED")

    val disconnectedStatus: String
        get() = localized("डिस्कनेक्टेड", "ડિસ્કનેક્ટ થયેલ", "डिस्कनेक्ट केलेले", "ಸಂಪರ್ಕ ಕಡಿತಗೊಂಡಿದೆ", "വിച്ഛേദിച്ചു", "துண்டிக்கப்பட்டது", "డిస్‌కనెక్ట్ చేయబడింది", "ବିଚ୍ଛିନ୍ନ", "বিচ্ছিন্ন", "DISCONNECTED")

    val typeMessage: String
        get() = localized("संदेश लिखें...", "સંદેશ લખો...", "संदेश लिहा...", "ಸಂದೇಶವನ್ನು ಟೈಪ್ ಮಾಡಿ...", "സന്ദേശം ടൈപ്പ് ചെയ്യുക...", "செய்தியை தட்டச்சு செய்க...", "సందేశాన్ని టైప్ చేయండి...", "ବାର୍ତ୍ତା ଲେଖନ୍ତୁ...", "বার্তা লিখুন...", "Type a message...")

    val connectAction: String
        get() = localized("जोड़ें", "જોડાઓ", "जोडा", "ಸಂಪರ್ಕಿಸಿ", "ബന്ധിപ്പിക്കുക", "இணைக்கவும்", "కనెక్ట్ చేయండి", "ସଂଯୋଗ କରନ୍ତୁ", "সংযুক্ত করুন", "Connect")

    val pairingQrCode: String
        get() = localized("पेयरिंग क्यूआर कोड", "પેરિંગ ક્યૂઆર કોડ", "पेअरिंग क्यूआर कोड", "ಜೋಡಣೆ ಕ್ಯೂಆರ್ ಕೋಡ್", "പെയറിംഗ് ക്യുആർ കോഡ്", "இணைக்கும் QR குறியீடு", "పెయిరింగ్ క్యూఆర్ కోడ్", "ଯୋଡ଼ିବା କ୍ୟୁଆର୍ କୋଡ୍", "পেয়ারিং কিউআর কোড", "Pairing QR Code")

    val exportIdentity: String
        get() = localized("निर्यात करें", "નિકાસ કરો", "निर्यात करा", "ರಫ್ತು ಮಾಡಿ", "എക്സ്പോർട്ട് ചെയ്യുക", "ஏற்றுமதி செய்க", "ఎగుమతి చేయండి", "ରପ୍ତାନି କରନ୍ତୁ", "রপ্তানি করুন", "Export")

    val identityExported: String
        get() = localized("पहचान क्लिपबोर्ड पर कॉपी की गई", "ઓળખ ક્લિપબોર્ડ પર કૉપિ થઈ", "ओळख क्लिपबोर्डवर कॉपी केली", "ಗುರುತನ್ನು ಕ್ಲಿಪ್‌ಬೋರ್ಡ್‌ಗೆ ನಕಲಿಸಲಾಗಿದೆ", "ഐഡന്റിറ്റി ക്ലിപ്പ്ബോർഡിലേക്ക് പകർത്തി", "அடையாளம் கிளிப்போர்டுக்கு நகலெடுக்கப்பட்டது", "గుర్తింపు క్లిప్‌బోర్డ్‌కి కాపీ చేయబడింది", "ପରିଚୟ କ୍ଲିପବୋର୍ଡକୁ କପି ହୋଇଛି", "পরিচয় ক্লিপবোর্ডে অনুলিপি করা হয়েছে", "Identity copied to clipboard")

    val importIdentity: String
        get() = localized("आयात करें", "આયાત કરો", "आयात करा", "ಆಮದು ಮಾಡಿ", "ഇമ്പോർട്ട് ചെയ്യുക", "இறக்குமதி செய்க", "దిగుమతి చేయండి", "ଆମଦାନୀ କରନ୍ତୁ", "আমদানি করুন", "Import")

    val importIdentityRequested: String
        get() = localized("पहचान आयात करने का अनुरोध किया गया", "ઓળખ આયાત કરવાની વિનંતી કરી", "ओळख आयात करण्याची विनंती केली", "ಗುರುತನ್ನು ಆಮದು ಮಾಡಲು ವಿನಂತಿಸಲಾಗಿದೆ", "ഐഡന്റിറ്റി ഇമ്പോർട്ട് അഭ്യർത്ഥിച്ചു", "அடையாள இறக்குமதி கோரப்பட்டது", "గుర్తింపు దిగుమతి అభ్యర్థించబడింది", "ପରିଚୟ ଆମଦାନୀ ଅନୁରୋଧ କରାଗଲା", "পরিচয় আমদানির অনুরোধ করা হয়েছে", "Import identity requested")

    val resetIdentity: String
        get() = localized("रीसेट करें", "રીસેટ કરો", "रीसेट करा", "ಮರುಹೊಂದಿಸಿ", "റീസെറ്റ് ചെയ്യുക", "மீட்டமைக்கவும்", "రీసెట్ చేయండి", "ପୁନଃସେଟ୍ କରନ୍ତୁ", "রিসেট করুন", "Reset")

    val identityResetToast: String
        get() = localized("पहचान सफलतापूर्वक रीसेट की गई", "ઓળખ સફળતાપૂર્વક રીસેટ થઈ", "ओळख यशस्वीरित्या रीसेट केली", "ಗುರುತನ್ನು ಯಶಸ್ವಿಯಾಗಿ ಮರುಹೊಂದಿಸಲಾಗಿದೆ", "ഐഡന്റിറ്റി വിജയകരമായി റീസെറ്റ് ചെയ്തു", "அடையாளம் வெற்றிகரமாக மீட்டமைக்கப்பட்டது", "గుర్తింపు విజయవంతంగా రీసెట్ చేయబడింది", "ପରିଚୟ ସଫଳତାର ସହ ପୁନଃସେଟ୍ ହେଲା", "পরিচয় সফলভাবে রিসেট করা হয়েছে", "Identity reset successfully")

    val emergencyDataWipe: String
        get() = localized("आपातकालीन डेटा वाइप", "કટોકટી ડેટા વાઇપ", "तातडीचा डेटा नष्ट करा", "ತುರ್ತು ಡೇಟಾ ಅಳಿಸಿ", "അടിയന്തര ഡാറ്റ വൈപ്പ്", "அவசர தரவு அழிப்பு", "అత్యవసర డేటా తుడిచివేత", "ଜରୁରୀକାଳୀନ ଡାଟା ଲିଭାନ୍ତୁ", "জরুরি ডেটা মুছে ফেলুন", "EMERGENCY DATA WIPE")

    val emergencyWipeToast: String
        get() = localized("सारा डेटा सफलतापूर्वक मिटा दिया गया", "બધો ડેટા સફળતાપૂર્વક ભૂંસી નાખ્યો", "सर्व डेटा यशस्वीरित्या हटवला", "ಎಲ್ಲಾ ಡೇಟಾವನ್ನು ಯಶಸ್ವಿಯಾಗಿ ಅಳಿಸಲಾಗಿದೆ", "എല്ലാ ഡാറ്റയും വിജയകരമായി മായ്‌ച്ചു", "அனைத்து தரவுகளும் வெற்றிகரமாக அழிக்கப்பட்டன", "మొత్తం డేటా విజయవంతంగా తుడిచివేయబడింది", "ସମସ୍ତ ଡାଟା ସଫଳତାର ସହ ଲିଭାଗଲା", "সমস্ত ডেটা সফলভাবে মুছে ফেলা হয়েছে", "All data wiped successfully")

    val noContactsFound: String
        get() = localized("कोई संपर्क नहीं मिला", "કોઈ સંપર્કો મળ્યા નથી", "कोणतेही संपर्क आढळले नाहीत", "ಯಾವುದೇ ಸಂಪರ್ಕಗಳು ಕಂಡುಬಂದಿಲ್ಲ", "ബന്ധങ്ങളൊന്നും കണ്ടെത്തിയില്ല", "தொடர்புகள் எதுவும் இல்லை", "పరిచయాలు కనుగొనబడలేదు", "କୌଣସି ଯୋଗାଯୋଗ ମିଳିଲା ନାହିଁ", "কোনো পরিচিতি পাওয়া যায়নি", "No contacts found")

    val newChat: String
        get() = localized("नया चैट", "નવી ચેટ", "नवीन चॅट", "ಹೊಸ ಚಾಟ್", "പുതിയ ചാറ്റ്", "புதிய அரட்டை", "కొత్త చాట్", "ନୂତନ ଚାଟ୍", "নতুন চ্যাট", "New Chat")

    val noChatsFound: String
        get() = localized("अभी कोई बातचीत नहीं है", "હજુ સુધી કોઈ વાતચીત નથી", "अद्याप कोणतेही संभाषण नाही", "ಇನ್ನೂ ಯಾವುದೇ ಸಂಭಾಷಣೆಗಳಿಲ್ಲ", "ഇതുവരെ സംഭാഷണങ്ങളൊന്നുമില്ല", "இன்னும் உரையாடல்கள் எதுவும் இல்லை", "ఇంకా సంభాషణలు లేవు", "ଏପର୍ଯ୍ୟନ୍ତ କୌଣସି କଥାବାର୍ତ୍ତା ନାହିଁ", "এখনো কোনো কথোপকথন নেই", "No conversations yet")

    val allUiStrings: List<String>
        get() = listOf(
            navDiscovery, navComms, navHistory, navStats, appName, offlineCommsMesh,
            meshActiveReady, meshActive, emergencyBroadcast, initializeSystem,
            enterTacticalMesh, selectPrimaryLanguage, selectLanguageSubtitle,
            continueButton, requiredPermissions, permissionsSubtitle, permMicTitle,
            permMicDesc, permBtTitle, permBtDesc, permWifiTitle, permWifiDesc,
            grantAndStart, scanningMesh, searchingLocalNodes, availableNodes,
            signalStrength, leader, connected, connecting, offline, settingsTitle,
            settingsSubtitle, userProfileTitle, operatorName, enterNamePlaceholder,
            tacticalCallsign, callsignPlaceholder, saveProfile, savedToSystem,
            languageSettingsTitle, appInterfaceLanguage, speechInputLanguage,
            receiverOutputLanguage, receiverTranslationTitle, receiverTranslationDesc,
            pttToggleTitle, pttToggleDesc, statePersisted, statePending,
            compactRepresentation, resetOnboarding, holdToBroadcast, pushToTalk,
            secureMesh, recordingAudio, transmitting, emergencyAlertTitle,
            emergencyAlertDesc, broadcastAlertNow, cancel, transcriptsTitle,
            searchTranscripts, noTranscripts, clearHistory, diagnosticsTitle,
            meshLatency, connectionSettings, devMode, peersActive, contacts,
            meshBroadcast, directPeer, allMesh, replay, sent, received,
            peerCountLabel, assignDeviceNamePrompt, deviceLabel, p2pMeshLink,
            sttOn, sttOff, recordingHoldToTalk, pushToTalkMode, holdToTalk,
            peerTransmittingBusy, walkieTalkieHalfDuplex, holdToTransmit,
            emergencyVoiceBroadcast, emergencyPriorityDescription, holdSosVoice,
            sending, handsFreeContinuousSpeech, transcriptLabel, initializing,
            packetsSent, packetsRelayed, packetsReceived, activeBleLinks,
            broadcastingEmergency, emergencyDispatched,
            youLabel, verifiedIdentity, voiceNoteLabel, idleStatus, listeningStatus,
            speechDetectedStatus, transcribingStatus, thinkingStatus, speakingStatus,
            connectedStatus, disconnectedStatus,
            typeMessage, connectAction, pairingQrCode, exportIdentity, identityExported,
            importIdentity, importIdentityRequested, resetIdentity, identityResetToast,
            emergencyDataWipe, emergencyWipeToast, noContactsFound, newChat, noChatsFound
        )

    companion object {

        val English = AppStrings(
            languageCode = "en",
            navDiscovery = "DISCOVERY",
            navComms = "COMMS",
            navHistory = "HISTORY",
            navStats = "STATS",
            appName = "ITANTRA",
            offlineCommsMesh = "OFFLINE COMMS MESH",
            meshActiveReady = "MESH ACTIVE • READY",
            meshActive = "MESH ACTIVE",
            emergencyBroadcast = "EMERGENCY BROADCAST",
            initializeSystem = "INITIALIZE SYSTEM",
            enterTacticalMesh = "ENTER TACTICAL MESH",
            selectPrimaryLanguage = "Select Primary Language",
            selectLanguageSubtitle = "Choose your native Indian language for offline speech translation.",
            continueButton = "Continue",
            requiredPermissions = "Required Permissions",
            permissionsSubtitle = "iTantra operates 100% offline without internet. Mic and local mesh networking permissions are required.",
            permMicTitle = "Microphone Access",
            permMicDesc = "To record voice audio for real-time speech-to-text transcription.",
            permBtTitle = "Bluetooth Scan & Connect",
            permBtDesc = "To discover nearby peer devices for offline walkie-talkie audio streaming.",
            permWifiTitle = "Wi-Fi Direct Peer Mesh",
            permWifiDesc = "To maintain high-bandwidth peer-to-peer audio channels.",
            grantAndStart = "Grant & Start iTantra",
            scanningMesh = "SCANNING MESH",
            searchingLocalNodes = "Searching for local nodes...",
            availableNodes = "AVAILABLE NODES",
            signalStrength = "Signal Strength",
            leader = "LEADER",
            connected = "CONNECTED",
            connecting = "CONNECTING...",
            offline = "OFFLINE",
            settingsTitle = "SETTINGS",
            settingsSubtitle = "OFFLINE STATE & SPEECH CONFIG",
            userProfileTitle = "USER PROFILE & CALLSIGN",
            operatorName = "Operator Name",
            enterNamePlaceholder = "Enter your name...",
            tacticalCallsign = "Tactical Call Sign / Node Tag",
            callsignPlaceholder = "e.g. ALPHA-7",
            saveProfile = "SAVE PROFILE DETAILS",
            savedToSystem = "SAVED TO SYSTEM",
            languageSettingsTitle = "OFFLINE SPEECH & LANGUAGE ENGINE",
            appInterfaceLanguage = "Application UI Language",
            speechInputLanguage = "Speech Input Language (STT)",
            receiverOutputLanguage = "Receiver Output Language (TTS)",
            receiverTranslationTitle = "RECEIVER-SIDE TRANSLATION",
            receiverTranslationDesc = "Translate messages into receiver output language before TTS playback",
            pttToggleTitle = "PTT TOGGLE MODE",
            pttToggleDesc = "Tap once to start/stop recording instead of holding button",
            statePersisted = "STATE: PERSISTED TO onboarding_state.txt",
            statePending = "STATE: ONBOARDING PENDING",
            compactRepresentation = "Compact Delimiter File Representation:",
            resetOnboarding = "RESET ONBOARDING & DELETE STATE FILE",
            holdToBroadcast = "HOLD TO BROADCAST",
            pushToTalk = "PUSH TO TALK",
            secureMesh = "SECURE MESH",
            recordingAudio = "RECORDING AUDIO...",
            transmitting = "TRANSMITTING...",
            emergencyAlertTitle = "Emergency Broadcast?",
            emergencyAlertDesc = "This will send a non-interruptible, maximum volume audio alert to all connected mesh nodes immediately.",
            broadcastAlertNow = "Broadcast Alert Now",
            cancel = "Cancel",
            transcriptsTitle = "TRANSCRIPTS",
            searchTranscripts = "Search transcripts...",
            noTranscripts = "No transcripts available",
            clearHistory = "CLEAR HISTORY",
            diagnosticsTitle = "DIAGNOSTICS",
            meshLatency = "MESH LATENCY (MS)",
            connectionSettings = "CONNECTION SETTINGS",
            devMode = "DEV MODE"
        )

        val Hindi = AppStrings(
            languageCode = "hi",
            navDiscovery = "खोज",
            navComms = "संचार",
            navHistory = "इतिहास",
            navStats = "आंकड़े",
            appName = "ITANTRA",
            offlineCommsMesh = "ऑफलाइन संचार मेश",
            meshActiveReady = "मेश सक्रिय • तैयार",
            meshActive = "मेश सक्रिय",
            emergencyBroadcast = "आपातकालीन प्रसारण",
            initializeSystem = "सिस्टम प्रारंभ करें",
            enterTacticalMesh = "टैक्टिकल मेश में प्रवेश करें",
            selectPrimaryLanguage = "प्राथमिक भाषा चुनें",
            selectLanguageSubtitle = "ऑफलाइन भाषण अनुवाद के लिए अपनी मूल भारतीय भाषा चुनें।",
            continueButton = "आगे बढ़ें",
            requiredPermissions = "आवश्यक अनुमतियाँ",
            permissionsSubtitle = "iTantra बिना इंटरनेट के 100% ऑफलाइन काम करता है। माइक और मेश नेटवर्किंग अनुमतियाँ आवश्यक हैं।",
            permMicTitle = "माइक्रोफ़ोन अनुमति",
            permMicDesc = "वास्तविक समय भाषण-से-पाठ प्रतिलेखन के लिए ऑडियो रिकॉर्ड करने हेतु।",
            permBtTitle = "ब्लूटूथ स्कैन और कनेक्ट",
            permBtDesc = "ऑफलाइन वॉकी-टॉकी ऑडियो स्ट्रीमिंग के लिए नजदीकी उपकरणों की खोज हेतु।",
            permWifiTitle = "वाई-फाई डायरेक्ट पीयर मेश",
            permWifiDesc = "उच्च गति पीयर-टू-पीयर ऑडियो चैनल बनाए रखने हेतु।",
            grantAndStart = "अनुमति दें और iTantra शुरू करें",
            scanningMesh = "मेश स्कैन हो रहा है",
            searchingLocalNodes = "स्थानीय नोड्स खोज रहे हैं...",
            availableNodes = "उपलब्ध नोड्स",
            signalStrength = "सिग्नल शक्ति",
            leader = "लीडर",
            connected = "कनेक्टेड",
            connecting = "कनेक्ट हो रहा है...",
            offline = "ऑफलाइन",
            settingsTitle = "सेटिंग्स",
            settingsSubtitle = "ऑफलाइन स्थिति और भाषा विन्यास",
            userProfileTitle = "उपयोगकर्ता प्रोफ़ाइल और कॉलसाइन",
            operatorName = "ऑपरेटर का नाम",
            enterNamePlaceholder = "अपना नाम दर्ज करें...",
            tacticalCallsign = "टैक्टिकल कॉल साइन / नोड टैग",
            callsignPlaceholder = "उदा. ALPHA-7",
            saveProfile = "प्रोफ़ाइल विवरण सहेजें",
            savedToSystem = "सिस्टम में सहेजा गया",
            languageSettingsTitle = "ऑफलाइन भाषण और भाषा इंजन",
            appInterfaceLanguage = "एप्लिकेशन इंटरफ़ेस भाषा",
            speechInputLanguage = "भाषण इनपुट भाषा (STT)",
            receiverOutputLanguage = "प्राप्तकर्ता आउटपुट भाषा (TTS)",
            receiverTranslationTitle = "प्राप्तकर्ता-पक्ष अनुवाद",
            receiverTranslationDesc = "टीटीएस प्लेबैक से पहले संदेशों को प्राप्तकर्ता की भाषा में अनुवाद करें",
            pttToggleTitle = "पीटीटी टॉगल मोड",
            pttToggleDesc = "बटन दबाए रखने के बजाय रिकॉर्डिंग शुरू/बंद करने के लिए एक बार टैप करें",
            statePersisted = "स्थिति: onboarding_state.txt में सहेजी गई",
            statePending = "स्थिति: ऑनबोर्डिंग लंबित",
            compactRepresentation = "संक्षिप्त परिसीमित फ़ाइल प्रतिनिधित्व:",
            resetOnboarding = "ऑनबोर्डिंग रीसेट करें और फ़ाइल हटाएं",
            holdToBroadcast = "प्रसारण के लिए दबाकर रखें",
            pushToTalk = "बोलने के लिए दबाएं",
            secureMesh = "सुरक्षित मेश",
            recordingAudio = "ऑडियो रिकॉर्ड हो रहा है...",
            transmitting = "प्रसारित हो रहा है...",
            emergencyAlertTitle = "आपातकालीन प्रसारण?",
            emergencyAlertDesc = "यह सभी जुड़े मेश नोड्स को तुरंत अधिकतम वॉल्यूम वाला ऑडियो अलर्ट भेजेगा।",
            broadcastAlertNow = "अब अलर्ट प्रसारित करें",
            cancel = "रद्द करें",
            transcriptsTitle = "ट्रांसक्रिप्ट्स",
            searchTranscripts = "ट्रांसक्रिप्ट खोजें...",
            noTranscripts = "कोई ट्रांसक्रिप्ट उपलब्ध नहीं है",
            clearHistory = "इतिहास साफ़ करें",
            diagnosticsTitle = "डायग्नोस्टिक्स",
            meshLatency = "मेश विलंबता (MS)",
            connectionSettings = "कनेक्शन सेटिंग्स",
            devMode = "डेव मोड"
        )

        val Marathi = AppStrings(
            languageCode = "mr",
            navDiscovery = "शोध",
            navComms = "संवाद",
            navHistory = "इतिहास",
            navStats = "आकडेवारी",
            appName = "ITANTRA",
            offlineCommsMesh = "ऑफलाइन संवाद मेश",
            meshActiveReady = "मेश सक्रिय • सज्ज",
            meshActive = "मेश सक्रिय",
            emergencyBroadcast = "तातडीचे प्रसारण",
            initializeSystem = "प्रणाली सुरू करा",
            enterTacticalMesh = "मेशमध्ये प्रवेश करा",
            selectPrimaryLanguage = "प्राथमिक भाषा निवडा",
            selectLanguageSubtitle = "ऑफलाइन भाषांतरासाठी तुमची मातृभाषा निवडा.",
            continueButton = "पुढे चला",
            requiredPermissions = "आवश्यक परवानग्या",
            permissionsSubtitle = "iTantra इंटरनेटशिवाय १००% ऑफलाइन कार्य करते. माइक आणि मेश परवानग्या आवश्यक आहेत.",
            permMicTitle = "मायक्रोफोन प्रवेश",
            permMicDesc = "रिअल-टाइम स्पीच-टू-टेक्स्टसाठी ऑडिओ रेकॉर्ड करण्याकरिता.",
            permBtTitle = "ब्लूटूथ स्कॅन आणि कनेक्ट",
            permBtDesc = "ऑफलाइन वॉकी-टॉकीसाठी जवळचे डिव्हाइसेस शोधण्यासाठी.",
            permWifiTitle = "वाय-फाय डायरेक्ट पीअर मेश",
            permWifiDesc = "हाय-स्पीड ऑडिओ चॅनेल राखण्यासाठी.",
            grantAndStart = "परवानगी द्या आणि iTantra सुरू करा",
            scanningMesh = "मेश स्कॅन होत आहे",
            searchingLocalNodes = "स्थानिक नोड्स शोधत आहे...",
            availableNodes = "उपलब्ध नोड्स",
            signalStrength = "सिग्नल सामर्थ्य",
            leader = "लीडर",
            connected = "जोडले गेले",
            connecting = "जोडत आहे...",
            offline = "ऑफलाइन",
            settingsTitle = "सेटिंग्ज",
            settingsSubtitle = "ऑफलाइन स्थिती आणि ऑडिओ संरचना",
            userProfileTitle = "वापरकर्ता प्रोफाइल आणि कॉलसाइन",
            operatorName = "ऑपरेटर नाव",
            enterNamePlaceholder = "तुमचे नाव प्रविष्ट करा...",
            tacticalCallsign = "टॅक्टिकल कॉल साइन / नोड टॅग",
            callsignPlaceholder = "उदा. ALPHA-7",
            saveProfile = "तपशील जतन करा",
            savedToSystem = "जतन केले",
            languageSettingsTitle = "ऑफलाइन भाषा आणि ऑडिओ इंजिन",
            appInterfaceLanguage = "ॲप इंटरफेस भाषा",
            speechInputLanguage = "बोलण्याची भाषा (STT)",
            receiverOutputLanguage = "ऐकण्याची भाषा (TTS)",
            receiverTranslationTitle = "प्राप्तकर्ता बाजूचे भाषांतर",
            receiverTranslationDesc = "टीटीएस ऐकण्यापूर्वी संदेशाचे प्राप्तकर्त्याच्या भाषेत भाषांतर करा",
            pttToggleTitle = "पीटीटी टॉगल मोड",
            pttToggleDesc = "बटण दाबून ठेवण्याऐवजी रेकॉर्डिंगसाठी एक टॅप करा",
            statePersisted = "स्थिती: onboarding_state.txt मध्ये जतन",
            statePending = "स्थिती: ऑनबोर्डिंग प्रलंबित",
            compactRepresentation = "संक्षिप्त फाइल स्वरूप:",
            resetOnboarding = "ऑनबोर्डिंग रीसेट करा आणि फाइल हटवा",
            holdToBroadcast = "बोलण्यासाठी दाबून ठेवा",
            pushToTalk = "बोलण्यासाठी दाबा",
            secureMesh = "सुरक्षित मेश",
            recordingAudio = "ऑडिओ रेकॉर्ड होत आहे...",
            transmitting = "प्रसारित होत आहे...",
            emergencyAlertTitle = "तातडीचे प्रसारण?",
            emergencyAlertDesc = "हे सर्व जोडलेल्या नोड्सना त्वरित उच्च आवाजाचा ऑडिओ इशारा पाठवेल.",
            broadcastAlertNow = "आता इशारा पाठवा",
            cancel = "रद्द करा",
            transcriptsTitle = "नोंदी",
            searchTranscripts = "नोंदी शोधा...",
            noTranscripts = "नोंदी उपलब्ध नाहीत",
            clearHistory = "इतिहास पुसा",
            diagnosticsTitle = "निदान",
            meshLatency = "मेश विलंब (MS)",
            connectionSettings = "कनेक्शन सेटिंग्ज",
            devMode = "डेव्ह मोड"
        )

        val Gujarati = AppStrings(
            languageCode = "gu",
            navDiscovery = "શોધ",
            navComms = "સંચાર",
            navHistory = "ઇતિહાસ",
            navStats = "આંકડા",
            appName = "ITANTRA",
            offlineCommsMesh = "ઑફલાઇન કૉમ્સ મેશ",
            meshActiveReady = "મેશ સક્રિય • તૈયાર",
            meshActive = "મેશ સક્રિય",
            emergencyBroadcast = "કટોકટી પ્રસારણ",
            initializeSystem = "સિસ્ટમ શરૂ કરો",
            enterTacticalMesh = "ટેક્ટિકલ મેશમાં પ્રવેશ કરો",
            selectPrimaryLanguage = "પ્રાથમિક ભાષા પસંદ કરો",
            selectLanguageSubtitle = "ઑફલાઇન ભાષણ અનુવાદ માટે તમારી મૂળ ભારતીય ભાષા પસંદ કરો.",
            continueButton = "આગળ વધો",
            requiredPermissions = "જરૂરી પરવાનગીઓ",
            permissionsSubtitle = "iTantra ઇન્ટરનેટ વિના ૧૦૦% ઑફલાઇન કાર્ય કરે છે. માઇક અને મેશ પરવાનગીઓ જરૂરી છે.",
            permMicTitle = "માઇક્રોફોન ઍક્સેસ",
            permMicDesc = "રીઅલ-ટાઇમ સ્પીચ-ટુ-ટેક્સ્ટ માટે ઑડિઓ રેકોર્ડ કરવા.",
            permBtTitle = "બ્લૂટૂથ સ્કેન અને કનેક્ટ",
            permBtDesc = "ઑફલાઇન વૉકી-ટૉકી માટે નજીકના ઉપકરણો શોધવા.",
            permWifiTitle = "વાઇ-ફાઇ ડાયરેક્ટ પીઅર મેશ",
            permWifiDesc = "હાઇ-સ્પીડ ઑડિઓ ચેનલ્સ જાળવવા.",
            grantAndStart = "મંજૂરી આપો અને iTantra શરૂ કરો",
            scanningMesh = "મેશ સ્કેનિંગ",
            searchingLocalNodes = "સ્થાનિક નોડ્સ શોધી રહ્યાં છીએ...",
            availableNodes = "ઉપલબ્ધ નોડ્સ",
            signalStrength = "સિગ્નલ શક્તિ",
            leader = "લીડર",
            connected = "જોડાયેલ",
            connecting = "જોડાઈ રહ્યું છે...",
            offline = "ઑફલાઇન",
            settingsTitle = "સેટિંગ્સ",
            settingsSubtitle = "ઑફલાઇન સ્થિતિ અને સ્પીચ રૂપરેખાંકન",
            userProfileTitle = "વપરાશકર્તા પ્રોફાઇલ અને કોલસાઇન",
            operatorName = "ઑપરેટર નામ",
            enterNamePlaceholder = "તમારું નામ દાખલ કરો...",
            tacticalCallsign = "ટેક્ટિકલ કૉલ સાઇન / નોડ ટૅગ",
            callsignPlaceholder = "દા.ત. ALPHA-7",
            saveProfile = "વિગતો સાચવો",
            savedToSystem = "સાચવેલ છે",
            languageSettingsTitle = "ઑફલાઇન સ્પીચ અને ભાષા એન્જિન",
            appInterfaceLanguage = "એપ્લિકેશન ઇન્ટરફેસ ભાષા",
            speechInputLanguage = "સ્પીચ ઇનપુટ ભાષા (STT)",
            receiverOutputLanguage = "રીસીવર આઉટપુટ ભાષા (TTS)",
            receiverTranslationTitle = "રીસીવર-સાઇડ અનુવાદ",
            receiverTranslationDesc = "TTS પ્લેબેક પહેલાં સંદેશાનું રીસીવર ભાષામાં અનુવાદ કરો",
            pttToggleTitle = "પીટીટી ટૉગલ મોડ",
            pttToggleDesc = "બટન દબાવી રાખવાને બદલે રેકોર્ડિંગ માટે એક વાર ટૅપ કરો",
            statePersisted = "સ્થિતિ: onboarding_state.txt માં સંગ્રહિત",
            statePending = "સ્થિતિ: ઓનબોર્ડિંગ બાકી",
            compactRepresentation = "સંક્ષિપ્ત ફાઇલ રજૂઆત:",
            resetOnboarding = "ઓનબોર્ડિંગ રીસેટ કરો અને ફાઇલ કાઢી નાખો",
            holdToBroadcast = "પ્રસારણ માટે દબાવી રાખો",
            pushToTalk = "બોલવા માટે દબાવો",
            secureMesh = "સુરક્ષિત મેશ",
            recordingAudio = "ઑડિઓ રેકોર્ડ થઈ રહ્યો છે...",
            transmitting = "પ્રસારિત થઈ રહ્યું છે...",
            emergencyAlertTitle = "કટોકટી પ્રસારણ?",
            emergencyAlertDesc = "આ તરત જ બધા કનેક્ટેડ નોડ્સને મહત્તમ અવાજનો ઑડિઓ એલર્ટ મોકલશે.",
            broadcastAlertNow = "હમણાં એલર્ટ મોકલો",
            cancel = "રદ કરો",
            transcriptsTitle = "ટ્રાન્સક્રિપ્ટ્સ",
            searchTranscripts = "ટ્રાન્સક્રિપ્ટ શોધો...",
            noTranscripts = "કોઈ ટ્રાન્સક્રિપ્ટ નથી",
            clearHistory = "ઇતિહાસ સાફ કરો",
            diagnosticsTitle = "નિદાન",
            meshLatency = "મેશ વિલંબ (MS)",
            connectionSettings = "કનેક્શન સેટિંગ્સ",
            devMode = "ડેવ મોડ"
        )

        val Bengali = AppStrings(
            languageCode = "bn",
            navDiscovery = "অনুসন্ধান",
            navComms = "যোগাযোগ",
            navHistory = "ইতিহাস",
            navStats = "পরিসংখ্যান",
            appName = "ITANTRA",
            offlineCommsMesh = "অফলাইন কমস মেশ",
            meshActiveReady = "মেশ সক্রিয় • প্রস্তুত",
            meshActive = "মেশ সক্রিয়",
            emergencyBroadcast = "জরুরি সম্প্রচার",
            initializeSystem = "সিস্টেম শুরু করুন",
            enterTacticalMesh = "ট্যাকটিক্যাল মেশে প্রবেশ করুন",
            selectPrimaryLanguage = "প্রাথমিক ভাষা নির্বাচন করুন",
            selectLanguageSubtitle = "অফলাইন কথ্য অনুবাদের জন্য আপনার মাতৃভাষা বেছে নিন।",
            continueButton = "এগিয়ে যান",
            requiredPermissions = "প্রয়োজনীয় অনুমতিসমূহ",
            permissionsSubtitle = "iTantra ইন্টারনেট ছাড়াই ১০০% অফলাইনে চলে। মাইক ও মেশ নেটওয়ার্কিং অনুমতি আবশ্যক।",
            permMicTitle = "মাইক্রোফোন অ্যাক্সেস",
            permMicDesc = "রিয়েল-টাইম স্পিচ-টু-টেক্সট ট্রান্সক্রিপশনের জন্য অডিও রেকর্ড করতে।",
            permBtTitle = "ব্লুটুথ স্ক্যান ও কানেক্ট",
            permBtDesc = "অফলাইন ওয়াকি-টকিতে কাছাকাছি ডিভাইস খুঁজে পেতে।",
            permWifiTitle = "ওয়াই-ফাই ডাইরেক্ট পিয়ার মেশ",
            permWifiDesc = "উচ্চ-গতির পিয়ার-টু-পিয়ার অডিও চ্যানেল বজায় রাখতে।",
            grantAndStart = "অনুমতি দিন এবং iTantra শুরু করুন",
            scanningMesh = "মেশ স্ক্যানিং",
            searchingLocalNodes = "স্থানীয় নোড খোঁজা হচ্ছে...",
            availableNodes = "উপলব্ধ নোড",
            signalStrength = "সংকেত শক্তি",
            leader = "লিডার",
            connected = "সংযুক্ত",
            connecting = "সংযোগ করা হচ্ছে...",
            offline = "অফলাইন",
            settingsTitle = "সেটিংস",
            settingsSubtitle = "অফলাইন স্থিতি ও ভাষা কনফিগারেশন",
            userProfileTitle = "ব্যবহারকারী প্রোফাইল ও কলসাইন",
            operatorName = "অপারেটরের নাম",
            enterNamePlaceholder = "আপনার নাম লিখুন...",
            tacticalCallsign = "ট্যাকটিক্যাল কল সাইন / নোড ট্যাগ",
            callsignPlaceholder = "যেমন ALPHA-7",
            saveProfile = "প্রোফাইল সংরক্ষণ করুন",
            savedToSystem = "সংরক্ষিত হয়েছে",
            languageSettingsTitle = "অফলাইন কথ্য ও ভাষা ইঞ্জিন",
            appInterfaceLanguage = "অ্যাপ ইন্টারফেস ভাষা",
            speechInputLanguage = "কথা বলার ভাষা (STT)",
            receiverOutputLanguage = "শোনার ভাষা (TTS)",
            receiverTranslationTitle = "প্রাপকের অনুবাদ",
            receiverTranslationDesc = "টিটিএস প্লেব্যাকের আগে বার্তাগুলি প্রাপকের ভাষায় অনুবাদ করুন",
            pttToggleTitle = "পিটিটি টগল মোড",
            pttToggleDesc = "বোতাম চেপে রাখার পরিবর্তে রেকর্ডিংয়ের জন্য একবার ট্যাপ করুন",
            statePersisted = "স্থিতি: onboarding_state.txt-এ সংরক্ষিত",
            statePending = "স্থিতি: অনবোর্ডিং বাকি আছে",
            compactRepresentation = "সংক্ষিপ্ত ফাইল রূপ:",
            resetOnboarding = "অনবোর্ডিং রিসেট ও ফাইল মুছুন",
            holdToBroadcast = "সম্প্রচারের জন্য চেপে রাখুন",
            pushToTalk = "কথা বলতে চাপুন",
            secureMesh = "সুরক্ষিত মেশ",
            recordingAudio = "অডিও রেকর্ড হচ্ছে...",
            transmitting = "সম্প্রচার করা হচ্ছে...",
            emergencyAlertTitle = "জরুরি সম্প্রচার?",
            emergencyAlertDesc = "এটি সমস্ত সংযুক্ত নোডে অবিলম্বে সর্বোচ্চ ভলিউমের অডিও সতর্কতা পাঠাবে।",
            broadcastAlertNow = "এখনই সতর্কতা সম্প্রচার করুন",
            cancel = "বাতিল",
            transcriptsTitle = "প্রতিলিপি",
            searchTranscripts = "অনুসন্ধান করুন...",
            noTranscripts = "কোন প্রতিলিপি নেই",
            clearHistory = "ইতিহাস মুছুন",
            diagnosticsTitle = "ডায়াগনস্টিকস",
            meshLatency = "মেশ লেটেন্সি (MS)",
            connectionSettings = "সংযোগ সেটিংস",
            devMode = "দেব মোড"
        )

        val Tamil = AppStrings(
            languageCode = "ta",
            navDiscovery = "கண்டறிதல்",
            navComms = "தொடர்புகள்",
            navHistory = "வரலாறு",
            navStats = "புள்ளிவிவரம்",
            appName = "ITANTRA",
            offlineCommsMesh = "ஆஃப்லைன் தொடர்பு மெஷ்",
            meshActiveReady = "மெஷ் செயலில் • தயார்",
            meshActive = "மெஷ் செயலில்",
            emergencyBroadcast = "அவசர ஒலிபரப்பு",
            initializeSystem = "கணினியைத் தொடங்கு",
            enterTacticalMesh = "மெஷில் நுழையவும்",
            selectPrimaryLanguage = "முதன்மை மொழியைத் தேர்ந்தெடுக்கவும்",
            selectLanguageSubtitle = "ஆஃப்லைன் பேச்சு மொழிபெயர்ப்பிற்கு உங்கள் தாய்மொழியைத் தேர்ந்தெடுக்கவும்.",
            continueButton = "தொடரவும்",
            requiredPermissions = "தேவையான அனுமதிகள்",
            permissionsSubtitle = "iTantra இணையம் இல்லாமல் 100% ஆஃப்லைனில் இயங்குகிறது. மைக் மற்றும் மெஷ் அனுமதிகள் தேவை.",
            permMicTitle = "மைக்ரோஃபோன் அணுகல்",
            permMicDesc = "நிகழ்நேர பேச்சு-க்கு-உரை படியெடுத்தலுக்கு ஆடியோ பதிவு செய்ய.",
            permBtTitle = "புளூடூத் ஸ்கேன் & இணைப்பு",
            permBtDesc = "ஆஃப்லைன் வாக்கி-டாக்கிக்காக அருகிலுள்ள சாதனங்களைக் கண்டறிய.",
            permWifiTitle = "வைஃபை டைரக்ட் பியர் மெஷ்",
            permWifiDesc = "அதிவேக ஆடியோ சேனல்களைப் பராமரிக்க.",
            grantAndStart = "அனுமதித்து iTantra-வைத் தொடங்கு",
            scanningMesh = "மெஷ் ஸ்கேனிங்",
            searchingLocalNodes = "முனையங்களைத் தேடுகிறது...",
            availableNodes = "கிடைக்கக்கூடிய முனையங்கள்",
            signalStrength = "சமிக்ஞை வலிமை",
            leader = "தலைவர்",
            connected = "இணைக்கப்பட்டது",
            connecting = "இணைக்கிறது...",
            offline = "ஆஃப்லைன்",
            settingsTitle = "அமைப்புகள்",
            settingsSubtitle = "ஆஃப்லைன் நிலை & மொழி கட்டமைப்பு",
            userProfileTitle = "பயனர் சுயவிவரம் & அழைப்புக்குறி",
            operatorName = "ஆபரேட்டர் பெயர்",
            enterNamePlaceholder = "உங்கள் பெயரை உள்ளிடவும்...",
            tacticalCallsign = "அழைப்புக்குறி / முனையக் குறியீடு",
            callsignPlaceholder = "எ.கா. ALPHA-7",
            saveProfile = "விவரங்களைச் சேமி",
            savedToSystem = "சேமிக்கப்பட்டது",
            languageSettingsTitle = "ஆஃப்லைன் பேச்சு மற்றும் மொழி எஞ்சின்",
            appInterfaceLanguage = "பயன்பாட்டு இடைமுக மொழி",
            speechInputLanguage = "பேச்சு உள்ளீட்டு மொழி (STT)",
            receiverOutputLanguage = "பெறுநர் வெளியீட்டு மொழி (TTS)",
            receiverTranslationTitle = "பெறுநர் பக்க மொழிபெயர்ப்பு",
            receiverTranslationDesc = "ஆடியோ கேட்பதற்கு முன் செய்திகளைப் பெறுநர் மொழியில் மொழிபெயர்க்கவும்",
            pttToggleTitle = "பிடிடி மாற்று முறை",
            pttToggleDesc = "பொத்தானை அழுத்திப் பிடிப்பதற்குப் பதிலாக ஒரு முறை தட்டவும்",
            statePersisted = "நிலை: onboarding_state.txt-இல் சேமிக்கப்பட்டது",
            statePending = "நிலை: நிலுவையில் உள்ளது",
            compactRepresentation = "கோப்பு வடிவம்:",
            resetOnboarding = "தொடக்க நிலையை மீட்டமை & கோப்பை நீக்கு",
            holdToBroadcast = "பேச அழுத்திப் பிடிக்கவும்",
            pushToTalk = "பேச அழுத்தவும்",
            secureMesh = "பாதுகாப்பான மெஷ்",
            recordingAudio = "ஆடியோ பதிவாகிறது...",
            transmitting = "ஒலிபரப்பப்படுகிறது...",
            emergencyAlertTitle = "அவசர ஒலிபரப்பா?",
            emergencyAlertDesc = "இது இணைக்கப்பட்ட அனைத்து முனையங்களுக்கும் உடனடியாக அதிக ஒலி எச்சரிக்கையை அனுப்பும்.",
            broadcastAlertNow = "இப்போதே எச்சரிக்கை செய்",
            cancel = "ரத்து செய்",
            transcriptsTitle = "பதிவுகள்",
            searchTranscripts = "தேடுக...",
            noTranscripts = "பதிவுகள் எதுவும் இல்லை",
            clearHistory = "வரலாற்றை அழி",
            diagnosticsTitle = "பகுப்பாய்வு",
            meshLatency = "மெஷ் தாமதம் (MS)",
            connectionSettings = "இணைப்பு அமைப்புகள்",
            devMode = "டெவ் பயன்முறை"
        )

        val Telugu = AppStrings(
            languageCode = "te",
            navDiscovery = "శోధన",
            navComms = "సంభాషణ",
            navHistory = "చరిత్ర",
            navStats = "గణాంకాలు",
            appName = "ITANTRA",
            offlineCommsMesh = "ఆఫ్‌లైన్ కమ్యూనికేషన్ మెష్",
            meshActiveReady = "మెష్ క్రియాశీలం • సిద్ధంగా ఉంది",
            meshActive = "మెష్ క్రియాశీలం",
            emergencyBroadcast = "అత్యవసర ప్రసారం",
            initializeSystem = "సిస్టమ్ ప్రారంభించండి",
            enterTacticalMesh = "మెష్‌లోకి ప్రవేశించండి",
            selectPrimaryLanguage = "ప్రాథమిక భాషను ఎంచుకోండి",
            selectLanguageSubtitle = "ఆఫ్‌లైన్ ప్రసంగ అనువాదం కోసం మీ మాతృభాషను ఎంచుకోండి.",
            continueButton = "కొనసాగించండి",
            requiredPermissions = "అవసరమైన అనుమతులు",
            permissionsSubtitle = "iTantra ఇంటర్నెట్ లేకుండా 100% ఆఫ్‌లైన్‌లో పనిచేస్తుంది. మైక్ మరియు మెష్ అనుమతులు తప్పనిసరి.",
            permMicTitle = "మైక్రోఫోన్ యాక్సెస్",
            permMicDesc = "రియల్ టైమ్ స్పీచ్-టు-టెక్స్ట్ కోసం ఆడియో రికార్డ్ చేయడానికి.",
            permBtTitle = "బ్లూటూత్ స్కాన్ & కనెక్ట్",
            permBtDesc = "ఆఫ్‌లైన్ వాకీ-టాకీ కోసం సమీప పరికరాలను కనుగొనడానికి.",
            permWifiTitle = "వై-ఫై డైరెక్ట్ పీర్ మెష్",
            permWifiDesc = "హై-స్పీడ్ ఆడియో ఛానెల్‌లను నిర్వహించడానికి.",
            grantAndStart = "అనుమతించి iTantra ప్రారంభించండి",
            scanningMesh = "మెష్ స్కానింగ్",
            searchingLocalNodes = "స్థానిక నోడ్‌ల కోసం శోధిస్తోంది...",
            availableNodes = "అందుబాటులో ఉన్న నోడ్‌లు",
            signalStrength = "సిగ్నల్ బలం",
            leader = "లీడర్",
            connected = "కనెక్ట్ చేయబడింది",
            connecting = "కనెక్ట్ అవుతోంది...",
            offline = "ఆఫ్‌లైన్",
            settingsTitle = "సెట్టింగ్‌లు",
            settingsSubtitle = "ఆఫ్‌లైన్ స్థితి & భాషా కాన్ఫిగరేషన్",
            userProfileTitle = "వినియోగదారు ప్రొఫైల్ & కాల్‌సైన్",
            operatorName = "ఆపరేటర్ పేరు",
            enterNamePlaceholder = "మీ పేరు నమోదు చేయండి...",
            tacticalCallsign = "కాల్ సైన్ / నోడ్ ట్యాగ్",
            callsignPlaceholder = "ఉదా. ALPHA-7",
            saveProfile = "వివరాలను సేవ్ చేయండి",
            savedToSystem = "సేవ్ చేయబడింది",
            languageSettingsTitle = "ఆఫ్‌లైన్ స్పీచ్ & లాంగ్వేజ్ ఇంజిన్",
            appInterfaceLanguage = "యాప్ ఇంటర్‌ఫేస్ భాష",
            speechInputLanguage = "స్పీచ్ ఇన్‌పుట్ భాష (STT)",
            receiverOutputLanguage = "రిసీవర్ అవుట్‌పుట్ భాష (TTS)",
            receiverTranslationTitle = "రిసీవర్ వైపు అనువాదం",
            receiverTranslationDesc = "వినడానికి ముందు సందేశాలను రిసీవర్ భాషలోకి అనువదించండి",
            pttToggleTitle = "పీటీటీ టోగుల్ మోడ్",
            pttToggleDesc = "బటన్ పట్టుకోవడానికి బదులుగా రికార్డింగ్ కోసం ఒకసారి నొక్కండి",
            statePersisted = "స్థితి: onboarding_state.txt లో సేవ్ చేయబడింది",
            statePending = "స్థితి: ఆన్‌బోర్డింగ్ పెండింగ్‌లో ఉంది",
            compactRepresentation = "సంక్షిప్త ఫైల్ ప్రాతినిధ్యం:",
            resetOnboarding = "రీసెట్ చేసి ఫైల్‌ను తొలగించండి",
            holdToBroadcast = "మాట్లాడటానికి నొక్కి పట్టుకోండి",
            pushToTalk = "మాట్లాడటానికి నొక్కండి",
            secureMesh = "సురక్షిత మెష్",
            recordingAudio = "ఆడియో రికార్డ్ అవుతోంది...",
            transmitting = "ప్రసారం అవుతోంది...",
            emergencyAlertTitle = "అత్యవసర ప్రసారమా?",
            emergencyAlertDesc = "ఇది కనెక్ట్ చేయబడిన అన్ని నోడ్‌లకు తక్షణమే గరిష్ట వాల్యూమ్ హెచ్చరికను పంపుతుంది.",
            broadcastAlertNow = "ఇప్పుడే హెచ్చరిక పంపండి",
            cancel = "రద్దు చేయండి",
            transcriptsTitle = "ట్రాన్స్‌క్రిప్ట్‌లు",
            searchTranscripts = "శోధించండి...",
            noTranscripts = "ట్రాన్స్‌క్రిప్ట్‌లు లేవు",
            clearHistory = "చరిత్రను తొలగించండి",
            diagnosticsTitle = "డయాగ్నోస్టిక్స్",
            meshLatency = "మెష్ లేటెన్సీ (MS)",
            connectionSettings = "కనెక్షన్ సెట్టింగ్‌లు",
            devMode = "దేవ్ మోడ్"
        )

        val Kannada = AppStrings(
            languageCode = "kn",
            navDiscovery = "ಹುಡುಕಾಟ",
            navComms = "ಸಂವಹನ",
            navHistory = "ಇತಿಹಾಸ",
            navStats = "ಅಂಕಿಅಂಶ",
            appName = "ITANTRA",
            offlineCommsMesh = "ಆಫ್‌ಲೈನ್ ಸಂವಹನ ಮೆಶ್",
            meshActiveReady = "ಮೆಶ್ ಸಕ್ರಿಯ • ಸಿದ್ಧ",
            meshActive = "ಮೆಶ್ ಸಕ್ರಿಯ",
            emergencyBroadcast = "ತುರ್ತು ಪ್ರಸಾರ",
            initializeSystem = "ವ್ಯವಸ್ಥೆ ಪ್ರಾರಂಭಿಸಿ",
            enterTacticalMesh = "ಮೆಶ್ ಪ್ರವೇಶಿಸಿ",
            selectPrimaryLanguage = "ಪ್ರಾಥಮಿಕ ಭಾಷೆಯನ್ನು ಆಯ್ಕೆಮಾಡಿ",
            selectLanguageSubtitle = "ಆಫ್‌ಲೈನ್ ಧ್ವನಿ ಅನುವಾದಕ್ಕಾಗಿ ನಿಮ್ಮ ಮಾತೃಭಾಷೆಯನ್ನು ಆಯ್ಕೆಮಾಡಿ.",
            continueButton = "ಮುಂದುವರಿಸಿ",
            requiredPermissions = "ಅಗತ್ಯ ಅನುಮತಿಗಳು",
            permissionsSubtitle = "iTantra ಇಂಟರ್ನೆಟ್ ಇಲ್ಲದೆ 100% ಆಫ್‌ಲೈನ್‌ನಲ್ಲಿ ಕಾರ್ಯನಿರ್ವಹಿಸುತ್ತದೆ. ಮೈಕ್ ಮತ್ತು ಮೆಶ್ ಅನುಮತಿಗಳು ಅಗತ್ಯವಿದೆ.",
            permMicTitle = "ಮೈಕ್ರೊಫೋನ್ ಪ್ರವೇಶ",
            permMicDesc = "ನೈಜ ಸಮಯದ ಧ್ವನಿಯಿಂದ ಪಠ್ಯಕ್ಕೆ ಆಡಿಯೊ ರೆಕಾರ್ಡ್ ಮಾಡಲು.",
            permBtTitle = "ಬ್ಲೂಟೂತ್ ಸ್ಕ್ಯಾನ್ ಮತ್ತು ಸಂಪರ್ಕ",
            permBtDesc = "ಆಫ್‌ಲೈನ್ ವಾಕಿ-ಟಾಕಿಗಾಗಿ ಹತ್ತಿರದ ಸಾಧನಗಳನ್ನು ಹುಡುಕಲು.",
            permWifiTitle = "ವೈ-ಫೈ ಡೈರೆಕ್ಟ್ ಪೀರ್ ಮೆಶ್",
            permWifiDesc = "ಹೈ-ಸ್ಪೀಡ್ ಆಡಿಯೊ ಚಾನೆಲ್‌ಗಳನ್ನು ನಿರ್ವಹಿಸಲು.",
            grantAndStart = "ಅನುಮತಿಸಿ ಮತ್ತು iTantra ಪ್ರಾರಂಭಿಸಿ",
            scanningMesh = "ಮೆಶ್ ಸ್ಕ್ಯಾನಿಂಗ್",
            searchingLocalNodes = "ಸ್ಥಳೀಯ ನೋಡ್‌ಗಳನ್ನು ಹುಡುಕಲಾಗುತ್ತಿದೆ...",
            availableNodes = "ಲಭ್ಯವಿರುವ ನೋಡ್‌ಗಳು",
            signalStrength = "ಸಿಗ್ನಲ್ ಸಾಮರ್ಥ್ಯ",
            leader = "ನಾಯಕ",
            connected = "ಸಂಪರ್ಕಿಸಲಾಗಿದೆ",
            connecting = "ಸಂಪರ್ಕಿಸಲಾಗುತ್ತಿದೆ...",
            offline = "ಆಫ್‌ಲೈನ್",
            settingsTitle = "ಸೆಟ್ಟಿಂಗ್‌ಗಳು",
            settingsSubtitle = "ಆಫ್‌ಲೈನ್ ಸ್ಥಿತಿ ಮತ್ತು ಭಾಷಾ ಸಂರಚನೆ",
            userProfileTitle = "ಬಳಕೆದಾರರ ಪ್ರೊಫೈಲ್ ಮತ್ತು ಕರೆಚಿಹ್ನೆ",
            operatorName = "ಆಪರೇಟರ್ ಹೆಸರು",
            enterNamePlaceholder = "ನಿಮ್ಮ ಹೆಸರನ್ನು ನಮೂದಿಸಿ...",
            tacticalCallsign = "ಕರೆ ಚಿಹ್ನೆ / ನೋಡ್ ಟ್ಯಾಗ್",
            callsignPlaceholder = "ಉದಾ. ALPHA-7",
            saveProfile = "ವಿವರಗಳನ್ನು ಉಳಿಸಿ",
            savedToSystem = "ಉಳಿಸಲಾಗಿದೆ",
            languageSettingsTitle = "ಆಫ್‌ಲೈನ್ ಧ್ವನಿ ಮತ್ತು ಭಾಷಾ ಎಂಜಿನ್",
            appInterfaceLanguage = "ಅಪ್ಲಿಕೇಶನ್ ಇಂಟರ್ಫೇಸ್ ಭಾಷೆ",
            speechInputLanguage = "ಧ್ವನಿ ಇನ್‌ಪುಟ್ ಭಾಷೆ (STT)",
            receiverOutputLanguage = "ಸ್ವೀಕರಿಸುವವರ ಔಟ್‌ಪುಟ್ ಭಾಷೆ (TTS)",
            receiverTranslationTitle = "ಸ್ವೀಕರಿಸುವವರ ಕಡೆಯ ಅನುವಾದ",
            receiverTranslationDesc = "ಆಡಿಯೊ ಕೇಳುವ ಮೊದಲು ಸಂದೇಶಗಳನ್ನು ಸ್ವೀಕರಿಸುವವರ ಭಾಷೆಗೆ ಅನುವಾದಿಸಿ",
            pttToggleTitle = "ಪಿಟಿಟಿ ಟಾಗಲ್ ಮೋಡ್",
            pttToggleDesc = "ಗುಂಡಿಯನ್ನು ಒತ್ತಿಹಿಡಿಯುವ ಬದಲು ರೆಕಾರ್ಡಿಂಗ್‌ಗಾಗಿ ಒಮ್ಮೆ ಟ್ಯಾಪ್ ಮಾಡಿ",
            statePersisted = "ಸ್ಥಿತಿ: onboarding_state.txt ನಲ್ಲಿ ಉಳಿಸಲಾಗಿದೆ",
            statePending = "ಸ್ಥಿತಿ: ಆನ್‌ಬೋರ್ಡಿಂಗ್ ಬಾಕಿ ಉಳಿದಿದೆ",
            compactRepresentation = "ಸಂಕ್ಷಿಪ್ತ ಕಡತ ರೂಪ:",
            resetOnboarding = "ಮರುಹೊಂದಿಸಿ ಮತ್ತು ಕಡತವನ್ನು ಅಳಿಸಿ",
            holdToBroadcast = "ಮಾತನಾಡಲು ಒತ್ತಿಹಿಡಿಯಿರಿ",
            pushToTalk = "ಮಾತನಾಡಲು ಒತ್ತಿರಿ",
            secureMesh = "ಸುರಕ್ಷಿತ ಮೆಶ್",
            recordingAudio = "ಆಡಿಯೊ ರೆಕಾರ್ಡ್ ಆಗುತ್ತಿದೆ...",
            transmitting = "ಪ್ರಸಾರವಾಗುತ್ತಿದೆ...",
            emergencyAlertTitle = "ತುರ್ತು ಪ್ರಸಾರವೇ?",
            emergencyAlertDesc = "ಇದು ಎಲ್ಲಾ ಸಂಪರ್ಕಿತ ನೋಡ್‌ಗಳಿಗೆ ತಕ್ಷಣವೇ ಹೆಚ್ಚಿನ ವಾಲ್ಯೂಮ್ ಎಚ್ಚರಿಕೆಯನ್ನು ಕಳುಹಿಸುತ್ತದೆ.",
            broadcastAlertNow = "ಈಗಲೇ ಎಚ್ಚರಿಕೆ ಕಳುಹಿಸಿ",
            cancel = "ರದ್ದುಮಾಡಿ",
            transcriptsTitle = "ದಾಖಲೆಗಳು",
            searchTranscripts = "ಹುಡುಕಿ...",
            noTranscripts = "ಯಾವುದೇ ದಾಖಲೆಗಳಿಲ್ಲ",
            clearHistory = "ಇತಿಹಾಸ ಅಳಿಸಿ",
            diagnosticsTitle = "ವಿಶ್ಲೇಷಣೆ",
            meshLatency = "ಮೆಶ್ ವಿಳಂಬ (MS)",
            connectionSettings = "ಸಂಪರ್ಕ ಸೆಟ್ಟಿಂಗ್‌ಗಳು",
            devMode = "ಡೆವ್ ಮೋಡ್"
        )

        val Malayalam = AppStrings(
            languageCode = "ml",
            navDiscovery = "കണ്ടെത്തൽ",
            navComms = "ആശയവിനിമയം",
            navHistory = "ചരിത്രം",
            navStats = "സ്ഥിതിവിവരങ്ങൾ",
            appName = "ITANTRA",
            offlineCommsMesh = "ഓഫ്‌ലൈൻ ആശയവിനിമയ മെഷ്",
            meshActiveReady = "മെഷ് സജീവം • തയ്യാർ",
            meshActive = "മെഷ് സജീവം",
            emergencyBroadcast = "അടിയന്തര പ്രക്ഷേപണം",
            initializeSystem = "സിസ്റ്റം ആരംഭിക്കുക",
            enterTacticalMesh = "മെഷിലേക്ക് പ്രവേശിക്കുക",
            selectPrimaryLanguage = "പ്രാഥമിക ഭാഷ തിരഞ്ഞെടുക്കുക",
            selectLanguageSubtitle = "ഓഫ്‌ലൈൻ സംഭാഷണ വിവർത്തനത്തിനായി നിങ്ങളുടെ മാതൃഭാഷ തിരഞ്ഞെടുക്കുക.",
            continueButton = "തുടരുക",
            requiredPermissions = "ആവശ്യമായ അനുമതികൾ",
            permissionsSubtitle = "iTantra ഇന്റർനെറ്റ് ഇല്ലാതെ 100% ഓഫ്‌ലൈനായി പ്രവർത്തിക്കുന്നു. മൈക്ക്, മെഷ് അനുമതികൾ ആവശ്യമാണ്.",
            permMicTitle = "മൈക്രോഫോൺ അനുമതി",
            permMicDesc = "തത്സമയ സംഭാഷണ-വാചക പരിവർത്തനത്തിനായി ഓഡിയോ റെക്കോർഡ് ചെയ്യാൻ.",
            permBtTitle = "ബ്ലൂടൂത്ത് സ്കാൻ & കണക്റ്റ്",
            permBtDesc = "ഓഫ്‌ലൈൻ വാക്കി-ടോക്കിക്കായി അടുത്തുള്ള ഉപകരണങ്ങൾ കണ്ടെത്താൻ.",
            permWifiTitle = "വൈ-ഫൈ ഡയറക്ട് പിയർ മെഷ്",
            permWifiDesc = "ഹൈ-സ്പീഡ് ഓഡിയോ ചാനലുകൾ നിലനിർത്താൻ.",
            grantAndStart = "അനുമതി നൽകി iTantra ആരംഭിക്കുക",
            scanningMesh = "മെഷ് സ്കാനിംഗ്",
            searchingLocalNodes = "ലോക്കൽ നോഡുകൾക്കായി തിരയുന്നു...",
            availableNodes = "ലഭ്യമായ നോഡുകൾ",
            signalStrength = "സിഗ്നൽ കരുത്ത്",
            leader = "ലീഡർ",
            connected = "കണക്റ്റഡ്",
            connecting = "കണക്റ്റുചെയ്യുന്നു...",
            offline = "ഓഫ്‌ലൈൻ",
            settingsTitle = "ക്രമീകരണങ്ങൾ",
            settingsSubtitle = "ഓഫ്‌ലൈൻ അവസ്ഥയും ഭാഷാ ക്രമീകരണവും",
            userProfileTitle = "ഉപയോക്തൃ പ്രൊഫൈലും കോൾസൈനും",
            operatorName = "ഓപ്പറേറ്ററുടെ പേര്",
            enterNamePlaceholder = "നിങ്ങളുടെ പേര് നൽകുക...",
            tacticalCallsign = "കോൾ സൈൻ / നോഡ് ടാഗ്",
            callsignPlaceholder = "ഉദാ. ALPHA-7",
            saveProfile = "വിവരങ്ങൾ സംരക്ഷിക്കുക",
            savedToSystem = "സംരക്ഷിച്ചു",
            languageSettingsTitle = "ഓഫ്‌ലൈൻ സംഭാഷണ ഭാഷാ എഞ്ചിൻ",
            appInterfaceLanguage = "ആപ്പ് ഇന്റർഫേസ് ഭാഷ",
            speechInputLanguage = "സംഭാഷണ ഇൻപുട്ട് ഭാഷ (STT)",
            receiverOutputLanguage = "സ്വീകർത്താവിന്റെ ഔട്ട്പുട്ട് ഭാഷ (TTS)",
            receiverTranslationTitle = "സ്വീകർത്താവിന്റെ ഭാഗത്തെ വിവർത്തനം",
            receiverTranslationDesc = "ഓഡിയോ കേൾക്കുന്നതിന് മുൻപ് സന്ദേശങ്ങൾ സ്വീകർത്താവിന്റെ ഭാഷയിലേക്ക് വിവർത്തനം ചെയ്യുക",
            pttToggleTitle = "പിടിടി ടോഗിൾ മോഡ്",
            pttToggleDesc = "ബട്ടൺ അമർത്തിപ്പിടിക്കുന്നതിന് പകരം റെക്കോർഡിംഗിനായി ഒരു തവണ ടാപ്പ് ചെയ്യുക",
            statePersisted = "അവസ്ഥ: onboarding_state.txt-ൽ സംരക്ഷിച്ചു",
            statePending = "അവസ്ഥ: പൂർത്തിയായിട്ടില്ല",
            compactRepresentation = "ഫയൽ വിവരണം:",
            resetOnboarding = "റീസെറ്റ് ചെയ്യുക & ഫയൽ ഇല്ലാതാക്കുക",
            holdToBroadcast = "സംസാരിക്കാൻ അമർത്തിപ്പിടിക്കുക",
            pushToTalk = "സംസാരിക്കാൻ അമർത്തുക",
            secureMesh = "സുരക്ഷിത മെഷ്",
            recordingAudio = "ഓഡിയോ റെക്കോർഡ് ചെയ്യുന്നു...",
            transmitting = "പ്രക്ഷേപണം ചെയ്യുന്നു...",
            emergencyAlertTitle = "അടിയന്തര പ്രക്ഷേപണമാണോ?",
            emergencyAlertDesc = "ഇത് ബന്ധിപ്പിച്ചിട്ടുള്ള എല്ലാ നോഡുകളിലേക്കും ഉടനടി ഉയർന്ന ശബ്ദത്തിലുള്ള മുന്നറിയിപ്പ് അയയ്ക്കും.",
            broadcastAlertNow = "ഉടൻ മുന്നറിയിപ്പ് അയയ്ക്കുക",
            cancel = "റദ്ദാക്കുക",
            transcriptsTitle = "രേഖകൾ",
            searchTranscripts = "തിരയുക...",
            noTranscripts = "രേഖകൾ ലഭ്യമല്ല",
            clearHistory = "ചരിത്രം മായ്‌ക്കുക",
            diagnosticsTitle = "ഡയഗ്നോസ്റ്റിക്സ്",
            meshLatency = "മെഷ് ലേറ്റൻസി (MS)",
            connectionSettings = "കണക്ഷൻ ക്രമീകരണങ്ങൾ",
            devMode = "ദേവ് മോഡ്"
        )

        val Odia = AppStrings(
            languageCode = "or",
            navDiscovery = "ସନ୍ଧାନ",
            navComms = "ସଂଚାର",
            navHistory = "ଇତିହାସ",
            navStats = "ପରିସଂଖ୍ୟାନ",
            appName = "ITANTRA",
            offlineCommsMesh = "ଅଫଲାଇନ୍ ସଂଚାର ମେଶ୍",
            meshActiveReady = "ମେଶ୍ ସକ୍ରିୟ • ପ୍ରସ୍ତୁତ",
            meshActive = "ମେଶ୍ ସକ୍ରିୟ",
            emergencyBroadcast = "ଜରୁରୀକାଳୀନ ପ୍ରସାରଣ",
            initializeSystem = "ସିଷ୍ଟମ ଆରମ୍ଭ କରନ୍ତୁ",
            enterTacticalMesh = "ମେଶ୍‌ରେ ପ୍ରବେଶ କରନ୍ତୁ",
            selectPrimaryLanguage = "ପ୍ରାଥମିକ ଭାଷା ଚୟନ କରନ୍ତୁ",
            selectLanguageSubtitle = "ଅଫଲାଇନ୍ ଭାଷଣ ଅନୁବାଦ ପାଇଁ ନିଜର ମାତୃଭାଷା ବାଛନ୍ତୁ।",
            continueButton = "ଆଗକୁ ବଢ଼ନ୍ତୁ",
            requiredPermissions = "ଆବଶ୍ୟକ ଅନୁମତି",
            permissionsSubtitle = "iTantra ଇଣ୍ଟରନେଟ୍ ବିନା ୧୦୦% ଅଫଲାଇନ୍ କାମ କରେ। ମାଇକ୍ ଏବଂ ମେଶ୍ ଅନୁମତି ଆବଶ୍ୟକ।",
            permMicTitle = "ମାଇକ୍ରୋଫୋନ୍ ଅନୁମତି",
            permMicDesc = "ରିଅଲ୍-ଟାଇମ୍ ସ୍ପିଚ୍-ଟୁ-ଟେକ୍ସଟ୍ ପାଇଁ ଅଡିଓ ରେକର୍ଡ କରିବାକୁ।",
            permBtTitle = "ବ୍ଲୁଟୁଥ୍ ସ୍କାନ୍ ଏବଂ ସଂଯୋଗ",
            permBtDesc = "ଅଫଲାଇନ୍ ୱାକି-ଟକି ପାଇଁ ନିକଟସ୍ଥ ଉପକରଣ ଖୋଜିବାକୁ।",
            permWifiTitle = "ୱାଇ-ଫାଇ ଡାଇରେକ୍ଟ ପିଅର୍ ମେଶ୍",
            permWifiDesc = "ହାଇ-ସ୍ପିଡ୍ ଅଡିଓ ଚ୍ୟାନେଲ୍ ବଜାୟ ରଖିବାକୁ।",
            grantAndStart = "ଅନୁମତି ଦିଅନ୍ତୁ ଏବଂ iTantra ଆରମ୍ଭ କରନ୍ତୁ",
            scanningMesh = "ମେଶ୍ ସ୍କାନିଂ",
            searchingLocalNodes = "ସ୍ଥାନୀୟ ନୋଡ୍ ଖୋଜା ଚାଲିଛି...",
            availableNodes = "ଉପଲବ୍ଧ ନୋଡ୍",
            signalStrength = "ସିଗ୍ନାଲ୍ ଶକ୍ତି",
            leader = "ନେତା",
            connected = "ସଂଯୁକ୍ତ",
            connecting = "ସଂଯୋଗ ହେଉଛି...",
            offline = "ଅଫଲାଇନ୍",
            settingsTitle = "ସେଟିଙ୍ଗ୍ସ",
            settingsSubtitle = "ଅଫଲାଇନ୍ ସ୍ଥିତି ଏବଂ ଭାଷା ବିନ୍ୟାସ",
            userProfileTitle = "ବ୍ୟବହାରକାରୀ ପ୍ରୋଫାଇଲ୍ ଏବଂ କଲ୍‌ସାଇନ୍",
            operatorName = "ଅପରେଟର୍ ନାମ",
            enterNamePlaceholder = "ଆପଣଙ୍କ ନାମ ଲେଖନ୍ତୁ...",
            tacticalCallsign = "କଲ୍ ସାଇନ୍ / ନୋଡ୍ ଟ୍ୟାଗ୍",
            callsignPlaceholder = "ଯଥା ALPHA-7",
            saveProfile = "ବିବରଣୀ ସଂରକ୍ଷଣ କରନ୍ତୁ",
            savedToSystem = "ସଂରକ୍ଷିତ ହେଲା",
            languageSettingsTitle = "ଅଫଲାଇନ୍ ଭାଷଣ ଓ ଭାଷା ଇଞ୍ଜିନ୍",
            appInterfaceLanguage = "ଆପ୍ ଇଣ୍ଟରଫେସ୍ ଭାଷା",
            speechInputLanguage = "ସ୍ପିଚ୍ ଇନପୁଟ୍ ଭାଷା (STT)",
            receiverOutputLanguage = "ଗ୍ରହଣକାରୀ ଆଉଟପୁଟ୍ ଭାଷା (TTS)",
            receiverTranslationTitle = "ଗ୍ରହଣକାରୀ ପାଖ ଅନୁବାଦ",
            receiverTranslationDesc = "ଅଡିଓ ଶୁଣିବା ପୂର୍ବରୁ ବାର୍ତ୍ତାକୁ ଗ୍ରହଣକାରୀଙ୍କ ଭାଷାରେ ଅନୁବାଦ କରନ୍ତୁ",
            pttToggleTitle = "ପିଟିଟି ଟଗଲ୍ ମୋଡ୍",
            pttToggleDesc = "ବଟନ୍ ଧରି ରଖିବା ପରିବର୍ତ୍ତେ ରେକର୍ଡିଂ ପାଇଁ ଥରେ ଟ୍ୟାପ୍ କରନ୍ତୁ",
            statePersisted = "ସ୍ଥିତି: onboarding_state.txt ରେ ସଂରକ୍ଷିତ",
            statePending = "ସ୍ଥିତି: ଅନ୍‌ବୋର୍ଡିଂ ବାକି ଅଛି",
            compactRepresentation = "ସଂକ୍ଷିପ୍ତ ଫାଇଲ୍ ରୂପ:",
            resetOnboarding = "ପୁନଃସେଟ୍ କରନ୍ତୁ ଏବଂ ଫାଇଲ୍ ଡିଲିଟ୍ କରନ୍ତୁ",
            holdToBroadcast = "ପ୍ରସାରଣ ପାଇଁ ଧରି ରଖନ୍ତୁ",
            pushToTalk = "କହିବାକୁ ଦବାନ୍ତୁ",
            secureMesh = "ସୁରକ୍ଷିତ ମେଶ୍",
            recordingAudio = "ଅଡିଓ ରେକର୍ଡ ହେଉଛି...",
            transmitting = "ପ୍ରସାରିତ ହେଉଛି...",
            emergencyAlertTitle = "ଜରୁରୀକାଳୀନ ପ୍ରସାରଣ?",
            emergencyAlertDesc = "ଏହା ସମସ୍ତ ସଂଯୁକ୍ତ ନୋଡ୍‌କୁ ତୁରନ୍ତ ଉଚ୍ଚ ଧ୍ୱନି ବିଶିଷ୍ଟ ଅଡିଓ ସତର୍କତା ପଠାଇବ।",
            broadcastAlertNow = "ଏବେ ସତର୍କତା ପ୍ରସାରଣ କରନ୍ତୁ",
            cancel = "ବାତିଲ୍",
            transcriptsTitle = "ନକଲ",
            searchTranscripts = "ଖୋଜନ୍ତୁ...",
            noTranscripts = "କୌଣସି ନକଲ ଉପଲବ୍ଧ ନାହିଁ",
            clearHistory = "ଇତିହାସ ସଫା କରନ୍ତୁ",
            diagnosticsTitle = "ଡାଇଗ୍ନୋଷ୍ଟିକ୍ସ",
            meshLatency = "ମେଶ୍ ବିଳମ୍ବ (MS)",
            connectionSettings = "ସଂଯୋଗ ସେଟିଙ୍ଗ୍ସ",
            devMode = "ଡେଭ୍ ମୋଡ୍"
        )

        val Punjabi = AppStrings(
            languageCode = "pa",
            navDiscovery = "ਖੋਜ",
            navComms = "ਸੰਚਾਰ",
            navHistory = "ਇਤਿਹਾਸ",
            navStats = "ਅੰਕੜੇ",
            appName = "ITANTRA",
            offlineCommsMesh = "ਆਫਲਾਈਨ ਸੰਚਾਰ ਮੈਸ਼",
            meshActiveReady = "ਮੈਸ਼ ਸਰਗਰਮ • ਤਿਆਰ",
            meshActive = "ਮੈਸ਼ ਸਰਗਰਮ",
            emergencyBroadcast = "ਐਮਰਜੈਂਸੀ ਪ੍ਰਸਾਰਣ",
            initializeSystem = "ਸਿਸਟਮ ਸ਼ੁਰੂ ਕਰੋ",
            enterTacticalMesh = "ਮੈਸ਼ ਵਿੱਚ ਦਾਖਲ ਹੋਵੋ",
            selectPrimaryLanguage = "ਮੁੱਢਲੀ ਭਾਸ਼ਾ ਚੁਣੋ",
            selectLanguageSubtitle = "ਆਫਲਾਈਨ ਅਨੁਵਾਦ ਲਈ ਆਪਣੀ ਮਾਤ ਭਾਸ਼ਾ ਚੁਣੋ।",
            continueButton = "ਜਾਰੀ ਰੱਖੋ",
            requiredPermissions = "ਲੋੜੀਂਦੀਆਂ ਇਜਾਜ਼ਤਾਂ",
            permissionsSubtitle = "iTantra ਇੰਟਰਨੈਟ ਤੋਂ ਬਿਨਾਂ 100% ਆਫਲਾਈਨ ਕੰਮ ਕਰਦਾ ਹੈ। ਮਾਈਕ ਅਤੇ ਮੈਸ਼ ਇਜਾਜ਼ਤਾਂ ਲਾਜ਼ਮੀ ਹਨ।",
            permMicTitle = "ਮਾਈਕ੍ਰੋਫੋਨ ਪਹੁੰਚ",
            permMicDesc = "ਰੀਅਲ-ਟਾਈਮ ਸਪੀਚ-ਟੂ-ਟੈਕਸਟ ਲਈ ਆਡੀਓ ਰਿਕਾਰਡ ਕਰਨ ਵਾਸਤੇ।",
            permBtTitle = "ਬਲੂਟੁੱਥ ਸਕੈਨ ਅਤੇ ਕਨੈਕਟ",
            permBtDesc = "ਆਫਲਾਈਨ ਵਾਕੀ-ਟਾਕੀ ਲਈ ਨੇੜਲੀਆਂ ਡਿਵਾਈਸਾਂ ਲੱਭਣ ਵਾਸਤੇ।",
            permWifiTitle = "ਵਾਈ-ਫਾਈ ਡਾਇਰੈਕਟ ਪੀਅਰ ਮੈਸ਼",
            permWifiDesc = "ਹਾਈ-ਸਪੀਡ ਆਡੀਓ ਚੈਨਲਾਂ ਨੂੰ ਬਣਾਈ ਰੱਖਣ ਵਾਸਤੇ।",
            grantAndStart = "ਇਜਾਜ਼ਤ ਦਿਓ ਅਤੇ iTantra ਸ਼ੁਰੂ ਕਰੋ",
            scanningMesh = "ਮੈਸ਼ ਸਕੈਨਿੰਗ",
            searchingLocalNodes = "ਸਥਾਨਕ ਨੋਡਾਂ ਦੀ ਖੋਜ ਜਾਰੀ ਹੈ...",
            availableNodes = "ਉਪਲਬਧ ਨੋਡਾਂ",
            signalStrength = "ਸਿਗਨਲ ਤਾਕਤ",
            leader = "ਲੀਡਰ",
            connected = "ਕਨੈਕਟ ਕੀਤਾ",
            connecting = "ਕਨੈਕਟ ਹੋ ਰਿਹਾ ਹੈ...",
            offline = "ਆਫਲਾਈਨ",
            settingsTitle = "ਸੈਟਿੰਗਾਂ",
            settingsSubtitle = "ਆਫਲਾਈਨ ਸਥਿਤੀ ਅਤੇ ਭਾਸ਼ਾ ਸੰਰਚਨਾ",
            userProfileTitle = "ਉਪਭੋਗਤਾ ਪ੍ਰੋਫਾਈਲ ਅਤੇ ਕਾਲਸਾਈਨ",
            operatorName = "ਓਪਰੇਟਰ ਦਾ ਨਾਮ",
            enterNamePlaceholder = "ਆਪਣਾ ਨਾਮ ਦਰਜ ਕਰੋ...",
            tacticalCallsign = "ਕਾਲ ਸਾਈਨ / ਨੋਡ ਟੈਗ",
            callsignPlaceholder = "ਜਿਵੇਂ ALPHA-7",
            saveProfile = "ਵੇਰਵੇ ਸੰਭਾਲੋ",
            savedToSystem = "ਸੰਭਾਲਿਆ ਗਿਆ",
            languageSettingsTitle = "ਆਫਲਾਈਨ ਭਾਸ਼ਾ ਅਤੇ ਆਡੀਓ ਇੰਜਨ",
            appInterfaceLanguage = "ਐਪ ਇੰਟਰਫੇਸ ਭਾਸ਼ਾ",
            speechInputLanguage = "ਸਪੀਚ ਇਨਪੁੱਟ ਭਾਸ਼ਾ (STT)",
            receiverOutputLanguage = "ਸੁਣਨ ਵਾਲੀ ਭਾਸ਼ਾ (TTS)",
            receiverTranslationTitle = "ਰਿਸੀਵਰ ਵਾਲੇ ਪਾਸੇ ਅਨੁਵਾਦ",
            receiverTranslationDesc = "ਸੁਣਨ ਤੋਂ ਪਹਿਲਾਂ ਸੁਨੇਹਿਆਂ ਦਾ ਰਿਸੀਵਰ ਦੀ ਭਾਸ਼ਾ ਵਿੱਚ ਅਨੁਵਾਦ ਕਰੋ",
            pttToggleTitle = "ਪੀਟੀਟੀ ਟੌਗਲ ਮੋਡ",
            pttToggleDesc = "ਬਟਨ ਦਬਾ ਕੇ ਰੱਖਣ ਦੀ ਬਜਾਏ ਰਿਕਾਰਡਿੰਗ ਲਈ ਇੱਕ ਵਾਰ ਟੈਪ ਕਰੋ",
            statePersisted = "ਸਥਿਤੀ: onboarding_state.txt ਵਿੱਚ ਸੰਭਾਲੀ ਗਈ",
            statePending = "ਸਥਿਤੀ: ਆਨਬੋਰਡਿੰਗ ਬਾਕੀ ਹੈ",
            compactRepresentation = "ਸੰਖੇਪ ਫਾਈਲ ਰੂਪ:",
            resetOnboarding = "ਮੁੜ ਸੈੱਟ ਕਰੋ ਅਤੇ ਫਾਈਲ ਮਿਟਾਓ",
            holdToBroadcast = "ਬੋਲਣ ਲਈ ਦਬਾ ਕੇ ਰੱਖੋ",
            pushToTalk = "ਬੋਲਣ ਲਈ ਦਬਾਓ",
            secureMesh = "ਸੁਰੱਖਿਅਤ ਮੈਸ਼",
            recordingAudio = "ਆਡੀਓ ਰਿਕਾਰਡ ਹੋ ਰਿਹਾ ਹੈ...",
            transmitting = "ਪ੍ਰਸਾਰਿਤ ਕੀਤਾ ਜਾ ਰਿਹਾ ਹੈ...",
            emergencyAlertTitle = "ਐਮਰਜੈਂਸੀ ਅਲਰਟ?",
            emergencyAlertDesc = "ਇਹ ਸਾਰੀਆਂ ਜੁੜੀਆਂ ਨੋਡਾਂ ਨੂੰ ਤੁਰੰਤ ਵੱਧ ਤੋਂ ਵੱਧ ਆਵਾਜ਼ ਵਾਲਾ ਆਡੀਓ ਅਲਰਟ ਭੇਜੇਗਾ।",
            broadcastAlertNow = "ਹੁਣੇ ਅਲਰਟ ਭੇਜੋ",
            cancel = "ਰੱਦ ਕਰੋ",
            transcriptsTitle = "ਨਕਲਾਂ",
            searchTranscripts = "ਖੋਜ ਕਰੋ...",
            noTranscripts = "ਕੋਈ ਨਕਲ ਉਪਲਬਧ ਨਹੀਂ",
            clearHistory = "ਇਤਿਹਾਸ ਸਾਫ਼ ਕਰੋ",
            diagnosticsTitle = "ਡਾਇਗਨੋਸਟਿਕਸ",
            meshLatency = "ਮੈਸ਼ ਦੇਰੀ (MS)",
            connectionSettings = "ਕਨੈਕਸ਼ਨ ਸੈਟਿੰਗਾਂ",
            devMode = "ਦੇਵ ਮੋਡ"
        )

        private val languageStringsMap: Map<String, AppStrings> = mapOf(
            "en" to English,
            "hi" to Hindi,
            "mr" to Marathi,
            "gu" to Gujarati,
            "bn" to Bengali,
            "ta" to Tamil,
            "te" to Telugu,
            "kn" to Kannada,
            "ml" to Malayalam,
            "or" to Odia
        )

        /**
         * Resolves the [AppStrings] instance corresponding to the given language code.
         * Supported UI language codes must have a dedicated translation pack.
         * Unknown codes (including Punjabi) are not part of the UI selector.
         */
        fun forLanguage(code: String?): AppStrings {
            if (code.isNullOrBlank()) return English
            val normalized = code.lowercase().trim()
            val isSupportedUiLanguage = AppLanguageState.supportedLanguages.any {
                it.code.equals(normalized, ignoreCase = true)
            }
            if (isSupportedUiLanguage) {
                return languageStringsMap[normalized]
                    ?: error("Missing AppStrings translations for UI language code: $normalized")
            }
            return English
        }

        /**
         * Updates the runtime system locale and configuration of the application.
         */
        fun updateSystemLocale(context: Context, langCode: String) {
            try {
                val locale = Locale(langCode)
                Locale.setDefault(locale)
                val resources = context.resources
                val configuration = resources.configuration
                configuration.setLocale(locale)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    configuration.setLocales(LocaleList(locale))
                }
                resources.updateConfiguration(configuration, resources.displayMetrics)
            } catch (e: Exception) {
                // Non-critical fallback if device security forbids runtime configuration update
            }
        }
    }
}

/**
 * CompositionLocal providing active [AppStrings] to the Compose hierarchy.
 */
val LocalAppStrings = compositionLocalOf(neverEqualPolicy<AppStrings>()) { AppStrings.English }

/**
 * Convenience accessor for the current [AppStrings] in Compose.
 */
@Composable
fun appStrings(): AppStrings = LocalAppStrings.current
