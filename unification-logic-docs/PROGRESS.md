# Unification Logic — Android Repository Progress

**Repo:** `Vtheonly/elimtiyaz-android`
**Branch:** `unify-financial-logic`
**Last updated:** 2026-08-20
**Authoritative spec:** `docs/CANONICAL-FINANCIAL-LOGIC.md` (committed in this repo)

This document tracks what has been completed, what remains, and what the
next iteration should focus on, in the Android repository specifically.
For the desktop side, see the matching `unification-logic-docs/` folder
in `Vtheonly/AgentGithubUplaod`.

---

## 1. Tier 1 Status (Canonical Foundation — COMPLETE)

All 8 Tier 1 recommendations from the audit (`financial-logic-comparison-v2.md`)
have been implemented in the Android repository.

### R2 — Enum extension (COMPLETE)

**File:** `app/src/main/java/com/example/core/Ledger.kt`

Added 4 new `PaymentCategory` values:
- `PARENT_CREDIT("parent_credit")`
- `THERAPY_PSYCHOLOGY("therapy_psychology")`
- `THERAPY_SPEECH("therapy_speech")`
- `SECOND_APRON("second_apron")`

Added 2 new `PaymentStatus` values:
- `PENDING_CLEARANCE("pending_clearance")`
- `UNPAID("unpaid")`

Added new `PaymentPlan` enum:
- `FULL_ANNUAL("full_annual")`
- `TRANCHES("tranches")`

All `fromCode` methods are now **total** — they return a sentinel default
(`OTHER` for PaymentCategory, `null` for PaymentStatus, `PENDING` for
PaymentStatus via `fromCodeOrDefault`) instead of throwing. Future
migrations adding new codes will not crash older clients.

### R3 — `unallocatedCredit` rollup (COMPLETE)

**Files:**
- `app/src/main/java/com/example/core/AccountBalance.kt`
- `app/src/main/java/com/example/core/ParentLedgerSummary.kt`
- `app/src/main/java/com/example/core/LedgerEngine.kt`

`AccountBalance` now has a `unallocatedCredit: Long` field (negative
number = banked credit, 0 = no credit). `ParentLedgerSummary` rolls it
up as `totalUnallocatedCredit`.

`LedgerEngine.computeAccountBalance` tracks `parent_credit` adjustments
separately from `totalAdjusted` so callers can auto-absorb them on
future charges. `computeParentSummary` aggregates per-account values.

**Overdue threshold** lowered from `> 100L centimes` (= 1 DZD) to `> 0L`
(= 0.001 DZD floor) — matches desktop's `> 0.001 DZD` threshold so a
half-DZD outstanding is flagged overdue on both apps.

### R4 — Overpayment credit account (COMPLETE)

**File:** `app/src/main/java/com/example/infrastructure/local/LocalRepositories.kt`

`LocalPaymentRepository.collect` now writes the overpayment credit
adjustment on:
- `category = PaymentCategory.PARENT_CREDIT`
- `studentId = null` (parent-scoped, NOT student-scoped)
- `accountId = deriveAccountId(parentId, PARENT_CREDIT, null)`
  → `"parent:{parentId}:category:parent_credit"`

The previous implementation wrote the credit on the input category's
student-scoped account (e.g. `tuition:student:X`), causing the desktop
reconciler to raise `UNBACKED_PARENT_CREDIT` and the auto-absorb logic
to miss the credit.

### R5 — Refund LIFO branch (COMPLETE)

**File:** `app/src/main/java/com/example/infrastructure/local/LocalRepositories.kt`

`LocalPaymentRepository.refund` now passes
`originalWasPending = (originalLedger.paymentStatus == PaymentStatus.PENDING.code)`
to `revertPaymentAllocation`. Previously `originalWasPending` defaulted
to `false`, so refunding an uncleared (pending) check/transfer tried to
subtract from `amountPaid` (= 0 for a pending payment) — a silent no-op
that left `amountPending` inflated.

### R6 — 5-rule discount engine (COMPLETE)

**File:** `app/src/main/java/com/example/core/DiscountEngine.kt` (NEW)

Created a Kotlin port of the desktop's
`domain/calc/pricing/discount-engine.ts` + `discount-rules.ts`. The
engine evaluates all 5 canonical rules in a SINGLE PASS on the gross
annual tuition:

