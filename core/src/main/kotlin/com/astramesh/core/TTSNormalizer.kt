package com.astramesh.core

import java.text.Normalizer
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Production-grade Text Normalizer for Text-to-Speech (TTS) across 10 Indic languages.
 * Implements Indic-to-ASCII numeral mapping, numeral expansion to words, NFC normalization,
 * and punctuation sanitization preserving sentence boundary stops.
 */
object TTSNormalizer {

    private val DIGIT_MAPPING: Map<Char, Char> = buildMap {
        // Devanagari: ०-९ (0x0966..0x096F)
        for (i in 0..9) put((0x0966 + i).toChar(), ('0'.code + i).toChar())
        // Bengali: ০-৯ (0x09E6..0x09EF)
        for (i in 0..9) put((0x09E6 + i).toChar(), ('0'.code + i).toChar())
        // Gurmukhi: ੦-੯ (0x0A66..0x0A6F)
        for (i in 0..9) put((0x0A66 + i).toChar(), ('0'.code + i).toChar())
        // Gujarati: ૦-૯ (0x0AE6..0x0AEF)
        for (i in 0..9) put((0x0AE6 + i).toChar(), ('0'.code + i).toChar())
        // Odia: ୦-୯ (0x0B66..0x0B6F)
        for (i in 0..9) put((0x0B66 + i).toChar(), ('0'.code + i).toChar())
        // Tamil: ௦-௯ (0x0BE6..0x0BEF)
        for (i in 0..9) put((0x0BE6 + i).toChar(), ('0'.code + i).toChar())
        // Telugu: ౦-౯ (0x0C66..0x0C6F)
        for (i in 0..9) put((0x0C66 + i).toChar(), ('0'.code + i).toChar())
        // Kannada: ೦-೯ (0x0CE6..0x0CEF)
        for (i in 0..9) put((0x0CE6 + i).toChar(), ('0'.code + i).toChar())
        // Malayalam: ൦-൯ (0x0D66..0x0D6F)
        for (i in 0..9) put((0x0D66 + i).toChar(), ('0'.code + i).toChar())
    }

    private val HINDI_NUMS: Map<Int, String> = mapOf(
        0 to "शून्य", 1 to "एक", 2 to "दो", 3 to "तीन", 4 to "चार", 5 to "पाँच",
        6 to "छह", 7 to "सात", 8 to "आठ", 9 to "नौ", 10 to "दस",
        11 to "ग्यारह", 12 to "बारह", 13 to "तेरह", 14 to "चौदह", 15 to "पंद्रह",
        16 to "सोलह", 17 to "सत्रह", 18 to "अठारह", 19 to "उन्नीस", 20 to "बीस",
        30 to "तीस", 40 to "चालीस", 50 to "पचास", 60 to "साठ", 70 to "सत्तर",
        80 to "अस्सी", 90 to "नब्बे", 100 to "सौ", 1000 to "हज़ार"
    )

    private val ENGLISH_NUMS: Map<Int, String> = mapOf(
        0 to "zero", 1 to "one", 2 to "two", 3 to "three", 4 to "four", 5 to "five",
        6 to "six", 7 to "seven", 8 to "eight", 9 to "nine", 10 to "ten",
        11 to "eleven", 12 to "twelve", 13 to "thirteen", 14 to "fourteen", 15 to "fifteen",
        16 to "sixteen", 17 to "seventeen", 18 to "eighteen", 19 to "nineteen", 20 to "twenty",
        30 to "thirty", 40 to "forty", 50 to "fifty", 60 to "sixty", 70 to "seventy",
        80 to "eighty", 90 to "ninety", 100 to "hundred", 1000 to "thousand"
    )

    private val GUJARATI_NUMS: Map<Int, String> = mapOf(
        0 to "શૂન્ય", 1 to "એક", 2 to "બે", 3 to "ત્રણ", 4 to "ચાર", 5 to "પાંચ",
        6 to "છ", 7 to "સાત", 8 to "આઠ", 9 to "નવ", 10 to "દસ",
        20 to "વીસ", 30 to "ત્રીસ", 40 to "ચાલીસ", 50 to "પચાસ", 100 to "સો", 1000 to "હજાર"
    )

