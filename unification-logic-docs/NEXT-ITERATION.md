# Next Iteration Roadmap — Android

**Last updated:** 2026-08-21 (TIER 3)

**Goal:** Close the remaining Tier 4 divergences — UI parity + polish +
test-layer port. These do NOT affect cross-app semantic parity
(Tier 1 + Tier 2 + Tier 3 closed all business-critical gaps), but they
bring the Android UI to parity with the desktop's UI and clean up the
remaining low-priority items.

**Priority order** is by user-visible impact. Items marked 🟡 close
UX parity gaps. Items marked 🟢 are polish / future-proofing. Items
marked ⚠️ are uncertain (require spec clarification before code work).

---

## Tier 4 — UI Parity + Polish + Test Port (next iteration)

### 🟡 R13 — Add `expectedAmount/excessAmount/excessRemark` to `Payment`
**Why:** Used by the desktop's `AdaptivePaymentSlider` to show the
"Excédent (crédit parent)" warning when the slider exceeds the
total remaining balance. Without these fields, Android's future
`AdaptivePaymentSlider` port (R22) cannot display the overpayment
breakdown. This is now a hard dependency of R22 — the desktop slider
consumes these fields directly.

**Where:**
- `app/src/main/java/com/example/domain/model/Payment.kt`
- `app/src/main/java/com/example/infrastructure/room/LocalEntities.kt` (`PaymentEntity`)
- `app/src/main/java/com/example/infrastructure/room/LocalMappers.kt`
- `app/src/main/java/com/example/infrastructure/supabase/SharedDtos.kt` (`PaymentDto`)
- `app/src/main/java/com/example/infrastructure/supabase/SharedDtoMappers.kt`
- `app/src/main/java/com/example/infrastructure/room/ElImtiyazDatabase.kt` (MIGRATION_6_7)
- `app/src/main/java/com/example/di/DatabaseModule.kt` (register migration)

**How:** Add 3 nullable fields (`expectedAmount: Long?`,
`excessAmount: Long?`, `excessRemark: String?`) to the domain, entity,
and DTO. Bump Room version v6 → v7 with an ALTER TABLE migration
adding the 3 columns. Verify the Supabase `payments` table already
has the columns (check migration 0028 / 0033); if not, add a paired
Supabase migration synced with the desktop repo.

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

**Depends on:** R13 (display fields).

### 🟡 R23 — Port `UnifiedDebtMeter`
**Why:** Android's debt meter doesn't display `unallocatedCredit`. The
desktop's `UnifiedDebtMeter` shows "Crédit parent disponible — sera
absorbé sur la prochaine facture" when parent has banked credit.
Without porting, Android users can't see their banked credit (even
though the data is now available in `ParentLedgerSummary.totalUnallocatedCredit`
since T1 R3 — only the UI is missing).

**Where:** New file
`app/src/main/java/com/example/ui/features/financials/UnifiedDebtMeter.kt`.

### 🟢 R9 / R20 — Port charge builders (refactoring)
**Why:** Currently Android constructs charge entries inline in
`DatabaseSeeder.seedLedgerForFamily` + `batchRegister`. The desktop
has named builders: `buildTuitionChargeEntries`,
`buildTransportChargeEntriesForDestination`, `buildClubEnrollmentCharge`,
`buildTherapyCharge`, `buildAdditionalServiceCharge`. Porting them
ensures the same category/metadata-rich charge entries are produced
as the desktop, and makes future maintenance easier.

**Where:** New file
`app/src/main/java/com/example/core/ChargeBuilders.kt` (Kotlin port of
`domain/calc/ledger/charges.ts` + `non-tuition-charges.ts`). Replace
the inline construction in the seeder + batchRegister.

This is a refactoring, NOT a parity fix — the current inline code
produces the correct categories + metadata. Verified during the
Tier 3 audit (`T3-DESK-AUDIT`).

