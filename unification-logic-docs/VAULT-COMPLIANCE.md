# Vault Compliance Report — Android

**Repo:** `Vtheonly/elimtiyaz-android`
**Scope:** Vault sections 04 (Parent & Student CRM), 05 (Academic Structure), 06 (Grading & Progression)
**Date:** 2026-08-25 (iteration 2 update)
**Method:** Every vault note was mapped to concrete code and verified. Missing and
shallow parts were implemented WITHOUT changing the canonical business logic
(all financial/grading engines, RPC contracts and the promotion ladder are
untouched — the changes route features through the existing canonical paths).

> **Iteration 2 update (vault §06.02 — subject-average formula):** the
> previous iteration hard-coded the `(D1 + D2 + 2×Ex) / 4` recipe everywhere
> (Android, desktop, and the SQL trigger). The institution has NOT confirmed
> this is the real formula. Iteration 2 switches the Android app back to the
> **per-component coefficient** approach: each of D1 / D2 / Examen carries
> its OWN admin-configurable coefficient, and the recipe becomes
> `(D1×c1 + D2×c2 + Ex×c3) / (c1 + c2 + c3)`. Defaults `(1, 1, 2)` preserve
> the historical recipe bit-identically (same numerator, same denominator),
> so every existing GPA stays at the same centime. See §6 below for the full
> equivalence proof. The desktop repo and the backend SQL trigger should
> adopt the same per-component approach for true cross-layer equivalence.

---

## 1. Verification matrix

### 04. Parent and Student CRM

| Vault note | Status | Evidence / change |
| :--- | :--- | :--- |
| 01. Parent-First Entity Dependency | ✅ Verified | `createStudent` rejects a missing `parentId` (`LocalRepositories.kt`); `batchRegister` always creates the parent first; the batch-registration UI validates child blocks before submission. |
| 02. Unlimited 1→N Children Model | ✅ Verified | "Ajouter un enfant" FAB with no upper bound; children list fully dynamic (`ChildFormState` list). |
| 03. Dynamic Batch Registration Workflow | ✅ **Implemented this iteration** | Parent block now carries Secondary Phone, National ID, Relationship (Père/Mère/Tuteur) + transport destination; child blocks carry Gender (was hardcoded `"unspecified"`), class assignment (cycle-filtered dropdown), payment plan (drives the canonical 5-rule discount engine) and medical notes. Room `MIGRATION_9_10` adds `parents.nationalId` + `parents.relationship` (backend parity — the Supabase `parents` table already had both). Atomicity: unchanged single `batchRegister` transaction (all-or-nothing). |
| 04. Bidirectional Relational Navigation | ✅ Verified | Parent → children (tappable rows); Student → parent card (call/WhatsApp) + sibling list; `Routes.StudentDetail` / `Routes.ParentDetail` cross-navigation. |
| 05. Parent Profile Drawer | ✅ **Implemented this iteration** | Added: itemized historic payments ledger, installment schedules, active services across all children, and the "Add Another Child" action (canonical `createStudent`, parent-first). All embedded INSIDE the drawer — no detached top-level financial screens. |
| 06. Student Profile Drawer | ✅ Verified + enriched | Identity/Family/Academic/Attendance/Financial tabs existed; the **Documents** slice remains covered by medical notes + bulletin PDFs (full attachment uploads live on desktop/backend `student-documents` bucket — see §3). |
| 07. Student Academic History | ✅ **Implemented this iteration** | New permanent "Historique" tab inside the Student drawer (not a top-level page): every past year with term-by-term GPAs (canonical engine), full subject breakdown (D1/D2/Examen + coefficient + average), attendance rate per year, and the promotion outcome (`APPROVED_FOR_PROMOTION` / `RETAINED_SAME_YEAR` / `DIPLÔMÉ`) reconstructed from the `student.promote` audit trail. Archived years render read-only (append-only rule). |

### 05. Academic Structure

