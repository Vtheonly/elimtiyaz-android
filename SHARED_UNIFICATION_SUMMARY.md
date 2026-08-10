# Shared Unification — Summary of Changes

This document summarizes all changes made to the Android repository as part
of the shared unification of the Desktop and Android applications against a
single Supabase backend.

## The contract: migration `0027_shared_unification.sql`

The canonical SQL migration lives in the Desktop repo at
`supabase/migrations/0027_shared_unification.sql`. It is the shared contract
between Desktop, Android, and Supabase.

What it adds (relevant to Android):

- `parents.display_name` (TEXT) — preserves the COMPLETE parent name.
- `students.display_name` (TEXT) — parity with parents.
- `payments.receipt_number` + `category` columns.
- `ledger_entries` unified columns: `source_type`, `source_id`, `method`,
  `receipt_number`, `payment_status`, `reverses_id`, `actor_id`,
  `actor_name`, `at`, `metadata`.
- `sync_queue` table — shared outbound mutation queue (Desktop + Android).
- `device_tokens` table — FCM/APNS token registry.
- SECURITY DEFINER RPCs that Android calls:
  - `register_fcm_token(p_user_id, p_token, p_platform)` — already invoked
    by `FcmTokenRegistrar`; now backed by a real table.
  - `upsert_parent_from_import`, `upsert_student_from_import`,
    `upsert_payment_from_import`, `upsert_ledger_entry_from_import` —
    idempotent write paths shared with Desktop.
  - `mark_sync_queue_processed(p_id, p_status, p_error)`.
  - `pull_parents_for_sync`, `pull_students_for_sync`, `pull_payments_for_sync`,
    `pull_ledger_entries_for_sync`, `pull_device_tokens_for_sync` — read
    changed rows since a watermark.

## Fixes applied

### `UserProfileDto` snake_case decoding

- **OLD**: `UserProfileDto` used camelCase property names without
  `@SerialName`. The Kotlin serialization framework would look for
  camelCase JSON keys and silently fall back to defaults — dropping
  `tenant_id`, `display_name`, `role_id`, etc. and forcing the user into
  demo mode.
- **NEW**: Every field has an explicit `@SerialName("snake_case")`.
  `tenantId` is nullable (Postgres may return null for unbound profiles)
  with a fallback to `"ten-elimtiyaz-001"` in `LocalAuthRepository`.

### Shared DTOs

- **NEW**: `app/src/main/java/com/example/infrastructure/supabase/SharedDtos.kt`
  declares snake_case DTOs for `ParentDto`, `StudentDto`, `PaymentDto`,
  `LedgerEntryDto`, `DeviceTokenDto`, and the upsert RPC result wrappers.
  Every field has an explicit `@SerialName` matching the PostgreSQL column
  name.

### Real `SyncQueueDispatcher.pushEntry`

- **OLD**: `SyncQueueDispatcher.pushEntry` was a no-op stub. Every queued
  mutation was silently discarded after being marked "synced".
- **NEW**: The dispatcher injects `SupabaseClientProvider` +
  `SessionManager` and routes by `entry.entity`:
  - `parent` → `upsert_parent_from_import` RPC
  - `student` → `upsert_student_from_import` RPC
  - `payment` → `upsert_payment_from_import` RPC
  - `ledger_entry` → `upsert_ledger_entry_from_import` RPC
  Each call is wrapped in `NetworkTimeouts.guard(tag, timeoutMs = 5_000L)`.
  Mock entries are skipped (defense in depth). When Supabase isn't
  configured, the dispatcher silently no-ops.

### Parent / Student displayName

- `domain/model/Parent.kt`: Added `displayName: String? = null`. Updated
  `fullName` getter to prefer `displayName` (trimmed) and fall back to
  `firstName + " " + lastName` only when `displayName` is null/empty.
- `domain/model/Student.kt`: Same treatment.
- `domain/repository/ParentRepository.kt`: Added `displayName` to
  `CreateParentInput` + `UpdateParentInput`.
