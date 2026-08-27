#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-/home/agent/Projects/kgs-calendar/.android-sdk}"
ANDROID_AVD_HOME="${ANDROID_AVD_HOME:-${ANDROID_SDK_ROOT}/avd}"
export ANDROID_SDK_ROOT ANDROID_AVD_HOME
AVD_NAME="${KGS_CALENDAR_AVD_NAME:-kgs_calendar_api36}"
UNIT_NAME="kgs-calendar-emulator.service"
ADB="${ANDROID_SDK_ROOT}/platform-tools/adb"
EMULATOR="${ANDROID_SDK_ROOT}/emulator/emulator"
AVDMANAGER="${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin/avdmanager"
SYSTEM_IMAGE="system-images;android-36;google_apis;x86_64"
PACKAGE_NAME="com.kgs501.kgscalendar.debug"
GRADLEW="${KGS_CALENDAR_GRADLEW:-$PROJECT_ROOT/gradlew}"
SETUP_TIMEOUT_SECONDS="${KGS_ANDROID_SETUP_TIMEOUT_SECONDS:-5}"

usage() {
    echo "Usage: tools/android-emulator.sh create|start|wait|status|serial|stop|install <apk>|launch|screenshot <png>|ui [xml]|logs|adb <args...>|connected-test [gradle args...]"
}

require_adb() {
    test -x "$ADB" || { echo "adb not found at $ADB" >&2; exit 1; }
}

require_tooling() {
    require_adb
    test -x "$EMULATOR" || { echo "emulator not found at $EMULATOR" >&2; exit 1; }
}

serial_for_avd() {
    require_adb
    local serial state avd_name avd_output
    while read -r serial state _; do
        [[ "$serial" == emulator-* && "$state" == device ]] || continue
        avd_output="$("$ADB" -s "$serial" emu avd name 2>/dev/null)" || continue
        avd_name="${avd_output%%$'\n'*}"
        avd_name="${avd_name//$'\r'/}"
        if [[ "$avd_name" == "$AVD_NAME" ]]; then
            printf '%s\n' "$serial"
            return 0
        fi
    done < <("$ADB" devices)
    return 1
}

ready_serial() {
    local serial
    serial="$(serial_for_avd)" || return 1
    "$ADB" -s "$serial" get-state >/dev/null 2>&1 &&
        test "$("$ADB" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" &&
        timeout 8 "$ADB" -s "$serial" shell pm path android >/dev/null 2>&1 &&
        printf '%s\n' "$serial"
}

device_ready() {
    ready_serial >/dev/null
}

run_setup_adb() {
    local serial="$1"
    shift
    timeout "$SETUP_TIMEOUT_SECONDS" "$ADB" -s "$serial" "$@" >/dev/null 2>&1 || true
}

create_avd() {
    require_tooling
    test -x "$AVDMANAGER" || { echo "avdmanager not found at $AVDMANAGER" >&2; exit 1; }
    if "$EMULATOR" -list-avds | grep -Fxq "$AVD_NAME"; then
        echo "AVD $AVD_NAME already exists"
        return
    fi
    mkdir -p "$ANDROID_AVD_HOME"
    printf 'no\n' | ANDROID_AVD_HOME="$ANDROID_AVD_HOME" "$AVDMANAGER" create avd \
        --name "$AVD_NAME" \
        --package "$SYSTEM_IMAGE" \
        --device pixel_6
    echo "Created $AVD_NAME"
}

start_emulator() {
    require_tooling
    if device_ready; then
        echo "Emulator is already ready"
        return
    fi
    if ! "$EMULATOR" -list-avds | grep -Fxq "$AVD_NAME"; then
        create_avd
    fi
    local available_kib
    available_kib="$(awk '/MemAvailable/ { print $2 }' /proc/meminfo)"
    if (( available_kib < 5242880 )); then
        echo "Warning: less than 5 GiB host memory is available; stop idle Gradle daemons if the emulator becomes sluggish." >&2
    fi
    systemctl --user reset-failed "$UNIT_NAME" >/dev/null 2>&1 || true
    if ! systemctl --user is-active --quiet "$UNIT_NAME"; then
        systemd-run --user \
            --unit="${UNIT_NAME%.service}" \
            --collect \
            --property="WorkingDirectory=$PROJECT_ROOT" \
            --setenv="ANDROID_SDK_ROOT=$ANDROID_SDK_ROOT" \
            --setenv="ANDROID_AVD_HOME=$ANDROID_AVD_HOME" \
            "$EMULATOR" \
            -avd "$AVD_NAME" \
            -no-window \
            -gpu swiftshader_indirect \
            -no-audio \
            -no-boot-anim \
            -no-snapshot-load \
            -no-snapshot-save
    fi
    wait_for_device
}

