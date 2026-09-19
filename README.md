# AstraMesh / iTantra

A production-grade offline-first decentralized peer-to-peer messaging platform for Android built with Kotlin 2.x, Jetpack Compose, and Android BLE APIs.

## Tech Stack & Specifications

- **Application ID**: `com.astra.itantra`
- **Target Platform**: Android 10+ (API level 29+)
- **Languages & Frameworks**: Kotlin 2.x, Jetpack Compose, Hilt Dependency Injection, Room Database, Kotlin Coroutines & Flow, ONNX Runtime.
- **Networking & Transport**: Android BLE Stack (Dual-role GATT Central & Peripheral), Decentralized Multi-hop Source-Based Routing.
- **Security & Cryptography**: Noise XX / Double Ratchet Protocol, Curve25519, Ed25519, HKDF-SHA256, AES-256-GCM / ChaCha20-Poly1305, Ephemeral Peer Identifiers.

## Features

- **Decentralized BLE Mesh Routing**: Multi-hop ad-hoc packet forwarding and loop prevention using compact Bloom filters.
- **Walkie-Talkie & Smart Push-to-Talk (PTT)**: Real-time live voice streaming and voice notes over BLE mesh links.
- **Offline Machine Translation**: On-device neural machine translation powered by int8 quantized ONNX NLLB-200 models.
- **Encrypted Local Storage**: Room SQLite database with panic-wipe support.
- **Peer Discovery & Radar**: Dynamic scanning, proximity discovery, and real-time mesh node visualization.

## Module Structure

- `app/` — Main Android application entry point & UI screens.
- `ble/` — Dual-role GATT server/client manager and BLE connection pool.
- `routing/` — Ad-hoc mesh routing protocol, shortest-path calculation, and DTN store-and-forward queue.
- `mesh/` — Mesh protocol packet codecs and packet framing.
- `crypto/` — Cryptographic engines, Noise protocol implementation, and key stores.
- `domain/` & `data/` — Core business models, repositories, and persistence engines.
- `services/` — AI pipeline services (ONNX translation, voice processing) and Android foreground services.
- `ui/` — Shared Compose UI components, design tokens, and theme.
- `feature-*` — Modularized feature screens (`feature-chat`, `feature-nearby`, `feature-settings`).

## Building & Testing

```bash
# Build Debug APK
./gradlew :app:assembleDebug

# Run Unit Tests
./gradlew test --continue

# Install to connected device
./gradlew :app:installDebug
```