- `domain/repository/StudentRepository.kt`: Added `displayName` to
  `CreateStudentInput` + `UpdateStudentInput`.
- `infrastructure/room/Entities.kt`: Added `displayName` to
  `ParentCacheEntity` + `StudentCacheEntity`.
- `infrastructure/room/LocalEntities.kt`: Added `displayName` to
  `ParentEntity` + `StudentEntity`.
- `infrastructure/room/ElImtiyazDatabase.kt`: Bumped version 2 → 3.
- `infrastructure/room/LocalMappers.kt`: `ParentEntity.toDomain()` and
  `StudentEntity.toDomain()` pass `displayName` through.
- `infrastructure/room/CacheMappers.kt`: All 4 mappers (Parent ↔ Cache,
  Student ↔ Cache) preserve `displayName`.
- `infrastructure/room/DatabaseSeeder.kt`: Updated 3 ParentEntity + 6
  StudentEntity seed entries to include `displayName`.
- `infrastructure/local/LocalRepositories.kt`: `createParent`,
  `createStudent`, and `batchRegister` populate `displayName` from input
  or derive from `firstName + " " + lastName`.

### Auth tenant fallback

- `infrastructure/local/LocalRepositories.kt`: `LocalAuthRepository.signIn`
  and `refreshSession` now pass `tenantId ?: "ten-elimtiyaz-001"` to the
  `Session` constructor (UserProfileDto.tenantId is now nullable).

## Build verification

Installed Android SDK 35 + cmdline-tools + JDK 21 (Temurin). Ran:

```
./gradlew :app:compileDebugKotlin --no-daemon
```

Result: **BUILD SUCCESSFUL** in 1m 56s. All Kotlin code compiles cleanly.
Only deprecation warnings (`Icons.Filled.ArrowBack` → `Icons.AutoMirrored`)
which are pre-existing and unrelated to this work.

## Files changed

- `app/src/main/java/com/example/infrastructure/supabase/UserProfileDto.kt`
- `app/src/main/java/com/example/infrastructure/supabase/SharedDtos.kt` (NEW)
- `app/src/main/java/com/example/domain/model/Parent.kt`
- `app/src/main/java/com/example/domain/model/Student.kt`
- `app/src/main/java/com/example/domain/repository/ParentRepository.kt`
- `app/src/main/java/com/example/domain/repository/StudentRepository.kt`
- `app/src/main/java/com/example/infrastructure/room/Entities.kt`
- `app/src/main/java/com/example/infrastructure/room/LocalEntities.kt`
- `app/src/main/java/com/example/infrastructure/room/ElImtiyazDatabase.kt`
- `app/src/main/java/com/example/infrastructure/room/LocalMappers.kt`
- `app/src/main/java/com/example/infrastructure/room/CacheMappers.kt`
- `app/src/main/java/com/example/infrastructure/room/DatabaseSeeder.kt`
- `app/src/main/java/com/example/infrastructure/local/LocalRepositories.kt`
- `app/src/main/java/com/example/infrastructure/sync/SyncQueueDispatcher.kt`
- `local.properties` (NEW — points at /home/z/android-sdk for the build)

## How Android and Desktop share the same data

Both clients call the SAME Supabase RPCs (declared in migration 0027):

```
                Supabase (PostgreSQL)
                       |
       ┌───────────────┴───────────────┐
       ↓                               ↓
   Desktop app                     Android app
   (Vite/React/TS)                (Kotlin/Compose/Room)
       │                               │
       ├─ upsert_parent_from_import    ├─ upsert_parent_from_import
       ├─ upsert_student_from_import   ├─ upsert_student_from_import
       ├─ upsert_payment_from_import   ├─ upsert_payment_from_import
       ├─ upsert_ledger_entry_...      ├─ upsert_ledger_entry_...
       └─ sync_queue (audit trail)     ├─ sync_queue (offline writes)
                                        ├─ register_fcm_token
                                        └─ pull_*_for_sync (reads)
```

Idempotency is guaranteed at the database layer (stable identifiers +
unique indexes), so re-pushing the same record from either client never
creates duplicates.
