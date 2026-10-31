#!/usr/bin/env bash
# Runs on the CI emulator: installs the debug build, grants what onboarding needs, runs the UI
# tour test, then collects screenshots and any crashes.
set -u
PKG=com.screenusdeletus.debug
APK=app/build/outputs/apk/debug/app-debug.apk
TEST_APK=app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

adb install -r -g "$APK"
adb install -r -g "$TEST_APK"
adb shell appops set $PKG GET_USAGE_STATS allow
adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow
adb shell settings put secure enabled_accessibility_services $PKG/com.screenusdeletus.monitoring.AppMonitoringService
adb shell settings put secure accessibility_enabled 1
adb shell dpm set-active-admin $PKG/com.screenusdeletus.security.GuardDeviceAdminReceiver || true
adb shell dumpsys deviceidle whitelist +$PKG || true
adb logcat -c
echo "===== PERMISSION STATE ====="
adb shell appops get $PKG GET_USAGE_STATS || true
adb shell settings get secure enabled_accessibility_services || true
adb shell dumpsys accessibility | grep -i -m5 "screenusdeletus" || true

run_tour() {
  adb shell am instrument -w -e class com.screenusdeletus.UiTourTest $PKG.test/androidx.test.runner.AndroidJUnitRunner | tee -a instrument.txt
}

mkdir -p screenshots
pull_shots() { adb pull /sdcard/Android/data/$PKG/files/screens/. screenshots/ || true; }

run_tour
pull_shots
# Second pass in dark mode on a fresh install.
adb shell dpm remove-active-admin $PKG/com.screenusdeletus.security.GuardDeviceAdminReceiver || true
adb shell pm clear $PKG || true
adb shell cmd uimode night yes
adb shell appops set $PKG GET_USAGE_STATS allow
adb shell settings put secure enabled_accessibility_services $PKG/com.screenusdeletus.monitoring.AppMonitoringService
adb shell settings put secure accessibility_enabled 1
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
sleep 3
run_tour
pull_shots
adb shell cmd uimode night no
ls -la screenshots

adb logcat -d > logcat.txt
echo "===== CRASHES ====="
grep -n -A40 "FATAL EXCEPTION" logcat.txt | head -400 || true
echo "===== APP WARNINGS ====="
grep -E " [EW] (SecurePrefs|Enforcement|GuardService|Guardian|AndroidRuntime|System.err)" logcat.txt | head -150 || true
echo "===== INSTRUMENT RESULT ====="
grep -E "OK \(|FAILURES|Tests run|INSTRUMENTATION_|Error|at com.screenusdeletus" instrument.txt | head -80 || true
test "$(grep -c "OK (1 test)" instrument.txt)" -eq 2
