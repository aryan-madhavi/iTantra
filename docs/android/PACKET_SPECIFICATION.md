# AstraMesh Binary Packet Wire Specification

AstraMesh uses a high-density, big-endian binary framing format designed specifically for bandwidth-constrained Bluetooth Low Energy MTU sizes.

---

## 1. Frame Layout Overview

Every AstraMesh packet consists of:
1. **38-byte Packed Binary Header**
2. **Variable-Length Payload** ($0 \le L \le 65535$ bytes)
3. **4-byte CRC-32 Trailer** (IEEE 802.3 polynomial `0xEDB88320`)

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|          MAGIC (0x4153)       |  VER  |  TYPE |     FLAGS     |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|      TTL      |   HOP COUNT   |        SEQUENCE NUMBER        |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                                                               |
+                       PACKET ID (64-bit)                      +
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                                                               |
+                   SOURCE NODE ID (64-bit)                     +
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                                                               |
+                DESTINATION NODE ID (64-bit)                   +
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                   VISITED BLOOM FILTER (32-bit)               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|        PAYLOAD LENGTH         |                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+                               +
|                                                               |
/                       PAYLOAD DATA (Variable)                 /
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                     CRC-32 CHECKSUM (32-bit)                  |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

---

## 2. Field Specifications

| Byte Offset | Size (Bytes) | Field Name | Description |
|---|---|---|---|
| `0..1` | 2 | `MAGIC` | Constant magic bytes: `0x41 0x53` (`'A'`, `'S'`) |
| `2` | 1 | `VERSION_AND_TYPE` | Upper 4 bits: Version (`0x01`). Lower 4 bits: `AstraPacketType` |
| `3` | 1 | `FLAGS` | Bitmask flags: Emergency, ACK required, Encrypted, Relay allowed |
| `4` | 1 | `TTL` | Time-to-Live hop limit (default: 7, emergency: 15). Decremented per hop |
| `5` | 1 | `HOP_COUNT` | Incrementing hop traversal counter (starts at 0) |
| `6..7` | 2 | `SEQUENCE_NUMBER` | Monotonic 16-bit sequence number for route freshness |
| `8..15` | 8 | `PACKET_ID` | Deterministic 64-bit packet hash for deduplication |
| `16..23` | 8 | `SOURCE_NODE_ID` | 64-bit ephemeral identifier of the originator |
| `24..31` | 8 | `DESTINATION_NODE_ID` | 64-bit target node ID, or `0xFFFFFFFFFFFFFFFF` for Broadcast |
| `32..35` | 4 | `VISITED_BLOOM_FILTER` | 32-bit compact Bloom filter tracking visited nodes |
| `36..37` | 2 | `PAYLOAD_LENGTH` | 16-bit unsigned big-endian integer ($0..65535$) |
| `38..N-4` | $L$ | `PAYLOAD` | Encrypted ciphertext or control message payload |
| `N-4..N-1` | 4 | `CRC32` | CRC-32 over bytes `0` to `N-5` |

---

## 3. Packet Types

| Code | Identifier | Description |
|---|---|---|
| `0x01` | `DATA_UNICAST` | End-to-end encrypted direct peer message |
| `0x02` | `DATA_BROADCAST` | Public broadcast message flooded across the network |
| `0x03` | `ACK` | End-to-end delivery acknowledgment |
| `0x04` | `ROUTE_REQUEST` (`RREQ`) | Reactive route discovery query |
| `0x05` | `ROUTE_REPLY` (`RREP`) | Reactive route discovery response |
| `0x06` | `HEARTBEAT` | Single-hop periodic link quality beacon |
| `0x07` | `ATTACHMENT_SLICE` | Fragment of bulk media attachment |
| `0x08` | `EMERGENCY_BROADCAST` | High-priority panic broadcast with extended TTL |

---

## 4. BLE MTU Slicing & Reassembly

When the serialized packet size exceeds the negotiated GATT MTU ($M - 3$), the `BleFrameFragmenter` divides the frame into sequential slices:
- **Slice Header (4 bytes)**:
  - `0..1`: Slice Index (16-bit unsigned)
  - `2..3`: Total Slices (16-bit unsigned)
- **Slice Payload**:
  - Up to $(M - 7)$ bytes of packet data per slice.

The `BleFrameReassembler` reconstructs incoming slices, rejects mismatched totals, enforces a 15-second reassembly window, and validates the trailing CRC-32 prior to delivering the reconstructed `AstraPacket` to the `MeshEngine`.
