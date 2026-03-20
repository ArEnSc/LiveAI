#!/bin/bash
# Wallpaper Apply Smoke Test
# Tests the full setup → apply → verify cycle N times.
# Usage: ./test_wallpaper_apply.sh [cycles]  (default: 5)

set -euo pipefail

ADB=~/Library/Android/sdk/platform-tools/adb
PKG=com.example.liveai
SETUP_ACTIVITY="$PKG/.WallpaperSetupActivity"
OUT_DIR=/tmp/wallpaper_test_$(date +%Y%m%d_%H%M%S)
CYCLES=${1:-5}
PASS=0
FAIL=0

# Tap coordinates (1080x2400 screen)
TAP_APPLY_X=655;  TAP_APPLY_Y=2275
TAP_SET_X=540;    TAP_SET_Y=2248
TAP_HOME_X=540;   TAP_HOME_Y=1133

mkdir -p "$OUT_DIR"

echo "============================================"
echo "  Wallpaper Apply Smoke Test — $CYCLES cycles"
echo "  Output: $OUT_DIR"
echo "============================================"
echo ""

check_device() {
    if ! $ADB devices | grep -q "device$"; then
        echo "ERROR: No device connected"
        exit 1
    fi
}

screenshot() {
    local path="$1"
    $ADB exec-out screencap -p > "$path"
}

check_crash() {
    $ADB logcat -d | grep -c "FATAL EXCEPTION" || true
}

run_cycle() {
    local n=$1
    local prefix="$OUT_DIR/cycle_${n}"
    local result="PASS"
    local detail=""

    echo "--- Cycle $n/$CYCLES ---"

    # 1. Force stop
    echo "  [1/7] Force stopping app..."
    $ADB shell am force-stop "$PKG"
    sleep 1

    # 2. Clear logs
    $ADB logcat -c

    # 3. Launch setup
    echo "  [2/7] Launching WallpaperSetupActivity..."
    $ADB shell am start -n "$SETUP_ACTIVITY" 2>&1 | grep -q "Error" && {
        echo "  FAIL: Could not launch activity (is it exported?)"
        FAIL=$((FAIL + 1))
        return
    }
    sleep 10

    # 4. Screenshot setup screen
    echo "  [3/7] Checking setup screen..."
    screenshot "${prefix}_setup.png"

    # 5. Tap APPLY WALLPAPER
    echo "  [4/7] Tapping APPLY WALLPAPER..."
    $ADB shell input tap $TAP_APPLY_X $TAP_APPLY_Y
    sleep 3

    # 6. Tap "Set wallpaper"
    echo "  [5/7] Tapping Set wallpaper..."
    $ADB shell input tap $TAP_SET_X $TAP_SET_Y
    sleep 2

    # 7. Tap "Home screen"
    echo "  [6/7] Tapping Home screen..."
    $ADB shell input tap $TAP_HOME_X $TAP_HOME_Y
    sleep 10

    # 8. Screenshot home screen
    echo "  [7/7] Checking home screen..."
    screenshot "${prefix}_home.png"

    # 9. Check for crashes
    local crashes
    crashes=$(check_crash)
    if [ "$crashes" -gt 0 ]; then
        result="FAIL"
        detail="$crashes FATAL EXCEPTION(s) in logcat"
        $ADB logcat -d | grep "FATAL EXCEPTION" -A 5 > "${prefix}_crash.log" 2>/dev/null
    fi

    # 10. Save logcat
    $ADB logcat -d > "${prefix}_logcat.txt" 2>/dev/null

    if [ "$result" = "PASS" ]; then
        echo "  ✓ Cycle $n: PASS (screenshots saved, no crashes)"
        PASS=$((PASS + 1))
    else
        echo "  ✗ Cycle $n: FAIL — $detail"
        FAIL=$((FAIL + 1))
    fi
    echo ""
}

# Pre-flight
check_device
echo "Device: $($ADB shell getprop ro.product.model)"
echo "Android: $($ADB shell getprop ro.build.version.release) (API $($ADB shell getprop ro.build.version.sdk))"
echo ""

# Run cycles
for i in $(seq 1 "$CYCLES"); do
    run_cycle "$i"
done

# Summary
echo "============================================"
echo "  RESULTS: $PASS passed, $FAIL failed out of $CYCLES"
echo "  Screenshots: $OUT_DIR"
echo "============================================"
echo ""
echo "Review screenshots manually to verify model is visible:"
echo "  open $OUT_DIR"

exit "$FAIL"
