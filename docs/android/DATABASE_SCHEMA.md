# AstraMesh Database Schema Specification

AstraMesh utilizes Room (SQLite) for encrypted-at-rest, ACID-compliant local persistence with Kotlin Symbol Processing (KSP).

---

## 1. Entities & Tables

### `chats`
Stores conversation metadata and participant rosters.
- `id` (TEXT, Primary Key)
- `title` (TEXT)
- `type` (TEXT): `DIRECT`, `GROUP`, `BROADCAST`, `EMERGENCY`
- `participant_ids_json` (TEXT): JSON array of 64-bit Node IDs
- `last_message_snippet` (TEXT, Nullable)
- `unread_count` (INTEGER)
- `updated_at` (INTEGER)

### `messages`
Stores message content, delivery lifecycle status, and routing telemetry.
- `id` (TEXT, Primary Key)
- `chat_id` (TEXT, Foreign Key -> `chats(id)` ON DELETE CASCADE)
- `sender_id` (INTEGER, 64-bit)
- `recipient_id` (INTEGER, 64-bit)
- `timestamp` (INTEGER)
- `content` (TEXT)
- `content_type` (TEXT): `TEXT`, `IMAGE`, `AUDIO`, `FILE`, `SYSTEM_ALERT`
- `status` (TEXT): `QUEUED`, `SENDING`, `SENT`, `DELIVERED`, `READ`, `FAILED`
- `priority` (INTEGER): `0` (Bulk) to `3` (Emergency)
- `hop_count` (INTEGER)
- `ttl` (INTEGER)
- `attachment_id` (TEXT, Nullable)

### `peers`
Stores discovered mesh participants, link quality, and trust classification.
- `node_id` (INTEGER, Primary Key, 64-bit)
- `device_address` (TEXT)
- `display_name` (TEXT)
- `rssi` (INTEGER)
- `link_quality` (REAL)
- `hop_distance` (INTEGER)
- `direct_state` (TEXT): `DISCONNECTED`, `CONNECTING`, `CONNECTED`
- `trust_level` (TEXT): `UNVERIFIED`, `VERIFIED_SAS`, `BLOCKED`
- `battery_level` (INTEGER)
- `last_seen_timestamp` (INTEGER)

### `routes`
Stores dynamic multi-hop routing paths.
- `destination` (INTEGER, Primary Key, 64-bit)
- `next_hop` (INTEGER, 64-bit)
- `cost` (REAL)
- `hop_count` (INTEGER)
- `sequence_number` (INTEGER)
- `expire_timestamp` (INTEGER)

### `pending_queue`
Stores delay-tolerant (DTN) packets awaiting peer contact.
- `id` (INTEGER, Primary Key Autoincrement)
- `destination` (INTEGER, 64-bit)
- `payload` (TEXT): Base64-encoded packet
- `priority` (INTEGER)
- `attempt_count` (INTEGER)
- `timestamp` (INTEGER)

### `session_keys`
Stores Double Ratchet cryptographic states and cached out-of-order keys.
- `peer_node_id` (INTEGER, Primary Key, 64-bit)
- `root_key` (TEXT): Base64-encoded 32-byte secret
- `send_chain_key` (TEXT)
- `recv_chain_key` (TEXT)
- `send_ratchet_pub` (TEXT)
- `recv_ratchet_pub` (TEXT)
- `send_sequence` (INTEGER)
- `recv_sequence` (INTEGER)
- `cached_skipped_keys_json` (TEXT)
- `updated_at` (INTEGER)

### `audit_logs`
Stores tamper-evident forensic security events.
- `id` (INTEGER, Primary Key Autoincrement)
- `event_type` (TEXT)
- `node_id` (INTEGER, Nullable)
- `details` (TEXT)
- `timestamp` (INTEGER)
