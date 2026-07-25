package com.elimtiyaz.data.mock

import com.elimtiyaz.domain.model.AcademicClass
import com.elimtiyaz.domain.model.AccountAdjustment
import com.elimtiyaz.domain.model.AppNotification
import com.elimtiyaz.domain.model.Assessment
import com.elimtiyaz.domain.model.AttendanceRecord
import com.elimtiyaz.domain.model.AttendanceSession
import com.elimtiyaz.domain.model.AuditEntry
import com.elimtiyaz.domain.model.ClassSubject
import com.elimtiyaz.domain.model.CreateParentInput
import com.elimtiyaz.domain.model.CreateStudentInput
import com.elimtiyaz.domain.model.DashboardKpi
import com.elimtiyaz.domain.model.DebtByAgingBucket
import com.elimtiyaz.domain.model.DebtSummary
import com.elimtiyaz.domain.model.DemographicSlice
import com.elimtiyaz.domain.model.Expense
import com.elimtiyaz.domain.model.ExpenseCategory
import com.elimtiyaz.domain.model.Gender
import com.elimtiyaz.domain.model.Homework
import com.elimtiyaz.domain.model.Installment
import com.elimtiyaz.domain.model.NotificationType
import com.elimtiyaz.domain.model.Parent
import com.elimtiyaz.domain.model.Payment
import com.elimtiyaz.domain.model.PaymentCategory
import com.elimtiyaz.domain.model.Personnel
import com.elimtiyaz.domain.model.PersonnelStatus
import com.elimtiyaz.domain.model.PromotionDecision
import com.elimtiyaz.domain.model.ReleveEntry
import com.elimtiyaz.domain.model.RevenuePoint
import com.elimtiyaz.domain.model.RoutingShift
import com.elimtiyaz.domain.model.RoutingStop
import com.elimtiyaz.domain.model.StaffCategory
import com.elimtiyaz.domain.model.Student
import com.elimtiyaz.domain.model.StudentStatus
import com.elimtiyaz.domain.model.Subject
import com.elimtiyaz.domain.model.TripLog
import com.elimtiyaz.domain.model.Vehicle
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.UUID

/**
 * Singleton holding the seed data for the mock repositories. The data is
 * fully cross-referenced: every student references a parent id; every payment
 * references a parent + (optional) student; every assessment references a
 * student + subject + class; every routing stop references a student.
 *
 * Names are bilingual (Arabic + French context) so the UI's fr/ar locales
 * both have something authentic to render.
 */
object MockData {

    /** Default tenant id used throughout the mock seed. */
    const val TENANT_ID = "tenant-elimtiyaz-oran"

    /** The current academic year for the mock seed. */
    const val ACADEMIC_YEAR = "2024-2025"

    /** Helper: returns the current ISO timestamp. */
    fun nowIso(): String = Clock.System.now().toString()

    /** Helper: returns a date N days ago (or in the future if [days] is negative) as an ISO string. */
    fun daysAgoIso(days: Int): String {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val shifted = today.minus(kotlinx.datetime.DatePeriod(days = days))
        return shifted.atStartOfDayIn(TimeZone.currentSystemDefault()).toString()
    }

    // ───────────────────────────── Parents ─────────────────────────────
    /** Eight seed parents with bilingual Algerian names. */
    val parents: List<Parent> = listOf(
        Parent("p1", TENANT_ID, "PAR-2024-A1B2", "Karim", "Benali", Gender.Male, "+213 770 11 22 33", "+213 770 11 22 33", "k.benali@example.dz", "Ingénieur", "Cité 1000 Logements, Oran", "t1", "fr", createdAt = daysAgoIso(220), updatedAt = daysAgoIso(20)),
        Parent("p2", TENANT_ID, "PAR-2024-C3D4", "Fatima", "Zidane", Gender.Female, "+213 661 45 67 89", "+213 661 45 67 89", "f.zidane@example.dz", "Enseignante", "Hai El Yasmine, Es Senia", "t2", "fr", createdAt = daysAgoIso(210), updatedAt = daysAgoIso(18)),
        Parent("p3", TENANT_ID, "PAR-2024-E5F6", "Ahmed", "Cherif", Gender.Male, "+213 555 12 34 56", null, null, "Commerçant", "Centre-ville, Oran", "t1", "ar", createdAt = daysAgoIso(205), updatedAt = daysAgoIso(15)),
        Parent("p4", TENANT_ID, "PAR-2024-G7H8", "Nadia", "Belkacem", Gender.Female, "+213 770 88 77 66", "+213 770 88 77 66", "n.belkacem@example.dz", "Médecin", "Boulevard Front de Mer, Oran", "t1", "fr", createdAt = daysAgoIso(190), updatedAt = daysAgoIso(12)),
        Parent("p5", TENANT_ID, "PAR-2024-I9J0", "Yacine", "Mansouri", Gender.Male, "+213 661 22 11 00", "+213 661 22 11 00", "y.mansouri@example.dz", "Chauffeur", "Sidi El Houari, Oran", "t2", "ar", createdAt = daysAgoIso(180), updatedAt = daysAgoIso(8)),
        Parent("p6", TENANT_ID, "PAR-2024-K1L2", "Samira", "Haddad", Gender.Female, "+213 555 99 88 77", null, null, "Femme au foyer", "Bir El Djir, Oran", "t3", "ar", createdAt = daysAgoIso(170), updatedAt = daysAgoIso(5)),
        Parent("p7", TENANT_ID, "PAR-2024-M3N4", "Rachid", "Saadi", Gender.Male, "+213 770 33 22 11", "+213 770 33 22 11", "r.saadi@example.dz", "Pharmacien", "Rue Larbi Ben M'hidi, Oran", "t1", "fr", createdAt = daysAgoIso(160), updatedAt = daysAgoIso(3)),
        Parent("p8", TENANT_ID, "PAR-2024-O5P6", "Leila", "Bouzid", Gender.Female, "+213 661 44 33 22", "+213 661 44 33 22", "l.bouzid@example.dz", "Avocate", "Hai El Badr, Es Senia", "t2", "fr", createdAt = daysAgoIso(150), updatedAt = daysAgoIso(2)),
    )

