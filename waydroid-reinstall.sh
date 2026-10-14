#!/usr/bin/env bash
# Rebuild (by default), replace, verify, and launch an APK in Waydroid.
# Default project: ~/Documents/Projects/elimtiyaz-android
# Logs: /tmp/waydroid-reinstall.log

set -Eeuo pipefail

SCRIPT_NAME="${0##*/}"
LOG_FILE="${WAYDROID_REINSTALL_LOG:-/tmp/waydroid-reinstall.log}"
DEFAULT_PROJECT="${HOME}/Documents/Projects/elimtiyaz-android"
PROJECT=""
APK=""
PACKAGE_EXPECTED=""
BUILD_APK=1
ASSUME_YES=0
EXPLICIT_APK=0

now() { date '+%H:%M:%S'; }
log() { printf '[%s] %s\n' "$(now)" "$*"; }
die() { log "ERROR: $*"; log "See ${LOG_FILE} for details."; exit 1; }
usage() {
  cat <<USAGE
Usage:
  ${SCRIPT_NAME} [options]

Default: clean-build the El-Imtiyaz Android debug APK, replace its existing
Waydroid installation, verify it through Android's package manager, then launch it.

Options:
  --project DIR   Android project directory containing gradlew and app/
  --apk FILE      Install this existing APK (does not build by default)
  --no-build      Use the project's existing app/build/outputs/apk/debug/app-debug.apk
  --package ID    Require the APK's actual package ID to equal ID
  --yes, -y       Do not ask before uninstalling the existing app
  --help, -h      Show this help

Examples:
  ./${SCRIPT_NAME}
  ./${SCRIPT_NAME} --no-build
  ./${SCRIPT_NAME} --yes
  ./${SCRIPT_NAME} --apk /path/to/app-debug.apk --yes
  ./${SCRIPT_NAME} --project /path/to/android-project --yes

Uninstalling an app can delete that app's local data. This script does not reset
Waydroid or remove unrelated apps. A container status of FROZEN is reported, but
is not treated as fatal if the Android package manager is responding correctly.
USAGE
}

# Keep a copy of all subsequent output in the log while still showing it live.
mkdir -p "$(dirname "$LOG_FILE")"
touch "$LOG_FILE" 2>/dev/null || {
  printf 'Cannot write log file: %s\n' "$LOG_FILE" >&2
  exit 1
}
exec > >(tee -a "$LOG_FILE") 2>&1

while (($#)); do
  case "$1" in
    --project)
      (($# >= 2)) || { usage; exit 2; }
      PROJECT="$2"; shift 2 ;;
    --apk)
      (($# >= 2)) || { usage; exit 2; }
      APK="$2"; EXPLICIT_APK=1; BUILD_APK=0; shift 2 ;;
    --no-build)
      BUILD_APK=0; shift ;;
    --package)
      (($# >= 2)) || { usage; exit 2; }
      PACKAGE_EXPECTED="$2"; shift 2 ;;
    --yes|-y)
      ASSUME_YES=1; shift ;;
    --help|-h)
      usage; exit 0 ;;
    --)
      shift; break ;;
    -* )
      usage >&2; die "Unknown option: $1" ;;
    * )
      # A positional APK path is accepted as a convenience.
      if [[ -z "$APK" && -f "$1" ]]; then
        APK="$1"; EXPLICIT_APK=1; BUILD_APK=0; shift
      else
        usage >&2; die "Unexpected argument: $1"
      fi ;;
  esac
done