| # | Code             | Condition                                                                  | Amount                       |
|---|------------------|----------------------------------------------------------------------------|------------------------------|
| 1 | `passage_palier` | Student transitioned 5ap → 1am OR 4am → 1ere_annee                        | −10,000 DZD (fixed)          |
| 2 | `sibling_fixed`  | Student has siblings in the same family also enrolled this year           | −5,000 DZD × (childrenCount − 1) |
| 3 | `full_annual`    | `paymentPlan == full_annual` AND payment date ≤ June 30 of start year     | −10% of gross (percentage)   |
| 4 | `highest_average`| Student was rank 1 in their previous palier last year                     | −10% of gross (percentage)   |
| 5 | `seniority_5y`   | Student enrolled ≥ 5 years before academic year start                      | −5% of gross (percentage)    |

All money is in centimes (Long). Percentage rules apply to the gross
pre-discount amount, not the running total (no compounding).

`LocalStudentRepository.batchRegister` now uses `evaluateAllSystemDiscounts`
instead of inline sibling-only logic. Branches on `paymentPlan`:
- `full_annual`: 1 charge entry with metadata `{ tranche: null, paymentPlan: "full_annual", ... }`
- `tranches`: 3 charge entries with metadata `{ tranche: 1|2|3, paymentPlan: "tranches", ... }`

`CreateStudentInput` gained 4 optional fields:
- `previousGradeLevel: String?`
- `paymentPlan: String?`
- `enrollmentDate: String?`
- `previousRank: Int?`

### R7 — SyncSupport wiring (COMPLETE)

**Files:**
- `app/src/main/java/com/example/infrastructure/sync/SyncSupport.kt`
- `app/src/main/java/com/example/infrastructure/local/LocalRepositories.kt`

Added `SyncSupport.enqueueOnly` helper — a non-throwing wrapper around
`syncService.enqueue` that returns the queue entry ID (or null on
failure). It's the canonical pattern for "local Room write happened,
enqueue the same operation for Supabase push".

`SyncSupport?` is now injected into all four `Local*Repository` classes:

| Repository                | Methods that now enqueue to the sync queue                |
|---------------------------|-------------------------------------------------------------|
| `LocalPaymentRepository`  | `collect` (payment + ledger entry + parent_credit adjustment), `refund` (payment status + reversal ledger entry), `adjust` (adjustment ledger entry) |
| `LocalStudentRepository`  | `batchRegister` (parent + N students + N ledger entries + N installments) |
| `LocalLedgerRepository`   | `append` (single ledger entry), `appendMany` (loop), `reverse` (reversal ledger entry) |
| `LocalInstallmentRepository` | `markPaid` (installment update with amount_paid=amount_due), `updateDueDate` (installment update with custom schedule) |

All four repositories use the same `syncJson` + `toSyncPayload` pattern
to serialize entities for the queue payload.

### R8 — SyncQueueDispatcher unit conversion (COMPLETE)

**File:** `app/src/main/java/com/example/infrastructure/sync/SyncQueueDispatcher.kt`

`pushPayment` and `pushLedgerEntry` both:
- Convert centimes → DZD (`amount / 100.0`) before sending to the
  upsert RPC. Previously sent raw centimes as DZD — a 100× inflation.
- Include `p_installment_id`, `p_check_number`, `p_check_bank_name`,
  `p_check_issue_date`, `p_check_clearance_date`, `p_transfer_reference`,
  `p_transfer_source_bank` on `pushPayment` (when present in payload).
- Include `p_metadata` (JSONB) on `pushLedgerEntry`, parsed from the
  payload's `metadataJson` string field.

### R11 — Metadata persistence (COMPLETE, supporting R8)

**Files:**
- `app/src/main/java/com/example/infrastructure/room/LocalEntities.kt`
- `app/src/main/java/com/example/infrastructure/room/ElImtiyazDatabase.kt`
- `app/src/main/java/com/example/infrastructure/room/LocalMappers.kt`
- `app/src/main/java/com/example/infrastructure/supabase/SharedDtos.kt`
- `app/src/main/java/com/example/infrastructure/supabase/SharedDtoMappers.kt`

`LedgerEntryEntity` gained a `metadataJson: String = "{}"` column.
Room migration v3 → v4 (`ElImtiyazDatabase.MIGRATION_3_4`) ALTERs
`ledger_entries` to add the column with default `'{}'`. Database
version bumped to 4.

