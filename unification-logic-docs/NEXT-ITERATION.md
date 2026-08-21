# Next Iteration Roadmap — Android

**Goal:** Close the remaining Tier 3 divergences — UI parity + polish.
These do NOT affect cross-app semantic parity (Tier 1 + Tier 2 closed
all business-critical gaps), but they bring the Android UI to parity
with the desktop's UI and clean up the remaining low-priority items.

**Priority order** is by user-visible impact. Items marked 🟡 close
UX parity gaps. Items marked 🟢 are polish / future-proofing.

---

## Tier 3 — UI Parity + Polish (next iteration, ~1 session)

### 🟡 R13 — Add `expectedAmount/excessAmount/excessRemark` to `Payment`
**Why:** Used by the desktop's `AdaptivePaymentSlider` to show the
"Excédent (crédit parent)" warning when the slider exceeds the
total remaining balance. Without these fields, Android's future
`AdaptivePaymentSlider` port (R22) cannot display the overpayment
breakdown.

**Where:**
- `app/src/main/java/com/example/domain/model/Payment.kt`
- `app/src/main/java/com/example/infrastructure/room/LocalEntities.kt` (`PaymentEntity`)
- `app/src/main/java/com/example/infrastructure/room/LocalMappers.kt`
- `app/src/main/java/com/example/infrastructure/supabase/SharedDtos.kt` (`PaymentDto`)
- `app/src/main/java/com/example/infrastructure/supabase/SharedDtoMappers.kt`

**How:** Add 3 nullable fields (`expectedAmount: Long?`, `excessAmount: Long?`,
`excessRemark: String?`) to the domain, entity, and DTO. Bump Room version
v5 → v6 with an ALTER TABLE migration adding the columns.

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
Without porting, Android users can't see their banked credit (even
though the data is now available in `ParentLedgerSummary.totalUnallocatedCredit`
after T1 R3).

**Where:** New file
`app/src/main/java/com/example/ui/features/financials/UnifiedDebtMeter.kt`.

### 🟢 R9 / R20 — Port charge builders
**Why:** Currently Android constructs charge entries inline in
`DatabaseSeeder.seedLedgerForFamily` + `batchRegister`. The desktop has
named builders: `buildTuitionChargeEntries`,
`buildTransportChargeEntriesForDestination`, `buildClubEnrollmentCharge`,
`buildTherapyCharge`, `buildAdditionalServiceCharge`. Porting them
ensures the same category/metadata-rich charge entries are produced as
the desktop, and makes future maintenance easier.

**Where:** New file
`app/src/main/java/com/example/core/ChargeBuilders.kt` (Kotlin port of
`domain/calc/ledger/charges.ts` + `non-tuition-charges.ts`). Replace
the inline construction in the seeder + batchRegister.

This is a refactoring, NOT a parity fix — the current inline code
produces the correct categories + metadata.

### 🟢 R18 — `LocalExpenseRepository.settleProof` finalAmount
**Why:** Android's `settleProof(finalAmount: Long)` accepts a final
spent amount but silently drops it — only updates `proofUrl`, `settledAt`,
`status`. The desktop's `ExpenseTicketDto.final_spent_amount` is lost
on Android.

**Where:** `app/src/main/java/com/example/infrastructure/local/LocalRepositories2.kt`
**How:** Add a `final_spent_amount` column to `ExpenseEntity` (Room
migration v5 → v6). Persist the value in `settleProof`.

### 🟢 R19 — `LocalAuditRepository.log` actor
**Why:** Android's `LocalAuditRepository.log(input)` hardcodes
`actorId = "system"`, `actorName = "System"` even when `AuditLogInput`
carries a real actor. All Android audit logs lose actual actor identity.
The query method also returns `emptyList()`.

**Where:** `app/src/main/java/com/example/infrastructure/local/LocalRepositories2.kt`
**How:** Use `input.actorId ?: "system"` (fall back to system only if null).
Implement `query()` to actually filter by the criteria in `AuditLogQuery`.

---

## Sequencing

The recommended order for Tier 3:

1. **R13** (Payment fields) — unblocks the overpayment display in R22.
2. **R22** (AdaptivePaymentSlider) — biggest UI parity gain.
3. **R23** (UnifiedDebtMeter) — completes the financials UI parity.
4. **R9 / R20** (charge builders) — refactoring, low risk.
5. **R18 + R19** (polish) — batch these together.

Items R22 + R23 can be done in parallel with R13 once the data layer
is in place.
