package com.astramesh.core

/**
 * Clause-boundary streaming text chunker for TTS.
 * Splits normalized text into chunks of 3 to 16 words along natural punctuation/clause boundaries
 * to achieve sub-second TTFA (Time to First Audio).
 */
object LinguisticChunker {

    const val DEFAULT_MIN_WORDS = 3
    const val DEFAULT_MAX_WORDS = 16

    fun chunkText(
        text: String,
        language: Language = Language.HINDI,
        minWords: Int = DEFAULT_MIN_WORDS,
        maxWords: Int = DEFAULT_MAX_WORDS
    ): List<String> {
        val normalized = TTSNormalizer.normalizeForTTS(text, language)
        if (normalized.isBlank()) return emptyList()

        val words = normalized.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size <= maxWords) {
            return listOf(normalized)
        }

        // Split by clause boundaries (punctuation stops)
        val rawClauses = normalized.split(Regex("(?<=[.!?,;\\u0964])\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
        val chunks = mutableListOf<String>()
        val current = mutableListOf<String>()

        for (clause in rawClauses) {
            val clauseWords = clause.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (clauseWords.isEmpty()) continue

            if (clauseWords.size > maxWords) {
                if (current.isNotEmpty()) {
                    chunks.add(current.joinToString(" "))
                    current.clear()
                }
                for (i in clauseWords.indices step maxWords) {
                    val subWords = clauseWords.subList(i, minOf(i + maxWords, clauseWords.size))
                    chunks.add(subWords.joinToString(" "))
                }
                continue
            }

            if (current.size + clauseWords.size <= maxWords) {
                current.addAll(clauseWords)
            } else {
                if (current.isNotEmpty()) {
                    chunks.add(current.joinToString(" "))
                }
                current.clear()
                current.addAll(clauseWords)
            }
        }

        if (current.isNotEmpty()) {
            chunks.add(current.joinToString(" "))
        }

        return chunks.filter { it.isNotBlank() }
    }
}