### 🟢 Android unit test execution in a proper environment
**Why:** The Tier 1 / Tier 2 / Tier 3 Android tests have been written
but couldn't be executed in the development sandbox (only the JRE is
installed, no JDK compiler). The Kotlin source is syntactically
correct and follows the same patterns as the existing tests.

**Where:** Run `./gradlew :app:testDebugUnitTest` in a proper Android
Studio / Gradle environment. Confirm all 40 Tier 1 + Tier 2 tests
pass (13 IdentityCodesTest + 11 Tier2EntryFactoryTest +
7 Tier2ReconcilerCrossChecksTest + 9 existing CrossPlatformScenarioRunner).

**If tests fail:** Fix the failures before adding new test layers
(R-property-based below). The failures would indicate a discrepancy
between the sandbox assumptions and the real Gradle build environment
— likely a missing dependency or a different Kotlin version.

### 🟢 Android property-based / generative test layer (port of desktop)
**Why:** The desktop repo added a `PropertyBasedEquivalence.test.ts`
in Tier 3 (601 tests, deterministic mulberry32 PRNG with boundary
amounts 0 / 1 / 99 / 100 / MAX / MAX+1). The Android repo has the
matching generator at `financial-tests/equivalence/generators/scenario_generator.ts`
but no Kotlin test runner that consumes it. Porting it would give
Android the same generative coverage as desktop.

**Where:** New file
`app/src/test/java/com/example/core/PropertyBasedEquivalenceTest.kt`
(Kotlin port of `src/tests/domain/calc/property-based-equivalence.test.ts`).
Use `kotlin.test.parameterizedTest` or a JUnit 5 `@ParameterizedTest`
with a deterministic seed (matching the desktop's mulberry32 PRNG).

**Depends on:** Android unit test execution above (the tests must
actually be runnable before adding 601 more).

### ⚠️ Overpayment canonical design issue (spec clarification)
**Why:** The Tier 3 audit (`T3-DESK-AUDIT` Stage Summary item 1)
found that the source account goes negative when there's an
overpayment — both desktop + Android have the same behavior, so
they're EQUIVALENT, but the canonical spec INV-3 says "a negative
balance on any non-`parent_credit` account is a reconciler violation
(`UNBACKED_PARENT_CREDIT`)". The current implementation triggers
this violation on every overpayment. Both implementations agree,
so cross-app parity is preserved, but the canonical spec might need
clarification on whether a `transfer` entry should move the credit
off the source account onto `parent_credit`.

**Where:**
- `docs/CANONICAL-FINANCIAL-LOGIC.md` (INV-3 + INV-7 sections) —
  clarify whether overpayment should (a) remain on the source
  account (current behavior), or (b) be moved to `parent_credit`
  via a `transfer` entry at write time.
- If (b): both repos need a code change in `collectPayment` /
  `collect_and_allocate_payment` RPC.

**Status:** This is a SPEC clarification, not a code fix. Both repos
already agree, so cross-app parity is preserved either way. Deferred
to Tier 4 (or later) until the canonical spec is clarified.

---

## Sequencing

The recommended order for Tier 4:

1. **Android unit test execution** — confirm the 40 existing tests
   pass in a real Gradle environment before adding more.
2. **R13** (Payment display fields) — unblocks the overpayment display
   in R22. Requires Room migration v6 → v7.
3. **R22** (AdaptivePaymentSlider) — biggest UI parity gain. Depends
   on R13.
4. **R23** (UnifiedDebtMeter) — completes the financials UI parity.
5. **R9 / R20** (charge builders) — refactoring, low risk, no
   dependencies.
6. **Android property-based test layer** — port the desktop's 601
   generative tests. Depends on (1).
7. **Overpayment spec clarification** — review INV-3 + INV-7 in the
   canonical spec. If the spec is changed, both repos need a paired
   code change.

Items R22 + R23 can be done in parallel with R13 once the data layer
is in place. The property-based test layer can be done in parallel
with the UI work — they don't share code paths.
