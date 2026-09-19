# AstraMesh / iTantra developer commands

default:
    @echo "AstraMesh Android developer commands:"
    @echo "  just build              Build the Android debug APK"
    @echo "  just test               Run unit tests across all Gradle modules"
    @echo "  just clean              Clean Gradle build outputs"
    @echo "  just install            Install debug APK to connected device"

build:
    ./gradlew :app:assembleDebug

test:
    ./gradlew test --continue

clean:
    ./gradlew clean

install:
    ./gradlew :app:installDebug

info:
    @echo "AstraMesh / iTantra — Decentralized Offline P2P Mesh Messaging for Android"
    @echo "Target: Android 10+ (API 29+)"