    // ───────────────────────────── Subjects ─────────────────────────────
    /** Twelve subjects spanning the three levels. */
    val subjects: List<Subject> = listOf(
        Subject("s1", TENANT_ID, "Mathématiques", "رياضيات", "MATH", "primaire", 4.0, false, 10.0),
        Subject("s2", TENANT_ID, "Français", "فرنسية", "FR", "primaire", 4.0, false, 10.0),
        Subject("s3", TENANT_ID, "Arabe", "عربية", "AR", "primaire", 4.0, false, 10.0),
        Subject("s4", TENANT_ID, "Sciences", "علوم", "SCI", "primaire", 2.0, false, 10.0),
        Subject("s5", TENANT_ID, "Éducation Islamique", "تربية إسلامية", "ISL", "primaire", 2.0, false, 10.0),
        Subject("s6", TENANT_ID, "Anglais", "إنجليزية", "EN", "cem", 3.0, false, 10.0),
        Subject("s7", TENANT_ID, "Histoire-Géographie", "تاريخ وجغرافيا", "HG", "cem", 2.0, false, 10.0),
        Subject("s8", TENANT_ID, "Physique", "فيزياء", "PHY", "lycee", 3.0, false, 10.0),
        Subject("s9", TENANT_ID, "Philosophie", "فلسفة", "PHILO", "lycee", 2.0, false, 10.0),
        Subject("s10", TENANT_ID, "Informatique", "إعلام آلي", "INFO", "lycee", 2.0, false, 10.0),
        Subject("s11", TENANT_ID, "Club Théâtre", "نادي المسرح", "THEATRE", "primaire", 1.0, true, 10.0),
        Subject("s12", TENANT_ID, "Orthophonie", "تخاطب", "ORTHO", "primaire", 1.0, true, 10.0),
    )

    // ───────────────────────────── Classes ─────────────────────────────
    /** Six classes spread across the three levels. */
    val classes: List<AcademicClass> = listOf(
        AcademicClass("c1", TENANT_ID, "1ère Primaire A", "primaire", 1, "t1", "Mme Bouzid", "Salle 12", 28, 24, ACADEMIC_YEAR),
        AcademicClass("c2", TENANT_ID, "3ème Primaire B", "primaire", 3, "t2", "M. Saadi", "Salle 7", 30, 27, ACADEMIC_YEAR),
        AcademicClass("c3", TENANT_ID, "5ème Primaire A", "primaire", 5, "t1", "Mme Haddad", "Salle 3", 28, 26, ACADEMIC_YEAR),
        AcademicClass("c4", TENANT_ID, "1ère CEM", "cem", 1, "t3", "M. Cherif", "Salle 21", 32, 30, ACADEMIC_YEAR),
        AcademicClass("c5", TENANT_ID, "4ème CEM", "cem", 4, "t2", "M. Mansouri", "Salle 18", 30, 28, ACADEMIC_YEAR),
        AcademicClass("c6", TENANT_ID, "3ème Lycée Sc. Exp.", "lycee", 3, "t3", "Mme Belkacem", "Salle 25", 30, 29, ACADEMIC_YEAR),
    )