if (($#)); then
  usage >&2
  die "Unexpected extra arguments: $*"
fi

log "Waydroid APK reinstall started"
log "Log file: ${LOG_FILE}"

# Locate project directory when building or using --no-build.
if [[ -z "$APK" ]]; then
  if [[ -n "$PROJECT" ]]; then
    :
  elif [[ -f "$PWD/gradlew" && -d "$PWD/app" ]]; then
    PROJECT="$PWD"
  else
    # Search the current directory and its parent chain before the known default.
    probe="$PWD"
    while [[ "$probe" != "/" ]]; do
      if [[ -f "$probe/gradlew" && -d "$probe/app" ]]; then
        PROJECT="$probe"
        break
      fi
      probe="$(dirname "$probe")"
    done
    [[ -n "$PROJECT" ]] || PROJECT="$DEFAULT_PROJECT"
  fi
  PROJECT="$(cd "$PROJECT" 2>/dev/null && pwd)" || die "Project directory does not exist: ${PROJECT}"
  [[ -f "$PROJECT/gradlew" ]] || die "Gradle wrapper not found: ${PROJECT}/gradlew"
  [[ -d "$PROJECT/app" ]] || die "Android app directory not found: ${PROJECT}/app"
  APK="${PROJECT}/app/build/outputs/apk/debug/app-debug.apk"
else
  APK="$(realpath "$APK" 2>/dev/null)" || die "APK path cannot be resolved: ${APK}"
fi

if [[ -n "$PROJECT" ]]; then
  log "Project: ${PROJECT}"
fi
log "APK target: ${APK}"

# Find the Waydroid executable without assuming a particular shell PATH.
if ! command -v waydroid >/dev/null 2>&1; then
  for candidate in /usr/bin/waydroid /usr/local/bin/waydroid /snap/bin/waydroid; do
    if [[ -x "$candidate" ]]; then
      PATH="$(dirname "$candidate"):$PATH"
      export PATH
      break
    fi
  done
fi
command -v waydroid >/dev/null 2>&1 || die "Waydroid executable not found. Install Waydroid or add it to PATH."
log "Waydroid executable: $(command -v waydroid)"

# Preserve the current desktop environment and provide a fallback for X11 sessions.
export DISPLAY="${DISPLAY:-:0}"

# Force a clean build by default so an old UP-TO-DATE APK is not silently reused.
if ((BUILD_APK)); then
  [[ -n "$PROJECT" ]] || die "Building requires --project; supply an Android project directory."
  log "Building a fresh debug APK (clean assembleDebug)..."
  if ! (cd "$PROJECT" && ./gradlew --no-daemon --console=plain clean assembleDebug); then
    die "Gradle build failed. The existing Waydroid installation has not been removed."
  fi
  log "Gradle build completed."
fi

[[ -f "$APK" ]] || die "APK not found: ${APK}"
[[ -s "$APK" ]] || die "APK exists but is empty: ${APK}"

# Reject obviously invalid/corrupt APK archives when unzip is available.
if command -v unzip >/dev/null 2>&1; then
  unzip -t "$APK" >/dev/null || die "APK archive validation failed: ${APK}"
fi
log "APK file details:"
ls -lh -- "$APK"
stat -c 'Modified: %y' -- "$APK" 2>/dev/null || true
sha256sum -- "$APK" 2>/dev/null || true

# Find Android SDK tools, including common SDK locations, to read the APK's real ID.
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-${HOME}/Android/Sdk}}"
find_sdk_tool() {
  local tool="$1" candidate
  if command -v "$tool" >/dev/null 2>&1; then
    command -v "$tool"
    return 0
  fi
  case "$tool" in
    apkanalyzer)
      for candidate in "$SDK_ROOT"/cmdline-tools/*/bin/apkanalyzer "$SDK_ROOT"/tools/bin/apkanalyzer; do
        [[ -x "$candidate" ]] && { printf '%s\n' "$candidate"; return 0; }
      done ;;
    aapt)
      for candidate in "$SDK_ROOT"/build-tools/*/aapt; do
        [[ -x "$candidate" ]] && { printf '%s\n' "$candidate"; return 0; }
      done ;;
    aapt2)
      for candidate in "$SDK_ROOT"/build-tools/*/aapt2; do
        [[ -x "$candidate" ]] && { printf '%s\n' "$candidate"; return 0; }
      done ;;
  esac
  return 1
}

PACKAGE_ACTUAL=""
if tool="$(find_sdk_tool apkanalyzer)"; then
  PACKAGE_ACTUAL="$("$tool" manifest application-id "$APK" 2>/dev/null | tr -d '\r' | tail -n 1 || true)"
fi
if [[ ! "$PACKAGE_ACTUAL" =~ ^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$ ]]; then
  if tool="$(find_sdk_tool aapt)"; then
    PACKAGE_ACTUAL="$("$tool" dump badging "$APK" 2>/dev/null | sed -n "s/^package: name='\([^']*\)'.*/\1/p" | head -n 1 || true)"
  fi
fi
if [[ ! "$PACKAGE_ACTUAL" =~ ^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$ ]]; then
  if tool="$(find_sdk_tool aapt2)"; then
    PACKAGE_ACTUAL="$("$tool" dump badging "$APK" 2>/dev/null | sed -n "s/^package: name='\([^']*\)'.*/\1/p" | head -n 1 || true)"
  fi
fi
[[ "$PACKAGE_ACTUAL" =~ ^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$ ]] || die "Could not read a valid application ID from the APK. Install Android SDK build-tools/cmdline-tools, or set ANDROID_SDK_ROOT correctly."
log "APK application ID: ${PACKAGE_ACTUAL}"

