path = 'core/src/main/kotlin/com/astramesh/core/TTSNormalizer.kt'
with open(path, 'r') as f:
    content = f.read()

old_clean = '''    fun cleanPunctuation(text: String): String {
        if (text.isBlank()) return ""
        var t = text.replace(Regex("\\\\.{2,}"), ".")
        t = t.replace(Regex("[\\\\u0964|]{2,}"), "।")
        t = t.replace(Regex("[\\\\u2014\\\\u2013_]"), ", ")
        for (ch in listOf('\\'', '\\"', '`', '“', '”', '‘', '’', '«', '»')) {
            t = t.replace(ch.toString(), "")
        }
        t = t.replace(Regex("[^a-zA-Z0-9\\\\u0900-\\\\u0DFF.,!?:;\\\\u0964 ]"), " ")
        t = t.replace(Regex("\\\\s*([,;:.!?\\\\u0964])\\\\s*"), "$1 ")
        return t.replace(Regex(" +"), " ").trim()
    }'''

new_clean = '''    fun cleanPunctuation(text: String): String {
        if (text.isBlank()) return ""
        var t = text.replace(Regex("\\\\.{2,}"), " ")
        t = t.replace(Regex("[\\\\u0964|]{1,}"), " ")
        t = t.replace(Regex("[\\\\u2014\\\\u2013_]"), " ")
        for (ch in listOf('\\"', '`', '“', '”', '‘', '’', '«', '»', '!', '?', ',', ';', ':', '(', ')', '[', ']', '{', '}', '<', '>', '/', '\\\\', '@', '#', '$', '%', '^', '&', '*', '+', '=')) {
            t = t.replace(ch.toString(), " ")
        }
        t = t.replace(Regex("[^a-zA-Z0-9\\\\u0900-\\\\u0DFF'\\\\- ]"), " ")
        return t.replace(Regex(" +"), " ").trim()
    }'''

content = content.replace(old_clean, new_clean)

with open(path, 'w') as f:
    f.write(content)

print("Updated TTSNormalizer.kt")
