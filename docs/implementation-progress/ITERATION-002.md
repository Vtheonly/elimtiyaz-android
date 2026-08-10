# ITERATION-002 — Local Room Data Layer + Business Logic

## What was implemented

Replaced the dummy Supabase-backed repository layer with a real, offline-first
local Room database that serves as the source of truth. Implemented the full
business logic (pricing, ledger, waterfall allocation, GPA) matching the
desktop's mathematics exactly. Seeded the database with real pricing data
from `Prices.md` (2026-2027 school year).

## Files/components changed

### Core domain logic (new)
- `core/WaterfallAllocation.kt` — Waterfall allocation engine + LIFO reverse-waterfall + aging buckets + tranche split. Mirrors desktop `domain/calc/payment/installments.ts` exactly.
- `core/Pricing.kt` — Updated `computeOverallGpa` to compute subject average when null (matches desktop behavior).

### Room data layer (new)
- `infrastructure/room/LocalEntities.kt` — 25 Room entities (parents, students, classes, subjects, attendance, assessments, homework, payments, installments, ledger_entries, expenses, personnel, departments, pricing_config, pricing_discounts, grade_level_tuition, transport_pricing, notifications, audit_logs, trip_logs, releve_entries, workflow_runs).
- `infrastructure/room/LocalDaos.kt` — Full CRUD DAOs for all 25 entities.
- `infrastructure/room/ElImtiyazDatabase.kt` — Updated to v2 with all new entities + DAOs.
- `infrastructure/room/LocalMappers.kt` — Pure entity ↔ domain mappers.
- `infrastructure/room/DatabaseSeeder.kt` — Seeds real pricing (14 grade levels, 4 transport zones, 5 discounts), subjects, classes, personnel, and 3 demo families with real ledger charges + installments + one demo payment.

### Local repository implementations (new)
- `infrastructure/local/LocalRepositories.kt` — LocalAuthRepository, LocalParentRepository, LocalStudentRepository (with atomic batchRegister), LocalPaymentRepository (with waterfall allocation), LocalInstallmentRepository, LocalLedgerRepository.
- `infrastructure/local/LocalRepositories2.kt` — LocalClassRepository, LocalDashboardRepository (real KPI computation), LocalDebtRepository, LocalPricingRepository, LocalAuditRepository, LocalAttendanceRepository, LocalGradeRepository, LocalExpenseRepository, LocalPersonnelRepository, LocalDepartmentRepository, LocalSubjectRepository, LocalHomeworkRepository, LocalNotificationRepository, LocalReleveRepository, LocalRoutingRepository, LocalWorkflowRepository, LocalStorageRepository.

### DI wiring
- `di/DatabaseModule.kt` — Updated with all new DAO providers + destructive migration.
- `di/RepositoryModule.kt` — Switched all 22 bindings from Supabase to local Room implementations.

### Deleted (replaced by local)
- All 22 `infrastructure/supabase/Supabase*Repository.kt` files.
- `infrastructure/supabase/SupabaseSyncDao.kt`, `GradeDtos.kt`.
- `di/SupabaseModule.kt`.

### Tests (new)
- `test/core/WaterfallAllocationTest.kt` — 11 tests covering waterfall allocation, LIFO reversal, aging buckets, tranche split conservation.
- `test/core/PricingCalculationTest.kt` — 12 tests covering sibling discount, subject average, GPA, passing threshold, score validation.

## Tests performed
- `./gradlew :app:testDebugUnitTest` — all core tests PASS.
- `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL (28MB APK).

## Business logic verified against desktop

| Calculation | Desktop source | Mobile implementation | Match |
|---|---|---|---|
| Waterfall allocation | `installments.ts:allocatePaymentToInstallments` | `WaterfallAllocation.kt:allocatePaymentToInstallments` | ✅ |
| LIFO reverse-waterfall | `installments.ts:revertPaymentAllocation` | `WaterfallAllocation.kt:revertPaymentAllocation` | ✅ |
| Aging buckets | `installments.ts:agingBucketFromDays` | `WaterfallAllocation.kt:agingBucketFromDays` | ✅ |
| Tranche split 40/30/30 | `tuition.ts:splitNetTuitionByOfficialSchedule` | `WaterfallAllocation.kt:splitNetTuitionByOfficialSchedule` | ✅ |
| Official due dates | `tuition.ts:getOfficialTuitionDueDates` | `WaterfallAllocation.kt:officialTuitionDueDates` | ✅ |
| Ledger balance replay | `balance.ts:computeAccountBalance` | `LedgerEngine.kt:computeAccountBalance` | ✅ (pre-existing) |
| Parent summary | `balance.ts:computeParentSummary` | `LedgerEngine.kt:computeParentSummary` | ✅ (pre-existing) |
| Subject average | `gpa.ts:computeSubjectAverage` | `Pricing.kt:computeSubjectAverage` | ✅ |
| Overall GPA | `gpa.ts:computeOverallGpa` | `Pricing.kt:computeOverallGpa` | ✅ |
| Sibling discount | `discounts.ts:computeSiblingDiscount` | `Pricing.kt:computeSiblingDiscount` | ✅ |
| Ledger entry factories | `entries.ts:createChargeEntry` etc. | `LedgerEntryFactory.kt` | ✅ (pre-existing) |

## Pricing data seeded (from Prices.md)

| Grade Level | Annual (DZD) | T1 (40%) | T2 (30%) | T3 (30%) |
|---|---|---|---|---|
| Préscolaire 1 | 130,000 | 52,000 | 39,000 | 39,000 |
| 1AP | 245,000 | 98,000 | 73,500 | 73,500 |
| 4AP | 285,000 | 114,000 | 85,500 | 85,500 |
| 1AM | 330,000 | 132,000 | 99,000 | 99,000 |
| 1ère Année | 375,000 | 150,000 | 112,500 | 112,500 |
| 3ème Année | 395,000 | 158,000 | 118,500 | 118,500 |

Transport zones: Ville Boumerdes 40,000 / Tidjelabine 43,000 / Boudouaou 52,000 / Autres 55,000 DA.

## Remaining problems
- None blocking. The app compiles, tests pass, and APK builds.

## Tasks completed
- Full local Room data layer (25 entities, full CRUD).
- All 22 repositories implemented with real business logic.
- Waterfall allocation engine ported from desktop.
- Real pricing data seeded from Prices.md.
- Demo families with real ledger entries, installments, and a demo payment.
- 23 unit tests (all passing).
- Debug APK builds successfully.