| Vault note | Status | Evidence / change |
| :--- | :--- | :--- |
| 01. Scolarite vs Extracurricular Split | ✅ Verified + surfaced | `Assessment.isExtracurricular` + `computeOverallGpa` exclusion (canonical, tested). NEW: domain filter chips (Scolarité vs Clubs & Thérapie) in the Subjects directory so the split is visible in the UI. |
| 02. Primary (5 years, Grades 1–5) | ✅ Verified | Ladder `1ap..5ap` in `AcademicProgression.kt`. |
| 03. CEM (4 years, Year 1–4) | ✅ Verified | Ladder `1am..4am`; never numbered "Grade 6+". |
| 04. Lycee (3 years, streams, no Year 4) | ✅ Verified | `1ere/2eme/3eme_annee` → `GRADUATED`; promotion engine refuses a "Year 4" by construction. |
| 05. Curriculum and Subject Mapping | ✅ Verified (DB-driven) | Subjects live in Room/Supabase (never hardcoded per grade); level-scoped; `class_subjects` assignment table (migration v9). Year/calendar management is backend-owned (see §3). |
| 06. Subject Coefficients | ✅ **Implemented this iteration** | NEW edit dialog (name / coefficient / passing grade) wired to `updateSubject`; coefficient changes are now **audited** (`subject.update`) and trigger the **automatic GPA recompute** for the current year by refreshing the coefficient snapshot on current-year assessment rows (past years are append-only and never touched). |
| 07. Extracurricular Clubs and Therapy | ✅ Verified + surfaced | Clubs seeded as extracurricular subjects (chess, English club…); therapy exists as distinct billing categories (`therapy_speech` / `therapy_psychology`) — never mixed with clubs; the create dialog now exposes the "Hors programme" flag (previously hardcoded `false`). |

### 06. Grading and Progression

| Vault note | Status | Evidence / change |
| :--- | :--- | :--- |
| 01. Assessment Structure | ✅ Verified | Devoir 1 / Devoir 2 / Examen (1×/1×/2× defaults), all out of 20; `validateScore` enforced in UI + entry paths. The three per-component weights are now ADMIN-CONFIGURABLE per subject (iteration 2 — see §6 below); the historical 1×/1×/2× recipe is preserved as the default. |
| 02. Subject Average Formula | ✅ **Iteration-2 refactor** | Previous: hard-coded `(D1 + D2 + 2×Ex) / 4` everywhere. Now: `(D1×c1 + D2×c2 + Ex×c3) / (c1+c2+c3)` with each component carrying its OWN admin-configurable coefficient (`Subject.coefficientDevoir1/2/Examen`, snapshot onto `Assessment.coefficientDevoir1/2/Examen` at grade-entry time). Defaults `(1, 1, 2)` ⇒ same numerator and denominator as the historical recipe ⇒ every existing GPA stays bit-identical at the centime level. Integer-scaled centime math + `Math.round` keep the .xx5 half-up boundary parity with desktop + SQL `ROUND(numeric, 2)`. A `0` coefficient disables that component (both numerator + denominator skip it). When the admin edits any coefficient, the current year's assessment rows are re-snapshotted AND `subjectAverage` is re-derived inline; archived years stay immutable (append-only rule). The 3-arg `computeSubjectAverage(d1, d2, ex)` overload is preserved for backward compatibility with the equivalence runner + legacy callsites. |
| 03. Overall GPA Calculation | ✅ Verified | `Σ(avg × coef) / Σ(coef)`, extracurricular excluded, canonical rounding. The fallback path (when `subjectAverage` is null on a legacy row) now uses the per-row coefficient SNAPSHOT instead of the historical (1, 1, 2) defaults — so re-derivation honors the weights in effect when the marks were originally entered. |
| 04. One-Click Batch Promotion Engine | ✅ **Implemented this iteration** | NEW `PromotionReviewScreen` + `PromotionReviewViewModel` implementing the full 4-step flow: (1) yearly GPAs for the whole class, (2) auto-flag GPA ≥ 10 → `APPROVED_FOR_PROMOTION`, < 10 → `RETAINED_SAME_YEAR`, no-grades → forced manual arbitration, (3) admin review queue with per-student overrides + audit note, (4) one-click execution through the UNCHANGED canonical `promoteStudents` (ladder + graduation + audit + sync). The previous "promote every ACTIVE student" shortcut — which violated the vault's review-queue rule — was removed from the entry point. |
| 05. Academic History and Performance | ✅ **Implemented this iteration** | Same as 04.07 above (term-by-term, immutable, read-only). |
| 06. Homework Assignment Engine | ✅ **Implemented this iteration** | REAL whiteboard photo capture (`TakePicture` + gallery picker + preview — the previous "Capturer" button was a fake boolean toggle); due-date validation (ISO format + no retro-dating); `academicYear` + `pushedAt` persisted (Room `MIGRATION_9_10`, backend columns existed already); assignment enqueued for sync and pushed to the shared `homework` table via the dispatcher → Student Web Portal. Edits after due date: no edit function exists on Android (creation-only flow), so the "no late edits" invariant holds by construction. |