`LocalMappers` gained `parseMetadataJson(raw: String?)` and
`serializeMetadataJson(metadata: Map<String, Any?>)` helpers.
`LedgerEntryEntity.toDomain()` now parses `metadataJson` instead of
hardcoding `emptyMap()`.

`SharedDtos.LedgerEntryDto` gained a `metadata: JsonElement?` field
that decodes the Supabase JSONB column. `SharedDtoMappers.LedgerEntryDto.toEntity()`
stores it verbatim in `metadataJson`.

The private `toEntity()` helper in `LocalRepositories.kt` now
serializes the domain `LedgerEntry.metadata` map via
`LocalMappers.serializeMetadataJson`.

---

## 2. Cross-Platform Consistency Tests (COMPLETE)

**Files:**
- `financial-tests/README.md` — DSL specification
- `financial-tests/scenarios/*.yml` — 8 scenario files
- `app/src/test/java/com/example/core/CrossPlatformScenarioRunner.kt` — Kotlin runner

8 scenario files covering the canonical cases:

| Scenario                              | What it tests                                                |
|---------------------------------------|--------------------------------------------------------------|
| `single_payment_partial`              | INV-1 (balance via replay) + INV-6 (waterfall allocation)  |
| `overpayment_creates_parent_credit`   | INV-7 (overpayment → parent_credit) — R4 regression test    |
| `pending_check_payment`               | INV-5 (pending reduces balance) + INV-6 (pending_clearance status) |
| `refund_cleared_payment`              | INV-8 cleared branch (LIFO reverts amountPaid)              |
| `refund_pending_payment`              | INV-8 pending branch (LIFO reverts amountPending) — R5 regression test |
| `discount_engine_all_5_rules`         | INV §5 — all 5 discounts fire on gross — R6 regression test  |
| `discount_engine_sibling_only`        | Single-rule case (only sibling_fixed fires)                  |
| `unknown_category_does_not_crash`     | R2 regression — fromCode is total, never throws              |

The Kotlin runner hardcodes the scenarios inline (dependency-free) and
asserts the canonical calc engine produces the expected state. The
matching TypeScript runner in the desktop repo
(`src/test/cross-platform/ScenarioRunner.ts`) runs the same scenarios
through the TypeScript calc engine. Both runners MUST produce the same
pass/fail result.

---

## 3. What Remains (Tier 2 — Future Iteration)

Tier 2 items from the audit (`financial-logic-comparison-v2.md`).
These do NOT block Tier 1 cross-app consistency, but they close more
of the divergence surface.

### R9 — Port charge builders
- `buildTuitionChargeEntries`, `buildTransportChargeEntriesForDestination`,
  `buildClubEnrollmentCharge`, `buildTherapyCharge`,
  `buildAdditionalServiceCharge`
- Currently the seeder + batchRegister construct charge entries inline.
- Port the named builders so the same category/metadata-rich charge
  entries are produced as the desktop.

### R10 — Reconciler cross-checks
- Port the 3 missing reconciler cross-checks:
  `crossCheckInstallmentPayments`, `crossCheckClearedBalance`,
  `crossCheckParentCredit`
- Wire them into `LocalLedgerRepository.reconcile()`.
- Add the 3 violation codes: `UNBACKED_TRANCHE_SATISFACTION`,
  `PAYMENT_LEDGER_MISMATCH`, `UNBACKED_PARENT_CREDIT`.

### R12 — `Student.paymentPlan`
- Add `paymentPlan: PaymentPlan` to the `Student` domain model +
  `StudentEntity` + `StudentDto.toEntity()` mapping.
- The column exists in Supabase (migration 0028) but the Android
  domain layer drops it.

### R13 — `Payment.expectedAmount/excessAmount/excessRemark`
- Add 3 fields to the `Payment` domain + `PaymentEntity` + `PaymentDto`.
- Used for overpayment breakdown display.

### R14 — Entry factory field alignment
- `createRefundEntry`: align field values (desktop has `method = null`,
  `paymentStatus = null`, `sourceType = "refund"`; Android currently
  has `method = parameter`, `paymentStatus = REFUNDED`,
  `sourceType = REFUND`).
- `createAdjustmentEntry`: align field values (desktop accepts
  `sourceType` parameter; Android hardcodes `ADJUSTMENT`).

### R15 — Deterministic `parent_code` + `activation_code`
- Replace `UUID.randomUUID().toString().takeLast(4).toUpperCase()` with
  a deterministic FNV-1a hash like the desktop's
  `deterministicParentCode(year, input)`.
