package com.astramesh.services.audio

import com.astramesh.common.AstraLog
import com.astramesh.core.Language
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.text.Normalizer

/**
 * Pure Kotlin BPE Tokenizer for NLLB-200 driven by HuggingFace fast-tokenizer `tokenizer.json`.
 *
 * Implements:
 * 1. NFKC Unicode Normalization & whitespace collapsing (matching `tokenizer.json` normalizers).
 * 2. Metaspace Pre-tokenization with boundary marker u2581 (matching `tokenizer.json` pre_tokenizer).
 * 3. Greedy BPE Merge Algorithm using prioritized merge ranks (matching `model.merges`).
 * 4. Token string to ID mapping with `<unk>` (ID 3L) fallback.
 * 5. Symmetrical decoding (replacing u2581 with space and trimming leading space).
 */
class NllbTokenizer(
    val tokenToId: Map<String, Long>,
    val idToToken: Map<Long, String>,
    val bpeRanks: Map<Pair<String, String>, Int>,
    val unkTokenId: Long = 3L,
    val eosTokenId: Long = 2L,
    val padTokenId: Long = 1L,
    val bosTokenId: Long = 0L
) {
    companion object {
        private const val TAG = "NllbTokenizer"
        const val METASPACE_REPLACEMENT = "\u2581" // U+2581 (lower one eighth block)

        // Authoritative mapping from Language enum to FLORES-200 language code strings
        val LANGUAGE_TO_NLLB_CODE = mapOf(
            Language.HINDI to "hin_Deva",
            Language.GUJARATI to "guj_Gujr",
            Language.MARATHI to "mar_Deva",
            Language.KANNADA to "kan_Knda",
            Language.MALAYALAM to "mal_Mlym",
            Language.TAMIL to "tam_Taml",
            Language.TELUGU to "tel_Telu",
            Language.ODIA to "ory_Orya",
            Language.BENGALI to "ben_Beng",
            Language.ENGLISH to "eng_Latn"
        )

        /**
         * Fast streaming parser to load NllbTokenizer from an InputStream (e.g. Android Asset).
         */
        fun fromInputStream(inputStream: InputStream): NllbTokenizer {
            return fromReader(BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8), 64 * 1024))
        }

        /**
         * Fast streaming parser to load NllbTokenizer from a local File.
         */
        fun fromFile(file: File): NllbTokenizer {
            return file.inputStream().use { fromInputStream(it) }
        }

        /**
         * Fast streaming parser to load NllbTokenizer from a Reader without external JSON dependencies.
         */
        fun fromReader(reader: Reader): NllbTokenizer {
            val parser = TokenizerJsonStreamingParser(reader)
            val parsed = parser.parse()
            AstraLog.i(TAG, "Loaded NllbTokenizer with ${parsed.tokenToId.size} vocab tokens and ${parsed.bpeRanks.size} BPE merge rules")
            return NllbTokenizer(
                tokenToId = parsed.tokenToId,
                idToToken = parsed.idToToken,
                bpeRanks = parsed.bpeRanks,
                unkTokenId = parsed.unkTokenId,
                eosTokenId = parsed.eosTokenId,
                padTokenId = parsed.padTokenId,
                bosTokenId = parsed.bosTokenId
            )
        }
    }

    /**
     * Resolves the token ID for the given language.
     */
    fun getLanguageTokenId(language: Language): Long {
        val code = LANGUAGE_TO_NLLB_CODE[language] ?: "eng_Latn"
        return tokenToId[code] ?: 256047L
    }

    /**
     * 1. Normalization:
     * - Standard Unicode NFKC normalization
     * - Collapse 2 or more spaces into a single space
     */
    fun normalize(text: String): String {
        val nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC)
        return nfkc.replace(Regex(" {2,}"), " ")
    }

    /**
     * 2. Metaspace Pre-tokenization:
     * - Replaces ASCII space (\u0020) with \u2581
     * - Prepends \u2581 if not already present (prepend_scheme = "always")
     * - Splits into words before each \u2581 (split = true)
     */
    fun preTokenize(text: String): List<String> {
        if (text.isEmpty()) return emptyList()

        var replaced = text.replace(" ", METASPACE_REPLACEMENT)
        if (!replaced.startsWith(METASPACE_REPLACEMENT)) {
            replaced = METASPACE_REPLACEMENT + replaced
        }

        val words = mutableListOf<String>()
        val currentWord = StringBuilder()

        for (i in 0 until replaced.length) {
            val c = replaced[i]
            if (c == '\u2581') {
                if (currentWord.isNotEmpty()) {
                    words.add(currentWord.toString())
                    currentWord.clear()
                }
            }
            currentWord.append(c)
        }
        if (currentWord.isNotEmpty()) {
            words.add(currentWord.toString())
        }

        return words
    }

    /**
     * Greedy BPE encoding for a single word piece:
     * Splits into initial characters, then repeatedly finds and merges the adjacent pair
     * with the lowest merge rank in bpeRanks until no applicable merges remain.
     */
    private fun bpeMergeWord(word: String): List<String> {
        if (word.isEmpty()) return emptyList()

        // Split word into characters / code points
        val symbols = mutableListOf<String>()
        var i = 0
        while (i < word.length) {
            val codePoint = word.codePointAt(i)
            val charCount = Character.charCount(codePoint)
            symbols.add(word.substring(i, i + charCount))
            i += charCount
        }

        if (symbols.size <= 1) {
            return symbols
        }

        while (symbols.size > 1) {
            var minRank = Int.MAX_VALUE
            var bestPair: Pair<String, String>? = null

            for (idx in 0 until symbols.size - 1) {
                val pair = Pair(symbols[idx], symbols[idx + 1])
                val rank = bpeRanks[pair]
                if (rank != null && rank < minRank) {
                    minRank = rank
                    bestPair = pair
                }
            }

            if (bestPair == null || minRank == Int.MAX_VALUE) {
                break
            }

            val newSymbols = mutableListOf<String>()
            var idx = 0
            while (idx < symbols.size) {
                if (idx < symbols.size - 1 && symbols[idx] == bestPair.first && symbols[idx + 1] == bestPair.second) {
                    newSymbols.add(symbols[idx] + symbols[idx + 1])
                    idx += 2
                } else {
                    newSymbols.add(symbols[idx])
                    idx += 1
                }
            }
            symbols.clear()
            symbols.addAll(newSymbols)
        }

        return symbols
    }

    /**
     * Encodes arbitrary text into a list of BPE token strings.
     */
    fun tokenize(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val normalized = normalize(text)
        if (normalized.isEmpty()) return emptyList()

        val words = preTokenize(normalized)
        val tokens = mutableListOf<String>()
        for (word in words) {
            tokens.addAll(bpeMergeWord(word))
        }
        return tokens
    }

    /**
     * Encodes arbitrary text into a list of vocabulary token IDs.
     */
    fun encode(text: String): List<Long> {
        val tokens = tokenize(text)
        return tokens.map { tokenToId[it] ?: unkTokenId }
    }

    /**
     * Decodes a sequence of token IDs back into human-readable text.
     * Symmetrically reverses Metaspace pre-tokenization.
     */
    fun decode(tokenIds: List<Long>): String {
        if (tokenIds.isEmpty()) return ""

        val rawSb = StringBuilder()
        for (id in tokenIds) {
            val token = idToToken[id] ?: continue
            // Skip special tokens in decoded text output
            if (token == "<s>" || token == "</s>" || token == "<pad>" || token.endsWith("_Deva") ||
                token.endsWith("_Beng") || token.endsWith("_Telu") || token.endsWith("_Taml") ||
                token.endsWith("_Gujr") || token.endsWith("_Knda") || token.endsWith("_Mlym") ||
                token.endsWith("_Orya") || token.endsWith("_Latn")
            ) {
                continue
            }
            rawSb.append(token)
        }

        val decoded = rawSb.toString().replace(METASPACE_REPLACEMENT, " ")
        return if (decoded.startsWith(" ")) decoded.substring(1).trim() else decoded.trim()
    }
}

