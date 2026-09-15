import re

path = 'services/src/main/kotlin/com/astramesh/services/audio/IndicConformerSttEngine.kt'
with open(path, 'r') as f:
    content = f.read()

# Remove the line `if (!isTokenInLanguageScript(v, language)) continue`
content = content.replace('                if (!isTokenInLanguageScript(v, language)) continue\n', '')

# In extractCtcTranscript, add token debug log
target = '''        return sb.toString().trim()
    }'''

replacement = '''        val assembled = sb.toString().trim()
        if (tokens.isNotEmpty()) {
            AstraLog.d(tag, "CTC greedy decoded ${tokens.size} tokens: $tokens -> '$assembled'")
        }
        return assembled
    }'''

content = content.replace(target, replacement)

with open(path, 'w') as f:
    f.write(content)

print("Successfully updated IndicConformerSttEngine.kt")