    /** Class-subject mappings (sample for each class). */
    val classSubjects: List<ClassSubject> = listOf(
        ClassSubject("cs1", "c1", "s1", "t1", "Mme Bouzid", 6, 4.0),
        ClassSubject("cs2", "c1", "s2", "t1", "Mme Bouzid", 6, 4.0),
        ClassSubject("cs3", "c1", "s3", "t1", "Mme Bouzid", 6, 4.0),
        ClassSubject("cs4", "c2", "s1", "t2", "M. Saadi", 5, 4.0),
        ClassSubject("cs5", "c2", "s4", "t2", "M. Saadi", 3, 2.0),
        ClassSubject("cs6", "c3", "s1", "t1", "Mme Haddad", 5, 4.0),
        ClassSubject("cs7", "c4", "s6", "t3", "M. Cherif", 4, 3.0),
        ClassSubject("cs8", "c4", "s7", "t3", "M. Cherif", 3, 2.0),
        ClassSubject("cs9", "c5", "s6", "t2", "M. Mansouri", 4, 3.0),
        ClassSubject("cs10", "c6", "s8", "t3", "Mme Belkacem", 5, 3.0),
        ClassSubject("cs11", "c6", "s9", "t3", "Mme Belkacem", 3, 2.0),
        ClassSubject("cs12", "c6", "s10", "t3", "Mme Belkacem", 2, 2.0),
    )

    // ───────────────────────────── Students ─────────────────────────────
    /** Fifteen students spread across the eight parents. */
    val students: List<Student> = listOf(
        Student("st1", TENANT_ID, "ELV-2024-000001", "p1", "Yacine", "Benali", Gender.Male, "2017-03-12", "2024-09-01", "primaire", 1, "c1", null, null, "t1", StudentStatus.Active, createdAt = daysAgoIso(220), updatedAt = daysAgoIso(20)),
        Student("st2", TENANT_ID, "ELV-2024-000002", "p1", "Amina", "Benali", Gender.Female, "2015-09-04", "2024-09-01", "primaire", 3, "c2", null, "Asthme léger", "t1", StudentStatus.Active, createdAt = daysAgoIso(220), updatedAt = daysAgoIso(18)),
        Student("st3", TENANT_ID, "ELV-2024-000003", "p2", "Omar", "Zidane", Gender.Male, "2018-01-22", "2024-09-01", "primaire", 1, "c1", null, null, "t2", StudentStatus.Active, createdAt = daysAgoIso(210), updatedAt = daysAgoIso(15)),
        Student("st4", TENANT_ID, "ELV-2024-000004", "p2", "Lina", "Zidane", Gender.Female, "2013-05-15", "2024-09-01", "cem", 1, "c4", null, null, "t2", StudentStatus.Active, createdAt = daysAgoIso(210), updatedAt = daysAgoIso(12)),
        Student("st5", TENANT_ID, "ELV-2024-000005", "p3", "Bilal", "Cherif", Gender.Male, "2016-08-30", "2024-09-01", "primaire", 1, "c1", null, null, "t1", StudentStatus.Active, createdAt = daysAgoIso(205), updatedAt = daysAgoIso(10)),
        Student("st6", TENANT_ID, "ELV-2024-000006", "p3", "Sara", "Cherif", Gender.Female, "2014-02-18", "2024-09-01", "primaire", 5, "c3", null, null, "t1", StudentStatus.Active, createdAt = daysAgoIso(205), updatedAt = daysAgoIso(8)),
        Student("st7", TENANT_ID, "ELV-2024-000007", "p4", "Mohamed", "Belkacem", Gender.Male, "2017-11-09", "2024-09-01", "primaire", 1, "c1", null, null, "t1", StudentStatus.Active, createdAt = daysAgoIso(190), updatedAt = daysAgoIso(6)),
        Student("st8", TENANT_ID, "ELV-2024-000008", "p4", "Khadija", "Belkacem", Gender.Female, "2012-07-25", "2024-09-01", "cem", 4, "c5", null, null, "t1", StudentStatus.Active, createdAt = daysAgoIso(190), updatedAt = daysAgoIso(4)),
        Student("st9", TENANT_ID, "ELV-2024-000009", "p4", "Anis", "Belkacem", Gender.Male, "2006-04-12", "2024-09-01", "lycee", 3, "c6", null, null, "t1", StudentStatus.Active, createdAt = daysAgoIso(190), updatedAt = daysAgoIso(2)),
        Student("st10", TENANT_ID, "ELV-2024-000010", "p5", "Imane", "Mansouri", Gender.Female, "2015-10-03", "2024-09-01", "primaire", 3, "c2", null, null, "t2", StudentStatus.Active, createdAt = daysAgoIso(180), updatedAt = daysAgoIso(8)),
        Student("st11", TENANT_ID, "ELV-2024-000011", "p6", "Adam", "Haddad", Gender.Male, "2018-12-19", "2024-09-01", "primaire", 1, "c1", null, "Allergie aux arachides", "t3", StudentStatus.Active, createdAt = daysAgoIso(170), updatedAt = daysAgoIso(5)),
        Student("st12", TENANT_ID, "ELV-2024-000012", "p7", "Maya", "Saadi", Gender.Female, "2013-03-28", "2024-09-01", "cem", 1, "c4", null, null, "t1", StudentStatus.Active, createdAt = daysAgoIso(160), updatedAt = daysAgoIso(3)),
        Student("st13", TENANT_ID, "ELV-2024-000013", "p7", "Nael", "Saadi", Gender.Male, "2007-06-14", "2024-09-01", "lycee", 3, "c6", null, null, "t1", StudentStatus.Active, createdAt = daysAgoIso(160), updatedAt = daysAgoIso(2)),
        Student("st14", TENANT_ID, "ELV-2024-000014", "p8", "Rania", "Bouzid", Gender.Female, "2014-09-08", "2024-09-01", "primaire", 5, "c3", null, null, "t2", StudentStatus.Active, createdAt = daysAgoIso(150), updatedAt = daysAgoIso(1)),
        Student("st15", TENANT_ID, "ELV-2024-000015", "p8", "Walid", "Bouzid", Gender.Male, "2012-11-30", "2024-09-01", "cem", 4, "c5", null, null, "t2", StudentStatus.Active, createdAt = daysAgoIso(150), updatedAt = daysAgoIso(1)),
    )

