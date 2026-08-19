# Next Iteration Roadmap — Android

**Goal:** Close the remaining divergences (Tier 2 + Tier 3) so the
Android repository achieves full parity with the desktop's canonical
calc engine + UI surface.

**Priority order** is by impact on cross-app consistency. Items marked
🔴 block semantic equivalence. Items marked 🟡 close UX/UI parity gaps.
Items marked 🟢 are polish / future-proofing.

---

## Tier 2 — Semantic Parity (next iteration, ~1-2 sessions)

### 🔴 R10 — Port the 3 missing reconciler cross-checks
**Why:** The Android `Reconcile` object runs only 3 of the 6 canonical
cross-checks. Without `crossCheckInstallmentPayments`, the reconciler
cannot detect a tranche marked `paid` without backing cleared payment
entries (`UNBACKED_TRANCHE_SATISFACTION`). Without
`crossCheckClearedBalance`, it cannot detect a payment row whose amount
doesn't match its ledger entry (`PAYMENT_LEDGER_MISMATCH`). Without
`crossCheckParentCredit`, it cannot detect a negative balance on a
non-`parent_credit` account (`UNBACKED_PARENT_CREDIT` — exactly the R4
bug that's now fixed but would not be detected by the Android reconciler).

**Where:** `app/src/main/java/com/example/core/Reconcile.kt`
**How:**
1. Add 3 new cross-check functions modeled on the desktop's
   `domain/calc/reconcile/cross-checks.ts`:
   - `crossCheckInstallmentPayments(installments, entries)` → `UNBACKED_TRANCHE_SATISFACTION`
   - `crossCheckClearedBalance(payments, entries)` → `PAYMENT_LEDGER_MISMATCH`
   - `crossCheckParentCredit(parentSummaries, entries)` → `UNBACKED_PARENT_CREDIT`
2. Wire them into `Reconcile.reconcileLedger` via the `CrossCheckInputs`
   parameter (extend it if needed).
3. Wire `LocalLedgerRepository.reconcile()` to pass real cross-check
   inputs (currently passes empty inputs).

### 🔴 R12 — Add `paymentPlan` to `Student` domain
**Why:** The Supabase schema (migration 0028) has `payment_plan` on the
`students` table. The Android `StudentDto` parses it but the domain
`Student` + `StudentEntity` + `toDomain()` mapping silently drop it.
The 10% early-annual discount (`full_annual`) cannot be applied or
displayed on Android without this field.

**Where:**
- `app/src/main/java/com/example/domain/model/Student.kt`
- `app/src/main/java/com/example/infrastructure/room/LocalEntities.kt` (`StudentEntity`)
- `app/src/main/java/com/example/infrastructure/room/LocalMappers.kt` (StudentEntity.toDomain)
- `app/src/main/java/com/example/infrastructure/supabase/SharedDtoMappers.kt` (StudentDto.toEntity)

**How:** Add a `paymentPlan: PaymentPlan?` field to the domain `Student`
and a `payment_plan: String` column to `StudentEntity`. Bump Room
version (4 → 5) with an ALTER TABLE migration.

### 🟡 R13 — Add `expectedAmount` / `excessAmount` / `excessRemark` to `Payment`
**Why:** Overpayment breakdown cannot be displayed on Android — the
canonical `Payment` interface has these fields, the Android domain
drops them. Used by the desktop's `AdaptivePaymentSlider` for the
"Excédent (crédit parent)" warning.

**Where:** Same pattern as R12 — domain + entity + DTO mapper.

### 🟡 R14 — Align entry factory field values
**Why:** `createRefundEntry` on Android has `method = parameter`,
`paymentStatus = REFUNDED`, `sourceType = REFUND`. The desktop's
canonical factory has `method = null`, `paymentStatus = null`,
`sourceType = "refund"`. The desktop's `crossCheckPayments` compares
`entry.paymentStatus !== p.status` — for a refund, the desktop stores
`paymentStatus = null` but Android stores `REFUNDED`, triggering
`PAYMENT_STATUS_MISMATCH` warnings on every Android-originated refund.

**Where:** `app/src/main/java/com/example/core/LedgerEntryFactory.kt`
**How:** Change `createRefundEntry` to set `method = null` and
`paymentStatus = null`. Update callers that pass `method = X` — they
should write a separate payment row with the method, not put it on
the refund entry. Change `createAdjustmentEntry` to accept a
`sourceType: LedgerSourceType? = LedgerSourceType.ADJUSTMENT` parameter
instead of hardcoding.

### 🟡 R15 — Deterministic `parent_code` + `activation_code`
**Why:** Android's `batchRegister` uses
`UUID.randomUUID().toString().takeLast(4).toUpperCase()` for
`parent_code` and `(100_000..999_999).random()` for `activation_code`.
The desktop uses a deterministic FNV-1a hash. Even if Android's sync
push is wired (R7), the upsert RPC's primary identity match would
never hit on a retry — each retry generates a new code.

**Where:** `app/src/main/java/com/example/infrastructure/local/LocalRepositories.kt`
**How:** Port the desktop's `deterministicParentCode(year, input)` and
`deterministicActivationCode(parentCode, tenantId)` to a new
`app/src/main/java/com/example/core/IdentityCodes.kt` file. Replace
the random calls in `batchRegister`.

### 🟡 R16 — Dashboard correctness
**Why:** `LocalDashboardRepository.observeKpis()` computes
`totalOutstanding` by summing ledger amounts directly
(`g2.ledger.filter { ... }.sumOf { it.amount }`) — this excludes
refunds, includes reversed originals, and doesn't replay per-account.
It also has hardcoded fallback values (390 students, 96.5%
attendance, 13.4M DZD monthly revenue) when Room is empty. The
monthlyRevenue filter has no upper bound (`< nextMonthStart`).

**Where:** `app/src/main/java/com/example/infrastructure/local/LocalRepositories2.kt`
**How:** Replace the naive sum with a call to
`LedgerEngine.computeParentSummary` or `totalOutstandingAcrossAccounts`.
Remove the hardcoded fallbacks. Add the upper bound.

### 🟡 R17 — Add `adjustments` to `ParentFinancialProfile`
**Why:** Android's `ParentFinancialProfile` doesn't have an
`adjustments` field. The desktop's interface has
`adjustments: readonly AccountAdjustment[]`. Android can never display
discretionary adjustment history.

**Where:** `app/src/main/java/com/example/domain/model/DebtSummary.kt` (or wherever
the local `ParentFinancialProfile` lives).
**How:** Add `adjustments: List<AccountAdjustment> = emptyList()` to
the data class. Populate it in `LocalDebtRepository.observeParentProfile`.

### 🟢 R18 — `LocalExpenseRepository.settleProof` finalAmount
**Why:** Android's `settleProof(finalAmount: Long)` accepts a final
spent amount but silently drops it — only updates `proofUrl`, `settledAt`,
`status`. The desktop's `ExpenseTicketDto.final_spent_amount` is lost
on Android.

**Where:** `app/src/main/java/com/example/infrastructure/local/LocalRepositories2.kt`
**How:** Add a `final_spent_amount` column to `ExpenseEntity` (or use an
existing column if there is one). Persist the value in `settleProof`.

### 🟢 R19 — `LocalAuditRepository.log` actor
**Why:** Android's `LocalAuditRepository.log(input)` hardcodes
`actorId = "system"`, `actorName = "System"` even when `AuditLogInput`
carries a real actor. All Android audit logs lose actual actor identity.
The query method also returns `emptyList()`.

**Where:** `app/src/main/java/com/example/infrastructure/local/LocalRepositories2.kt`
**How:** Use `input.actorId ?: "system"` (fall back to system only if null).
Implement `query()` to actually filter by the criteria in `AuditLogQuery`.

### 🟢 R20 — Port charge builders
**Why:** Currently Android constructs charge entries inline in
`DatabaseSeeder.seedLedgerForFamily` + `batchRegister`. The desktop has
named builders: `buildTuitionChargeEntries`,
`buildTransportChargeEntriesForDestination`, `buildClubEnrollmentCharge`,
`buildTherapyCharge`, `buildAdditionalServiceCharge`. Porting them
ensures the same category/metadata-rich charge entries are produced as
the desktop.

**Where:** New file
`app/src/main/java/com/example/core/ChargeBuilders.kt` (Kotlin port of
`domain/calc/ledger/charges.ts` + `non-tuition-charges.ts`). Replace
the inline construction in the seeder + batchRegister.

---

## Tier 3 — UI Parity (lower priority, ~1 session)

### 🟡 R22 — Port `AdaptivePaymentSlider` (3 modes)
**Why:** Android's `CounterPaymentScreen` uses a basic `Slider` with
hardcoded 500-DZD rounding. The desktop's `AdaptivePaymentSlider` is
397 lines with 3 modes (`single_item` / `installment_tranche` /
`consolidated_debt`), REMAINING-balance snap points (not gross),
magnetic snap (within 500 DZD), per-tranche live preview, overpayment
credit display, `allowPartial` flag. Without porting, the Android UI
cannot represent consolidated-debt payments or per-tranche previews.

**Where:** New file
`app/src/main/java/com/example/ui/features/financials/AdaptivePaymentSlider.kt`.
Replace the basic `Slider` in `CounterPaymentScreen.kt`.

### 🟡 R23 — Port `UnifiedDebtMeter`
**Why:** Android's debt meter doesn't display `unallocatedCredit`. The
desktop's `UnifiedDebtMeter` shows "Crédit parent disponible — sera
absorbé sur la prochaine facture" when parent has banked credit.
Without porting, Android users can't see their banked credit.

**Where:** New file
`app/src/main/java/com/example/ui/features/financials/UnifiedDebtMeter.kt`.

### 🟢 R24 — `parent_credit` in seedLedger (desktop side)
This is a desktop item, listed here for cross-reference. See the
desktop's `unification-logic-docs/NEXT-ITERATION.md`.

---

## Sequencing

The recommended order for the next iteration:

1. **R12** (Student.paymentPlan) — unblocks the `full_annual` discount UI.
2. **R14** (entry factory field alignment) — closes the
   `PAYMENT_STATUS_MISMATCH` warning on every Android refund.
3. **R15** (deterministic parent_code) — closes idempotency at the source.
4. **R10** (reconciler cross-checks) — completes INV-9 (6 cross-checks).
5. **R16** (dashboard correctness) — removes fabricated numbers.
6. **R13 + R17** (Payment + ParentFinancialProfile fields) — UI display.
7. **R22 + R23** (UI parity) — last, since the underlying data is now
   correct.

Items R18, R19, R20 can be batched with whatever iteration is convenient.