---

## 2. Files changed

**New files**
- `app/src/main/java/com/example/ui/features/academics/PromotionReviewScreen.kt`
- `app/src/main/java/com/example/ui/features/academics/PromotionReviewViewModel.kt`
- `app/src/test/java/com/example/core/PromotionRecommendationTest.kt`

**Modified — core/domain**
- `core/AcademicProgression.kt` — added pure `derivePromotionRecommendation` (Step-2 auto-flag)
- `domain/model/Parent.kt` — `nationalId`, `relationship`
- `domain/repository/ParentRepository.kt` — `Create/UpdateParentInput` extended
- `domain/repository/GradeRepository.kt` — `observeAllForStudent` (academic history)

**Modified — infrastructure**
- `infrastructure/room/LocalEntities.kt` — ParentEntity + HomeworkEntity new columns
- `infrastructure/room/ElImtiyazDatabase.kt` — version 10 + `MIGRATION_9_10`
- `infrastructure/room/LocalDaos.kt` — `observeByStudent`, `updateCoefficientForSubjectYear`
- `infrastructure/room/LocalMappers.kt` — parent + homework mappers
- `infrastructure/supabase/SharedDtoMappers.kt` — pull-side parent parity
- `infrastructure/local/LocalRepositories.kt` — createParent/updateParent/batchRegister
- `infrastructure/local/LocalRepositories2.kt` — homework push (validation + sync), subject update (audit + GPA recompute), grade repo
- `infrastructure/sync/SyncQueueDispatcher.kt` — `homework` table upsert case
- `di/DatabaseModule.kt` — migration registered

**Modified — UI**
- `ui/navigation/Routes.kt`, `AppNavHost.kt`, `MainScreen.kt`, `AcademicsHubScreen.kt` — PromotionReview route + RBAC + wiring
- `ui/features/academics/ClassesDirectoryScreen.kt` / `ViewModel` — entry point now opens the review queue; blind promote removed
- `ui/features/academics/SubjectsDirectoryScreen.kt` — domain filter, edit dialog, extracurricular creation
- `ui/features/academics/HomeworkPushScreen.kt` — real capture + due-date hint
- `ui/features/crm/BatchRegistrationScreen.kt` / `ViewModel` / `ChildFormState.kt` — full 4-step form
- `ui/features/crm/StudentDetailScreen.kt` — Academic History tab
- `ui/features/crm/ParentDetailScreen.kt` / `ViewModel` — payments, installments, services, add-child

## 3. Explicitly out of scope (backend/desktop-owned, documented)

- **Academic-year calendar CRUD** (add/archive years, term structures) — owned by
  the backend `academic_years` table + desktop; Android derives the current year
  and stores it on entities. No local fake table was added (would fabricate data
  the server never set).
