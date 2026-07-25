package com.elimtiyaz.core.common

/**
 * Lifecycle status codes used across modules — defined in the master plan §20.04.
 * Keep this single source of truth so UI badges, audit logs and CSV exports
 * agree on terminology.
 */

enum class PaymentStatus(val key: String, val displayFr: String, val displayAr: String) {
    Pending("pending",     "En attente", "قيد الانتظار"),
    Partial("partial",     "Partiel",    "جزئي"),
    Paid("paid",           "Payé",       "مدفوع"),
    Overdue("overdue",     "En retard",  "متأخر"),
    Refunded("refunded",   "Remboursé",  "مسترد"),
    Cancelled("cancelled", "Annulé",     "ملغى");
    companion object { fun from(key: String?) = values().firstOrNull { it.key == key } }
}

enum class PaymentMethod(val key: String, val displayFr: String, val displayAr: String) {
    Cash("cash",       "Espèces", "نقدا"),
    Check("check",     "Chèque",  "شيك"),
    Transfer("transfer","Virement", "تحويل");
    companion object { fun from(key: String?) = values().firstOrNull { it.key == key } }
}

enum class AttendanceStatus(val key: String, val displayFr: String, val displayAr: String) {
    Present("present",      "Présent",      "حاضر"),
    AbsentExcused("absent_excused", "Absence excusée", "غيب بعذر"),
    AbsentUnexcused("absent_unexcused", "Absence non excusée", "غيب بدون عذر"),
    Late("late",            "Retard",       "تأخر");
    companion object { fun from(key: String?) = values().firstOrNull { it.key == key } }
}

enum class ExpenseStatus(val key: String, val displayFr: String, val displayAr: String) {
    Draft("draft",            "Brouillon",        "مسودة"),
    Submitted("submitted",    "Soumise",          "مقدمة"),
    Approved("approved",      "Approuvée",        "معتمدة"),
    Rejected("rejected",      "Rejetée",          "مرفوضة"),
    Disbursed("disbursed",    "Décaissée",        "مصروفة"),
    Settled("settled",        "Justifiée",        "مبررة"),
    Anomaly("anomaly",        "Anomalie détectée","شذوذ مكتشف");
    companion object { fun from(key: String?) = values().firstOrNull { it.key == key } }
}

enum class AcademicLevel(val key: String, val displayFr: String, val displayAr: String, val yearsCount: Int) {
    Primaire("primaire", "Primaire", "ابتدائي", 5),
    CEM("cem",            "CEM",      "متوسط",   4),
    Lycee("lycee",        "Lycée",    "ثانوي",   3);
    companion object { fun from(key: String?) = values().firstOrNull { it.key == key } }
}

enum class TenancyTier(val key: String, val displayFr: String, val displayAr: String) {
    T1("t1", "Zone Urbaine",   "منطقة حضرية"),
    T2("t2", "Zone Périurbaine","منطقة شبه حضرية"),
    T3("t3", "Zone Rurale",    "منطقة ريفية");
    companion object { fun from(key: String?) = values().firstOrNull { it.key == key } }
}