if [[ -n "$PACKAGE_EXPECTED" && "$PACKAGE_EXPECTED" != "$PACKAGE_ACTUAL" ]]; then
  die "Package mismatch: --package expected '${PACKAGE_EXPECTED}', but APK contains '${PACKAGE_ACTUAL}'. Nothing was uninstalled."
fi
PACKAGE="$PACKAGE_ACTUAL"

# Ask for administrator authentication before any package removal. `waydroid shell`
# requires root on this machine; using it without sudo caused the original false negative.
command -v sudo >/dev/null 2>&1 || die "sudo is required to query Android's package manager on this setup."
log "Checking administrator access for Android package-manager verification..."
sudo -v || die "Administrator authentication failed. Nothing was uninstalled."

waydroid_status() {
  waydroid status 2>&1 || true
}
status="$(waydroid_status)"
printf '%s\n' "$status"
if grep -qi 'Container:[[:space:]]*FROZEN' <<<"$status"; then
  log "WARNING: Waydroid reports Container: FROZEN. Continuing only if Android's package manager responds."
fi

# Bring up a user session if it is not already running. Do not reset/restart a running
# container just because status says FROZEN; package-manager responsiveness is the test.
if ! grep -qi 'Session:[[:space:]]*RUNNING' <<<"$status"; then
  log "Waydroid session is not reported as running; requesting session startup..."
  SESSION_LOG="/tmp/waydroid-session-start.log"
  nohup waydroid session start >"$SESSION_LOG" 2>&1 </dev/null &
  session_pid=$!
  session_ready=0
  for _ in {1..30}; do
    sleep 1
    status="$(waydroid_status)"
    if grep -qi 'Session:[[:space:]]*RUNNING' <<<"$status"; then
      session_ready=1
      break
    fi
    # If the CLI process exits quickly, keep polling status; a session can start anyway.
    kill -0 "$session_pid" 2>/dev/null || true
  done
  printf '%s\n' "$status"
  if (( ! session_ready )); then
    log "Session startup did not report RUNNING. Startup log: ${SESSION_LOG}"
    tail -n 40 "$SESSION_LOG" 2>/dev/null || true
    die "Waydroid session is not ready. No app was uninstalled."
  fi
fi

# Query all packages through the privileged Android shell and require a successful probe.
get_packages() {
  timeout 30 sudo waydroid shell pm list packages 2>&1
}
if ! PACKAGE_LIST="$(get_packages)"; then
  printf '%s\n' "$PACKAGE_LIST"
  die "Cannot query Android's package manager with 'sudo waydroid shell'. No app was uninstalled."
fi
if grep -qiE 'needs root access|permission denied|unable to connect|container.*not running|error:' <<<"$PACKAGE_LIST"; then
  printf '%s\n' "$PACKAGE_LIST"
  die "Android package-manager probe returned an error. No app was uninstalled."
fi

# Return codes: 0 = installed, 1 = conclusively absent, 2 = query failed.
# Never interpret a package-manager error as proof that the app is absent.
package_is_installed() {
  local output
  output="$(timeout 30 sudo waydroid shell pm list packages 2>&1)" || return 2
  if grep -qiE 'needs root access|permission denied|unable to connect|container.*not running|error:' <<<"$output"; then
    return 2
  fi
  if grep -Fxq "package:${PACKAGE}" <<<"$(printf '%s\n' "$output" | tr -d '\r')"; then
    return 0
  fi
  return 1
}
package_path() {
  timeout 30 sudo waydroid shell pm path "$PACKAGE" 2>&1
}

