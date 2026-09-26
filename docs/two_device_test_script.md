# iTantra Two-Device Live Test Script
**Purpose:** a precise, numbered sequence to follow while `monitor_two_devices.sh` is running, so one continuous monitoring session cleanly captures every scenario: translation ON/OFF, same/different language, mid-session language switching, cold-load vs warm-load translation latency, idle memory return, and background/foreground behavior.

**Before you start:**
1. Run `adb devices` and note both serials.
2. Launch the monitor from your host machine:
   ```bash
   ./scripts/monitor_two_devices.sh -a <SENDER_SERIAL> -b <RECEIVER_SERIAL> -l "full-scenario-run-1"
   ```
3. Wait for the "Both device monitors running" confirmation before touching either phone.
4. Keep this checklist open and **tick off each phase as you complete it** — note the approximate wall-clock time or a rough elapsed-seconds estimate next to each phase if you can, as a sanity-check cross-reference against the CSV later (not required, but helpful if anything looks odd in the chart afterward).

**Device roles for this script:** "Device A" = sender, "Device B" = receiver — matching the `-a`/`-b` flags above. Swap roles and repeat the whole script a second time later if you want sender/receiver-reversed data too (optional, only if time allows).

---

## Phase 0 — Baseline idle (captures true cold-start memory floor)
- [ ] Ensure both apps are freshly launched (not already warm from earlier testing today — force-stop and relaunch both if unsure) and sitting on the main screen, doing nothing.
- [ ] **Wait 15 seconds** without touching anything. This gives you a clean "just launched, nothing loaded yet" baseline point at the very start of your chart.

## Phase 1 — Same language, translation ON (should be a no-op path)
- [ ] On both devices, set language to **Hindi**.
- [ ] Ensure translation toggle is **ON** on Device B (receiver).
- [ ] On Device A, send a short voice message in Hindi (a few words) via PTT to Device B.
- [ ] Wait for it to fully play on Device B.
- [ ] **Pause 5 seconds.**
- **What this proves:** same-language messages should never trigger NLLB load at all, even with translation ON — confirms the "skip translation when languages match" logic from earlier fix rounds.

## Phase 2 — Switch Device B to a different language (mid-session language switch)
- [ ] On Device B, open the language selector and switch to **Marathi**.
- [ ] **Pause 5 seconds** (lets any `LANGUAGE_CHANGE` event settle before the next action).
- **What this proves:** the `[UI_EVENT] LANGUAGE_CHANGE` marker fires correctly, and — per your fixed lazy-loading behavior — this alone should NOT yet trigger a TTS model swap (that only happens on next actual playback, per the timing note your logging already documents).

## Phase 3 — Different language, translation ON, COLD load (first translation of this session)
- [ ] On Device A (still Hindi), send a voice message via PTT to Device B (now Marathi), translation still ON.
- [ ] **Note the approximate time you release the PTT button** (rough wall-clock, for your own cross-reference).
- [ ] Wait for the full response — this should be your **slow, cold-load case (~8+ seconds)** since NLLB hasn't been loaded yet this session.
- [ ] Confirm audio plays correctly in Marathi.
- [ ] **Pause 5 seconds.**

## Phase 4 — Different language, translation ON, WARM load (repeat immediately)
- [ ] Send **2-3 more short voice messages** from Device A to Device B, same Hindi→Marathi pair, translation still ON, spaced a few seconds apart.
- [ ] These should each be the **faster, warm-load case (~3+ seconds)**, since NLLB should still be resident in memory from Phase 3 (well within your ~60s idle timeout).
- [ ] **Pause 5 seconds after the last one.**
- **What this proves:** the cold-vs-warm latency difference you described — this phase's `TRANSLATION_START`→`TRANSLATION_END` durations should be visibly shorter than Phase 3's.

## Phase 5 — Translation OFF, different language (regression check for the distortion-bug fix)
- [ ] Toggle translation **OFF** on Device B.
- [ ] Send another voice message from Device A (Hindi) to Device B (still set to Marathi).
- [ ] **Critical check:** confirm the audio plays back in clear, undistorted **Hindi** (the original language) — NOT distorted, NOT in a Marathi-model-mangled version. This is the live confirmation of tonight's translation-toggle/TTS-routing fix.
- [ ] Confirm the message popup shows "HI · ORIGINAL" (or equivalent), not a false translation pair.
- [ ] **Pause 5 seconds.**

## Phase 6 — Translation back ON (does NLLB reload from scratch, or was it still warm?)
- [ ] Toggle translation **ON** again on Device B.
- [ ] Send one more voice message, Hindi→Marathi.
- [ ] Note whether this feels closer to the cold (~8s) or warm (~3s) case — this is genuinely informative either way: if NLLB was disposed during the OFF period (Phase 5) due to the idle timeout, this will be cold again; if the OFF toggle doesn't actually unload NLLB (it was still "on" in memory, just not being invoked), this might still be warm. Both are useful data points.
- [ ] **Pause 5 seconds.**

## Phase 7 — Extended idle (memory return to baseline)
- [ ] Stop interacting with both devices entirely.
- [ ] **Wait 90 seconds** (comfortably clears your documented 60s idle-unload timeout with margin).
- [ ] Do not touch either phone during this window.
- **What this proves:** PSS should visibly drop back down toward baseline on both devices as STT/TTS/NLLB all release per their idle timeouts — this is your "lowest possible memory consumption" evidence.

## Phase 8 — Background the app (distinct from foreground-idle)
- [ ] On BOTH devices, press the home button (do not force-stop, do not swipe away — just background it normally, the way a real user would while the mesh service keeps running).
- [ ] **Wait 30 seconds** backgrounded.
- **What this proves:** shows whether backgrounding behaves differently from foreground-idle (e.g., does Android's own background memory trimming kick in additionally, on top of your app's own lazy-unload logic?).

## Phase 9 — Return to foreground
- [ ] Bring both apps back to the foreground (tap the app icon/recent-apps, not a fresh relaunch).
- [ ] **Wait 10 seconds**, confirm both apps resumed cleanly (peer list still shows the other device, no crash, no stuck loading state).
- **What this proves:** clean resume behavior — no lingering bad state from backgrounding.

## Phase 10 — UI Closed / Service Still Running
- [ ] On BOTH devices, open the Recent Apps view and swipe away the iTantra app to completely remove the UI (this is the `onDestroy()` phase).
- [ ] **Wait 30 seconds**.
- **What this proves:** `AstraMeshForegroundService` should remain active in the foreground and maintain the process. The script will capture if the PID survives the UI being completely closed.

## Phase 11 — Done
- [ ] Return to your host machine's terminal and press **Ctrl+C** to stop the monitor.
- [ ] Confirm the script prints the "Run complete" message listing all 5 output files under your run directory.

---

## After the run
Send me (or keep handy to send later) the full run folder — specifically:
- `sender_resource.csv`, `sender_events.csv`
- `receiver_resource.csv`, `receiver_events.csv`
- `run_info.txt`

I'll use these to generate the combined chart (both devices' memory over time, phase-labeled) and the cold-vs-warm latency comparison, and to read off a simple pass/fail table for each scenario directly from the `[UI_EVENT]`/`[MODEL_LIFECYCLE]` sequence.

**Total estimated time for this script: roughly 4-5 minutes of active steps** (excluding the 90s + 30s wait phases, which add about 2 more minutes) — plan for around 6-7 minutes total if followed at a normal pace.
