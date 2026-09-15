package com.azimulkabir.actua.data.budget.model

/** Canonical metadata row from Actual Budget's synced `tags` dataset. */
data class ActualTag(
    val id: String,
    val tag: String,
    val color: String? = null,
    val description: String? = null,
    val hidden: Boolean = false,
)

/** Capabilities exposed by the active budget's tag schema. */
data class ActualTagCapabilities(
    val available: Boolean,
    val hidden: Boolean,
)
