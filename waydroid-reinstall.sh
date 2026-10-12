#!/usr/bin/env bash
# Safely build, install, verify, and optionally launch the El-Imtiyaz Android APK.
# Does not reset the Waydroid container or remove unrelated apps.

set -Eeuo pipefail

PROJECT="${HOME}/Documents/Projects/elimtiyaz-android"
APK=""
EXPECTED_PACKAGE="com.aistudio.elimtiyazstaff.bxmzlx"
BUILD=1
YES=0
LAUNCH=1
LOG_FILE="/tmp/waydroid-reinstall.log"
SESSION_LOG="/tmp/waydroid-session-start.log"

usage() {
  cat <<'USAGE'
Usage: ./waydroid-reinstall.sh [options]

Options:
  --project PATH   Android project directory
  --apk PATH       APK to install (default: PROJECT/app/build/outputs/apk/debug/app-debug.apk)
  --package ID     Expected application ID (default: El-Imtiyaz package ID)
  --no-build       Do not run Gradle; use the existing APK
  --yes            Do not ask before uninstalling this package
  --no-launch      Install and verify, but do not launch
  -h, --help       Show this help

Examples:
  ./waydroid-reinstall.sh
  ./waydroid-reinstall.sh --no-build
  ./waydroid-reinstall.sh --apk /path/to/app.apk --package com.example.app
USAGE
}

log() { printf '[%s] %s\n' "$(date '+%H:%M:%S')" "$*"; }
die() { log "ERROR: $*"; log "See $LOG_FILE for details."; exit 1; }
run_logged() {
  log "+ $*"
  "$@" 2>&1 | tee -a "$LOG_FILE"
}

while (($#)); do
  case "$1" in
    --project) PROJECT="${2:?Missing path after --project}"; shift 2 ;;
    --apk) APK="${2:?Missing path after --apk}"; shift 2 ;;
    --package) EXPECTED_PACKAGE="${2:?Missing ID after --package}"; shift 2 ;;
    --no-build) BUILD=0; shift ;;
    --yes) YES=1; shift ;;
    --no-launch) LAUNCH=0; shift ;;
    -h|--help) usage; exit 0 ;;
    *) die "Unknown option: $1 (use --help)" ;;
  esac
done

PROJECT="$(realpath -m "$PROJECT")"
[[ -n "$APK" ]] || APK="$PROJECT/app/build/outputs/apk/debug/app-debug.apk"
APK="$(realpath -m "$APK")"
: > "$LOG_FILE"

log "Waydroid APK reinstall started"
log "Log file: $LOG_FILE"
log "Project: $PROJECT"
log "Expected package: $EXPECTED_PACKAGE"

command -v waydroid >/dev/null 2>&1 || die "waydroid was not found in PATH."
command -v sudo >/dev/null 2>&1 || die "sudo was not found; this Waydroid setup requires privileged shell access."
log "Checking sudo access for Android package-manager verification..."
sudo -v || die "Administrator privileges are required to query Android through waydroid shell."
[[ -d "$PROJECT" ]] || die "Project directory does not exist: $PROJECT"

if (( BUILD )); then
  [[ -x "$PROJECT/gradlew" ]] || die "Gradle wrapper not found or not executable in $PROJECT"
  log "Building debug APK..."
  (cd "$PROJECT" && ./gradlew assembleDebug --console=plain) 2>&1 | tee -a "$LOG_FILE" || die "Gradle build failed."
fi

[[ -f "$APK" ]] || die "APK not found: $APK"
log "APK found: $APK"
ls -lh "$APK" | tee -a "$LOG_FILE"
sha256sum "$APK" | tee -a "$LOG_FILE"

apk_package=""
if command -v apkanalyzer >/dev/null 2>&1; then
  apk_package="$(apkanalyzer manifest application-id "$APK" 2>/dev/null || true)"
fi
if [[ -z "$apk_package" ]] && command -v aapt >/dev/null 2>&1; then
  apk_package="$(aapt dump badging "$APK" 2>/dev/null | sed -n "s/^package: name='\([^']*\)'.*/\1/p" | head -n 1)"
