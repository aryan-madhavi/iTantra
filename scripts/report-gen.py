#!/usr/bin/env python3
"""
scripts/generate_dual_device_report.py

Consumes the 4 CSV files produced by monitor_two_devices.sh (sender_resource.csv,
sender_events.csv, receiver_resource.csv, receiver_events.csv) and produces:

  1. A combined memory-over-time chart (both devices on one time axis, phase
     annotations if a phase log is provided) — for the SIH deck / submission.
  2. A cold-vs-warm translation latency comparison (bar chart + printed table),
     computed directly from TRANSLATION_START/TRANSLATION_END event pairs.
  3. A plain-text scenario summary reconstructed from the event sequence —
     a best-effort pass/fail read of what actually happened, for your own
     sanity-check against the two_device_test_script.md checklist.

Usage:
    python3 generate_dual_device_report.py <run_dir> [--output-dir <dir>]

Where <run_dir> is the folder monitor_two_devices.sh created (e.g. logs/full-scenario-run-1/),
containing sender_resource.csv, sender_events.csv, receiver_resource.csv, receiver_events.csv.

Requires: matplotlib, pandas (pip install matplotlib pandas --break-system-packages)
"""

import argparse
import re
import sys
from pathlib import Path

try:
    import pandas as pd
    import matplotlib.pyplot as plt
    import matplotlib.ticker as mticker
except ImportError:
    print("Missing dependencies. Install with:")
    print("  pip install matplotlib pandas --break-system-packages")
    sys.exit(1)


def load_resource_csv(path: Path) -> pd.DataFrame:
    df = pd.read_csv(path, comment="#")
    for col in ["elapsed_s", "pss_mb", "rss_mb", "cpu_pct", "threads", "battery_pct", "battery_temp_c"]:
        if col in df.columns:
            df[col] = pd.to_numeric(df[col], errors="coerce")
    return df


def load_events_csv(path: Path) -> pd.DataFrame:
    df = pd.read_csv(path, comment="#")
    df["elapsed_s"] = pd.to_numeric(df["elapsed_s"], errors="coerce")

    # De-duplicate: if an orphaned monitor process from a prior run wasn't fully
    # killed (a real bug found and fixed in monitor_two_devices.sh's cleanup trap,
    # but kept here as a defensive safeguard in the analysis step too), the same
    # real event can appear multiple times with DIFFERENT elapsed_s values sharing
    # the same wall-clock `time`, category, event_type, and detail — since two
    # separate monitor processes, started at different times, each compute their
    # own elapsed_s independently for the same underlying logcat line. Detect this:
    # group by everything except elapsed_s, and if a group has more than one
    # distinct elapsed_s value, keep only the SMALLEST one (the earliest-started,
    # and therefore correct, monitor session for a properly-conducted single test
    # run) and drop the rest, warning the user so they know this happened.
    dedup_keys = ["time", "category", "event_type", "detail"]
    before_count = len(df)
    grouped = df.groupby(dedup_keys)["elapsed_s"].nunique()
    conflicting = grouped[grouped > 1]
    if not conflicting.empty:
        print(f"⚠️  WARNING: {path.name} contains {len(conflicting)} events each logged with multiple "
              f"different elapsed_s values (likely an orphaned monitor process from a prior run wasn't "
              f"fully terminated before this run started). Keeping only the smallest elapsed_s per event "
              f"(the correct, earliest-started session) and discarding the rest.")
        df = df.sort_values("elapsed_s").drop_duplicates(subset=dedup_keys, keep="first")
        # Also drop any remaining exact full-row duplicates (same elapsed_s too)
        df = df.drop_duplicates()
        print(f"    {before_count} rows -> {len(df)} rows after de-duplication.")

    df = df.sort_values("elapsed_s").reset_index(drop=True)
    return df


