#!/usr/bin/env bash
# ==============================================================================
# scripts/monitor_android_resources_with_events.sh
# Combined resource + lifecycle/interaction event monitor for iTantra / AstraMesh
#
# This is a NEW, separate script alongside the original monitor_android_resources.sh
# (which is left untouched). It does everything the original does (PSS/RSS/CPU/
# battery sampling on a fixed interval, written to a CSV), PLUS it runs `adb logcat`
# in parallel, filtered to the app's PID and the [UI_EVENT] / [MODEL_LIFECYCLE]
# markers added in recent fix rounds, capturing those events into a SECOND CSV
# on the same elapsed-time clock as the resource samples — so a chart script can
# plot real causal event markers (mic press, model load, translation, etc.)
# directly on the memory graph, instead of guessing from the shape of the curve.
#
# Two output files per run:
#   <output_dir>/resource_monitor_<timestamp>.csv   — same format as the original script
#   <output_dir>/events_<timestamp>.csv             — new: one row per matched log line
#
# Usage:
#   ./monitor_android_resources_with_events.sh [-s <serial>] [-p <package>] [-i <interval>] [-o <output_dir>] [-l <label>]
#
# Same flags as the original script, plus:
#   -o now takes a DIRECTORY (not a single file path), since this script produces
#      two files per run that need to share a timestamp and directory to be
#      easily paired up afterward.
# ==============================================================================

set -euo pipefail

PACKAGE_NAME="com.astra.itantra.debug"
FALLBACK_PACKAGE="com.astra.itantra"
DEVICE_SERIAL=""
INTERVAL=1
OUTPUT_DIR="logs"
LABEL=""

usage() {
    echo "Usage: $0 [-s <device_serial>] [-p <package_name>] [-i <interval_seconds>] [-o <output_dir>] [-l <run_label>]"
    echo "  -s <serial>    Specific adb device serial"
    echo "  -p <package>   Target Android package (default: $PACKAGE_NAME)"
    echo "  -i <seconds>   Polling interval in seconds for resource sampling (default: $INTERVAL)"
    echo "  -o <dir>       Output directory for both CSV files (default: logs/)"
    echo "  -l <label>     Optional short label for this run, stored in both CSV headers"
    exit 1
}

while getopts "s:p:i:o:l:h" opt; do
    case "$opt" in
        s) DEVICE_SERIAL="$OPTARG" ;;
        p) PACKAGE_NAME="$OPTARG" ;;
        i) INTERVAL="$OPTARG" ;;
        o) OUTPUT_DIR="$OPTARG" ;;
        l) LABEL="$OPTARG" ;;
        h|*) usage ;;
    esac
done

ADB_CMD="adb"
if [ -n "$DEVICE_SERIAL" ]; then
    ADB_CMD="adb -s $DEVICE_SERIAL"
fi

if ! command -v adb >/dev/null 2>&1; then
    echo "❌ Error: 'adb' command not found in PATH."
    exit 1
fi

CONNECTED_DEVICES=$($ADB_CMD devices | grep -w "device" || true)
if [ -z "$CONNECTED_DEVICES" ]; then
    echo "❌ Error: No Android devices connected via ADB."
    exit 1
fi

mkdir -p "$OUTPUT_DIR"
RUN_TIMESTAMP=$(date "+%Y%m%d_%H%M%S")
RESOURCE_FILE="${OUTPUT_DIR}/resource_monitor_${RUN_TIMESTAMP}.csv"
EVENTS_FILE="${OUTPUT_DIR}/events_${RUN_TIMESTAMP}.csv"

DEVICE_MODEL=$($ADB_CMD shell "getprop ro.product.model" 2>/dev/null | tr -d '\r\n' || echo "unknown")

write_header() {
    local file="$1"
    local extra_header="$2"
    {
        echo "# iTantra / AstraMesh Monitor Log"
        echo "# Run started: $(date '+%Y-%m-%d %H:%M:%S')"
        echo "# Device model: ${DEVICE_MODEL:-unknown}"
        echo "# Device serial: ${DEVICE_SERIAL:-default}"
        echo "# Target package: $PACKAGE_NAME"
        if [ -n "$LABEL" ]; then
            echo "# Run label: $LABEL"
        fi
        echo "$extra_header"
    } > "$file"
}

write_header "$RESOURCE_FILE" "elapsed_s,time,pid,cpu_pct,pss_mb,rss_mb,threads,battery_pct,battery_temp_c,state"
write_header "$EVENTS_FILE" "elapsed_s,time,category,event_type,detail"

