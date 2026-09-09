# AstraMesh Routing & Loop Detection Protocol

AstraMesh implements a hybrid proactive/reactive decentralized ad-hoc routing protocol optimized for high-churn, intermittent BLE mesh topologies.

---

## 1. Composite Route Metric Calculus

Rather than relying purely on hop counts (which leads to selection of fragile, high-attenuation long-distance links), AstraMesh computes a composite link cost:

$$\text{Cost} = (w_h \times \text{HopCount}) + (w_q \times \text{LqiPenalty}) + (w_c \times \text{CongestionPenalty}) + (w_b \times \text{BatteryPenalty})$$

### Default Metric Weights (`AstraNetworkConfig`)
- $w_h = 1.0$ (Hop Count Weight)
- $w_q = 2.0$ (Link Quality Indicator Weight)
- $w_c = 1.5$ (Interface Queue Congestion Weight)
- $w_b = 1.2$ (Battery Level Conservation Weight)

### Component Formulas
1. **LQI Penalty**:
   $$\text{LqiPenalty} = \max\left(0.0, \frac{-40 - \text{RSSI}}{50.0}\right)$$
   Links with RSSI $\ge -40\text{ dBm}$ incur zero penalty; RSSI $\le -90\text{ dBm}$ incurs maximum penalty ($1.0$).
2. **Battery Penalty**:
   Nodes with low battery or running without AC power advertise lower willingness to relay packets:
   $$\text{BatteryPenalty} = \max\left(0.0, 1.0 - \frac{\text{BatteryLevel}}{100.0}\right) \times (\text{if charging } 0.2 \text{ else } 1.0)$$

---

## 2. Zero-Allocation Bloom Filter Loop Prevention

To prevent broadcast radiation storms and circular forwarding loops across mesh cycles, every packet embeds a **32-bit compact Bloom filter**:

### Algorithm
For each node with 64-bit `NodeId`, two independent 5-bit bit positions are computed:
$$h_1(\text{NodeId}) = (\text{NodeId} \oplus (\text{NodeId} \gg 32)) \pmod{32}$$
$$h_2(\text{NodeId}) = ((\text{NodeId} \times \text{0x9E3779B97F4A7C15}) \oplus \dots) \pmod{32}$$

- **Check Loop**: If $(F \text{ AND } (1 \ll h_1) \neq 0)$ AND $(F \text{ AND } (1 \ll h_2) \neq 0)$, the packet has likely already traversed this node and is immediately dropped.
- **Record Node**: When relaying, $F_{\text{out}} = F_{\text{in}} \mid (1 \ll h_1) \mid (1 \ll h_2)$.

---

## 3. Shortest Path Computation & Routing Table

The `RoutingTable` manages soft-state routes with TTL-based expiration:
- **Freshness Dominance**: Route advertisements with higher monotonic sequence numbers always overwrite older entries regardless of cost.
- **Cost Minimization**: If sequence numbers match, the lower composite cost route is retained.
- **Dijkstra Engine (`ShortestPathFinder`)**: Computes optimal multi-hop paths across the local node's known neighbor graph.
- **Periodic Pruning**: Expired routes are evicted periodically by `DatabasePruneWorker` and runtime table sweeps.

---

## 4. Delay-Tolerant Store-and-Forward (DTN)

When no active route exists to a destination:
1. Unicast packets are stored in the local `StoreAndForwardQueue` (persisted in SQLite via `pending_queue`).
2. Packets remain queued with exponential retry backoff and TTL expiration.
3. When `BleScannerManager` detects a peer advertisement matching the destination or a valid next-hop relay, the queue is automatically flushed and transmitted over the newly formed link.
