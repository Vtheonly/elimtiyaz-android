package com.example.ui.designsystem.components.data

/** Horizontal alignment of a table column's content. */
enum class ElColumnAlign { START, CENTER, END }

/** Column definition for an [ElTable]. */
data class ElTableColumn(
    val title: String,
    val weight: Float = 1f,
    val align: ElColumnAlign = ElColumnAlign.START,
    val sortable: Boolean = false,
)

/** A single row in an [ElTable]. */
data class ElTableRow(
    val id: String,
    val cells: List<String>,
    val onClick: (() -> Unit)? = null,
)
