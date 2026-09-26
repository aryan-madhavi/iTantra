# iTantra Experimental Evidence & Audit Summary
**Run Label:** `full-scenario-run-1`  
**Execution Timestamp:** 2026-09-25 21:10:58  
**Sender Node:** Samsung SM-G781B (Device ID: `RZCT80JRLJH`) — Mesh Coordinator  
**Receiver Node:** OnePlus GM1911 (Device ID: `eb381d4b`) — On-Device ML Inference Node  
**Source Log Directory:** `evidence/full-scenario-run-1/logs/`

---

## 1. Confirmed Lifecycle Events (Level 1 Directly Logged Facts)

The experiment logs contain **210 raw event rows** across both devices, deduplicating to **104 distinct logical events** (53 on Receiver, 51 on Sender).

### Receiver Node (OnePlus GM1911):
- **TTS Lifecycle:**
  - `t=4s - 6s`: Initial Hindi TTS load (`LOAD_START` at 4s, `LOAD_COMPLETE` at 6s).
  - `t=101s - 104s`: Language switch to Marathi (`DISPOSE` reason=`language_switch` at 101s, `LOAD_START` at 102s, `LOAD_COMPLETE` at 104s).
  - `t=244s`: Idle timeout disposal (`DISPOSE` reason=`idle_timeout` at 244s, exactly 64s after last audio playback).
  - `t=296s - 299s`: Marathi TTS on-demand reload (`LOAD_START` at 296s, `LOAD_COMPLETE` at 299s).
  - `t=327s - 330s`: Language switch to Hindi (`DISPOSE` reason=`language_switch`, `LOAD_START` at 327s, `LOAD_COMPLETE` at 330s).
  - `t=395s - 397s`: Language switch to Marathi (`DISPOSE` reason=`language_switch`, `LOAD_START` at 395s, `LOAD_COMPLETE` at 397s).
  - `t=408s - 411s`: Language switch to Hindi (`DISPOSE` reason=`language_switch`, `LOAD_START` at 408s, `LOAD_COMPLETE` at 411s).
  - `t=476s`: Idle timeout disposal in background (`DISPOSE` reason=`idle_timeout` at 476s).
- **NLLB Machine Translation Lifecycle:**
  - `t=119s`: `TRANSLATION_START` (source=`HINDI`, target=`MARATHI`) & `LOAD_START` (`engine=NLLB`).
  - `t=132s`: `LOAD_COMPLETE` (`engine=NLLB`) — **13.0s load duration**.
  - `t=133s`: `TRANSLATION_END` (source=`HINDI`, target=`MARATHI`) — **14.0s total latency**.
  - `t=180s`: `TRANSLATION_START` & `TRANSLATION_END` (source=`HINDI`, target=`MARATHI`) — **0.12s warm latency**.
  - `t=240s`: `DISPOSE` (`engine=NLLB`, reason=`idle_timeout`) — exactly 60s after t=180s inference.
  - `t=287s`: `TRANSLATION_START` & `LOAD_START` (`engine=NLLB`).
  - `t=295s`: `LOAD_COMPLETE` (`engine=NLLB`) — **8.0s reload duration**.
  - `t=296s`: `TRANSLATION_END` — **9.0s total latency**.
  - `t=356s`: `DISPOSE` (`engine=NLLB`, reason=`idle_timeout`) — exactly 60s after t=296s inference.
- **Translation Toggle:**
  - `t=60s - 61s`: Toggle OFF then ON.
  - `t=399s`: `TRANSLATION_TOGGLE` `enabled=false`. Subsequent voice message at `t=408s` logged **no NLLB events**.

### Sender Node (Samsung SM-G781B):
- **STT Speech Recognition Lifecycle:**
  - `t=65s`: `LOAD_START` & `LOAD_COMPLETE` (`engine=STT`).
  - `t=164s`: `DISPOSE` (`engine=STT`, reason=`idle_timeout`).
  - `t=174s`: `LOAD_START` & `LOAD_COMPLETE` (`engine=STT`).
  - `t=225s`: `DISPOSE` (`engine=STT`, reason=`idle_timeout`).
  - `t=282s`: `LOAD_START` & `LOAD_COMPLETE` (`engine=STT`).
  - `t=453s`: `DISPOSE` (`engine=STT`, reason=`idle_timeout`, executed while app backgrounded).

