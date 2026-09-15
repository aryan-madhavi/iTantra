path = 'app/build.gradle.kts'
with open(path, 'r') as f:
    content = f.read()

target = '''    sourceSets {
        getByName("main") {
            assets {
                srcDirs("src/main/assets")
                exclude("models/mt/**")
            }
        }
    }'''

replacement = '''    androidResources {
        ignoreAssetsPattern = "!:mt*"
    }'''

content = content.replace(target, replacement)

with open(path, 'w') as f:
    f.write(content)

print("Updated app/build.gradle.kts with ignoreAssetsPattern")