echo "=============================================================================="
echo "  AstraMesh / iTantra Resource + Event Monitor"
echo "  Target Package : $PACKAGE_NAME"
echo "  Device         : ${DEVICE_SERIAL:-default} (${DEVICE_MODEL:-unknown})"
echo "  Interval       : ${INTERVAL}s (resource sampling)"
echo "  Resource log   : $RESOURCE_FILE"
echo "  Events log     : $EVENTS_FILE"
echo "=============================================================================="
echo "  This captures BOTH memory/CPU samples AND [UI_EVENT]/[MODEL_LIFECYCLE] log"
echo "  lines on the same elapsed-time clock. Run through a full test scenario"
echo "  (idle -> mic press -> speak -> release -> translation -> TTS playback ->"
echo "  idle timeout) then Ctrl+C. Both CSVs will be ready to feed into the chart"
echo "  generation script together."
echo "=============================================================================="

START_EPOCH=$(date +%s)

# --- Background logcat capture ---
# We clear the log buffer first so this run isn't polluted by old lines from
# before the monitor started, then tail logcat continuously, filtering for our
# two markers. `adb logcat` timestamps each line itself, but we compute our own
# elapsed_s at the moment we SEE the line arrive in this script, which keeps it
# on the exact same clock basis as the resource-sampling loop below (both use
# $(date +%s) - $START_EPOCH), rather than trying to parse and convert logcat's
# own timestamp format, which would need to reconcile against a possibly-different
# clock source on the device vs. this host machine.
$ADB_CMD logcat -c 2>/dev/null || true

(
    $ADB_CMD logcat -v raw 2>/dev/null | grep --line-buffered -E "\[UI_EVENT\]|\[MODEL_LIFECYCLE\]" | while IFS= read -r line; do
        NOW_EPOCH=$(date +%s)
        ELAPSED=$((NOW_EPOCH - START_EPOCH))
        TIMESTAMP=$(date "+%H:%M:%S")

        if echo "$line" | grep -q "\[UI_EVENT\]"; then
            CATEGORY="UI_EVENT"
        else
            CATEGORY="MODEL_LIFECYCLE"
        fi

        # Extract the event type: the token immediately after the marker, e.g.
        # "[UI_EVENT] MIC_PRESS mode=..." -> event_type = MIC_PRESS
        EVENT_TYPE=$(echo "$line" | sed -E 's/.*\[(UI_EVENT|MODEL_LIFECYCLE)\][[:space:]]*([A-Z_]+).*/\2/')

        # Detail = everything after the event type token, trimmed. Escape any
        # commas in the detail so it doesn't break CSV parsing downstream —
        # wrap the whole detail field in quotes rather than trying to strip commas,
        # since the detail often legitimately contains commas (e.g. multiple
        # key=value pairs) and we'd rather preserve that than lose information.
        DETAIL=$(echo "$line" | sed -E "s/.*\[(UI_EVENT|MODEL_LIFECYCLE)\][[:space:]]*[A-Z_]+[[:space:]]*//" | tr -d '\r')
        DETAIL_ESCAPED=$(echo "$DETAIL" | sed 's/"/""/g')

        echo "${ELAPSED},${TIMESTAMP},${CATEGORY},${EVENT_TYPE},\"${DETAIL_ESCAPED}\"" >> "$EVENTS_FILE"
    done
) &
LOGCAT_BG_PID=$!

# Ensure the background logcat pipeline is killed when this script exits, however it exits
cleanup() {
    echo ""
    echo "Stopping..."
    kill "$LOGCAT_BG_PID" 2>/dev/null || true
    # Also kill any adb logcat / grep child processes left over from the pipeline,
    # since killing the subshell PID alone doesn't always kill everything piped into it
    pkill -P "$LOGCAT_BG_PID" 2>/dev/null || true
    echo "Resource log saved to: $RESOURCE_FILE"
    echo "Events log saved to:   $EVENTS_FILE"
    exit 0
}
trap cleanup INT TERM

printf "%-10s | %-6s | %-6s | %-8s | %-8s | %-7s | %-8s | %-6s | %-6s | %-8s\n" \
    "ELAPSED" "TIME" "PID" "CPU(%)" "PSS(MB)" "RSS(MB)" "THREADS" "BAT(%)" "TEMP(C)" "STATE"
