package com.example.infrastructure.room

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * El Imtiyaz local database — the PRIMARY source of truth for this build.
 *
 * The mobile app is offline-first: every UI screen reads from and writes to
 * these tables. The schema mirrors the desktop's Supabase schema
 * (`supabase/migrations/`) field-by-field so the business logic, calculations,
 * and financial mathematics produce identical numbers on both platforms.
 *
 * Versioned at 2. The original cache entities (parent_cache, student_cache,
 * payment_cache, ledger_cache, sync_queue) are retained for backward
 * compatibility with the sync layer; the new source-of-truth tables are
 * the non-suffixed ones (parents, students, payments, ledger_entries, etc.).
 */
@Database(
    entities = [
        // ── Original cache layer (kept for sync compatibility) ──
        ParentCacheEntity::class,
        StudentCacheEntity::class,
        PaymentCacheEntity::class,
        LedgerCacheEntity::class,
        SyncQueueEntity::class,
        // ── Local source-of-truth tables ──
        ParentEntity::class,
        StudentEntity::class,
        AcademicClassEntity::class,
        SubjectEntity::class,
        AttendanceEntity::class,
        AssessmentEntity::class,
        HomeworkEntity::class,
        PaymentEntity::class,
        InstallmentEntity::class,
        LedgerEntryEntity::class,
        ExpenseEntity::class,
        PersonnelEntity::class,
        DepartmentEntity::class,
        PricingConfigEntity::class,
        PricingDiscountEntity::class,
        GradeLevelTuitionEntity::class,
        TransportPricingEntity::class,
        NotificationEntity::class,
        AuditLogEntity::class,
        TripLogEntity::class,
        VehicleEntity::class,
        RoutingStopEntity::class,
        ClassSubjectEntity::class,
        ReleveEntryEntity::class,
        WorkflowRunEntity::class,
    ],
    version = 12,
    exportSchema = false,
)
abstract class ElImtiyazDatabase : RoomDatabase() {
    // ── Original cache DAOs ──
    abstract fun parentCacheDao(): ParentCacheDao
    abstract fun studentCacheDao(): StudentCacheDao
    abstract fun paymentCacheDao(): PaymentCacheDao
    abstract fun ledgerCacheDao(): LedgerCacheDao
    abstract fun syncQueueDao(): SyncQueueDao

    // ── Local source-of-truth DAOs ──
    abstract fun parentDao(): ParentDao
    abstract fun studentDao(): StudentDao
    abstract fun academicClassDao(): AcademicClassDao
    abstract fun subjectDao(): SubjectDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun assessmentDao(): AssessmentDao
    abstract fun homeworkDao(): HomeworkDao
    abstract fun paymentDao(): PaymentDao
    abstract fun installmentDao(): InstallmentDao
    abstract fun ledgerEntryDao(): LedgerEntryDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun personnelDao(): PersonnelDao
    abstract fun departmentDao(): DepartmentDao
    abstract fun pricingConfigDao(): PricingConfigDao
    abstract fun notificationDao(): NotificationDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun tripLogDao(): TripLogDao
    abstract fun vehicleDao(): VehicleDao
    abstract fun routingStopDao(): RoutingStopDao
    abstract fun classSubjectDao(): ClassSubjectDao
    abstract fun releveEntryDao(): ReleveEntryDao
    abstract fun workflowRunDao(): WorkflowRunDao