    // ───────────────────────────── Personnel ─────────────────────────────
    /** Ten staff across the five categories. */
    val personnel: List<Personnel> = listOf(
        Personnel("pe1", TENANT_ID, "Mme", "Bouzid", StaffCategory.Teacher, "+213 770 11 22 33", "mme.bouzid@elimtiyaz.dz", "2019-09-01", 65000.0, 24, 22, null, PersonnelStatus.Active),
        Personnel("pe2", TENANT_ID, "M.", "Saadi", StaffCategory.Teacher, "+213 770 44 55 66", "m.saadi@elimtiyaz.dz", "2018-09-01", 72000.0, 24, 20, null, PersonnelStatus.Active),
        Personnel("pe3", TENANT_ID, "Mme", "Haddad", StaffCategory.Teacher, "+213 770 77 88 99", "mme.haddad@elimtiyaz.dz", "2020-09-01", 60000.0, 24, 24, null, PersonnelStatus.Active),
        Personnel("pe4", TENANT_ID, "M.", "Cherif", StaffCategory.Teacher, "+213 770 12 34 56", "m.cherif@elimtiyaz.dz", "2017-09-01", 75000.0, 24, 21, null, PersonnelStatus.Active),
        Personnel("pe5", TENANT_ID, "M.", "Mansouri", StaffCategory.Teacher, "+213 770 65 43 21", "m.mansouri@elimtiyaz.dz", "2019-09-01", 68000.0, 24, 23, null, PersonnelStatus.Active),
        Personnel("pe6", TENANT_ID, "Mme", "Belkacem", StaffCategory.Teacher, "+213 770 99 88 77", "mme.belkacem@elimtiyaz.dz", "2015-09-01", 80000.0, 24, 24, null, PersonnelStatus.Active),
        Personnel("pe7", TENANT_ID, "M.", "Boudjelal", StaffCategory.Administration, "+213 770 11 99 88", "m.boudjelal@elimtiyaz.dz", "2014-09-01", 90000.0, 40, 38, null, PersonnelStatus.Active),
        Personnel("pe8", TENANT_ID, "Mme", "Larbi", StaffCategory.Support, "+213 770 22 33 44", "mme.larbi@elimtiyaz.dz", "2021-09-01", 45000.0, 35, 33, null, PersonnelStatus.Active),
        Personnel("pe9", TENANT_ID, "M.", "Toumi", StaffCategory.Maintenance, "+213 770 55 66 77", "m.toumi@elimtiyaz.dz", "2019-01-01", 38000.0, 40, 36, null, PersonnelStatus.OnLeave),
        Personnel("pe10", TENANT_ID, "M.", "Belhadj", StaffCategory.Driver, "+213 770 98 76 54", "m.belhadj@elimtiyaz.dz", "2020-09-01", 42000.0, 30, 28, null, PersonnelStatus.Active),
    )

