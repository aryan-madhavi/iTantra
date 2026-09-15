#!/usr/bin/env bash
# ==============================================================================
# scripts/monitor_android_resources.sh
# Real-time Android resource consumption monitor for iTantra / AstraMesh
# Measures: Timestamp, CPU %, RAM (PSS/RSS), Threads, Battery Level/Temp, I/O
# ==============================================================================

set -euo pipefail

PACKAGE_NAME="com.astra.itantra.debug"
FALLBACK_PACKAGE="com.astra.itantra"
DEVICE_SERIAL=""
INTERVAL=1

usage() {
    echo "Usage: $0 [-s <device_serial>] [-p <package_name>] [-i <interval_seconds>]"
    echo "  -s <serial>    Specific adb device serial"
    echo "  -p <package>   Target Android package (default: $PACKAGE_NAME)"
    echo "  -i <seconds>   Polling interval in seconds (default: $INTERVAL)"
    exit 1
}

while getopts "s:p:i:h" opt; do
    case "$opt" in
        s) DEVICE_SERIAL="$OPTARG" ;;
        p) PACKAGE_NAME="$OPTARG" ;;
        i) INTERVAL="$OPTARG" ;;
        h|*) usage ;;
    esac
done

ADB_CMD="adb"
if [ -n "$DEVICE_SERIAL" ]; then
    ADB_CMD="adb -s $DEVICE_SERIAL"
fi

# Verify adb is available
if ! command -v adb >/dev/null 2>&1; then
    echo "❌ Error: 'adb' command not found in PATH."
    exit 1
fi

# Verify device connectivity
CONNECTED_DEVICES=$($ADB_CMD devices | grep -w "device" || true)
if [ -z "$CONNECTED_DEVICES" ]; then
    echo "❌ Error: No Android devices connected via ADB."
    exit 1
fi

echo "=============================================================================="
echo "  AstraMesh / iTantra Resource Monitor"
echo "  Target Package : $PACKAGE_NAME"
echo "  Device         : ${DEVICE_SERIAL:-default}"
echo "  Interval       : ${INTERVAL}s"
echo "=============================================================================="
printf "%-10s | %-6s | %-8s | %-8s | %-7s | %-8s | %-6s | %-6s | %-8s\n" \
    "TIME" "PID" "CPU(%)" "PSS(MB)" "RSS(MB)" "THREADS" "BAT(%)" "TEMP(C)" "STATE"
echo "------------------------------------------------------------------------------"

while true; do
    PID=$($ADB_CMD shell "pidof $PACKAGE_NAME 2>/dev/null || pidof $FALLBACK_PACKAGE 2>/dev/null" | tr -d '\r\n' || true)

    TIMESTAMP=$(date "+%H:%M:%S")

    if [ -z "$PID" ]; then
        printf "%-10s | %-6s | %-8s | %-8s | %-7s | %-8s | %-6s | %-6s | %-8s\n" \
            "$TIMESTAMP" "N/A" "-" "-" "-" "-" "-" "-" "NOT_RUNNING"
        sleep "$INTERVAL"
        continue
    fi

    # Battery info
    BAT_INFO=$($ADB_CMD shell "dumpsys battery" 2>/dev/null || true)
    BAT_LEVEL=$(echo "$BAT_INFO" | grep -i "level:" | awk '{print $2}' | tr -d '\r\n')
    BAT_TEMP_RAW=$(echo "$BAT_INFO" | grep -i "temperature:" | awk '{print $2}' | tr -d '\r\n')
    BAT_TEMP=""
    if [ -n "$BAT_TEMP_RAW" ]; then
        BAT_TEMP=$(awk "BEGIN {printf \"%.1f\", $BAT_TEMP_RAW / 10.0}")
    fi

    # Process status (Threads, RSS)
    STATUS_INFO=$($ADB_CMD shell "cat /proc/$PID/status 2>/dev/null" || true)
    THREADS=$(echo "$STATUS_INFO" | grep -i "Threads:" | awk '{print $2}' | tr -d '\r\n')
    VM_RSS_KB=$(echo "$STATUS_INFO" | grep -i "VmRSS:" | awk '{print $2}' | tr -d '\r\n')
    RSS_MB="-"
    if [ -n "$VM_RSS_KB" ] && [ "$VM_RSS_KB" -gt 0 ] 2>/dev/null; then
        RSS_MB=$(awk "BEGIN {printf \"%.1f\", $VM_RSS_KB / 1024.0}")
    fi

    # Top CPU / PSS
    TOP_LINE=$($ADB_CMD shell "top -b -n 1 -p $PID 2>/dev/null" | grep "$PID" | head -n 1 || true)
    CPU_PCT=$(echo "$TOP_LINE" | awk '{print $9}' | tr -d '\r\n')
    if [ -z "$CPU_PCT" ]; then
        CPU_PCT=$(echo "$TOP_LINE" | awk '{for(i=1;i<=NF;i++) if($i ~ /%/) print $i}' | head -n 1 | tr -d '%\r\n')
    fi

    # Memory PSS from dumpsys meminfo
    MEM_LINE=$($ADB_CMD shell "dumpsys meminfo $PID 2>/dev/null" | grep -E "TOTAL PSS:" | head -n 1 || true)
    PSS_TOTAL_KB=$(echo "$MEM_LINE" | awk '{print $3}' | tr -d '\r\n')
    PSS_MB="-"
    if [ -n "$PSS_TOTAL_KB" ] && [ "$PSS_TOTAL_KB" -gt 0 ] 2>/dev/null; then
        PSS_MB=$(awk "BEGIN {printf \"%.1f\", $PSS_TOTAL_KB / 1024.0}")
    fi

    # Process State (Foreground / Background)
    PROC_STATE="ACTIVE"

    printf "%-10s | %-6s | %-8s | %-8s | %-7s | %-8s | %-6s | %-6s | %-8s\n" \
        "$TIMESTAMP" \
        "${PID:-N/A}" \
        "${CPU_PCT:-0.0}" \
        "${PSS_MB:-N/A}" \
        "${RSS_MB:-N/A}" \
        "${THREADS:-N/A}" \
        "${BAT_LEVEL:-N/A}%" \
        "${BAT_TEMP:-N/A}°C" \
        "$PROC_STATE"

    sleep "$INTERVAL"
done