def parse_detail_kv(detail: str) -> dict:
    """Parse a 'key=value key2=value2' style detail string into a dict. Best-effort."""
    out = {}
    if not isinstance(detail, str):
        return out
    for match in re.finditer(r"(\w+)=([^\s]+(?:\s[^\s=]+)*?)(?=\s+\w+=|$)", detail):
        out[match.group(1)] = match.group(2)
    return out


def compute_translation_latencies(events_df: pd.DataFrame) -> pd.DataFrame:
    """
    Pairs TRANSLATION_START with the next TRANSLATION_END for the same engine,
    and classifies each as 'cold' (first translation after a LOAD_COMPLETE for
    NLLB) or 'warm' (subsequent translation before the next NLLB DISPOSE).

    Returns an empty DataFrame (with the expected columns) if no MODEL_LIFECYCLE
    events are present at all -- this can legitimately happen if translation was
    never actually triggered during the test run, but can ALSO indicate the
    logcat capture isn't matching [MODEL_LIFECYCLE] lines (e.g. a grep pattern
    mismatch, or the app build being monitored predates that logging being added).
    Either way, this function must not crash -- it reports what it can and lets
    the caller decide whether the absence is expected or worth investigating.
    """
    empty_result = pd.DataFrame(columns=["start_s", "end_s", "duration_ms", "classification", "detail"])

    if "event_type" not in events_df.columns or events_df.empty:
        return empty_result

    has_any_lifecycle_events = (events_df["category"] == "MODEL_LIFECYCLE").any() if "category" in events_df.columns else False
    if not has_any_lifecycle_events:
        print("⚠️  NOTE: no [MODEL_LIFECYCLE] events found in this events file at all — "
              "translation cold/warm latency cannot be computed. This could mean translation "
              "was never actually triggered during this test run, OR that [MODEL_LIFECYCLE] "
              "logcat lines aren't being captured correctly (e.g. a grep pattern mismatch in "
              "the monitor script, or this app build predates that logging). Worth checking "
              "directly with 'adb logcat | grep MODEL_LIFECYCLE' during a live translation "
              "event to confirm the logging actually fires and is captured.")
        return empty_result

    starts = events_df[events_df["event_type"] == "TRANSLATION_START"].sort_values("elapsed_s").reset_index(drop=True)
    ends = events_df[events_df["event_type"] == "TRANSLATION_END"].sort_values("elapsed_s").reset_index(drop=True)

    if starts.empty or ends.empty:
        print("⚠️  NOTE: [MODEL_LIFECYCLE] events exist, but no TRANSLATION_START/TRANSLATION_END "
              "pairs were found — translation may not have been triggered, or these specific "
              "event types aren't firing. Other lifecycle events (LOAD_START/LOAD_COMPLETE/DISPOSE) "
              "may still be present and useful even without translation timing.")
        return empty_result

    loads = events_df[events_df["event_type"] == "LOAD_COMPLETE"].copy()
    loads["detail_parsed"] = loads["detail"].apply(parse_detail_kv)
    nllb_load_times = sorted(loads[loads["detail_parsed"].apply(lambda d: d.get("engine") == "NLLB")]["elapsed_s"].tolist())

    disposes = events_df[events_df["event_type"] == "DISPOSE"].copy()
    disposes["detail_parsed"] = disposes["detail"].apply(parse_detail_kv)
    nllb_dispose_times = sorted(disposes[disposes["detail_parsed"].apply(lambda d: d.get("engine") == "NLLB")]["elapsed_s"].tolist())

    # First pass: pair each START with the next unused END (sequential, non-destructive
    # to the lookups used for classification — this was a real bug in an earlier version
    # of this function, where consuming `ends` rows during the loop corrupted the
    # "any prior translation since load" check for later iterations; fixed by doing
    # the pairing fully first, then classifying every pair afterward using the complete,
    # untouched `ends` timeline).
    pairs = []
    used_end_indices = set()
    for _, start_row in starts.iterrows():
        candidates = ends[(ends["elapsed_s"] >= start_row["elapsed_s"]) & (~ends.index.isin(used_end_indices))]
        if candidates.empty:
            continue
        end_idx = candidates.index[0]
        used_end_indices.add(end_idx)
        end_row = ends.loc[end_idx]
        pairs.append((start_row, end_row))

    rows = []
    for start_row, end_row in pairs:
        detail = parse_detail_kv(end_row["detail"])
        duration_ms = detail.get("duration_ms")
        try:
            duration_ms = float(duration_ms) if duration_ms else (end_row["elapsed_s"] - start_row["elapsed_s"]) * 1000
        except ValueError:
            duration_ms = (end_row["elapsed_s"] - start_row["elapsed_s"]) * 1000

        # Classify: find the most recent NLLB load-or-dispose event strictly before
        # this translation's start. If the most recent such event is a LOAD and no
        # OTHER translation's END has occurred between that load and this start,
        # this is the cold (first-use) translation for that load cycle. Otherwise warm.
        last_load_t = max([t for t in nllb_load_times if t <= start_row["elapsed_s"]], default=None)
        last_dispose_t = max([t for t in nllb_dispose_times if t <= start_row["elapsed_s"]], default=None)

        if last_load_t is None:
            classification = "unknown (no prior LOAD_COMPLETE found)"
        elif last_dispose_t is not None and last_dispose_t > last_load_t:
            # Disposed after the last load and before this start with no re-load in
            # between shouldn't normally happen (can't translate on a disposed model),
            # but if the data shows it, flag rather than mis-classify silently.
            classification = "unknown (dispose after load, before this translation)"
        else:
            # Was this translation's END the FIRST end to occur after last_load_t?
            other_ends_since_load = [
                e_row["elapsed_s"] for _, e_row in ends.iterrows()
                if last_load_t < e_row["elapsed_s"] < end_row["elapsed_s"]
            ]
            classification = "cold" if not other_ends_since_load else "warm"

        rows.append({
            "start_s": start_row["elapsed_s"],
            "end_s": end_row["elapsed_s"],
            "duration_ms": duration_ms,
            "classification": classification,
            "detail": end_row["detail"],
        })

    return pd.DataFrame(rows)


