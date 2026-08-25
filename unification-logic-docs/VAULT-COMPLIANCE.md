# Vault Compliance Report — Android

**Repo:** `Vtheonly/elimtiyaz-android`
**Scope:** Vault sections 04 (Parent & Student CRM), 05 (Academic Structure), 06 (Grading & Progression)
**Date:** 2026-08-25
**Method:** Every vault note was mapped to concrete code and verified. Missing and
shallow parts were implemented WITHOUT changing the canonical business logic
(all financial/grading engines, RPC contracts and the promotion ladder are
untouched — the changes route features through the existing canonical paths).

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
| 01. Assessment Structure | ✅ Verified | Devoir 1 / Devoir 2 / Examen (1×/1×/2×), all out of 20; `validateScore` enforced in UI + entry paths. |
| 02. Subject Average Formula | ✅ Verified | `(D1 + D2 + 2×Ex) / 4` — integer-scaled cents math, bit-identical to desktop + SQL trigger; null while any mark is missing. |
| 03. Overall GPA Calculation | ✅ Verified | `Σ(avg × coef) / Σ(coef)`, extracurricular excluded, canonical rounding. |
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
- The promotion execution path still applies the canonical Algerian ladder
  (prescolaire → 5ap → 1am → 4am → 1ere → 3eme_annee → GRADUATED).
- Club/therapy grades still never contaminate the Scolarite GPA.
- The `upsert_parent_from_import` dispatcher params are untouched (new parent
  fields ride the payload as extra keys; the server maps what it knows).
- Room migrations are additive (nullable columns with defaults) — no data loss
  on upgrade.