    // ───────────────────────────── Payments (30) ─────────────────────────────
    /** Thirty payments across the eight parents. */
    val payments: List<Payment> = List(30) { idx ->
        val parent = parents[idx % parents.size]
        val student = students.firstOrNull { it.parentId == parent.id }
        val category = PaymentCategory.values()[idx % PaymentCategory.values().size]
        val method = listOf("cash", "check", "transfer")[idx % 3]
        val amount = listOf(15000.0, 25000.0, 8000.0, 12000.0, 5000.0, 18000.0)[idx % 6]
        val receiptSeq = 100 + idx
        Payment(
            id = "pay${idx + 1}", tenantId = TENANT_ID, receiptNumber = "REC-2024-${receiptSeq.toString().padStart(6, '0')}",
            parentId = parent.id, studentId = student?.id, amount = amount, method = method,
            status = if (idx % 9 == 0) "pending" else "paid", category = category, installmentId = null,
            proofUrl = if (method != "cash") "https://elimtiyaz.supabase.co/storage/v1/object/public/proofs/pay${idx + 1}.jpg" else null,
            notes = null, collectedBy = "pe7", collectedAt = daysAgoIso(120 - idx * 3),
            createdAt = daysAgoIso(120 - idx * 3), updatedAt = daysAgoIso(120 - idx * 3),
        )
    }

    // ───────────────────────────── Installments ─────────────────────────────
    /** Three tuition installments per parent (T1/T2/T3) + transport installments. */
    val installments: List<Installment> = parents.flatMap { p ->
        val student = students.firstOrNull { it.parentId == p.id } ?: return@flatMap emptyList()
        listOf(
            Installment("i_${p.id}_t1", p.id, student.id, PaymentCategory.Tuition, "Tranche 1", 25000.0, 25000.0, "2024-09-15", "2024-09-10", "paid"),
            Installment("i_${p.id}_t2", p.id, student.id, PaymentCategory.Tuition, "Tranche 2", 25000.0,
                if (p.id == "p5" || p.id == "p6") 5000.0 else 25000.0, "2024-12-15",
                if (p.id == "p5" || p.id == "p6") null else "2024-12-10",
                if (p.id == "p5" || p.id == "p6") "partial" else "paid"),
            Installment("i_${p.id}_t3", p.id, student.id, PaymentCategory.Tuition, "Tranche 3", 25000.0, 0.0, "2025-03-15", null, "overdue"),
        )
    }

    // ───────────────────────────── Expenses (5) ─────────────────────────────
    /** Five expense requests spanning the lifecycle. */
    val expenses: List<Expense> = listOf(
        Expense("e1", TENANT_ID, "EXP-2024-001", "Facture SONELGAZ", "Facture d'électricité septembre", 18500.0, ExpenseCategory.Utilities, "SONELGAZ", "settled", "pe7", daysAgoIso(60), "pe7", daysAgoIso(58), "Approuvé — urgence hivernage", "pe7", daysAgoIso(55), "https://elimtiyaz.supabase.co/storage/v1/object/public/proofs/e1.pdf", "pe7", daysAgoIso(50), null, null),
        Expense("e2", TENANT_ID, "EXP-2024-002", "Fournitures bureau", "Stylos, cahiers, papier A4", 12500.0, ExpenseCategory.Supplies, "Papeterie El Ilm", "disbursed", "pe7", daysAgoIso(40), "pe7", daysAgoIso(38), "OK", "pe7", daysAgoIso(35), null, null, null, null, null),
        Expense("e3", TENANT_ID, "EXP-2024-003", "Réparation climatisation", "Salle 25 — panne compresseur", 42000.0, ExpenseCategory.Maintenance, "Clima Service Oran", "approved", "pe7", daysAgoIso(20), "pe7", daysAgoIso(18), "Approuvé — chaleur excessive", null, null, null, null, null, null, null),
        Expense("e4", TENANT_ID, "EXP-2024-004", "Carburant transport", "Plein bus scolaire — 2 semaines", 22000.0, ExpenseCategory.Transport, "Naftal", "submitted", "pe7", daysAgoIso(5), null, null, null, null, null, null, null, null, null, null),
        Expense("e5", TENANT_ID, "EXP-2024-005", "Fête de fin d'année", "Décoration + goûter 200 élèves", 35000.0, ExpenseCategory.Event, "Déco Plus", "submitted", "pe7", daysAgoIso(2), null, null, null, null, null, null, null, null, null, null),
    )

    // ───────────────────────────── Assessments ─────────────────────────────
    /** Sample assessments for the first few students. */
    val assessments: List<Assessment> = listOf(
        Assessment("a1", "st1", "s1", "c1", "T1", ACADEMIC_YEAR, 14.0, 16.0, 15.0, 15.0, 4.0, "pe3", daysAgoIso(15)),
        Assessment("a2", "st1", "s2", "c1", "T1", ACADEMIC_YEAR, 12.0, 13.0, 14.0, 13.25, 4.0, "pe3", daysAgoIso(14)),
        Assessment("a3", "st2", "s1", "c2", "T1", ACADEMIC_YEAR, 15.5, 14.0, 17.0, 15.375, 4.0, "pe2", daysAgoIso(13)),
        Assessment("a4", "st4", "s6", "c4", "T1", ACADEMIC_YEAR, 13.0, 14.0, 15.0, 14.25, 3.0, "pe4", daysAgoIso(12)),
        Assessment("a5", "st9", "s8", "c6", "T1", ACADEMIC_YEAR, 16.0, 15.0, 17.5, 16.5, 3.0, "pe6", daysAgoIso(10)),
        Assessment("a6", "st13", "s9", "c6", "T1", ACADEMIC_YEAR, 11.0, 13.0, 14.0, 13.0, 2.0, "pe6", daysAgoIso(9)),
    )