def plot_combined_memory(sender_df: pd.DataFrame, receiver_df: pd.DataFrame, output_path: Path,
                          sender_events: pd.DataFrame = None, receiver_events: pd.DataFrame = None,
                          inferred_bands: list = None, title_suffix: str = ""):
    """
    Plots both devices' PSS over time, with vertical event markers overlaid from
    each device's de-duplicated events CSV, plus optional shaded "inferred" bands
    for memory-curve features that look meaningful but have NO corresponding
    logged event to confirm them.

    IMPORTANT — event marker honesty: only events that actually exist as rows in
    the events CSVs are drawn as solid, labeled vertical lines. `inferred_bands`
    (a list of (start_s, end_s, label) tuples) are drawn as clearly distinct
    shaded/hatched regions with an explicit "(inferred — not directly logged)"
    caption, so a viewer can never mistake a guess for confirmed data. This
    distinction matters: a real run was found where [MODEL_LIFECYCLE] events
    (which would confirm e.g. a model DISPOSE causing a memory drop) were not
    captured at all, even though [UI_EVENT] events were -- the memory curve
    still shows the drop, but without a logged event to point to, it must be
    presented as an inference, not a fact.
    """
    fig, ax = plt.subplots(figsize=(14, 7), dpi=150)

    ax.plot(sender_df["elapsed_s"], sender_df["pss_mb"], color="#0d9488", linewidth=2, label="Sender PSS", zorder=3)
    ax.plot(receiver_df["elapsed_s"], receiver_df["pss_mb"], color="#dc2626", linewidth=2, label="Receiver PSS", zorder=3)

    ax.set_xlabel("Elapsed Time (seconds)", fontsize=11)
    ax.set_ylabel("Memory (MB)", fontsize=11)
    ax.set_title(f"On-Device Memory Footprint — Sender vs Receiver{title_suffix}", fontsize=14, fontweight="bold", color="#0f172a", pad=45)
    ax.grid(True, linestyle=":", linewidth=0.6, alpha=0.6)
    ax.spines["top"].set_visible(False)
    ax.spines["right"].set_visible(False)
    ax.yaxis.set_major_formatter(mticker.FuncFormatter(lambda x, _: f"{int(x):,}"))

    y_max = max(sender_df["pss_mb"].max(skipna=True) or 0, receiver_df["pss_mb"].max(skipna=True) or 0)
    ax.set_ylim(bottom=0, top=y_max * 1.28)

    # Event type -> (color, short label). Kept curated rather than showing every
    # event type (a chart with a label for every single MIC_PRESS/MSG_SEND in a
    # long session becomes unreadable) — high-signal state-change events only.
    EVENT_STYLE = {
        "LANGUAGE_CHANGE": ("#7c3aed", "Lang"),
        "TRANSLATION_TOGGLE": ("#ea580c", "Translate"),
        "MSG_RECEIVE": ("#2563eb", "Recv"),
    }

    def draw_event_markers(events_df):
        if events_df is None or events_df.empty:
            return
        for event_type, (color, short_label) in EVENT_STYLE.items():
            subset = events_df[events_df["event_type"] == event_type]
            for _, row in subset.iterrows():
                x = row["elapsed_s"]
                ax.axvline(x=x, color=color, linestyle="--", linewidth=0.9, alpha=0.6, zorder=1)
                detail = str(row.get("detail", ""))
                extra = ""
                if event_type == "LANGUAGE_CHANGE":
                    m = re.search(r"lang=(\w+)", detail)
                    if m:
                        extra = f"\n{m.group(1)[:3]}"
                elif event_type == "TRANSLATION_TOGGLE":
                    extra = "\nON" if "true" in detail else "\nOFF"
                ax.annotate(
                    f"{short_label}{extra}",
                    xy=(x, y_max * 1.20),
                    fontsize=7, color=color, rotation=0,
                    ha="center", va="bottom", zorder=4,
                    annotation_clip=False,
                )

    draw_event_markers(receiver_events)

    # Inferred (unconfirmed) bands — distinct hatched shading + explicit caption
    if inferred_bands:
        for start_s, end_s, label in inferred_bands:
            ax.axvspan(start_s, end_s, color="#94a3b8", alpha=0.18, hatch="//", zorder=0)
            mid = (start_s + end_s) / 2
            ax.annotate(
                f"{label}\n(inferred — not directly logged)",
                xy=(mid, y_max * 1.04),
                fontsize=8, color="#475569", ha="center", va="bottom",
                style="italic", annotation_clip=False,
            )

    # Build legend combining line labels + a manual entry explaining the dashed
    # vertical lines / hatched band convention, since axvline/axvspan calls above
    # don't auto-register in the standard legend.
    from matplotlib.lines import Line2D
    from matplotlib.patches import Patch
    handles = [
        Line2D([0], [0], color="#0d9488", linewidth=2, label="Sender PSS"),
        Line2D([0], [0], color="#dc2626", linewidth=2, label="Receiver PSS"),
        Line2D([0], [0], color="#7c3aed", linestyle="--", linewidth=1, label="Language change"),
        Line2D([0], [0], color="#ea580c", linestyle="--", linewidth=1, label="Translation toggle"),
        Line2D([0], [0], color="#2563eb", linestyle="--", linewidth=1, label="Message received"),
    ]
    if inferred_bands:
        handles.append(Patch(facecolor="#94a3b8", alpha=0.3, hatch="//", label="Inferred period (unconfirmed)"))
    ax.legend(handles=handles, loc="upper right", frameon=False, fontsize=8, bbox_to_anchor=(1.0, 1.0))

    fig.tight_layout()
    fig.savefig(output_path, bbox_inches="tight")
    print(f"Combined memory chart saved to: {output_path}")