/**
 * Lightweight, high-throughput streaming pull parser for HuggingFace fast `tokenizer.json`.
 * Operates without intermediate AST object graph allocation.
 */
internal class TokenizerJsonStreamingParser(private val reader: Reader) {
    private val buffer = CharArray(64 * 1024)
    private var bufPos = 0
    private var bufLimit = 0
    private var isEof = false

    data class ParsedData(
        val tokenToId: Map<String, Long>,
        val idToToken: Map<Long, String>,
        val bpeRanks: Map<Pair<String, String>, Int>,
        var unkTokenId: Long = 3L,
        var eosTokenId: Long = 2L,
        var padTokenId: Long = 1L,
        var bosTokenId: Long = 0L
    )

    fun parse(): ParsedData {
        val tokenToId = HashMap<String, Long>(260_000)
        val idToToken = HashMap<Long, String>(260_000)
        val bpeRanks = HashMap<Pair<String, String>, Int>(520_000)

        var unkId = 3L
        var eosId = 2L
        var padId = 1L
        var bosId = 0L

        skipWhitespace()
        if (peekChar() == '{') {
            readChar()
        }

        while (!isEof) {
            val key = nextObjectKey() ?: break
            when (key) {
                "model" -> {
                    // Enter model object
                    expectChar('{')
                    while (true) {
                        val modelKey = nextObjectKey() ?: break
                        when (modelKey) {
                            "vocab" -> {
                                parseVocab(tokenToId, idToToken)
                            }
                            "merges" -> {
                                parseMerges(bpeRanks)
                            }
                            else -> {
                                skipValue()
                            }
                        }
                    }
                }
                "added_tokens" -> {
                    // Extract special token IDs
                    parseAddedTokens { content, id ->
                        when (content) {
                            "<s>" -> bosId = id
                            "</s>" -> eosId = id
                            "<pad>" -> padId = id
                            "<unk>" -> unkId = id
                        }
                        tokenToId[content] = id
                        idToToken[id] = content
                    }
                }
                else -> {
                    skipValue()
                }
            }
        }

        return ParsedData(
            tokenToId = tokenToId,
            idToToken = idToToken,
            bpeRanks = bpeRanks,
            unkTokenId = unkId,
            eosTokenId = eosId,
            padTokenId = padId,
            bosTokenId = bosId
        )
    }

