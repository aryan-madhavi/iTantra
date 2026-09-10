package com.astramesh.core

/**
 * Canonical 10 languages supported by iTantra (SIH Problem Statement #26173)
 * across STT, TTS, Translation, Messaging Envelope, UI and Persistence.
 */
enum class Language(
    val code: String,
    val nativeName: String,
    val englishName: String,
    val bcp47: String,
    val wireCode: Byte
) {
    HINDI("hi", "हिन्दी", "Hindi", "hi-IN", 0),
    GUJARATI("gu", "ગુજરાતી", "Gujarati", "gu-IN", 1),
    MARATHI("mr", "मराठी", "Marathi", "mr-IN", 2),
    KANNADA("kn", "ಕನ್ನಡ", "Kannada", "kn-IN", 3),
    MALAYALAM("ml", "മലയാളം", "Malayalam", "ml-IN", 4),
    TAMIL("ta", "தமிழ்", "Tamil", "ta-IN", 5),
    TELUGU("te", "తెలుగు", "Telugu", "te-IN", 6),
    ODIA("or", "ଓଡ଼ିଆ", "Odia", "or-IN", 7),
    BENGALI("bn", "বাংলা", "Bengali", "bn-IN", 8),
    ENGLISH("en", "English", "English", "en-US", 9);

    fun getDefaultVoiceNoteText(): String = when (this) {
        HINDI -> "वॉयस संदेश प्राप्त हुआ।"
        GUJARATI -> "વૉઇસ સંદેશ મળ્યો."
        MARATHI -> "व्हॉइस संदेश प्राप्त झाला."
        KANNADA -> "ಧ್ವನಿ ಸಂದೇಶ ಸ್ವೀಕರಿಸಲಾಗಿದೆ."
        MALAYALAM -> "വോയ്‌സ് സന്ദേശം ലഭിച്ചു."
        TAMIL -> "குரல் செய்தி பெறப்பட்டது."
        TELUGU -> "వాయిస్ సందేశం అందింది."
        ODIA -> "ଭଏସ୍ ବାର୍ତ୍ତା ମିଳିଲା।"
        BENGALI -> "ভয়েস বার্তা প্রাপ্ত হয়েছে।"
        ENGLISH -> "Voice message received."
    }

    fun getDefaultEmergencyText(): String = when (this) {
        HINDI -> "आपातकालीन चेतावनी! तत्काल सहायता चाहिए!"
        GUJARATI -> "ઇમરજન્સી એલર્ટ! તાત્કાલિક સહાયની જરૂર છે!"
        MARATHI -> "आणीबाणी इशारा! त्वरित मदतीची गरज आहे!"
        KANNADA -> "ತುರ್ತು ಎಚ್ಚರಿಕೆ! ತಕ್ಷಣದ ನೆರವು ಬೇಕು!"
        MALAYALAM -> "അടിയന്തര മുന്നറിയിപ്പ്! ഉടൻ സഹായം ആവശ്യമാണ്!"
        TAMIL -> "அவசர எச்சரிக்கை! உடனடி உதவி தேவை!"
        TELUGU -> "అత్యవసర హెచ్చరిక! వెంటనే సహాయం కావాలి!"
        ODIA -> "ଜରୁରୀକାଳୀନ ସତର୍କତା! ତୁରନ୍ତ ସହାୟତା ଆବଶ୍ୟକ!"
        BENGALI -> "জরুরী সতর্কতা! অবিলম্বে সহায়তা প্রয়োজন!"
        ENGLISH -> "Emergency Alert! Immediate assistance required!"
    }

    companion object {
        fun fromCode(code: String): Language {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: ENGLISH
        }

        fun fromWireCode(wireCode: Byte): Language {
            return entries.firstOrNull { it.wireCode == wireCode } ?: ENGLISH
        }
    }
}