fi
if [[ -z "$apk_package" ]] && [[ -x "$HOME/Android/Sdk/build-tools" ]]; then
  while IFS= read -r tool; do
    [[ -x "$tool" ]] || continue
    apk_package="$("$tool" dump badging "$APK" 2>/dev/null | sed -n "s/^package: name='\([^']*\)'.*/\1/p" | head -n 1)"
    [[ -n "$apk_package" ]] && break
  done < <(find "$HOME/Android/Sdk/build-tools" -mindepth 2 -maxdepth 2 -type f -name aapt -print 2>/dev/null | sort -V -r)
fi
[[ -n "$apk_package" ]] || die "Could not read the APK application ID. Install Android SDK Build Tools (aapt) or command-line tools (apkanalyzer)."
log "APK application ID: $apk_package"
[[ "$apk_package" == "$EXPECTED_PACKAGE" ]] || die "Package mismatch: expected '$EXPECTED_PACKAGE' but APK contains '$apk_package'. Check app/build.gradle.kts or pass the correct --package."

log "Checking Waydroid status..."
status="$(waydroid status 2>&1 || true)"
printf '%s\n' "$status" | tee -a "$LOG_FILE"
if ! grep -Eq 'Session:[[:space:]]+RUNNING' <<<"$status"; then
  log "Waydroid session is not running; attempting to start it..."
  if ! (export DISPLAY="${DISPLAY:-:0}"; export WAYLAND_DISPLAY="${WAYLAND_DISPLAY:-wayland-0}"; nohup waydroid session start >"$SESSION_LOG" 2>&1 &); then
    die "Could not start the Waydroid session."
  fi
  for _ in $(seq 1 30); do
    sleep 1
    status="$(waydroid status 2>&1 || true)"
    if grep -Eq 'Session:[[:space:]]+RUNNING' <<<"$status"; then break; fi
  done
  printf '%s\n' "$status" | tee -a "$LOG_FILE"
  grep -Eq 'Session:[[:space:]]+RUNNING' <<<"$status" || die "Waydroid session did not start. See $SESSION_LOG."
fi

# Query Android's package manager directly. 'waydroid app list' only lists
# launcher-visible apps and is not the authoritative installed-package check.
package_installed() {
  local packages
  packages="$(timeout 15 sudo waydroid shell pm list packages 2>>"$LOG_FILE" || true)"
  grep -Fqx "package:$EXPECTED_PACKAGE" <<<"$packages"
}

if package_installed; then
  log "Existing installation found: $EXPECTED_PACKAGE"
  if (( ! YES )); then
    read -r -p "Uninstall $EXPECTED_PACKAGE and replace it with this APK? [y/N] " answer
    [[ "$answer" =~ ^[Yy]([Ee][Ss])?$ ]] || die "Cancelled by user; existing installation was left unchanged."
  fi
  log "Removing existing package..."
  if ! waydroid app remove "$EXPECTED_PACKAGE" >>"$LOG_FILE" 2>&1; then
    log "Waydroid app remove failed; trying Android package manager uninstall..."
    sudo waydroid shell pm uninstall "$EXPECTED_PACKAGE" 2>&1 | tee -a "$LOG_FILE" || die "Could not uninstall the existing package."
  fi
else
  log "No existing installation detected for $EXPECTED_PACKAGE."
fi

log "Installing APK..."
if ! waydroid app install "$APK" 2>&1 | tee -a "$LOG_FILE"; then
  die "Waydroid reported an installation failure."
fi

log "Verifying installation through Android package manager..."
installed=0
for _ in $(seq 1 20); do
  if package_installed; then installed=1; break; fi
  sleep 1
done

if (( ! installed )); then
  log "Android package manager does not report package:$EXPECTED_PACKAGE."
  log "Package-manager output and installation output are recorded in $LOG_FILE."
  log "Recent Waydroid status:"
  waydroid status 2>&1 | tee -a "$LOG_FILE" || true
  log "Launcher-visible apps (diagnostic only):"
  waydroid app list 2>&1 | tee -a "$LOG_FILE" || true
  die "Installation could not be verified. The script will not claim success or launch an unverified package."
fi

log "Verified installed package:"
sudo waydroid shell pm path "$EXPECTED_PACKAGE" 2>&1 | tee -a "$LOG_FILE" || true

if (( LAUNCH )); then
  log "Launching $EXPECTED_PACKAGE..."
  waydroid app launch "$EXPECTED_PACKAGE" 2>&1 | tee -a "$LOG_FILE" || die "Package is installed, but launch failed."
fi

log "SUCCESS: $EXPECTED_PACKAGE is installed and verified."