    private val MARATHI_NUMS: Map<Int, String> = mapOf(
        0 to "शून्य", 1 to "एक", 2 to "दोन", 3 to "तीन", 4 to "चार", 5 to "पाच",
        6 to "सहा", 7 to "सात", 8 to "आठ", 9 to "नऊ", 10 to "दहा",
        20 to "वीस", 30 to "तीस", 40 to "चाळीस", 50 to "पन्नास", 100 to "शंभर", 1000 to "हजार"
    )

    private val BENGALI_NUMS: Map<Int, String> = mapOf(
        0 to "শূন্য", 1 to "এক", 2 to "দুই", 3 to "তিন", 4 to "চার", 5 to "পাঁচ",
        6 to "ছয়", 7 to "সাত", 8 to "আট", 9 to "নয়", 10 to "দশ",
        20 to "বিশ", 30 to "ত্রিশ", 40 to "চল্লিশ", 50 to "পঞ্চাশ", 100 to "একশ", 1000 to "হাজার"
    )

    private val TAMIL_NUMS: Map<Int, String> = mapOf(
        0 to "பூஜ்யம்", 1 to "ஒன்று", 2 to "இரண்டு", 3 to "மூன்று", 4 to "நான்கு", 5 to "ஐந்து",
        6 to "ஆறு", 7 to "ஏழு", 8 to "எட்டு", 9 to "ஒன்பது", 10 to "பத்து",
        20 to "இருபது", 30 to "முப்பது", 40 to "நாற்பது", 50 to "ஐம்பது", 100 to "நூறு", 1000 to "ஆயிரம்"
    )

    private val TELUGU_NUMS: Map<Int, String> = mapOf(
        0 to "సున్నా", 1 to "ఒకటి", 2 to "రెండు", 3 to "మూడు", 4 to "నాలుగు", 5 to "ఐదు",
        6 to "ఆరు", 7 to "ఏడు", 8 to "ఎనిమిది", 9 to "తొమ్మిది", 10 to "పది",
        20 to "ఇరవై", 30 to "ముప్పై", 40 to "నలభై", 50 to "యాభై", 100 to "వంద", 1000 to "వెయ్యి"
    )

    private val KANNADA_NUMS: Map<Int, String> = mapOf(
        0 to "ಶೂನ್ಯ", 1 to "ಒಂದು", 2 to "ಎರಡು", 3 to "ಮೂರು", 4 to "ನಾಲ್ಕು", 5 to "ಐದು",
        6 to "ಆರು", 7 to "ಏಳು", 8 to "ಎಂಟು", 9 to "ಒಂಬತ್ತು", 10 to "ಹತ್ತು",
        20 to "ಇಪ್ಪತ್ತು", 30 to "ಮೂವತ್ತು", 40 to "ನಲವತ್ತು", 50 to "ಐವತ್ತು", 100 to "ನೂರು", 1000 to "ಸಾವಿರ"
    )

    private val MALAYALAM_NUMS: Map<Int, String> = mapOf(
        0 to "പൂജ്യം", 1 to "ഒന്ന്", 2 to "രണ്ട്", 3 to "മൂന്ന്", 4 to "നാല്", 5 to "അഞ്ച്",
        6 to "ആറ്", 7 to "ഏഴ്", 8 to "എട്ട്", 9 to "ഒൻപത്", 10 to "പത്ത്",
        20 to "ഇരുപത്", 30 to "മുപ്പത്", 40 to "നാൽപ്പത്", 50 to "അമ്പത്", 100 to "നൂറ്", 1000 to "ആയിരം"
    )

    private val ODIA_NUMS: Map<Int, String> = mapOf(
        0 to "ଶୂନ", 1 to "ଏକ", 2 to "ଦୁଇ", 3 to "ତିନି", 4 to "ଚାରି", 5 to "ପାଞ୍ଚ",
        6 to "ଛଅ", 7 to "ସାତ", 8 to "ଆଠ", 9 to "ନଅ", 10 to "ଦଶ",
        20 to "କୋଡ଼ିଏ", 30 to "ତିରିଶ", 40 to "ଚାଳିଶ", 50 to "ପଚାଶ", 100 to "ଶହ", 1000 to "ହଜାର"
    )