    companion object {
        /**
         * Room migration v3 → v4 (CANONICAL-FINANCIAL-LOGIC.md §7.5 + §8.4).
         *
         * Adds the `metadataJson` TEXT column to `ledger_entries` so that
         * pull-side metadata (tranche, level, gradeLevel, paymentPlan,
         * academicCycle, clubCategory, therapyKind, period, sessionCount,
         * serviceQualifier, pricingSource, reversedEntryId, reason) is
         * preserved across the full sync cycle instead of being dropped.
         *
         * Default value `'{}'` so existing rows continue to map to an empty
         * metadata map (matching the previous `metadata = emptyMap()` behavior).
         */
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE ledger_entries ADD COLUMN metadataJson TEXT NOT NULL DEFAULT '{}'"
                )
            }
        }

        /**
         * Room migration v4 → v5 (TIER 2 R12).
         *
         * Adds the `paymentPlan` TEXT column to `students` so the Android
         * domain layer can represent + apply the 10% early-annual discount
         * (CANONICAL-FINANCIAL-LOGIC.md §5 rule 3). The Supabase schema has
         * had this column since migration 0028 — this migration brings the
         * local Room schema in line.
         *
         * Default value `'tranches'` so existing students default to the
         * 3-tranche schedule (matching the desktop's default for imported
         * students without an explicit `payment_plan`).
         */
        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE students ADD COLUMN paymentPlan TEXT NOT NULL DEFAULT 'tranches'"
                )
            }
        }

        /**
         * Room migration v5 → v6 (TIER 3 R18).
         *
         * Adds the `finalSpentAmount` INTEGER column to `expenses` so the
         * local Room schema matches the Supabase schema (which has had
         * `final_spent_amount` since migration 0028). This column stores
         * the actual spent amount confirmed by the proof scan at settlement
         * time — previously `settleProof()` accepted the parameter but
         * silently dropped it because the column didn't exist on the entity.
         *
         * Nullable (no default) so existing expense rows continue to map
         * to `finalSpentAmount = null` (matching the previous behavior for
         * expenses that were settled before this migration).
         */
        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE expenses ADD COLUMN finalSpentAmount INTEGER"
                )
            }
        }

        /**
         * Room migration v6 → v7 (TIER 4 cross-platform equivalence fixes).
         *
         * 1. subjects.coefficient + assessments.coefficient: INTEGER → REAL
         *    (SQL NUMERIC(4,2) parity — Int truncated decimal coefficients).
         * 2. assessments.isExtracurricular: NEW — canonical GPA exclusion rule.
         * 3. classes.capacity: nullable (null = unlimited; desktop parity).
         * 4. parents.cityTier: NEW TEXT (0028 schema parity).
         * 5. payments.expectedAmount / excessAmount / excessRemark: NEW
         *    (v2 audit D13/R13 — partial/overpayment tracking).
         * 6. ledger_cache.metadataJson — metadata survives the cache path.
         */
        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS subjects_new (" +
                        "id TEXT NOT NULL PRIMARY KEY, tenantId TEXT NOT NULL, code TEXT NOT NULL, " +
                        "name TEXT NOT NULL, category TEXT NOT NULL, coefficient REAL NOT NULL, " +
                        "weeklyHours REAL NOT NULL, isExtracurricular INTEGER NOT NULL, isActive INTEGER NOT NULL)"
                )
                database.execSQL(
                    "INSERT INTO subjects_new (id, tenantId, code, name, category, coefficient, weeklyHours, isExtracurricular, isActive) " +
                        "SELECT id, tenantId, code, name, category, CAST(coefficient AS REAL), weeklyHours, isExtracurricular, isActive FROM subjects"
                )
                database.execSQL("DROP TABLE subjects")
                database.execSQL("ALTER TABLE subjects_new RENAME TO subjects")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_subjects_code ON subjects(code)")

                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS assessments_new (" +
                        "id TEXT NOT NULL PRIMARY KEY, tenantId TEXT NOT NULL, studentId TEXT NOT NULL, " +
                        "subjectId TEXT NOT NULL, classId TEXT NOT NULL, term TEXT NOT NULL, academicYear TEXT NOT NULL, " +
                        "devoir1 REAL, devoir2 REAL, examen REAL, coefficient REAL NOT NULL, " +
                        "isExtracurricular INTEGER NOT NULL DEFAULT 0, subjectAverage REAL, " +
                        "enteredBy TEXT NOT NULL, enteredAt TEXT NOT NULL)"
                )
                database.execSQL(
                    "INSERT INTO assessments_new (id, tenantId, studentId, subjectId, classId, term, academicYear, devoir1, devoir2, examen, coefficient, isExtracurricular, subjectAverage, enteredBy, enteredAt) " +
                        "SELECT id, tenantId, studentId, subjectId, classId, term, academicYear, devoir1, devoir2, examen, CAST(coefficient AS REAL), 0, subjectAverage, enteredBy, enteredAt FROM assessments"
                )
                database.execSQL("DROP TABLE assessments")
                database.execSQL("ALTER TABLE assessments_new RENAME TO assessments")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_assessments_studentId ON assessments(studentId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_assessments_subjectId ON assessments(subjectId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_assessments_classId ON assessments(classId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_assessments_term ON assessments(term)")

                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS classes_new (" +
                        "id TEXT NOT NULL PRIMARY KEY, tenantId TEXT NOT NULL, code TEXT NOT NULL, " +
                        "name TEXT NOT NULL, level TEXT NOT NULL, gradeYear INTEGER NOT NULL, gradeLevel TEXT NOT NULL, " +
                        "section TEXT, room TEXT, capacity INTEGER, homeroomTeacherId TEXT, homeroomTeacherName TEXT, " +
                        "academicYear TEXT NOT NULL, isActive INTEGER NOT NULL, createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL)"
                )
                database.execSQL(
                    "INSERT INTO classes_new (id, tenantId, code, name, level, gradeYear, gradeLevel, section, room, capacity, homeroomTeacherId, homeroomTeacherName, academicYear, isActive, createdAt, updatedAt) " +
                        "SELECT id, tenantId, code, name, level, gradeYear, gradeLevel, section, room, capacity, homeroomTeacherId, homeroomTeacherName, academicYear, isActive, createdAt, updatedAt FROM classes"
                )
                database.execSQL("DROP TABLE classes")
                database.execSQL("ALTER TABLE classes_new RENAME TO classes")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_classes_code ON classes(code)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_classes_gradeLevel ON classes(gradeLevel)")

                database.execSQL("ALTER TABLE parents ADD COLUMN cityTier TEXT")
                database.execSQL("ALTER TABLE payments ADD COLUMN expectedAmount INTEGER")
                database.execSQL("ALTER TABLE payments ADD COLUMN excessAmount INTEGER")
                database.execSQL("ALTER TABLE payments ADD COLUMN excessRemark TEXT")
                database.execSQL(
                    "ALTER TABLE ledger_cache ADD COLUMN metadataJson TEXT NOT NULL DEFAULT '{}'"
                )
            }
        }

        /**
         * Room migration v7 → v8 (subjects level + passing grade).
         *
         * FIX (broken level filter + fake archive):
         *   1. subjects.level — NEW TEXT, default 'all'. The SubjectsDirectory
         *      level filter chips (primaire/CEM/Lycée) previously filtered on
         *      a hardcoded `level = "all"` so every chip showed an EMPTY list.
         *   2. subjects.passingGrade — NEW REAL, default 10. The directory
         *      showed a hardcoded "Seuil réussite: 10/20".
         */
        val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE subjects ADD COLUMN level TEXT NOT NULL DEFAULT 'all'"
                )
                database.execSQL(
                    "ALTER TABLE subjects ADD COLUMN passingGrade REAL NOT NULL DEFAULT 10.0"
                )
            }
        }

        /**
         * Room migration v8 → v9 (routing + class-subject wiring).
         *
         * The routing feature was previously a stub repository that returned
         * empty lists and "Not implemented" errors — the three routing screens
         * (hub, live map, trip history) could never show real data. This
         * migration adds the backing tables:
         *
         *   1. `vehicles` — transport vehicles (plate, driver, capacity, PMR).
         *   2. `routing_stops` — pickup/drop-off stops with coordinates + shift.
         *   3. `class_subjects` — per-class subject assignments (previously the
         *      `assignSubjectToClass` repository call was a silent no-op).
         *   4. `trip_logs.vehicleId` + `trip_logs.stopsCompleted` — the domain
         *      TripLog carries both fields; the entity previously had neither,
         *      so trip rows could not be linked to a vehicle or record how many
         *      stops the driver actually completed.
         */
        val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS vehicles (" +
                        "id TEXT NOT NULL PRIMARY KEY, tenantId TEXT NOT NULL, plate TEXT NOT NULL, " +
                        "driverId TEXT, driverName TEXT, capacity INTEGER NOT NULL, " +
                        "hasWheelchairAccess INTEGER NOT NULL DEFAULT 0, " +
                        "isActive INTEGER NOT NULL DEFAULT 1, createdAt TEXT NOT NULL)"
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_vehicles_plate ON vehicles(plate)"
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS routing_stops (" +
                        "id TEXT NOT NULL PRIMARY KEY, tenantId TEXT NOT NULL, studentId TEXT NOT NULL, " +
                        "studentName TEXT NOT NULL, address TEXT NOT NULL, lat REAL NOT NULL, lng REAL NOT NULL, " +
                        "shift TEXT NOT NULL, orderInRoute INTEGER NOT NULL DEFAULT 0, " +
                        "estimatedMinutesFromPrevious REAL NOT NULL DEFAULT 0.0, " +
                        "isActive INTEGER NOT NULL DEFAULT 1, createdAt TEXT NOT NULL)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_routing_stops_studentId ON routing_stops(studentId)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_routing_stops_shift ON routing_stops(shift)"
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS class_subjects (" +
                        "id TEXT NOT NULL PRIMARY KEY, tenantId TEXT NOT NULL, classId TEXT NOT NULL, " +
                        "subjectId TEXT NOT NULL, teacherId TEXT, weeklyHours INTEGER NOT NULL, " +
                        "coefficient REAL NOT NULL, createdAt TEXT NOT NULL)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_class_subjects_classId ON class_subjects(classId)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_class_subjects_subjectId ON class_subjects(subjectId)"
                )
                database.execSQL(
                    "ALTER TABLE trip_logs ADD COLUMN vehicleId TEXT NOT NULL DEFAULT ''"
                )
                database.execSQL(
                    "ALTER TABLE trip_logs ADD COLUMN stopsCompleted INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * Room migration v9 → v10 (vault-compliance batch registration +
         * homework engine).
         *
         *   1. `parents.nationalId` + `parents.relationship` — vault §04.03
         *      batch-registration master-info fields. The Supabase
         *      `parents` table already carries `national_id` and
         *      `relationship`; this brings the local cache to schema parity
         *      (the pull-side ParentDto mapper fills them from the server).
         *   2. `homework.academicYear` + `homework.pushedAt` — vault §06.06
         *      Homework Assignment Engine: the academic year scopes the
         *      assignment and `pushedAt` stamps the portal push (both columns
         *      exist on the backend homework table).
         */
        val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE parents ADD COLUMN nationalId TEXT"
                )
                database.execSQL(
                    "ALTER TABLE parents ADD COLUMN relationship TEXT"
                )
                database.execSQL(
                    "ALTER TABLE homework ADD COLUMN academicYear TEXT"
                )
                database.execSQL(
                    "ALTER TABLE homework ADD COLUMN pushedAt TEXT"
                )
            }
        }

        /**
         * Room migration v10 → v11 (vault §06.02 iteration 2 — per-component
         * subject-average coefficients).
         *
         * Adds three REAL columns to `subjects` (admin-configurable per-
         * component weights for Devoir 1 / Devoir 2 / Examen) and the same
         * three columns to `assessments` (a per-row snapshot taken at grade-
         * entry time, so archived years stay immutable when an admin later
         * edits the subject's coefficients — vault §04.07 append-only).
         *
         * Defaults (1.0, 1.0, 2.0) preserve the historical `(D1 + D2 + 2×Ex) / 4`
         * recipe bit-identically: (D1×1 + D2×1 + Ex×2) / (1+1+2) is the same
         * numerator and the same denominator. So existing GPAs computed under
         * the previous build do not move by a single centime after the
         * migration — the new columns only enable admins to override the
         * weights per subject going forward.
         *
         * This is the "old approach" the user asked for: each component
         * carries its OWN coefficient instead of the previous hard-coded
         * formula. The shared Supabase `subjects` table can adopt the same
         * three columns (`coefficient_devoir_1`, `coefficient_devoir_2`,
         * `coefficient_examen`) when the backend repo catches up; until then
         * the Android app falls back to the (1, 1, 2) defaults on pull-side
         * (see [SharedDtoMappers.SubjectDto.toEntity]).
         */
        val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Subject-level per-component coefficients (admin-configurable).
                database.execSQL(
                    "ALTER TABLE subjects ADD COLUMN coefficientDevoir1 REAL NOT NULL DEFAULT 1.0"
                )
                database.execSQL(
                    "ALTER TABLE subjects ADD COLUMN coefficientDevoir2 REAL NOT NULL DEFAULT 1.0"
                )
                database.execSQL(
                    "ALTER TABLE subjects ADD COLUMN coefficientExamen REAL NOT NULL DEFAULT 2.0"
                )
                // Per-row coefficient SNAPSHOT on assessments — copied from
                // the subject at grade-entry time; archived years never change.
                database.execSQL(
                    "ALTER TABLE assessments ADD COLUMN coefficientDevoir1 REAL NOT NULL DEFAULT 1.0"
                )
                database.execSQL(
                    "ALTER TABLE assessments ADD COLUMN coefficientDevoir2 REAL NOT NULL DEFAULT 1.0"
                )
                database.execSQL(
                    "ALTER TABLE assessments ADD COLUMN coefficientExamen REAL NOT NULL DEFAULT 2.0"
                )
            }
        }

        /**
         * Room migration v11 → v12 (T-054 / WEAK-008): `workflow_runs` gains
         * the REAL trigger column (manual | scheduled | event). The
         * WorkflowRunDto already pulls `trigger` from the server's
         * `workflow_runs.trigger` column — the local entity simply dropped
         * it at the mapping boundary and toDomain() hardcoded "manual".
         * Default 'manual' keeps existing rows' historical meaning (the old
         * display said "Manuel" for everything).
         */
        val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE workflow_runs ADD COLUMN trigger TEXT NOT NULL DEFAULT 'manual'"
                )
            }
        }
    }
}