    // ───────────────────────────── Attendance ─────────────────────────────
    /** Attendance for the last day for class c1. */
    val attendanceRecords: List<AttendanceRecord> = students.filter { it.classId == "c1" }.mapIndexed { idx, s ->
        AttendanceRecord(
            id = "att_${s.id}", studentId = s.id, classId = "c1", date = daysAgoIso(1).take(10),
            session = AttendanceSession.Morning,
            status = if (idx == 2) "absent_unexcused" else if (idx == 4) "late" else "present",
            note = null, recordedBy = "pe7", recordedAt = daysAgoIso(1), syncedAt = daysAgoIso(1),
        )
    }

    // ───────────────────────────── Homework ─────────────────────────────
    /** Sample homework pushed by teachers. */
    val homework: List<Homework> = listOf(
        Homework("h1", "c1", "s1", "Mathématiques", "pe3", "Mme Haddad", "Exercices page 24",
            "Faire les exercices 1 à 5 sur les additions à retenue.", (daysAgoIso(-3)).take(10),
            emptyList(), ACADEMIC_YEAR, daysAgoIso(1), daysAgoIso(1), 12),
        Homework("h2", "c4", "s6", "Anglais", "pe4", "M. Cherif", "Vocabulary — Family",
            "Learn the family members vocabulary on page 18.", (daysAgoIso(-2)).take(10),
            emptyList(), ACADEMIC_YEAR, daysAgoIso(1), daysAgoIso(1), 18),
        Homework("h3", "c6", "s8", "Physique", "pe6", "Mme Belkacem", "TD — Cinématique",
            "Résoudre les exercices 1-4 du TD sur le mouvement rectiligne uniforme.",
            (daysAgoIso(-5)).take(10), emptyList(), ACADEMIC_YEAR, daysAgoIso(2), daysAgoIso(2), 22),
    )

    // ───────────────────────────── Personnel (Releve entries) ─────────────────────────────
    /** Releve entries for the past week for the teachers. */
    val releveEntries: List<ReleveEntry> = listOf(
        ReleveEntry("r1", "pe1", "Mme Bouzid", daysAgoIso(1).take(10), 8.0, 12.0, "Cours de mathématiques — 1ère Primaire A", "c1", "s1", daysAgoIso(1)),
        ReleveEntry("r2", "pe2", "M. Saadi", daysAgoIso(1).take(10), 8.0, 12.0, "Cours de mathématiques — 3ème Primaire B", "c2", "s1", daysAgoIso(1)),
        ReleveEntry("r3", "pe3", "Mme Haddad", daysAgoIso(2).take(10), 13.0, 17.0, "Cours de mathématiques — 5ème Primaire A", "c3", "s1", daysAgoIso(2)),
        ReleveEntry("r4", "pe4", "M. Cherif", daysAgoIso(1).take(10), 8.0, 12.0, "Cours d'anglais — 1ère CEM", "c4", "s6", daysAgoIso(1)),
        ReleveEntry("r5", "pe6", "Mme Belkacem", daysAgoIso(1).take(10), 13.0, 18.0, "Cours de physique — 3ème Lycée", "c6", "s8", daysAgoIso(1)),
    )

    // ───────────────────────────── Audit log ─────────────────────────────
    /** Sample audit entries for the past week. */
    val auditEntries: List<AuditEntry> = listOf(
        AuditEntry("au1", TENANT_ID, "payment.create", "payment", "pay1", "pe7", "M. Boudjelal", null, "Encaissement comptoir", null, null, daysAgoIso(1)),
        AuditEntry("au2", TENANT_ID, "expense.submit", "expense", "e4", "pe7", "M. Boudjelal", null, "Demande carburant", null, null, daysAgoIso(5)),
        AuditEntry("au3", TENANT_ID, "attendance.submit", "attendance_batch", "c1", "pe3", "Mme Haddad", null, "Appel matinal", null, null, daysAgoIso(1)),
        AuditEntry("au4", TENANT_ID, "auth.login", "session", "pe7", "pe7", "M. Boudjelal", null, null, null, null, daysAgoIso(1)),
        AuditEntry("au5", TENANT_ID, "homework.push", "homework", "h3", "pe6", "Mme Belkacem", null, "Devoir physique", null, null, daysAgoIso(2)),
        AuditEntry("au6", TENANT_ID, "expense.approve", "expense", "e3", "pe7", "M. Boudjelal", null, "Réparation clim", null, null, daysAgoIso(18)),
    )