def plot_latency_comparison(latency_df: pd.DataFrame, output_path: Path):
    if latency_df.empty:
        print("No translation events found — skipping latency comparison chart.")
        return

    fig, ax = plt.subplots(figsize=(8, 5), dpi=150)

    colors = {"cold": "#dc2626", "warm": "#0d9488"}
    for i, row in latency_df.iterrows():
        color = colors.get(row["classification"], "#94a3b8")
        ax.bar(i, row["duration_ms"] / 1000.0, color=color, width=0.6)

    ax.set_xticks(range(len(latency_df)))
    ax.set_xticklabels([f"#{i+1}\n({row['classification']})" for i, row in latency_df.iterrows()], fontsize=9)
    ax.set_ylabel("Translation Latency (seconds)", fontsize=11)
    ax.set_title("Translation Latency: Cold Load vs Warm (Subsequent) Requests", fontsize=13, fontweight="bold", pad=15)
    ax.grid(True, axis="y", linestyle=":", linewidth=0.6, alpha=0.6)
    ax.spines["top"].set_visible(False)
    ax.spines["right"].set_visible(False)

    # simple legend via proxy patches
    from matplotlib.patches import Patch
    legend_elements = [Patch(facecolor=colors["cold"], label="Cold (model just loaded)"),
                        Patch(facecolor=colors["warm"], label="Warm (model already resident)")]
    ax.legend(handles=legend_elements, loc="upper right", frameon=False, fontsize=9)

    fig.tight_layout()
    fig.savefig(output_path, bbox_inches="tight")
    print(f"Latency comparison chart saved to: {output_path}")


