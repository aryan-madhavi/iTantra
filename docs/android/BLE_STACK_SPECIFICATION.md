# AstraMesh Bluetooth Low Energy (BLE) Stack Specification

AstraMesh operates directly on Android's BLE APIs without proprietary or external vendor libraries.

---

## 1. Dual-Role Architecture

Every AstraMesh node acts simultaneously as:
1. **GATT Peripheral (`GattServerManager`)**:
   - Advertises presence and service UUID (`0000A57A-0000-1000-8000-00805F9B34FB`).
   - Hosts the primary communication service with Rx and Tx characteristics.
   - Listens for write requests and delivers slices to the reassembly pipeline.
2. **GATT Central (`GattClientManager`)**:
   - Continuously scans for neighboring AstraMesh nodes.
   - Initiates outbound connections upon discovery.
   - Negotiates maximum ATT MTU (requesting 517 bytes).
   - Writes sliced data packets to peripheral characteristics.

---

## 2. UUID Allocation

| Identifier | UUID | Description |
|---|---|---|
| `ASTRA_SERVICE_UUID` | `0000A57A-0000-1000-8000-00805F9B34FB` | Primary AstraMesh BLE Service |
| `RX_CHARACTERISTIC_UUID` | `0000A57B-0000-1000-8000-00805F9B34FB` | Write / Write Without Response (Inbound to node) |
| `TX_CHARACTERISTIC_UUID` | `0000A57C-0000-1000-8000-00805F9B34FB` | Notify / Read (Outbound from node) |

---

## 3. Connection Pool & Resource Management (`BleConnectionPool`)

To avoid hitting Android hardware radio connection limits (typically 4–7 simultaneous BLE connections on modern chipsets):
- Up to `MAX_CONCURRENT_CONNECTIONS = 7` active GATT handles are maintained.
- Connections that remain idle with no packet activity for $> 60\text{ seconds}$ are gracefully disconnected to liberate radio slots for undiscovered peers (`evictIdleConnections`).
- Thread-safe tracking of negotiated MTU, device hardware addresses, and connection states.

---

## 4. Power & Duty Cycle Adaptation

To ensure battery longevity while running 24/7 background mesh operations:
- **Foreground / Active Mode**:
  - Scanning: `SCAN_MODE_LOW_LATENCY`
  - Advertising: `ADVERTISE_MODE_BALANCED`
- **Background / Idle Mode**:
  - Scanning: `SCAN_MODE_LOW_POWER` with hardware batching filters.
  - Advertising: `ADVERTISE_MODE_LOW_POWER`
- **WakeLock Management (`PowerOptimizationCoordinator`)**:
  - Partial WakeLock is held strictly during active packet transmit bursts, automatically released after a 5-second safety timeout.
  - Whitelisting integration with Android Doze / Battery Optimization.