    // ───────────────────────────── Notifications ─────────────────────────────
    /** Sample in-app notifications. */
    val notifications: List<AppNotification> = listOf(
        AppNotification("n1", "Paiement en retard", "Parent Karim Benali — tranche 3 impayée depuis 12 jours.", NotificationType.PaymentOverdue, "parent", "p1", null, daysAgoIso(1)),
        AppNotification("n2", "Dépense en attente", "Demande de carburant (22 000 DZD) à approuver.", NotificationType.ExpensePending, "expense", "e4", null, daysAgoIso(5)),
        AppNotification("n3", "Absence signalée", "3 absences enregistrées aujourd'hui en 1ère Primaire A.", NotificationType.AttendanceAlert, "class", "c1", null, daysAgoIso(1)),
        AppNotification("n4", "Nouveau devoir", "Mme Belkacem a publié un devoir de physique pour la 3ème Lycée.", NotificationType.Homework, "homework", "h3", null, daysAgoIso(2)),
    )

    // ───────────────────────────── Routing ─────────────────────────────
    /** Twelve routing stops around Oran for the morning shift. */
    val routingStops: List<RoutingStop> = listOf(
        RoutingStop("rs1", "st1", "Yacine Benali", "Cité 1000 Logements Bât 12, Oran", 35.6976, -0.6337, RoutingShift.Morning, 1, 0.0),
        RoutingStop("rs2", "st3", "Omar Zidane", "Hai El Yasmine, Es Senia", 35.6550, -0.6180, RoutingShift.Morning, 2, 8.5),
        RoutingStop("rs3", "st5", "Bilal Cherif", "Centre-ville, Oran", 35.6980, -0.6349, RoutingShift.Morning, 3, 6.2),
        RoutingStop("rs4", "st7", "Mohamed Belkacem", "Bd Front de Mer, Oran", 35.7050, -0.6450, RoutingShift.Morning, 4, 4.0),
        RoutingStop("rs5", "st9", "Anis Belkacem", "Bd Front de Mer, Oran", 35.7050, -0.6450, RoutingShift.Morning, 5, 0.0),
        RoutingStop("rs6", "st10", "Imane Mansouri", "Sidi El Houari, Oran", 35.7190, -0.6460, RoutingShift.Morning, 6, 5.5),
        RoutingStop("rs7", "st11", "Adam Haddad", "Bir El Djir, Oran", 35.6980, -0.5280, RoutingShift.Morning, 7, 12.0),
        RoutingStop("rs8", "st12", "Maya Saadi", "Rue Larbi Ben M'hidi, Oran", 35.6960, -0.6330, RoutingShift.Morning, 8, 9.3),
        RoutingStop("rs9", "st13", "Nael Saadi", "Rue Larbi Ben M'hidi, Oran", 35.6960, -0.6330, RoutingShift.Morning, 9, 0.0),
        RoutingStop("rs10", "st14", "Rania Bouzid", "Hai El Badr, Es Senia", 35.6600, -0.6210, RoutingShift.Afternoon, 1, 0.0),
        RoutingStop("rs11", "st2", "Amina Benali", "Cité 1000 Logements Bât 8, Oran", 35.6972, -0.6340, RoutingShift.Afternoon, 2, 4.5),
        RoutingStop("rs12", "st6", "Sara Cherif", "Centre-ville, Oran", 35.6985, -0.6352, RoutingShift.Afternoon, 3, 5.0),
    )

    /** Two vehicles for the routing module. */
    val vehicles: List<Vehicle> = listOf(
        Vehicle("v1", "16-001-2345", "pe10", "M. Belhadj", 24, hasWheelchairLift = false),
        Vehicle("v2", "16-002-6789", "pe10", "M. Belhadj", 12, hasWheelchairLift = true),
    )

    /** Trip logs from the past week. */
    val tripLogs: List<TripLog> = listOf(
        TripLog("tl1", "v1", "pe10", daysAgoIso(1), daysAgoIso(1).dropLast(1) + "1", 8, 8, 18.5, null),
        TripLog("tl2", "v2", "pe10", daysAgoIso(2), daysAgoIso(2).dropLast(1) + "1", 6, 6, 12.0, null),
        TripLog("tl3", "v1", "pe10", daysAgoIso(3), daysAgoIso(3).dropLast(1) + "1", 7, 7, 16.5, "Trafic dense sur l'autoroute."),
    )