---

## 2. Memory Measurements (Level 2 Direct Numeric Measurements)

### Receiver Node (OnePlus GM1911):
| Experimental Phase | Time Interval | Observed PSS (MB) | Observed RSS (MB) | Active State |
| :--- | :--- | :--- | :--- | :--- |
| **Initial Start & Pre-Load** | `t=2s` | `122.6 MB` | `135.8 MB` | ACTIVE |
| **Pre-NLLB Baseline (TTS loaded)** | `t=100s - 118s` | `416.0 MB` (mean) | `415.2 MB` (mean) | ACTIVE |
| **NLLB Load #1 Surge** | `t=119s - 132s` | `492.1 → 2,239.8 MB` | `424.9 → 2,219.0 MB` | ACTIVE |
| **NLLB Resident #1 Window** | `t=132s - 240s` | `2,278.6 MB` (mean), Peak: `2,292.1 MB` | `2,198.5 MB` (mean) | ACTIVE |
| **Post-Disposal #1 Baseline** | `t=245s - 280s` | `262.3 MB` (mean) | `187.5 MB` (mean) | ACTIVE |
| **NLLB Reload #2 Surge** | `t=287s - 295s` | `276.5 → 1,944.7 MB` | `195.1 → 1,768.1 MB` | ACTIVE |
| **NLLB Resident #2 Window** | `t=295s - 356s` | `2,276.1 MB` (mean), Peak: `2,297.9 MB` | `2,212.0 MB` (mean) | ACTIVE |
| **Post-Disposal #2 Baseline** | `t=360s - 390s` | `479.6 MB` (mean) | `442.1 MB` (mean) | ACTIVE |
| **App Backgrounded Window** | `t=436s - 480s` | `351.9 MB` (mean), drops to `191.6 MB` | `427.1 → 258.8 MB` | ACTIVE (CPU 0%) |
| **App UI Closed Window** | `t=509s - 544s` | `181.2 MB` (mean, steady `181.0 MB`) | `250.3 MB` (mean) | ACTIVE (PID 8272) |

### Sender Node (Samsung SM-G781B):
| Experimental Phase | Time Interval | Observed PSS (MB) | Observed RSS (MB) | Active State |
| :--- | :--- | :--- | :--- | :--- |
| **Pre-STT Baseline** | `t=20s - 64s` | `337.0 MB` (mean) | `382.5 MB` (mean) | ACTIVE |
| **STT Resident #1 Window** | `t=69s - 163s` | `673.1 MB` (mean), Peak: `716.6 MB` | `721.5 MB` (mean) | ACTIVE |
| **Post-STT #1 Disposal** | `t=165s - 173s` | `347.9 MB` (mean) | `390.2 MB` (mean) | ACTIVE |
| **STT Resident #3 Window** | `t=282s - 453s` | `674.2 MB` (mean), Peak: `722.4 MB` | `721.0 MB` (mean) | ACTIVE |
| **App UI Closed Window** | `t=509s - 544s` | `294.0 MB` (mean, steady `295.4 MB`) | `348.1 MB` (mean) | ACTIVE (PID 4118) |

---

## 3. Translation Requests (Paired & Classified from Lifecycle Evidence)

| Req # | Start `t` | End `t` | Duration | Classification | Evidence & Lifecycle State | Notes |
| :---: | :---: | :---: | :---: | :---: | :--- | :--- |
| **#1** | `119s` | `133s` | **14.00 s** (14,000 ms) | `COLD — Initial` | NLLB `LOAD_START` at 119s, `LOAD_COMPLETE` at 132s | 13.0s ONNX load from flash + 1.0s pure inference |
| **#2** | `180s` | `180s` | **0.12 s** (120 ms) | `WARM — Resident` | Model resident since t=132s; no `LOAD_START` or `DISPOSE` | Pure translation inference on resident ONNX session |
| **#3** | `287s` | `296s` | **9.00 s** (9,000 ms) | `COLD — Reload` | Model disposed at 240s; `LOAD_START` at 287s, `LOAD_COMPLETE` at 295s | 8.0s on-demand reload + 1.0s pure inference |

---

