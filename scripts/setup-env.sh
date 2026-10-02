#!/usr/bin/env bash
# =============================================================================
# El-Imtiyaz Android — Supabase token setup (the AGENTS.md §8.1 fix, in-repo)
# =============================================================================
# PURPOSE
#   Creates or repairs the ROOT-level `.env` that the secrets-gradle-plugin
#   reads (propertiesFileName = ".env", resolved against the ROOT project —
#   NEVER app/.env; see AGENTS.md §8.1). Without a root .env whose key slots
#   are NON-EMPTY, the plugin emits bare `SUPABASE_ANON_KEY = ;` literals
#   into the generated BuildConfig and the build FAILS TO COMPILE.
#
# SECURITY MODEL (ADR-009 — dual acceptance, publishable-preferred)
#   - The ONLY values this script writes by default are PUBLIC client
#     identifiers: the project URL and the sb_publishable_… key. They are
#     protected by Row-Level Security, not by secrecy (the same values are
#     committed in the website's public-config.ts).
#   - It REFUSES to write any secret-class value: sb_secret_…, sbp_…
#     access tokens, service_role keys, or anything passed as a service key.
#     Those must NEVER ship inside the APK (they would bypass RLS).
#   - The generated .env is gitignored (this script verifies it).
#   - Key values are never printed in full — only a short fingerprint.
#
# USAGE
#   ./scripts/setup-env.sh                 # canonical public defaults
#   ./scripts/setup-env.sh --verify        # + live auth/v1/health probe
#   ./scripts/setup-env.sh --url URL --key sb_publishable_…   # custom project
#   ./scripts/setup-env.sh --force         # overwrite an existing .env
#   ./scripts/setup-env.sh --check         # only validate the current .env
# =============================================================================
set -euo pipefail

cd "$(dirname "$0")/.."
ROOT_DIR="$(pwd)"
ENV_FILE="$ROOT_DIR/.env"
ENV_EXAMPLE="$ROOT_DIR/.env.example"

# ── Canonical public defaults (ADR-009: committed public identifiers) ──────
DEFAULT_URL="https://vebfehrpzajhstyhinnw.supabase.co"
DEFAULT_KEY="sb_publishable_IPUtQMYQzr1wNnfGTcl5MA_wuz3RUdg"
# JWKS is derived from the URL (credentials sheet §2.1):
derive_jwks() { printf '%s/auth/v1/.well-known/jwks.json' "$1"; }

URL="$DEFAULT_URL"
KEY="$DEFAULT_KEY"
ANON_KEY=""          # empty = same value as KEY (dual acceptance)
DO_VERIFY=0
FORCE=0
CHECK_ONLY=0

say()  { printf '%s\n' "$*"; }
die()  { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
fingerprint() { local v="$1"; printf '%s…(%d chars)' "${v:0:10}" "${#v}"; }

# ── Security guard: reject secret-class values ──────────────────────────────
assert_public_key() {
  local v="$1" label="$2"
  [ -n "$v" ] || die "$label is empty — the secrets plugin would emit a bare literal and break compilation (AGENTS.md §8.1)."
  case "$v" in
    sb_secret_*|sbp_*|service_role*|SUPABASE_SERVICE_KEY*)
      die "$label looks like a SECRET-class credential ($label starts with '${v:0:11}'). Secrets must NEVER be written to .env — they ship inside the APK and bypass RLS (T-064/SEC-005)."
      ;;
    sb_publishable_*) ;;   # the ADR-009 preferred public identifier
    eyJ*) ;;               # legacy anon JWT (public by design, dual-accepted)
    *) die "$label has an unrecognized format (expected sb_publishable_… or a legacy anon JWT). Refusing to write it." ;;
  esac
}

assert_url() {
  local v="$1"
  case "$v" in
    https://*.supabase.co) ;;
    *) die "SUPABASE_URL must look like https://<project-ref>.supabase.co (got: $v)" ;;
  esac
}

# ── Args ─────────────────────────────────────────────────────────────────────
while [ $# -gt 0 ]; do
  case "$1" in
    --url)       URL="${2:?--url needs a value}"; shift 2 ;;
    --key)       KEY="${2:?--key needs a value}"; shift 2 ;;
    --anon-key)  ANON_KEY="${2:?--anon-key needs a value}"; shift 2 ;;
    --verify)    DO_VERIFY=1; shift ;;
    --force)     FORCE=1; shift ;;
    --check)     CHECK_ONLY=1; shift ;;
    -h|--help)   sed -n '2,30p' "$0"; exit 0 ;;
    *) die "unknown argument: $1 (see --help)" ;;
  esac
done
[ -n "$ANON_KEY" ] || ANON_KEY="$KEY"

