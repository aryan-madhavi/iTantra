path = 'app/build.gradle.kts'
with open(path, 'r') as f:
    content = f.read()

target = '''    androidResources {
        ignoreAssetsPattern = "!:mt*"
    }'''

replacement = '''    androidResources {
        // Assets configured cleanly
    }'''

content = content.replace(target, replacement)

with open(path, 'w') as f:
    f.write(content)

print("Cleaned app/build.gradle.kts")