if grep -Fxq "package:${PACKAGE}" <<<"$(printf '%s\n' "$PACKAGE_LIST" | tr -d '\r')"; then
  OLD_PATH="$(package_path || true)"
  log "Existing installation detected: ${PACKAGE}"
  [[ -z "$OLD_PATH" ]] || printf '%s\n' "$OLD_PATH"
  if (( ! ASSUME_YES )); then
    printf '\nThis will uninstall %s and can delete its local app data.\n' "$PACKAGE"
    read -r -p 'Remove the old installation and install the new APK? [y/N] ' answer
    case "$answer" in
      y|Y|yes|YES) ;;
      *) log "Cancelled. Existing installation was left untouched."; exit 0 ;;
    esac
  else
    log "Confirmation skipped (--yes)."
  fi

  log "Removing existing application ${PACKAGE}..."
  # NOTE: `waydroid app remove` on this host prints "Failed with code: -3" /
  # "Failed to uninstall package" but STILL RETURNS EXIT CODE 0. Trusting the
  # exit status alone would make the script believe the removal succeeded when
  # it did not. Capture the output and treat the failure markers as failure
  # regardless of the exit status. The authoritative decision remains the
  # package-manager poll loop below; this only keeps the branch/log honest and
  # skips the pointless 10s wait before the fallback runs.
  REMOVE_OUT="$(timeout 60 waydroid app remove "$PACKAGE" 2>&1)" && REMOVE_RC=0 || REMOVE_RC=$?
  [[ -z "$REMOVE_OUT" ]] || printf '%s\n' "$REMOVE_OUT"
  if (( REMOVE_RC != 0 )) || grep -qiE 'failed with code|failed to uninstall|failed to remove' <<<"$REMOVE_OUT"; then
    log "'waydroid app remove' did not succeed (rc=${REMOVE_RC}); trying Android package manager uninstall as a fallback."
  else
    log "'waydroid app remove' reported success."
  fi

  # Poll after CLI removal. Only proceed when the package manager confirms absence;
  # query failures are retried and never treated as a successful removal.
  removal_verified=0
  for _ in {1..10}; do
    if package_is_installed; then
      : # Still installed; continue polling.
    else
      state=$?
      if (( state == 1 )); then
        removal_verified=1
        break
      fi
      log "Package-manager query failed while checking removal; retrying."
    fi
    sleep 1
  done

  # If the Waydroid CLI did not remove the app, try Android's package manager.
  if (( ! removal_verified )); then
    if package_is_installed; then
      # NOTE: do NOT pass "--user 0" — waydroid's own argparse intercepts the
      # "--user" flag before it reaches pm ("unrecognized arguments: --user 0"),
      # which is what made this fallback fail. pm uninstall defaults to user 0
      # (the owner) anyway, so the bare form works.
      fallback_out="$(timeout 60 sudo waydroid shell pm uninstall "$PACKAGE" 2>&1 || true)"
      printf '%s\n' "$fallback_out"
    else
      state=$?
      if (( state == 1 )); then
        removal_verified=1
      fi
    fi
  fi

  # Verify again after the fallback, allowing package-manager state to settle.
  if (( ! removal_verified )); then
    for _ in {1..10}; do
      if package_is_installed; then
        : # Still installed.
      else
        state=$?
        if (( state == 1 )); then
          removal_verified=1
          break
        fi
        log "Package-manager query failed while confirming removal; retrying."
      fi
      sleep 1
    done
  fi

  (( removal_verified )) || die "Could not confirm that the old package was removed. Refusing to continue."
  log "Old package removal verified."
else
  log "No existing installation found for ${PACKAGE}; nothing to remove."
fi

log "Installing APK..."
if ! timeout 180 waydroid app install "$APK"; then
  log "Waydroid installation command failed. Collecting diagnostics..."
  waydroid_status
  timeout 20 sudo waydroid shell pm list packages 2>&1 | grep -F "$PACKAGE" || true
  timeout 20 sudo waydroid shell pm path "$PACKAGE" 2>&1 || true
  waydroid log -n 40 2>&1 || true
  die "APK installation command failed."
fi

log "Verifying installation through Android's package manager..."
verified=0
for _ in {1..20}; do
  if package_is_installed; then
    candidate_path="$(package_path 2>&1 || true)"
    if grep -q '^package:' <<<"$(printf '%s\n' "$candidate_path" | tr -d '\r')"; then
      printf '%s\n' "$candidate_path"
      verified=1
      break
    fi
  fi
  sleep 1
done

if (( ! verified )); then
  log "Android package manager did not confirm ${PACKAGE}. Launcher listing is diagnostic only."
  log "Package list output:"
  timeout 30 sudo waydroid shell pm list packages 2>&1 | grep -F -C 2 "$PACKAGE" || true
  log "Package path output:"
  timeout 30 sudo waydroid shell pm path "$PACKAGE" 2>&1 || true
  log "Waydroid status:"
  waydroid_status
  log "Recent Waydroid logs:"
  waydroid log -n 60 2>&1 || true
  die "Installation could not be verified; the script will not claim success or launch an unverified package."
fi

log "Installation verified: ${PACKAGE}"
log "Launching ${PACKAGE}..."
if timeout 60 waydroid app launch "$PACKAGE"; then
  log "SUCCESS: APK installed, package-manager verification passed, and launch command succeeded."
else
  launch_code=$?
  log "WARNING: Installation is verified, but the launch command returned exit code ${launch_code}."
  log "Try launching the app from the Waydroid launcher; check 'waydroid status' and the logs if it does not open."
  exit "$launch_code"
fi

log "Done. APK SHA-256: $(sha256sum "$APK" | awk '{print $1}')"
log "Full log: ${LOG_FILE}"