assert_url "$URL"
assert_public_key "$KEY" "SUPABASE_PUBLISHABLE_KEY"
assert_public_key "$ANON_KEY" "SUPABASE_ANON_KEY"
JWKS="$(derive_jwks "$URL")"

# ── --check: validate the existing .env and exit ────────────────────────────
if [ "$CHECK_ONLY" -eq 1 ]; then
  [ -f "$ENV_FILE" ] || die "no .env found at $ENV_FILE — run ./scripts/setup-env.sh"
  ok=1
  check_slot() { # name, allowed-to-be-empty?
    local val
    val="$(sed -n "s/^$1=//p" "$ENV_FILE" | tail -n1)"
    if [ -z "$val" ]; then
      if [ "${2:-0}" = "1" ]; then
        say "  [BROKEN] $1 is EMPTY → bare BuildConfig literal → compilation fails (AGENTS.md §8.1)"
        ok=0
      else
        say "  [ok]     $1 (empty)"
      fi
    else
      if printf '%s' "$val" | grep -qE '^(sb_secret_|sbp_|service_role)'; then
        say "  [DANGER] $1 holds a SECRET-class value — remove it immediately (bypasses RLS if shipped)"
        ok=0
      else
        say "  [ok]     $1 = $(fingerprint "$val")"
      fi
    fi
  }
  say "Checking $ENV_FILE"
  check_slot SUPABASE_URL 1
  check_slot SUPABASE_ANON_KEY 1
  check_slot SUPABASE_PUBLISHABLE_KEY 1
  check_slot SUPABASE_JWKS_URL 1
  [ "$ok" -eq 1 ] || die ".env validation FAILED"
  say ".env OK"
  exit 0
fi

# ── Write / repair the root .env ─────────────────────────────────────────────
if [ -f "$ENV_FILE" ] && [ "$FORCE" -ne 1 ]; then
  say ".env already exists — repairing empty key slots in place (use --force to fully reset)."
  ensure_slot() { # name, value
    if ! grep -q "^$1=" "$ENV_FILE"; then
      printf '%s=%s\n' "$1" "$2" >> "$ENV_FILE"
      say "  [added]  $1 = $(fingerprint "$2")"
    elif [ -z "$(sed -n "s/^$1=//p" "$ENV_FILE" | tail -n1)" ]; then
      sed -i "s|^$1=$|$1=$2|" "$ENV_FILE"
      say "  [filled] $1 (was empty — the compile-breaking state)"
    else
      say "  [kept]   $1 (already set: $(fingerprint "$(sed -n "s/^$1=//p" "$ENV_FILE" | tail -n1)"))"
    fi
  }
  ensure_slot SUPABASE_URL "$URL"
  ensure_slot SUPABASE_ANON_KEY "$ANON_KEY"
  ensure_slot SUPABASE_PUBLISHABLE_KEY "$KEY"
  ensure_slot SUPABASE_JWKS_URL "$JWKS"
else
  if [ -f "$ENV_FILE" ]; then cp "$ENV_FILE" "$ENV_FILE.bak"; say "backup: .env.bak"; fi
  cat > "$ENV_FILE" <<EOF
# Generated by scripts/setup-env.sh — PUBLIC client identifiers only (ADR-009).
# NEVER add sb_secret_…, sbp_…, or service_role values here (they ship inside
# the APK and bypass RLS). This file is gitignored.
SUPABASE_URL=$URL
SUPABASE_ANON_KEY=$ANON_KEY
SUPABASE_PUBLISHABLE_KEY=$KEY
SUPABASE_JWKS_URL=$JWKS
EOF
  say "Created $ENV_FILE"
  say "  SUPABASE_URL             = $URL"
  say "  SUPABASE_ANON_KEY        = $(fingerprint "$ANON_KEY")"
  say "  SUPABASE_PUBLISHABLE_KEY = $(fingerprint "$KEY")"
  say "  SUPABASE_JWKS_URL        = $JWKS"
fi

# ── Gitignore guard ──────────────────────────────────────────────────────────
if git check-ignore -q "$ENV_FILE"; then
  say "[ok] .env is gitignored (verified)"
else
  die ".env is NOT gitignored — add it to .gitignore before proceeding. Refusing to leave a credential file in a committable state."
fi

# ── Optional live probe ──────────────────────────────────────────────────────
if [ "$DO_VERIFY" -eq 1 ]; then
  say "Probing ${URL}/auth/v1/health …"
  status="$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 \
    -H "apikey: $ANON_KEY" "${URL}/auth/v1/health" || echo 000)"
  if [ "$status" = "200" ]; then
    say "[ok] live auth/v1/health → 200 (URL + key accepted)"
  else
    die "live probe failed (HTTP $status) — check the URL/key or your connection"
  fi
fi

say "Done. Build with: ./gradlew assembleDebug   (test: ./gradlew test)"