wait_for_device() {
    require_tooling
    local deadline=$((SECONDS + 240)) serial
    while (( SECONDS < deadline )); do
        if serial="$(ready_serial)"; then
            run_setup_adb "$serial" shell input keyevent KEYCODE_WAKEUP
            run_setup_adb "$serial" shell wm dismiss-keyguard
            run_setup_adb "$serial" shell svc power stayon true
            run_setup_adb "$serial" shell settings put system screen_off_timeout 2147483647
            run_setup_adb "$serial" shell settings put global hide_error_dialogs 1
            run_setup_adb "$serial" shell settings put global window_animation_scale 1
            run_setup_adb "$serial" shell settings put global transition_animation_scale 1
            run_setup_adb "$serial" shell settings put global animator_duration_scale 1
            for package_name in \
                com.google.android.apps.wellbeing \
                com.google.android.as \
                com.google.android.dialer \
                com.google.android.apps.messaging \
                com.android.stk; do
                run_setup_adb "$serial" shell pm disable-user --user 0 "$package_name"
            done
            echo "Emulator $AVD_NAME is ready as $serial"
            return
        fi
        sleep 2
    done
    echo "Emulator did not become ready within 240 seconds" >&2
    systemctl --user status "$UNIT_NAME" --no-pager >&2 || true
    exit 1
}

status() {
    local serial
    systemctl --user is-active "$UNIT_NAME" || true
    "$ADB" devices -l
    if serial="$(ready_serial)"; then
        echo "target=$AVD_NAME serial=$serial boot_completed=1 package_manager=ready"
    else
        echo "target=$AVD_NAME device_not_ready"
    fi
}

stop_emulator() {
    systemctl --user stop "$UNIT_NAME" >/dev/null 2>&1 || true
    echo "Stopped $UNIT_NAME"
}

install_apk() {
    local apk="${1:-}" serial
    test -n "$apk" || { usage; exit 2; }
    test -f "$apk" || { echo "APK not found: $apk" >&2; exit 1; }
    wait_for_device
    serial="$(serial_for_avd)"
    timeout 120 "$ADB" -s "$serial" install -r -t "$apk"
}

launch_app() {
    local serial
    wait_for_device
    serial="$(serial_for_avd)"
    "$ADB" -s "$serial" shell pm path "$PACKAGE_NAME" >/dev/null 2>&1 || {
        echo "$PACKAGE_NAME is not installed; run the install command first" >&2
        exit 1
    }
    "$ADB" -s "$serial" shell am force-stop "$PACKAGE_NAME"
    "$ADB" -s "$serial" shell monkey -p "$PACKAGE_NAME" -c android.intent.category.LAUNCHER 1 >/dev/null
    echo "Launched $PACKAGE_NAME on $serial"
}

take_screenshot() {
    local destination="${1:-}" serial
    test -n "$destination" || { usage; exit 2; }
    wait_for_device
    serial="$(serial_for_avd)"
    mkdir -p "$(dirname "$destination")"
    timeout 30 "$ADB" -s "$serial" exec-out screencap -p > "$destination"
    echo "$destination"
}

dump_ui() {
    local destination="${1:-}" serial
    wait_for_device
    serial="$(serial_for_avd)"
    "$ADB" -s "$serial" shell uiautomator dump /sdcard/kgs-calendar-window.xml >/dev/null
    if test -n "$destination"; then
        mkdir -p "$(dirname "$destination")"
        "$ADB" -s "$serial" exec-out cat /sdcard/kgs-calendar-window.xml > "$destination"
        echo "$destination"
    else
        "$ADB" -s "$serial" exec-out cat /sdcard/kgs-calendar-window.xml
    fi
}

app_logs() {
    local app_pid serial
    wait_for_device
    serial="$(serial_for_avd)"
    app_pid="$("$ADB" -s "$serial" shell pidof "$PACKAGE_NAME" 2>/dev/null | tr -d '\r')"
    if test -n "$app_pid"; then
        "$ADB" -s "$serial" logcat -d --pid="$app_pid"
    else
        echo "$PACKAGE_NAME is not running" >&2
        exit 1
    fi
}

run_targeted_adb() {
    local serial
    (( $# > 0 )) || { usage; exit 2; }
    serial="$(serial_for_avd)" || {
        echo "Emulator $AVD_NAME is not connected" >&2
        exit 1
    }
    "$ADB" -s "$serial" "$@"
}

run_connected_tests() {
    local serial
    wait_for_device
    serial="$(serial_for_avd)"
    (
        cd "$PROJECT_ROOT"
        ANDROID_SERIAL="$serial" "$GRADLEW" :app:connectedDebugAndroidTest "$@"
    )
}

case "${1:-}" in
    create) create_avd ;;
    start) start_emulator ;;
    wait) wait_for_device ;;
    status) status ;;
    serial) serial_for_avd ;;
    stop) stop_emulator ;;
    install) install_apk "${2:-}" ;;
    launch) launch_app ;;
    screenshot) take_screenshot "${2:-}" ;;
    ui) dump_ui "${2:-}" ;;
    logs) app_logs ;;
    adb) shift; run_targeted_adb "$@" ;;
    connected-test) shift; run_connected_tests "$@" ;;
    *) usage; exit 2 ;;
esac
