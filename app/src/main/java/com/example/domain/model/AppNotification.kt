package com.example.domain.model

import kotlinx.serialization.Serializable

/**
 * In-app notification entity. Pushed via FCM and rendered in the notifications tray.
 */
@Serializable
data class AppNotification(
    val id: String,
    val tenantId: String,
    val title: String,
    val body: String,
    val type: String,                    // payment_overdue | expense_pending | attendance_alert | homework | audit | system | message | custom
    val priority: String,                // low | medium | high | urgent
    val source: String,                  // system | manual | workflow | schedule | audit
    val sourceLabel: String,
    val entityType: String? = null,
    val entityId: String? = null,
    val targetUserId: String? = null,
    val targetRole: String? = null,
    val triggeredAt: String? = null,
    val readAt: String? = null,
    val createdAt: String,
    val createdBy: String,
)
