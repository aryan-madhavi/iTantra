# AstraMesh Developer & Testing Guide

This guide details building, testing, simulating, and contributing to AstraMesh.

---

## 1. Prerequisites

- **JDK**: Java 21 LTS
- **Android SDK**: Build Tools 35.0.0, Compile SDK 35, Min SDK 29 (Android 10+)
- **Gradle**: 8.13+ (Use included wrapper `./gradlew`)

---

## 2. Building AstraMesh

### Assemble Debug APK
```bash
./gradlew :app:assembleDebug
```
The resulting debug APK is located at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 3. Running Test Suites

AstraMesh features 100% pure unit test coverage across all modules without requiring physical hardware:

### Execute Complete Unit Test Matrix
```bash
./gradlew testDebugUnitTest
```

### Module-Specific Unit Tests
| Module | Command | What It Verifies |
|---|---|---|
| `:common` | `./gradlew :common:test` | CRC-32 IEEE 802.3, ByteUtils big-endian decoding, AstraResult |
| `:crypto` | `./gradlew :crypto:test` | P-256 ECDH, AES-256-GCM, Double Ratchet, 64-bit Anti-Replay window |
| `:core` | `./gradlew :core:test` | Value classes, deterministic PacketId generation, NetworkConfig |
| `:domain` | `./gradlew :domain:test` | Use cases (`SendMessageUseCase`, `EmergencyBroadcastUseCase`) |
| `:routing` | `./gradlew :routing:test` | Composite metric formula, Bloom filter loop detector, Dijkstra |
| `:ble` | `./gradlew :ble:testDebugUnitTest` | BleFrameCodec, out-of-order fragment reassembly, connection pool |
| `:mesh` | `./gradlew :mesh:testDebugUnitTest` | 38-byte binary packet serialization, forwarding engine, LRU cache |
| `:storage`| `./gradlew :storage:testDebugUnitTest` | Robolectric SQLite in-memory Room DAOs, cascades, type converters |
| `:data` | `./gradlew :data:testDebugUnitTest` | Repository implementations mapping Room DAOs and MeshEngine |
| `:services`| `./gradlew :services:testDebugUnitTest`| Foreground service configuration and Power coordinator |
| `:workers`| `./gradlew :workers:testDebugUnitTest` | WorkManager tasks (DTN sync, key rotation, DB prune) |
| `:ui` | `./gradlew :ui:testDebugUnitTest` | Theme colors and common UI components |
| `:feature-chat` | `./gradlew :feature-chat:testDebugUnitTest` | ChatList and Conversation ViewModels |
| `:feature-nearby`| `./gradlew :feature-nearby:testDebugUnitTest`| NearbyPeers and MeshTopology ViewModels |
| `:feature-settings`| `./gradlew :feature-settings:testDebugUnitTest`| Settings ViewModel, panic wipe, ephemeral rotation |
| `:testing`| `./gradlew :testing:testDebugUnitTest` | Multi-node linear mesh simulator ($A \leftrightarrow B \leftrightarrow C \leftrightarrow D \leftrightarrow E$) |
| `:app` | `./gradlew :app:testDebugUnitTest` | Hilt dependency injection contracts |

---

## 4. Virtual BLE Mesh Simulator (`:testing`)

You can test multi-hop mesh topologies in memory without any physical Android devices using `VirtualBleMedium` and `VirtualMeshNode`:

```kotlin
val medium = VirtualBleMedium()

// Spin up 5 virtual nodes
val nodeA = VirtualMeshNode(NodeId(1L), medium)
val nodeB = VirtualMeshNode(NodeId(2L), medium)
val nodeC = VirtualMeshNode(NodeId(3L), medium)
val nodeD = VirtualMeshNode(NodeId(4L), medium)
val nodeE = VirtualMeshNode(NodeId(5L), medium)

// Establish linear topology: A <-> B <-> C <-> D <-> E
medium.addBidirectionalLink(nodeA.nodeId, nodeB.nodeId)
medium.addBidirectionalLink(nodeB.nodeId, nodeC.nodeId)
medium.addBidirectionalLink(nodeC.nodeId, nodeD.nodeId)
medium.addBidirectionalLink(nodeD.nodeId, nodeE.nodeId)

// Transmit packet across 4 relays
val payload = "Hello from Node A to Node E".toByteArray()
nodeA.sendPacket(nodeE.nodeId, payload)
```
See `testing/src/test/kotlin/com/astramesh/testing/MultiNodeSimulationTest.kt` for full test scenarios.
