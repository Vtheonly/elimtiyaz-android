package com.example.ui.features.settings

import com.example.core.Role

internal fun roleLabel(role: Role): String = when (role) {
    Role.SUPER_ADMIN -> "Super Admin"
    Role.FINANCIAL_OFFICER -> "Agent financier"
    Role.TEACHER -> "Enseignant"
    Role.SUPPORT_STAFF -> "Support"
    Role.MANAGER -> "Manager"
    Role.BUYER -> "Acheteur"
    Role.DRIVER -> "Chauffeur"
    Role.WAREHOUSE_WORKER -> "Magasinier"
    Role.WORKER -> "Employé"
    Role.PARENT -> "Parent"
    Role.STUDENT -> "Élève"
}

/** Brand color for a [Role] badge. */
