#!/usr/bin/env bash
# ==============================================================================
# scripts/monitor_two_devices.sh
# Parallel sender+receiver resource & event monitor for iTantra / AstraMesh
#
# Launches TWO instances of the resource+event monitoring logic (same approach
# as monitor_android_resources_with_events.sh) simultaneously against two
# different Android devices, each writing its own pair of CSVs into a shared
# timestamped run directory, so sender-side and receiver-side behavior can be
# correlated afterward on one shared elapsed-time clock (this host's clock,
# not either device's clock, to avoid any phone-to-phone clock drift issue).
#
# No device serials are hardcoded — always pass them explicitly via flags.
# Run `adb devices` first to get your two serials.
#
# Usage:
#   ./monitor_two_devices.sh -a <sender_serial> -b <receiver_serial> \
#       [-p <package_name>] [-i <interval_seconds>] [-o <output_dir>] [-l <run_label>]
#
# Output structure (all under one shared timestamped run folder):
#   <output_dir>/<run_label_or_timestamp>/
#       sender_resource.csv
#       sender_events.csv
#       receiver_resource.csv
#       receiver_events.csv
#       run_info.txt          <- device serials, models, start time, for reference
#
# Stop with Ctrl+C — both device sessions and their background logcat captures
# are cleaned up together.
# ==============================================================================

set -euo pipefail

SENDER_SERIAL=""
RECEIVER_SERIAL=""
PACKAGE_NAME="com.astra.itantra.debug"
FALLBACK_PACKAGE="com.astra.itantra"
INTERVAL=1
OUTPUT_ROOT="logs"
LABEL=""

usage() {
    echo "Usage: $0 -a <sender_serial> -b <receiver_serial> [-p <package_name>] [-i <interval_seconds>] [-o <output_dir>] [-l <run_label>]"
    echo "  -a <serial>    ADB device serial for the SENDER device (required)"
    echo "  -b <serial>    ADB device serial for the RECEIVER device (required)"
    echo "  -p <package>   Target Android package (default: $PACKAGE_NAME)"
    echo "  -i <seconds>   Polling interval in seconds for resource sampling (default: $INTERVAL)"
    echo "  -o <dir>       Root output directory (default: logs/)"
    echo "  -l <label>     Optional run label; used as the run folder name if given, else a timestamp is used"
    echo ""
    echo "Run 'adb devices' first to find your sender/receiver serials — nothing is hardcoded."
    exit 1
}

while getopts "a:b:p:i:o:l:h" opt; do
    case "$opt" in
        a) SENDER_SERIAL="$OPTARG" ;;
        b) RECEIVER_SERIAL="$OPTARG" ;;
        p) PACKAGE_NAME="$OPTARG" ;;
        i) INTERVAL="$OPTARG" ;;
        o) OUTPUT_ROOT="$OPTARG" ;;
        l) LABEL="$OPTARG" ;;
        h|*) usage ;;
    esac
done

if [ -z "$SENDER_SERIAL" ] || [ -z "$RECEIVER_SERIAL" ]; then
    echo "❌ Error: both -a <sender_serial> and -b <receiver_serial> are required."
    echo ""
    usage
fi

if ! command -v adb >/dev/null 2>&1; then
    echo "❌ Error: 'adb' command not found in PATH."
    exit 1
fi

for SERIAL in "$SENDER_SERIAL" "$RECEIVER_SERIAL"; do
    if ! adb -s "$SERIAL" devices | grep -qw "$SERIAL"; then
        echo "❌ Error: device '$SERIAL' not found via 'adb devices'. Run 'adb devices' to check available serials."
        exit 1
    fi
done

RUN_NAME="${LABEL:-$(date "+%Y%m%d_%H%M%S")}"
RUN_DIR="${OUTPUT_ROOT}/${RUN_NAME}"
mkdir -p "$RUN_DIR"

SENDER_MODEL=$(adb -s "$SENDER_SERIAL" shell "getprop ro.product.model" 2>/dev/null | tr -d '\r\n' || echo "unknown")
RECEIVER_MODEL=$(adb -s "$RECEIVER_SERIAL" shell "getprop ro.product.model" 2>/dev/null | tr -d '\r\n' || echo "unknown")