    fun getLanguageNumberMap(lang: Language): Map<Int, String> = when (lang) {
        Language.HINDI -> HINDI_NUMS
        Language.GUJARATI -> GUJARATI_NUMS
        Language.MARATHI -> MARATHI_NUMS
        Language.BENGALI -> BENGALI_NUMS
        Language.TAMIL -> TAMIL_NUMS
        Language.TELUGU -> TELUGU_NUMS
        Language.KANNADA -> KANNADA_NUMS
        Language.MALAYALAM -> MALAYALAM_NUMS
        Language.ODIA -> ODIA_NUMS
        Language.ENGLISH -> ENGLISH_NUMS
    }

    fun numberToWords(numStr: String, language: Language = Language.HINDI): String {
        val n = numStr.toIntOrNull() ?: return numStr
        val numMap = getLanguageNumberMap(language)
        if (numMap.containsKey(n)) return numMap[n]!!

        if (language == Language.ENGLISH) {
            if (n < 100) {
                val t = (n / 10) * 10
                val u = n % 10
                return "${ENGLISH_NUMS[t] ?: ""} ${ENGLISH_NUMS[u] ?: ""}".trim()
            }
            if (n < 1000) {
                val h = n / 100
                val rem = n % 100
                val remStr = if (rem > 0) " and " + numberToWords(rem.toString(), Language.ENGLISH) else ""
                return "${ENGLISH_NUMS[h] ?: ""} hundred$remStr".trim()
            }
            return numStr.map { ENGLISH_NUMS[it - '0'] ?: it.toString() }.joinToString(" ")
        }

        if (n < 100) {
            val t = (n / 10) * 10
            val u = n % 10
            return "${numMap[t] ?: ""} ${numMap[u] ?: ""}".trim()
        }
        if (n < 1000) {
            val h = n / 100
            val rem = n % 100
            val remStr = if (rem > 0) " " + numberToWords(rem.toString(), language) else ""
            val hundredWord = if (language == Language.MARATHI) "शंभर" else if (language == Language.GUJARATI) "સો" else "सौ"
            return "${numMap[h] ?: ""} $hundredWord$remStr".trim()
        }

        return numStr.map { numMap[it - '0'] ?: it.toString() }.joinToString(" ")
    }

    fun normalizeUnicode(text: String): String {
        if (text.isBlank()) return ""
        val norm = Normalizer.normalize(text, Normalizer.Form.NFC)
        return norm.replace(Regex("[\\r\\n\\t]+"), " ").replace(Regex(" +"), " ").trim()
    }

    fun expandNumerals(text: String, language: Language = Language.HINDI): String {
        if (text.isBlank()) return ""
        val asciiText = buildString {
            for (ch in text) {
                append(DIGIT_MAPPING[ch] ?: ch)
            }
        }
        val pattern = Pattern.compile("\\b\\d+\\b")
        val matcher = pattern.matcher(asciiText)
        val sb = StringBuffer()
        while (matcher.find()) {
            val word = numberToWords(matcher.group(), language)
            matcher.appendReplacement(sb, Matcher.quoteReplacement(word))
        }
        matcher.appendTail(sb)
        return sb.toString()
    }

    fun cleanPunctuation(text: String): String {
        if (text.isBlank()) return ""
        var t = text.replace(Regex("\\.{2,}"), ".")
        t = t.replace(Regex("[\\u0964|]{2,}"), "।")
        t = t.replace(Regex("[\\u2014\\u2013_]"), ", ")
        for (ch in listOf('\'', '\"', '`', '“', '”', '‘', '’', '«', '»')) {
            t = t.replace(ch.toString(), "")
        }
        t = t.replace(Regex("[^a-zA-Z0-9\\u0900-\\u0DFF.,!?:;\\u0964 ]"), " ")
        t = t.replace(Regex("\\s*([,;:.!?\\u0964])\\s*"), "$1 ")
        return t.replace(Regex(" +"), " ").trim()
    }

    fun normalizeForTTS(text: String, language: Language = Language.HINDI): String {
        if (text.isBlank()) return ""
        val t1 = normalizeUnicode(text)
        val t2 = expandNumerals(t1, language)
        return cleanPunctuation(t2)
    }
}