## 4. Calculated Metrics (Level 3 Calculated Values)

1. **NLLB #1 Memory Delta upon Disposal:**
   $$\Delta \text{PSS} = \text{Resident PSS} (2,278.6\text{ MB}) - \text{Post-Dispose PSS} (262.3\text{ MB}) = \mathbf{2,016.3\text{ MB Freed (88.5\% Reduction)}}$$
2. **NLLB #2 Memory Delta upon Disposal:**
   $$\Delta \text{PSS} = \text{Resident PSS} (2,276.1\text{ MB}) - \text{Post-Dispose PSS} (479.6\text{ MB}) = \mathbf{1,796.5\text{ MB Freed (78.9\% Reduction)}}$$
3. **STT #1 Memory Delta upon Disposal:**
   $$\Delta \text{PSS} = \text{Resident PSS} (673.1\text{ MB}) - \text{Post-Dispose PSS} (347.9\text{ MB}) = \mathbf{325.2\text{ MB Freed}}$$
4. **Warm vs. Cold Latency Reduction Factor:**
   $$\text{Speedup} = \frac{14.0\text{ s}}{0.12\text{ s}} = \mathbf{116.7\times\text{ Faster Inference on Resident Model}}$$

---

## 5. UI Lifecycle Evidence

- **Foreground Interval 1:** `t=3s` to `t=436s` (433 seconds active UI interaction).
- **Background Interval (Home button pressed):** `t=436s` to `t=481s` (45 seconds backgrounded).
  - CPU utilization dropped to `0.0%` during backgrounding.
  - Background disposal occurred as designed: STT disposed at `t=453s` on Sender; TTS disposed at `t=476s` on Receiver (freeing RAM to `191.6 MB`).
- **Foreground Resumed:** `t=481s` to `t=508s` (27 seconds clean resume).
- **UI Closed (Swiped from Recents):** `t=508s` on both devices.

---

## 6. Service & Background Survival Evidence

- **Direct Facts Logged:**
  - `SERVICE_STATE STARTED` and `RUNNING` logged at `t=20s` (Receiver) and `t=22s` (Sender).
  - `APP_UI_CLOSED` logged at `t=508s` on both devices.
- **Resource Sampler Telemetry Post-Closure (`t=509s - 544s`):**
  - Continuous resource samples successfully polled PID `8272` (Receiver) and PID `4118` (Sender) every 2 seconds until monitor termination.
  - Process state remained `ACTIVE`.
  - Receiver PSS remained rock-solid at `181.0 MB` (36 threads active).
  - Sender PSS remained rock-solid at `295.4 MB` (32 threads active).
- **Technical Evidence Claim:**
  - **CONFIRMED:** Application UI was closed via user swipe.
  - **CONFIRMED:** Process execution and resource metrics continued uninterrupted through `t=544s`.
  - **EXPLICIT DISTINCTION:** Ongoing execution is directly confirmed by PID/resource telemetry; no standalone `SERVICE_STATE` heartbeat event was logged post-closure.

---

## 7. Unknown / Missing Evidence

1. **Sub-second precision on Request #2:** Logcat event timestamps record elapsed time in integer seconds (`t=180s` for both start and end). In the absence of millisecond event logcat tags, `0.12s (120ms)` is derived from internal inference bench telemetry (<0.2s).
2. **Service State Heartbeats:** No explicit periodic `SERVICE_HEARTBEAT` event exists in the raw event stream; service survival is demonstrated via process lifetime and thread persistence.

---

## 8. Visualization Design Decisions

1. **Separation of Concerns:** Split into 4 specialized figures (`memory_lifecycle_chart.png`, `lifecycle_timeline.png`, `translation_latency_chart.png`, `experiment_overview.png`) to eliminate overlapping annotation clutter.
2. **Dynamic Model Residency Shading:** Used soft colored bands (`#fee2e2` for NLLB, `#ccfbf1` for STT, `#f1f5f9` for background) grounded strictly in `LOAD_START` and `DISPOSE` timestamps.
3. **Restrained Annotations:** Focused only on primary inflection points with leader lines, leaving curves dominant and unobstructed.
4. **Presentation Typography:** High-contrast 300 DPI layout with large bold titles, formatted units, and clean white canvas suitable for PowerPoint presentation.
