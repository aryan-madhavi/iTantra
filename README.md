# iTantra

A production-grade offline-first decentralized peer-to-peer messaging platform for Android built with Kotlin 2.x and Jetpack Compose.

## Architecture

- **Application ID**: `com.astra.itantra`
- **Application Name**: `iTantra`
- **Technology Stack**: Kotlin 2.x, Android 10+ (API 29+), Hilt, Room Database, Jetpack Compose, Coroutines/Flow, Foreground Services, Android BLE APIs.
- **Security**: Double Ratchet Key Exchange, HKDF-SHA256, AES-256-GCM / ChaCha20-Poly1305, Ephemeral Node IDs.

## Features

- **BLE Mesh Routing**: Offline multi-hop packet forwarding over Bluetooth Low Energy.
- **Peer Discovery & Radar**: Dynamic scanning & advertising of nearby nodes.
- **Encrypted Local Storage**: Room Database for messages, chats, and peer state.
- **Foreground Service**: Persistent radio stack execution for continuous mesh participation.

## Building

```bash
./gradlew :app:build
./gradlew installDebug
```
