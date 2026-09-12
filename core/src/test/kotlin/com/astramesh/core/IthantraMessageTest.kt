package com.astramesh.core

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class IthantraMessageTest {

    @Test
    fun `IthantraMessage serialization and deserialization roundtrip across all 10 languages`() {
        val testPhrases = mapOf(
            Language.HINDI to "नमस्ते, सेक्टर बी सुरक्षित है।",
            Language.GUJARATI to "નમસ્તે, બધું બરાબર છે.",
            Language.MARATHI to "नमस्कार, परिसर सुरक्षित आहे.",
            Language.KANNADA to "ನಮಸ್ಕಾರ, ಎಲ್ಲವೂ ಸುರಕ್ಷಿತವಾಗಿದೆ.",
            Language.MALAYALAM to "നമസ്കാരം, എല്ലാം സുരക്ഷിതമാണ്.",
            Language.TAMIL to "வணக்கம், பகுதி பாதுகாப்பாக உள்ளது.",
            Language.TELUGU to "నమస్కారం, ప్రాంతం సురక్షితంగా ఉంది.",
            Language.ODIA to "ନମସ୍କାର, ସବୁକିଛି ସୁରକ୍ଷିତ ଅଛି।",
            Language.BENGALI to "নমস্কার, এলাকাটি নিরাপদ।",
            Language.ENGLISH to "Hello, sector perimeter is clear."
        )

        for ((lang, text) in testPhrases) {
            val msg = IthantraMessage(
                senderId = NodeId(0x123456789ABCDEF0L),
                messageType = MessageType.NORMAL,
                language = lang,
                sequenceNumber = 101L,
                timestamp = 1710000000000L,
                text = text,
                hopTtl = 5,
                gpsLatitude = 18.5204f,
                gpsLongitude = 73.8567f
            )

            val binary = msg.toBinary()
            assertThat(IthantraMessage.isIthantraMessage(binary)).isTrue()

            val decoded = IthantraMessage.fromBinary(binary)
            assertThat(decoded.senderId).isEqualTo(msg.senderId)
            assertThat(decoded.messageType).isEqualTo(MessageType.NORMAL)
            assertThat(decoded.language).isEqualTo(lang)
            assertThat(decoded.sequenceNumber).isEqualTo(101L)
            assertThat(decoded.timestamp).isEqualTo(1710000000000L)
            assertThat(decoded.text).isEqualTo(text)
            assertThat(decoded.hopTtl).isEqualTo(5.toByte())
            assertThat(decoded.gpsLatitude).isEqualTo(18.5204f)
            assertThat(decoded.gpsLongitude).isEqualTo(73.8567f)
        }
    }

    @Test
    fun `CRC16 verification detects bit flips and rejects corrupted packet`() {
        val msg = IthantraMessage(
            senderId = NodeId(12345L),
            messageType = MessageType.ALERT,
            language = Language.HINDI,
            sequenceNumber = 1L,
            timestamp = 1000L,
            text = "मदद चाहिए"
        )
        val binary = msg.toBinary()

        // Corrupt one byte in the text payload
        binary[15] = (binary[15].toInt() xor 0xFF).toByte()

        assertThrows(IllegalArgumentException::class.java) {
            IthantraMessage.fromBinary(binary)
        }
    }

    @Test
    fun `EmergencyClassifier detects distress keywords in multiple Indian languages`() {
        assertThat(EmergencyClassifier.isEmergency("हमें तुरंत मदद चाहिए", Language.HINDI)).isTrue()
        assertThat(EmergencyClassifier.isEmergency("There is a fire on the 3rd floor", Language.ENGLISH)).isTrue()
        assertThat(EmergencyClassifier.isEmergency("मला वाचवा, येथे खूप मोठा अपघात झाला आहे", Language.MARATHI)).isTrue()
        assertThat(EmergencyClassifier.isEmergency("અહીં આગ લાગી છે", Language.GUJARATI)).isTrue()
        assertThat(EmergencyClassifier.isEmergency("எங்களுக்கு உதவி தேவை", Language.TAMIL)).isTrue()
        assertThat(EmergencyClassifier.isEmergency("ನಾವು ಸಿಲುಕಿಕೊಂಡಿದ್ದೇವೆ, ದಯವಿಟ್ಟು ಸಹಾಯ ಮಾಡಿ", Language.KANNADA)).isTrue()
        assertThat(EmergencyClassifier.isEmergency("ഞങ്ങളെ രക്ഷിക്കൂ", Language.MALAYALAM)).isTrue()

        assertThat(EmergencyClassifier.isEmergency("आज मौसम बहुत अच्छा है", Language.HINDI)).isFalse()
        assertThat(EmergencyClassifier.isEmergency("Everything is normal here", Language.ENGLISH)).isFalse()
    }

    @Test
    fun `EmergencyClassifier prevents false triggers on benign words containing distress substrings`() {
        // Words containing 'sos', 'help', 'fire' as substrings must NOT trigger emergency alerts
        assertThat(EmergencyClassifier.isEmergency("I am having espresso today", Language.ENGLISH)).isFalse()
        assertThat(EmergencyClassifier.isEmergency("We completed the lessons for today", Language.ENGLISH)).isFalse()
        assertThat(EmergencyClassifier.isEmergency("She found a sea shell on the beach", Language.ENGLISH)).isFalse()
        assertThat(EmergencyClassifier.isEmergency("The firefly is glowing nicely", Language.ENGLISH)).isFalse()
        assertThat(EmergencyClassifier.isEmergency("The shelter is warm and dry", Language.ENGLISH)).isFalse()
        assertThat(EmergencyClassifier.isEmergency("The response was prompt and helpful", Language.ENGLISH)).isFalse()

        // Genuine standalone distress keywords must trigger
        assertThat(EmergencyClassifier.isEmergency("We need help now!", Language.ENGLISH)).isTrue()
        assertThat(EmergencyClassifier.isEmergency("SOS SOS emergency at sector 4", Language.ENGLISH)).isTrue()
        assertThat(EmergencyClassifier.isEmergency("बचाओ! यहां आग लगी है!", Language.HINDI)).isTrue()
    }

    @Test
    fun `OfflineTranslationEngine same language optimization returns original text immediately`() {
        val text = "यह एक परीक्षण संदेश है।"
        val result = OfflineTranslationEngine.translate(text, Language.HINDI, Language.HINDI)
        assertThat(result).isEqualTo(text)
    }

    @Test
    fun `OfflineTranslationEngine translates emergency phrase across languages`() {
        val hindiHelp = "कृपया मेरी मदद कीजिए।"
        val translatedToEnglish = OfflineTranslationEngine.translate(hindiHelp, Language.HINDI, Language.ENGLISH)
        assertThat(translatedToEnglish).isEqualTo("Please help me.")

        val englishHelp = "Please help me."
        val translatedToMarathi = OfflineTranslationEngine.translate(englishHelp, Language.ENGLISH, Language.MARATHI)
        assertThat(translatedToMarathi).isEqualTo("मला मदत करा.")
    }

    @Test
    fun `OfflineTranslationEngine translates voice message received phrase across languages`() {
        val hindiVoiceNote = "वॉयस संदेश प्राप्त हुआ।"
        val translatedToEnglish = OfflineTranslationEngine.translate(hindiVoiceNote, Language.HINDI, Language.ENGLISH)
        assertThat(translatedToEnglish).isEqualTo("Voice message received.")

        val englishVoiceNote = "Voice message received."
        val translatedToTamil = OfflineTranslationEngine.translate(englishVoiceNote, Language.ENGLISH, Language.TAMIL)
        assertThat(translatedToTamil).isEqualTo("குரல் செய்தி பெறப்பட்டது.")

        val marathiVoiceNote = "व्हॉइस संदेश प्राप्त झाला."
        val translatedToBengali = OfflineTranslationEngine.translate(marathiVoiceNote, Language.MARATHI, Language.BENGALI)
        assertThat(translatedToBengali).isEqualTo("ভয়েস বার্তা প্রাপ্ত হয়েছে।")
    }

    @Test
    fun `Language getDefaultVoiceNoteText and getDefaultEmergencyText provides non-empty localized strings for all 10 languages`() {
        for (lang in Language.entries) {
            val voiceText = lang.getDefaultVoiceNoteText()
            val emergencyText = lang.getDefaultEmergencyText()

            assertThat(voiceText).isNotEmpty()
            assertThat(emergencyText).isNotEmpty()

            // Verify they translate cleanly to English
            val translatedVoice = OfflineTranslationEngine.translate(voiceText, lang, Language.ENGLISH)
            assertThat(translatedVoice).isNotEmpty()

            val translatedEmergency = OfflineTranslationEngine.translate(emergencyText, lang, Language.ENGLISH)
            assertThat(translatedEmergency).isNotEmpty()
        }
    }
}