echo "------------------------------------------------------------------------------"

# --- Foreground resource sampling loop (same logic as the original script) ---
while true; do
    NOW_EPOCH=$(date +%s)
    ELAPSED=$((NOW_EPOCH - START_EPOCH))
    TIMESTAMP=$(date "+%H:%M:%S")

    PID=$($ADB_CMD shell "pidof $PACKAGE_NAME 2>/dev/null || pidof $FALLBACK_PACKAGE 2>/dev/null" | tr -d '\r\n' || true)

    if [ -z "$PID" ]; then
        printf "%-10s | %-6s | %-6s | %-8s | %-8s | %-7s | %-8s | %-6s | %-6s | %-8s\n" \
            "${ELAPSED}s" "$TIMESTAMP" "N/A" "-" "-" "-" "-" "-" "-" "NOT_RUNNING"
        echo "${ELAPSED},${TIMESTAMP},,,,,,,,NOT_RUNNING" >> "$RESOURCE_FILE"
        sleep "$INTERVAL"
        continue
    fi

    BAT_INFO=$($ADB_CMD shell "dumpsys battery" 2>/dev/null || true)
    BAT_LEVEL=$(echo "$BAT_INFO" | grep -i "level:" | awk '{print $2}' | tr -d '\r\n')
    BAT_TEMP_RAW=$(echo "$BAT_INFO" | grep -i "temperature:" | awk '{print $2}' | tr -d '\r\n')
    BAT_TEMP=""
    if [ -n "$BAT_TEMP_RAW" ]; then
        BAT_TEMP=$(awk "BEGIN {printf \"%.1f\", $BAT_TEMP_RAW / 10.0}")
    fi

    STATUS_INFO=$($ADB_CMD shell "cat /proc/$PID/status 2>/dev/null" || true)
    THREADS=$(echo "$STATUS_INFO" | grep -i "Threads:" | awk '{print $2}' | tr -d '\r\n')
    VM_RSS_KB=$(echo "$STATUS_INFO" | grep -i "VmRSS:" | awk '{print $2}' | tr -d '\r\n')
    RSS_MB="-"
    if [ -n "$VM_RSS_KB" ] && [ "$VM_RSS_KB" -gt 0 ] 2>/dev/null; then
        RSS_MB=$(awk "BEGIN {printf \"%.1f\", $VM_RSS_KB / 1024.0}")
    fi

    TOP_LINE=$($ADB_CMD shell "top -b -n 1 -p $PID 2>/dev/null" | grep "$PID" | head -n 1 || true)
    CPU_PCT=$(echo "$TOP_LINE" | awk '{print $9}' | tr -d '\r\n')
    if [ -z "$CPU_PCT" ]; then
        CPU_PCT=$(echo "$TOP_LINE" | awk '{for(i=1;i<=NF;i++) if($i ~ /%/) print $i}' | head -n 1 | tr -d '%\r\n')
    fi

    MEM_LINE=$($ADB_CMD shell "dumpsys meminfo $PID 2>/dev/null" | grep -E "TOTAL PSS:" | head -n 1 || true)
    PSS_TOTAL_KB=$(echo "$MEM_LINE" | awk '{print $3}' | tr -d '\r\n')
    PSS_MB="-"
    if [ -n "$PSS_TOTAL_KB" ] && [ "$PSS_TOTAL_KB" -gt 0 ] 2>/dev/null; then
        PSS_MB=$(awk "BEGIN {printf \"%.1f\", $PSS_TOTAL_KB / 1024.0}")
    fi

    PROC_STATE="ACTIVE"

    printf "%-10s | %-6s | %-6s | %-8s | %-8s | %-7s | %-8s | %-6s | %-6s | %-8s\n" \
        "${ELAPSED}s" "$TIMESTAMP" "${PID:-N/A}" "${CPU_PCT:-0.0}" "${PSS_MB:-N/A}" \
        "${RSS_MB:-N/A}" "${THREADS:-N/A}" "${BAT_LEVEL:-N/A}%" "${BAT_TEMP:-N/A}°C" "$PROC_STATE"

    echo "${ELAPSED},${TIMESTAMP},${PID},${CPU_PCT:-0.0},${PSS_MB},${RSS_MB},${THREADS:-},${BAT_LEVEL:-},${BAT_TEMP:-},${PROC_STATE}" >> "$RESOURCE_FILE"

    sleep "$INTERVAL"
done