{
    echo "Run label: $RUN_NAME"
    echo "Started: $(date '+%Y-%m-%d %H:%M:%S')"
    echo "Package: $PACKAGE_NAME"
    echo "Sender device:   $SENDER_SERIAL ($SENDER_MODEL)"
    echo "Receiver device: $RECEIVER_SERIAL ($RECEIVER_MODEL)"
    echo "Sampling interval: ${INTERVAL}s"
} > "${RUN_DIR}/run_info.txt"

echo "=============================================================================="
echo "  AstraMesh / iTantra Two-Device Monitor"
echo "  Sender   : $SENDER_SERIAL ($SENDER_MODEL)"
echo "  Receiver : $RECEIVER_SERIAL ($RECEIVER_MODEL)"
echo "  Package  : $PACKAGE_NAME"
echo "  Run dir  : $RUN_DIR"
echo "=============================================================================="
echo "  Follow your test script now. Both devices are being sampled and their"
echo "  [UI_EVENT]/[MODEL_LIFECYCLE] logs captured in parallel, on one shared clock."
echo "  Press Ctrl+C when your full test sequence is complete."
echo "=============================================================================="

START_EPOCH=$(date +%s)

# --- One monitoring pipeline per device (resource sampling loop + parallel logcat capture) ---
# This mirrors monitor_android_resources_with_events.sh's logic, parameterized per device,
# run as two independent background job groups so a failure/hang on one device doesn't
# block sampling on the other.
#
# IMPORTANT: this function is called DIRECTLY (not via command substitution / $(...)),
# and appends background PIDs to the ALL_BG_PIDS array declared in the parent shell's
# scope below. Capturing PIDs via `$(...)` around a function that itself backgrounds jobs
# is a trap — the function body runs in a subshell in that case, and jobs it backgrounds
# get orphaned/killed when that subshell exits, rather than surviving in this script's
# own process group where the cleanup trap can actually reach them. This was caught and
# fixed after testing the pattern in isolation; do not "simplify" this back to using
# command substitution for PID capture.

ALL_BG_PIDS=()