    /** Pre-built account adjustments. */
    val accountAdjustments: List<AccountAdjustment> = listOf(
        AccountAdjustment("ad1", "p6", -5000.0, "Aide sociale — discrétionnaire", "pe7", daysAgoIso(30)),
        AccountAdjustment("ad2", "p3", +1500.0, "Remboursement activité annulée", "pe7", daysAgoIso(15)),
    )

    // ───────────────────────────── Dashboard aggregations ─────────────────────────────
    /** Pre-built dashboard KPI block. */
    val dashboardKpis: DashboardKpi = DashboardKpi(
        totalStudents = students.size, totalParents = parents.size, totalStaff = personnel.size,
        monthlyRevenue = payments.filter { it.collectedAt.startsWith(daysAgoIso(0).take(7)) }.sumOf { it.amount }
            .takeIf { it > 0 } ?: 85000.0,
        outstandingDebt = installments.filter { it.status != "paid" }.sumOf { it.amountDue - it.amountPaid },
        pendingExpenses = expenses.count { it.status == "submitted" || it.status == "approved" },
        attendanceRateToday = 0.92, overdueAlerts = 3,
    )

    /** 12-month revenue series (mocked). */
    val revenueLast12Months: List<RevenuePoint> = listOf(
        RevenuePoint("2023-12", 145000.0), RevenuePoint("2024-01", 132000.0),
        RevenuePoint("2024-02", 98000.0),  RevenuePoint("2024-03", 175000.0),
        RevenuePoint("2024-04", 162000.0), RevenuePoint("2024-05", 158000.0),
        RevenuePoint("2024-06", 122000.0), RevenuePoint("2024-07", 88000.0),
        RevenuePoint("2024-08", 105000.0), RevenuePoint("2024-09", 245000.0),
        RevenuePoint("2024-10", 198000.0), RevenuePoint("2024-11", 85000.0),
    )

    /** Debt aging-bucket breakdown (mocked). */
    val debtByAging: List<DebtByAgingBucket> = listOf(
        DebtByAgingBucket("Bucket0_30", 28000.0, 2),
        DebtByAgingBucket("Bucket31_60", 50000.0, 3),
        DebtByAgingBucket("Bucket61_90", 75000.0, 2),
        DebtByAgingBucket("Bucket91_180", 0.0, 0),
        DebtByAgingBucket("Bucket180Plus", 0.0, 0),
    )

    /** Demographic slices by academic level. */
    val demographics: List<DemographicSlice> = listOf(
        DemographicSlice("primaire", 9, 60.0),
        DemographicSlice("cem", 4, 26.67),
        DemographicSlice("lycee", 2, 13.33),
    )

    /** Compute the parent financial profile from the seed data. */
    fun parentFinancialProfile(parentId: String): com.elimtiyaz.domain.model.ParentFinancialProfile {
        val parent = parents.first { it.id == parentId }
        val installments = installments.filter { it.parentId == parentId }
        val payments = payments.filter { it.parentId == parentId }
        val adjustments = accountAdjustments.filter { it.parentId == parentId }
        val totalDue = installments.sumOf { it.amountDue }
        val totalPaid = payments.sumOf { it.amount }
        val outstanding = (totalDue - totalPaid).coerceAtLeast(0.0)
        val overdue = installments.filter { it.status == "overdue" }.sumOf { (it.amountDue - it.amountPaid).coerceAtLeast(0.0) }
        return com.elimtiyaz.domain.model.ParentFinancialProfile(
            parentId = parent.id, parentName = "${parent.firstName} ${parent.lastName}",
            totalDue = totalDue, totalPaid = totalPaid, totalOutstanding = outstanding,
            overdueAmount = overdue, installments = installments, recentPayments = payments.take(10),
            adjustments = adjustments,
        )
    }

    /** Compute the debt summaries from the seed data. */
    fun debtSummaries(): List<DebtSummary> = parents.map { p ->
        val installmentsForParent = installments.filter { it.parentId == p.id }
        val outstanding = installmentsForParent.sumOf { (it.amountDue - it.amountPaid).coerceAtLeast(0.0) }
        val studentCount = students.count { it.parentId == p.id }
        val daysOverdue = installmentsForParent.firstOrNull { it.status == "overdue" }?.let {
            runCatching {
                val due = kotlinx.datetime.LocalDate.parse(it.dueDate)
                val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                (today.toEpochDays() - due.toEpochDays()).coerceAtLeast(0)
            }.getOrDefault(45)
        } ?: 0
        DebtSummary(
            parentId = p.id, parentName = "${p.firstName} ${p.lastName}", parentPhone = p.phone,
            studentCount = studentCount, outstandingAmount = outstanding, daysOverdue = daysOverdue,
        )
    }.filter { it.outstandingAmount > 0 }
}