    private fun parseVocab(tokenToId: MutableMap<String, Long>, idToToken: MutableMap<Long, String>) {
        expectChar('{')
        skipWhitespace()
        if (peekChar() == '}') {
            readChar()
            return
        }

        while (true) {
            val token = readString()
            expectChar(':')
            val id = readLong()
            tokenToId[token] = id
            idToToken[id] = token

            skipWhitespace()
            val c = readChar()
            if (c == '}') break
            if (c != ',') throw IllegalArgumentException("Expected ',' or '}' in vocab, found '$c'")
            skipWhitespace()
        }
    }

    private fun parseMerges(bpeRanks: MutableMap<Pair<String, String>, Int>) {
        expectChar('[')
        skipWhitespace()
        if (peekChar() == ']') {
            readChar()
            return
        }

        var rank = 0
        while (true) {
            skipWhitespace()
            val peek = peekChar()
            if (peek == '[') {
                // Merges as [ "tok1", "tok2" ]
                readChar()
                val first = readString()
                skipWhitespace()
                expectChar(',')
                val second = readString()
                skipWhitespace()
                expectChar(']')
                bpeRanks[Pair(first, second)] = rank++
            } else if (peek == '"') {
                // Merges as "tok1 tok2"
                val mergeStr = readString()
                val spaceIdx = mergeStr.indexOf(' ')
                if (spaceIdx > 0) {
                    val first = mergeStr.substring(0, spaceIdx)
                    val second = mergeStr.substring(spaceIdx + 1)
                    bpeRanks[Pair(first, second)] = rank++
                }
            } else {
                throw IllegalArgumentException("Unexpected merge entry start char '$peek'")
            }

            skipWhitespace()
            val c = readChar()
            if (c == ']') break
            if (c != ',') throw IllegalArgumentException("Expected ',' or ']' in merges, found '$c'")
            skipWhitespace()
        }
    }