run_device_monitor() {
    local role="$1"       # "sender" or "receiver", used only for file naming/labels
    local serial="$2"
    local resource_file="${RUN_DIR}/${role}_resource.csv"
    local events_file="${RUN_DIR}/${role}_events.csv"

    {
        echo "elapsed_s,time,pid,cpu_pct,pss_mb,rss_mb,threads,battery_pct,battery_temp_c,state"
    } > "$resource_file"
    {
        echo "elapsed_s,time,category,event_type,detail"
    } > "$events_file"

    # Clear this device's log buffer so old lines don't leak into this run
    adb -s "$serial" logcat -c 2>/dev/null || true

    # Background logcat capture for this device
    (
        adb -s "$serial" logcat -v raw 2>/dev/null | grep --line-buffered -E "\[UI_EVENT\]|\[MODEL_LIFECYCLE\]|\[SERVICE_STATE\]" | while IFS= read -r line; do
            local now_epoch elapsed timestamp category event_type detail detail_escaped
            now_epoch=$(date +%s)
            elapsed=$((now_epoch - START_EPOCH))
            timestamp=$(date "+%H:%M:%S")

            if echo "$line" | grep -q "\[UI_EVENT\]"; then
                category="UI_EVENT"
            elif echo "$line" | grep -q "\[SERVICE_STATE\]"; then
                category="SERVICE_STATE"
            else
                category="MODEL_LIFECYCLE"
            fi
            event_type=$(echo "$line" | sed -E 's/.*\[(UI_EVENT|MODEL_LIFECYCLE|SERVICE_STATE)\][[:space:]]*([A-Z_]+).*/\2/')
            detail=$(echo "$line" | sed -E "s/.*\[(UI_EVENT|MODEL_LIFECYCLE|SERVICE_STATE)\][[:space:]]*[A-Z_]+[[:space:]]*//" | tr -d '\r')
            detail_escaped=$(echo "$detail" | sed 's/"/""/g')

            echo "${elapsed},${timestamp},${category},${event_type},\"${detail_escaped}\"" >> "$events_file"
        done
    ) &
    ALL_BG_PIDS+=("$!")

    # Resource sampling loop for this device, ALSO backgrounded relative to the overall
    # script, so both devices sample concurrently.
    (
        while true; do
            local now_epoch elapsed timestamp pid bat_info bat_level bat_temp_raw bat_temp
            local status_info threads vm_rss_kb rss_mb top_line cpu_pct mem_line pss_total_kb pss_mb proc_state

            now_epoch=$(date +%s)
            elapsed=$((now_epoch - START_EPOCH))
            timestamp=$(date "+%H:%M:%S")

            pid=$(adb -s "$serial" shell "pidof $PACKAGE_NAME 2>/dev/null || pidof $FALLBACK_PACKAGE 2>/dev/null" | tr -d '\r\n' || true)

            if [ -z "$pid" ]; then
                echo "${elapsed},${timestamp},,,,,,,,NOT_RUNNING" >> "$resource_file"
                sleep "$INTERVAL"
                continue
            fi

            bat_info=$(adb -s "$serial" shell "dumpsys battery" 2>/dev/null || true)
            bat_level=$(echo "$bat_info" | grep -i "level:" | awk '{print $2}' | tr -d '\r\n')
            bat_temp_raw=$(echo "$bat_info" | grep -i "temperature:" | awk '{print $2}' | tr -d '\r\n')
            bat_temp=""
            if [ -n "$bat_temp_raw" ]; then
                bat_temp=$(awk "BEGIN {printf \"%.1f\", $bat_temp_raw / 10.0}")
            fi

            status_info=$(adb -s "$serial" shell "cat /proc/$pid/status 2>/dev/null" || true)
            threads=$(echo "$status_info" | grep -i "Threads:" | awk '{print $2}' | tr -d '\r\n')
            vm_rss_kb=$(echo "$status_info" | grep -i "VmRSS:" | awk '{print $2}' | tr -d '\r\n')
            rss_mb="-"
            if [ -n "$vm_rss_kb" ] && [ "$vm_rss_kb" -gt 0 ] 2>/dev/null; then
                rss_mb=$(awk "BEGIN {printf \"%.1f\", $vm_rss_kb / 1024.0}")
            fi

            top_line=$(adb -s "$serial" shell "top -b -n 1 -p $pid 2>/dev/null" | grep "$pid" | head -n 1 || true)
            cpu_pct=$(echo "$top_line" | awk '{print $9}' | tr -d '\r\n')
            if [ -z "$cpu_pct" ]; then
                cpu_pct=$(echo "$top_line" | awk '{for(i=1;i<=NF;i++) if($i ~ /%/) print $i}' | head -n 1 | tr -d '%\r\n')
            fi

            mem_line=$(adb -s "$serial" shell "dumpsys meminfo $pid 2>/dev/null" | grep -E "TOTAL PSS:" | head -n 1 || true)
            pss_total_kb=$(echo "$mem_line" | awk '{print $3}' | tr -d '\r\n')
            pss_mb="-"
            if [ -n "$pss_total_kb" ] && [ "$pss_total_kb" -gt 0 ] 2>/dev/null; then
                pss_mb=$(awk "BEGIN {printf \"%.1f\", $pss_total_kb / 1024.0}")
            fi

            proc_state="ACTIVE"

            echo "${elapsed},${timestamp},${pid},${cpu_pct:-0.0},${pss_mb},${rss_mb},${threads:-},${bat_level:-},${bat_temp:-},${proc_state}" >> "$resource_file"

            sleep "$INTERVAL"
        done
    ) &
    ALL_BG_PIDS+=("$!")
}

# Launch both devices' monitors — called directly, NOT via command substitution,
# so their backgrounded jobs register in ALL_BG_PIDS in THIS shell's scope (see note above).
run_device_monitor "sender" "$SENDER_SERIAL"
run_device_monitor "receiver" "$RECEIVER_SERIAL"

cleanup() {
    echo ""
    echo "Stopping all monitors..."
    for p in "${ALL_BG_PIDS[@]}"; do
        kill "$p" 2>/dev/null || true
        pkill -P "$p" 2>/dev/null || true
    done
    echo ""
    echo "Run complete. Files saved under: $RUN_DIR"
    echo "  ${RUN_DIR}/sender_resource.csv"
    echo "  ${RUN_DIR}/sender_events.csv"
    echo "  ${RUN_DIR}/receiver_resource.csv"
    echo "  ${RUN_DIR}/receiver_events.csv"
    echo "  ${RUN_DIR}/run_info.txt"
    exit 0
}
trap cleanup INT TERM

echo "Both device monitors running (PIDs: ${ALL_BG_PIDS[*]}). Follow your test script now."
echo "Press Ctrl+C when done."

# Keep the main script alive while background jobs run; wait on all of them.
wait
