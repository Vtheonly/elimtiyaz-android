#!/usr/bin/env bash
# =============================================================================
# Build, Start Waydroid, Remove Old Build, Install & Launch New Version
# Package: com.aistudio.elimtiyazstaff.bxmzlx
# =============================================================================
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PACKAGE_ID="com.aistudio.elimtiyazstaff.bxmzlx"
APK_PATH="${SCRIPT_DIR}/app/build/outputs/apk/debug/app-debug.apk"

log() { printf '\033[1;34m[%s]\033[0m %s\n' "$(date '+%H:%M:%S')" "$*"; }
success() { printf '\033[1;32m[%s] SUCCESS:\033[0m %s\n' "$(date '+%H:%M:%S')" "$*"; }
warn() { printf '\033[1;33m[%s] WARNING:\033[0m %s\n' "$(date '+%H:%M:%S')" "$*"; }
err() { printf '\033[1;31m[%s] ERROR:\033[0m %s\n' "$(date '+%H:%M:%S')" "$*" >&2; exit 1; }

cd "${SCRIPT_DIR}"

log "=== Starting El-Imtiyaz Waydroid Build & Deploy ==="

# -----------------------------------------------------------------------------
# 1. Build the APK
# -----------------------------------------------------------------------------
log "Step 1/5: Building debug APK..."
if [[ -x "${SCRIPT_DIR}/gradlew" ]]; then
  GRADLE_CMD="${SCRIPT_DIR}/gradlew"
elif command -v gradle >/dev/null 2>&1; then
  GRADLE_CMD="gradle"
else
  err "Neither gradlew nor gradle was found on your system."
fi

log "Running: ${GRADLE_CMD} assembleDebug"
"${GRADLE_CMD}" assembleDebug --console=plain || err "Gradle build failed."

[[ -f "${APK_PATH}" ]] || err "Built APK not found at ${APK_PATH}"
log "APK built successfully: $(ls -lh "${APK_PATH}" | awk '{print $5}')"

# -----------------------------------------------------------------------------
# 2. Check & Start Waydroid
# -----------------------------------------------------------------------------
log "Step 2/5: Checking Waydroid status..."
command -v waydroid >/dev/null 2>&1 || err "Waydroid CLI not found. Please ensure Waydroid is installed and in PATH."

export DISPLAY="${DISPLAY:-:0}"
export WAYLAND_DISPLAY="${WAYLAND_DISPLAY:-wayland-0}"

status="$(waydroid status 2>&1 || true)"

# Check container status
if ! grep -qiE 'Container:[[:space:]]*(RUNNING|FROZEN)' <<<"$status"; then
  log "Waydroid container is not running; starting container..."
  if command -v sudo >/dev/null 2>&1; then
    sudo systemctl restart waydroid-container 2>/dev/null || sudo waydroid container start 2>/dev/null || true
  fi
  sleep 2
  status="$(waydroid status 2>&1 || true)"
fi

# Check session status
if ! grep -qi 'Session:[[:space:]]*RUNNING' <<<"$status"; then
  log "Waydroid session is not running; starting session..."
  nohup waydroid session start >/tmp/waydroid-session.log 2>&1 &
  
  session_ready=0
  for i in {1..30}; do
    sleep 1
    status="$(waydroid status 2>&1 || true)"
    if grep -qi 'Session:[[:space:]]*RUNNING' <<<"$status"; then
      session_ready=1
      break
    fi
  done
  
  if (( ! session_ready )); then
    warn "Session start not confirmed RUNNING within 30s. Checking if Waydroid responds anyway..."
  else
    log "Waydroid session is now RUNNING."
  fi
else
  log "Waydroid session is already RUNNING."
fi

# -----------------------------------------------------------------------------
# 3. Remove Old Build
# -----------------------------------------------------------------------------
log "Step 3/5: Removing old installation of ${PACKAGE_ID}..."

is_installed() {
  local pkgs=""
  if command -v sudo >/dev/null 2>&1; then
    pkgs="$(timeout 10 sudo waydroid shell pm list packages 2>/dev/null || true)"
  fi
  if [[ -z "$pkgs" ]]; then
    pkgs="$(timeout 10 waydroid app list 2>/dev/null || true)"
  fi
  grep -qi "${PACKAGE_ID}" <<<"$pkgs"
}

if is_installed; then
  log "Old version detected. Uninstalling..."
  # Try waydroid app remove
  waydroid app remove "${PACKAGE_ID}" >/dev/null 2>&1 || true
  
  # Try pm uninstall as fallback
  if command -v sudo >/dev/null 2>&1; then
    sudo waydroid shell pm uninstall "${PACKAGE_ID}" >/dev/null 2>&1 || true
  fi
  
  # Wait for removal confirmation
  for _ in {1..10}; do
    if ! is_installed; then
      break
    fi
    sleep 1
  done
  log "Old version removed."
else
  log "No existing installation found (clean slate)."
fi

# -----------------------------------------------------------------------------
# 4. Install the New APK
# -----------------------------------------------------------------------------
log "Step 4/5: Installing new APK into Waydroid..."
waydroid app install "${APK_PATH}" || {
  # Try fallback with Android package manager via shell
  if command -v sudo >/dev/null 2>&1; then
    log "Retrying install via Android pm..."
    sudo waydroid shell pm install -r "${APK_PATH}" || err "Failed to install APK."
  else
    err "Failed to install APK."
  fi
}

# Verify installation
verified=0
for _ in {1..15}; do
  if is_installed; then
    verified=1
    break
  fi
  sleep 1
done

if (( ! verified )); then
  warn "Package manager has not confirmed installation yet. Proceeding with launch attempt..."
else
  log "Installation verified!"
fi

# -----------------------------------------------------------------------------
# 5. Launch the New Version
# -----------------------------------------------------------------------------
log "Step 5/5: Launching ${PACKAGE_ID}..."
if waydroid app launch "${PACKAGE_ID}"; then
  success "El-Imtiyaz Staff app launched successfully in Waydroid!"
else
  warn "App launch command exited with code $?. You can open it from the Waydroid launcher drawer."
fi

log "=== Finished ==="