- Replace `(100_000..999_999).random()` with a deterministic derivation.

### R16 — Dashboard correctness
- `LocalDashboardRepository.observeKpis()` should call
  `LedgerEngine.computeParentSummary` /
  `totalOutstandingAcrossAccounts` instead of naive Σ amounts.
- Remove the hardcoded fallback values (390 students, 96.5% attendance,
  13.4M DZD monthly revenue).
- Add upper bound (`< nextMonthStart`) to the `monthlyRevenue` filter.

### R17 — `ParentFinancialProfile.adjustments`
- Add `adjustments: List<AccountAdjustment>` to the
  `ParentFinancialProfile` domain model.

### R18 — `LocalExpenseRepository.settleProof`
- Persist `finalAmount` instead of silently dropping it.

### R19 — `LocalAuditRepository.log`
- Use the real `actorId` / `actorName` from `AuditLogInput` instead
  of hardcoding `"system"`.
- Implement `LocalAuditRepository.query()`.

---

## 4. What Remains (Tier 3 — UI Parity, Lower Priority)

### R22 — `AdaptivePaymentSlider`
- Port the desktop's 397-line `AdaptivePaymentSlider` component.
- 3 modes: `single_item`, `installment_tranche`, `consolidated_debt`.
- Remaining-balance snap points (not gross), magnetic snap (within
  500 DZD), per-tranche live preview, overpayment credit display,
  `allowPartial` flag.
- Replace the basic `Slider` in `CounterPaymentScreen.kt`.

### R23 — `UnifiedDebtMeter`
- Port the desktop's `UnifiedDebtMeter` component, including the
  `unallocatedCredit` row that shows "Crédit parent disponible —
  sera absorbé sur la prochaine facture".

### R24 — `parent_credit` in seedLedger
- Add `parent_credit` adjustments to the desktop's `buildSeedLedger()`
  so the canonical overpayment flow is exercised in mock mode. (This
  is a desktop-side item but listed here for cross-reference.)

---

## 5. Files Touched in This Branch

```
app/src/main/java/com/example/core/AccountBalance.kt           (R3)
app/src/main/java/com/example/core/DiscountEngine.kt           (R6, NEW)
app/src/main/java/com/example/core/Ledger.kt                    (R2)
app/src/main/java/com/example/core/LedgerEngine.kt              (R3)
app/src/main/java/com/example/core/ParentLedgerSummary.kt       (R3)
app/src/main/java/com/example/domain/repository/StudentRepository.kt  (R6)
app/src/main/java/com/example/infrastructure/local/LocalRepositories.kt  (R4, R5, R7)
app/src/main/java/com/example/infrastructure/room/ElImtiyazDatabase.kt  (R11)
app/src/main/java/com/example/infrastructure/room/LocalEntities.kt     (R11)
app/src/main/java/com/example/infrastructure/room/LocalMappers.kt      (R11)
app/src/main/java/com/example/infrastructure/supabase/SharedDtoMappers.kt  (R11)
app/src/main/java/com/example/infrastructure/supabase/SharedDtos.kt    (R11)
app/src/main/java/com/example/infrastructure/sync/SyncQueueDispatcher.kt  (R8)
app/src/main/java/com/example/infrastructure/sync/SyncSupport.kt       (R7)
app/src/test/java/com/example/core/CrossPlatformScenarioRunner.kt      (NEW, tests)
docs/CANONICAL-FINANCIAL-LOGIC.md                                     (NEW, spec)
financial-tests/README.md                                              (NEW, DSL spec)
financial-tests/scenarios/*.yml                                        (NEW, 8 scenarios)
unification-logic-docs/PROGRESS.md                                     (THIS FILE)
```

---

## 6. How to Apply These Changes

The changes are committed on the `unify-financial-logic` branch. To apply
locally:

```bash
cd /path/to/elimtiyaz-android
git fetch origin
git checkout unify-financial-logic
# OR if you have the patch files:
git am /home/z/my-project/download/android-unify-financial-logic/*.patch
```

Then build and run the cross-platform tests:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest --tests '*CrossPlatformScenarioRunner*'
```

The Kotlin runner should produce 8 passing tests. The same 8 scenarios
in the desktop TypeScript runner (`src/test/cross-platform/ScenarioRunner.ts`)
MUST produce the same pass/fail result.