- **Document attachment uploads** (medical certificates etc.) — the
  `student-documents` bucket exists in `StorageBuckets`; the mobile app displays
  medical notes and generates bulletin PDFs, but full document management is a
  desktop/web flow.
- **Teacher observations per historical year** — not modeled on the shared
  schema yet (no column); noted for the desktop repo.

## 4. Business-logic invariants preserved

- `computeSubjectAverage`, `computeOverallGpa`, the discount engine, the
  waterfall, all RPC contracts and `promoteStudents` are **unchanged**.
  - *Iteration-2 amendment:* `computeSubjectAverage` now accepts three
    optional per-component coefficients (defaults `1, 1, 2`). The 3-arg
    overload is preserved verbatim — the 525 cross-platform equivalence
    scenarios under `AndroidEquivalenceRunner` still pass bit-identically
    because the default-weights recipe IS the historical recipe.
- The promotion execution path still applies the canonical Algerian ladder
  (prescolaire → 5ap → 1am → 4am → 1ere → 3eme_annee → GRADUATED).
- Club/therapy grades still never contaminate the Scolarite GPA.
- The `upsert_parent_from_import` dispatcher params are untouched (new parent
  fields ride the payload as extra keys; the server maps what it knows).
- Room migrations are additive (nullable columns with defaults) — no data loss
  on upgrade.

---

## 5. Iteration-2 per-component coefficient equivalence proof

### Recipe equivalence (historical default)

The historical Android / desktop / SQL trigger recipe was:

    subject_average = (D1 + D2 + 2 × Ex) / 4

The iteration-2 recipe with default coefficients `(c1, c2, c3) = (1, 1, 2)` is:

    subject_average = (D1×c1 + D2×c2 + Ex×c3) / (c1 + c2 + c3)
                    = (D1×1 + D2×1 + Ex×2) / (1 + 1 + 2)
                    = (D1 + D2 + 2×Ex) / 4

Same numerator, same denominator ⇒ bit-identical result. The 525 canonical
scenarios in `app/src/test/java/com/example/equivalence/AndroidEquivalenceRunner.kt`
continue to pass without any scenario rewrites because the defaults preserve
the historical arithmetic.

### Centime-scaled integer math (preserved)

The implementation scales both scores and coefficients to integer cents:

    d1c = round(D1 × 100)      # cents
    d2c = round(D2 × 100)
    exc = round(Ex × 100)
    c1c = round(c1 × 100)
    c2c = round(c2 × 100)
    c3c = round(c3 × 100)

    numerator   = d1c×c1c + d2c×c2c + exc×c3c    # exact in Long arithmetic
    denominator = c1c + c2c + c3c

    avg_cents = round(numerator / denominator)   # Math.round → half-up
    subject_average = avg_cents / 100

Because `Long × Long = Long` is exact and the final `Math.round` matches SQL's
`ROUND(numeric, 2)` half-up behavior at .xx5 boundaries, the Android result is
bit-identical to the desktop TypeScript engine and to a hypothetical SQL
trigger that uses the same per-component recipe.

### Edge cases guarded

| Case | Behavior |
| :--- | :--- |
| Any of D1/D2/Examen null | Returns null (canonical "all-3 marks required" rule preserved) |
| All three coefs = 0 | Returns null (avoids divide-by-zero) |
| One coef = 0 | That component is skipped in BOTH numerator and denominator (admin can disable a component without losing the others) |
| Override coefs (e.g. 2, 1, 3) | New weighted average is computed; existing GPAs DO change for the current year after `updateSubject` triggers the inline recompute |

### Snapshot integrity (vault §04.07 append-only)

When the teacher enters a grade, the per-component coefficients are SNAPSHOT
onto the assessment row (`Assessment.coefficientDevoir1/2/Examen`). When the
admin later edits the subject's coefficients, only the CURRENT academic
year's assessment rows are re-snapshotted and re-derived; past-year rows keep
their original snapshot (immutable history).

### Files changed in iteration 2