def print_scenario_summary(sender_events: pd.DataFrame, receiver_events: pd.DataFrame, latency_df: pd.DataFrame):
    print("\n" + "=" * 70)
    print("SCENARIO SUMMARY (best-effort, cross-check against your test script)")
    print("=" * 70)

    print(f"\nSender events captured: {len(sender_events)}")
    print(f"Receiver events captured: {len(receiver_events)}")

    lang_changes = receiver_events[receiver_events["event_type"] == "LANGUAGE_CHANGE"]
    print(f"\nLanguage changes on receiver: {len(lang_changes)}")
    for _, row in lang_changes.iterrows():
        print(f"  t={row['elapsed_s']}s: {row['detail']}")

    toggles = receiver_events[receiver_events["event_type"] == "TRANSLATION_TOGGLE"]
    print(f"\nTranslation toggles on receiver: {len(toggles)}")
    for _, row in toggles.iterrows():
        print(f"  t={row['elapsed_s']}s: {row['detail']}")

    print(f"\nTranslation events (paired, classified):")
    if latency_df.empty:
        print("  None found.")
    else:
        for i, row in latency_df.iterrows():
            print(f"  #{i+1}: {row['classification']:>4} | {row['duration_ms']:.0f}ms | t={row['start_s']:.0f}s-{row['end_s']:.0f}s | {row['detail']}")

    disposes = receiver_events[(receiver_events["event_type"] == "DISPOSE")]
    print(f"\nModel dispose events on receiver: {len(disposes)}")
    for _, row in disposes.iterrows():
        print(f"  t={row['elapsed_s']}s: {row['detail']}")

    print("\n" + "=" * 70)