    private fun parseAddedTokens(onAddedToken: (String, Long) -> Unit) {
        expectChar('[')
        skipWhitespace()
        if (peekChar() == ']') {
            readChar()
            return
        }

        while (true) {
            expectChar('{')
            var id: Long? = null
            var content: String? = null

            while (true) {
                val key = nextObjectKey() ?: break
                when (key) {
                    "id" -> id = readLong()
                    "content" -> content = readString()
                    else -> skipValue()
                }
            }

            if (id != null && content != null) {
                onAddedToken(content, id)
            }

            skipWhitespace()
            val c = readChar()
            if (c == ']') break
            if (c != ',') throw IllegalArgumentException("Expected ',' or ']' in added_tokens, found '$c'")
            skipWhitespace()
        }
    }

    private fun nextObjectKey(): String? {
        skipWhitespace()
        val c = peekChar()
        if (c == '}' || c == (0).toChar()) {
            if (c == '}') readChar()
            return null
        }
        if (c == ',') {
            readChar()
            skipWhitespace()
        }
        if (peekChar() == '}') {
            readChar()
            return null
        }
        val key = readString()
        expectChar(':')
        return key
    }

    private fun skipValue() {
        skipWhitespace()
        val c = peekChar()
        when (c) {
            '{' -> {
                readChar()
                var depth = 1
                while (depth > 0 && !isEof) {
                    when (readChar()) {
                        '{' -> depth++
                        '}' -> depth--
                        '"' -> skipStringBody()
                    }
                }
            }
            '[' -> {
                readChar()
                var depth = 1
                while (depth > 0 && !isEof) {
                    when (readChar()) {
                        '[' -> depth++
                        ']' -> depth--
                        '"' -> skipStringBody()
                    }
                }
            }
            '"' -> {
                readString()
            }
            else -> {
                // Primitive token (number, boolean, null)
                while (!isEof) {
                    val p = peekChar()
                    if (p == ',' || p == '}' || p == ']' || p.isWhitespace()) break
                    readChar()
                }
            }
        }
    }

    private fun skipStringBody() {
        var escaped = false
        while (!isEof) {
            val c = readChar()
            if (escaped) {
                escaped = false
            } else if (c == '\\') {
                escaped = true
            } else if (c == '"') {
                break
            }
        }
    }

    private fun readString(): String {
        skipWhitespace()
        expectChar('"')
        val sb = StringBuilder()
        var escaped = false

        while (!isEof) {
            val c = readChar()
            if (escaped) {
                when (c) {
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    '/' -> sb.append('/')
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000C')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    'u' -> {
                        val hex = StringBuilder()
                        for (k in 0 until 4) {
                            hex.append(readChar())
                        }
                        sb.append(hex.toString().toInt(16).toChar())
                    }
                    else -> sb.append(c)
                }
                escaped = false
            } else if (c == '\\') {
                escaped = true
            } else if (c == '"') {
                break
            } else {
                sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun readLong(): Long {
        skipWhitespace()
        val sb = StringBuilder()
        while (!isEof) {
            val c = peekChar()
            if (c in '0'..'9' || c == '-') {
                sb.append(readChar())
            } else {
                break
            }
        }
        return sb.toString().toLong()
    }

    private fun skipWhitespace() {
        while (!isEof) {
            val c = peekChar()
            if (c.isWhitespace()) {
                readChar()
            } else {
                break
            }
        }
    }

    private fun expectChar(expected: Char) {
        skipWhitespace()
        val c = readChar()
        if (c != expected) {
            throw IllegalArgumentException("Expected '$expected' but found '$c'")
        }
    }

    private fun peekChar(): Char {
        if (bufPos >= bufLimit) {
            fillBuffer()
            if (isEof) return (0).toChar()
        }
        return buffer[bufPos]
    }

    private fun readChar(): Char {
        if (bufPos >= bufLimit) {
            fillBuffer()
            if (isEof) return (0).toChar()
        }
        return buffer[bufPos++]
    }

    private fun fillBuffer() {
        val count = reader.read(buffer, 0, buffer.size)
        if (count <= 0) {
            isEof = true
            bufPos = 0
            bufLimit = 0
        } else {
            bufPos = 0
            bufLimit = count
        }
    }
}