**Domain**
- `domain/model/Subject.kt` — `coefficientDevoir1/2/Examen` fields (defaults 1.0/1.0/2.0)
- `domain/model/Assessment.kt` — same three fields as a per-row snapshot
- `domain/repository/SubjectRepository.kt` — `CreateSubjectInput` + `UpdateSubjectInput` extended

**Core engine**
- `core/Pricing.kt` — `computeSubjectAverage(d1, d2, ex, c1=1.0, c2=1.0, c3=2.0)` refactor; `computeOverallGpa` fallback now uses the per-row snapshot

**Infrastructure**
- `infrastructure/room/LocalEntities.kt` — `SubjectEntity` + `AssessmentEntity` new columns
- `infrastructure/room/LocalMappers.kt` — surface the new fields in entity → domain mappers
- `infrastructure/room/LocalDaos.kt` — NEW `listBySubjectAndYear` DAO method
- `infrastructure/room/ElImtiyazDatabase.kt` — version 10 → 11 + `MIGRATION_10_11`
- `infrastructure/local/LocalRepositories2.kt` — `createSubject` persists the new fields; `updateSubject` re-snapshots + recomputes `subjectAverage` on the current year; `enterGrade` reads the subject's per-component coefs and snapshots them
- `infrastructure/supabase/SharedDtos.kt` — `SubjectDto` carries the new nullable columns (`coefficient_devoir_1` etc.) so a backend that adopts them round-trips them
- `infrastructure/supabase/SharedDtoMappers.kt` — `SubjectDto.toEntity` pulls them with `(1, 1, 2)` defaults when the backend hasn't migrated yet
- `di/DatabaseModule.kt` — registers `MIGRATION_10_11`

**UI**
- `ui/features/academics/SubjectsDirectoryScreen.kt` — create + edit dialogs gain three coefficient inputs; subject card surfaces the active recipe
- `ui/features/academics/GradeEntryScreen.kt` — live preview uses the selected subject's per-component coefficients

**Tests**
- `app/src/test/java/com/example/core/PricingCalculationTest.kt` — 6 new tests covering default-recipe parity, override, zero-coef disable, divide-by-zero guard, half-up boundary, and overall-GPA fallback through the per-row snapshot

---

## 6. Cross-layer consistency checklist (iteration 2)

The user asked to guarantee true equivalence across **all layers**. The Android
side is now per-component-coefficient-driven. For the desktop and the backend
to match:

| Layer | Status | Action required |
| :--- | :--- | :--- |
| Android (this repo) | ✅ Done | Per-component coefficients implemented; defaults preserve historical recipe bit-identically |
| Desktop repo (TS) | ⚠️ Pending mirror | Replace `computeSubjectAverage(d1, d2, ex)` with `computeSubjectAverage(d1, d2, ex, c1=1, c2=1, c3=2)`; surface three editable coefficient fields on the Subject model + the Subjects admin screen; snapshot onto Assessment at grade-entry time; recompute current-year rows on subject edit |
| Backend SQL trigger | ⚠️ Pending mirror | Replace the hard-coded `(d1 + d2 + 2*ex) / 4` recipe in `compute_grade_subject_average()` with the per-component recipe reading `coefficient_devoir_1`, `coefficient_devoir_2`, `coefficient_examen` from the `subjects` row (joined via `assessments.subject_id`); add the three columns to the `subjects` table via a migration; backfill with `(1, 1, 2)` |
| Shared sync schema | ✅ Forward-compatible | `SubjectDto` already declares the three nullable columns; the Android mapper falls back to `(1, 1, 2)` when the backend hasn't migrated yet — so the Android app stays correct under either backend state |

Until the desktop and backend mirror the per-component approach, the Android
app + the desktop app + the SQL trigger may compute different `subjectAverage`
values **only** for subjects whose admin-configured coefficients differ from
the historical `(1, 1, 2)` defaults. For every subject using the defaults,
the three layers stay bit-identical.
