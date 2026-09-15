path = 'services/src/main/kotlin/com/astramesh/services/audio/MmsVitsTtsEngine.kt'
with open(path, 'r') as f:
    content = f.read()

content = content.replace('LinguisticChunker.chunk(normalized, language)', 'LinguisticChunker.chunkText(text, language)')
content = content.replace('for (char in processedText) {', 'for (i in 0 until processedText.length) {\n                    val char = processedText[i]')

with open(path, 'w') as f:
    f.write(content)

print("Updated MmsVitsTtsEngine.kt")