def detect_unexplained_memory_drop(receiver_resource: pd.DataFrame, receiver_events: pd.DataFrame,
                                     drop_threshold_mb: float = 500) -> list:
    """
    Looks for a large, sustained PSS drop on the receiver that has NO logged
    event (LANGUAGE_CHANGE, TRANSLATION_TOGGLE, MSG_RECEIVE, or any
    MODEL_LIFECYCLE event) within a reasonable window of it. If found, returns
    it as an inferred_bands entry so plot_combined_memory can shade it with an
    explicit "inferred, not directly logged" caption rather than silently
    treating it as confirmed. This is intentionally conservative -- it only
    flags a drop this large, and only labels it generically as a likely idle
    release, since the actual cause (idle timeout vs. something else) cannot be
    confirmed without the missing DISPOSE event.
    """
    df = receiver_resource.dropna(subset=["pss_mb"]).reset_index(drop=True)
    if len(df) < 3:
        return []

    diffs = df["pss_mb"].diff()
    drop_idx = diffs.idxmin()
    if pd.isna(drop_idx) or -diffs.loc[drop_idx] < drop_threshold_mb:
        return []

    drop_start = df.loc[drop_idx - 1, "elapsed_s"] if drop_idx > 0 else df.loc[drop_idx, "elapsed_s"]
    drop_end = df.loc[drop_idx, "elapsed_s"]

    # Check whether ANY event (of any type) exists within a window around this drop
    window = 20  # seconds
    if receiver_events is not None and not receiver_events.empty:
        nearby = receiver_events[
            (receiver_events["elapsed_s"] >= drop_start - window) &
            (receiver_events["elapsed_s"] <= drop_end + window)
        ]
        if not nearby.empty:
            return []  # a real event explains this drop -- nothing to infer

    return [(drop_start, drop_end, "Likely idle-timeout memory release")]


def main():
    parser = argparse.ArgumentParser(description="Generate a combined dual-device report from monitor_two_devices.sh output.")
    parser.add_argument("run_dir", type=Path, help="Run directory containing sender_resource.csv, sender_events.csv, receiver_resource.csv, receiver_events.csv")
    parser.add_argument("--output-dir", type=Path, default=None, help="Where to write output charts (default: same as run_dir)")
    args = parser.parse_args()

    run_dir = args.run_dir
    output_dir = args.output_dir or run_dir

    required = ["sender_resource.csv", "sender_events.csv", "receiver_resource.csv", "receiver_events.csv"]
    missing = [f for f in required if not (run_dir / f).exists()]
    if missing:
        print(f"Error: missing expected files in {run_dir}: {missing}")
        sys.exit(1)

    sender_resource = load_resource_csv(run_dir / "sender_resource.csv")
    sender_events = load_events_csv(run_dir / "sender_events.csv")
    receiver_resource = load_resource_csv(run_dir / "receiver_resource.csv")
    receiver_events = load_events_csv(run_dir / "receiver_events.csv")

    inferred_bands = detect_unexplained_memory_drop(receiver_resource, receiver_events)
    if inferred_bands:
        start_s, end_s, label = inferred_bands[0]
        print(f"ℹ️  Detected a large, unexplained memory drop on the receiver at t={start_s:.0f}s-{end_s:.0f}s "
              f"with no corresponding logged event nearby. Marking it on the chart as an INFERRED "
              f"'{label}' — this is a plausible explanation based on the memory curve shape and the "
              f"idle-timeout behavior described in prior fix rounds, but is NOT confirmed by a "
              f"[MODEL_LIFECYCLE] DISPOSE event, since none were captured in this run's logs.")

    plot_combined_memory(
        sender_resource, receiver_resource, output_dir / "combined_memory_chart.png",
        sender_events=sender_events, receiver_events=receiver_events,
        inferred_bands=inferred_bands,
    )

    latency_df = compute_translation_latencies(receiver_events)
    if not latency_df.empty:
        plot_latency_comparison(latency_df, output_dir / "translation_latency_chart.png")
        latency_df.to_csv(output_dir / "translation_latency_summary.csv", index=False)
        print(f"Latency summary CSV saved to: {output_dir / 'translation_latency_summary.csv'}")

    print_scenario_summary(sender_events, receiver_events, latency_df)


if __name__ == "__main__":
    main()