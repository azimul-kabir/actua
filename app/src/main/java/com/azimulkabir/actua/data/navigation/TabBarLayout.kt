package com.azimulkabir.actua.data.navigation

import org.json.JSONArray
import org.json.JSONObject

/**
 * Stable bottom-tab identifiers. [ADD] is a pseudo-tab: an action shortcut to the add-transaction
 * flow, not a navigable destination, and is never assigned selection/back-stack state the way the
 * other entries are.
 */
enum class TabItem(val label: String) {
    HOME("Home"),
    BUDGET("Budget"),
    TRANSACTIONS("Transactions"),
    ACCOUNTS("Accounts"),
    REPORTS("Reports"),
    ADD("Add"),
    MANAGE("Manage"),
}

/** Minimum and maximum number of tabs the bottom bar can show at once. */
private const val MIN_VISIBLE_TABS = 3
private const val MAX_VISIBLE_TABS = 5

/**
 * A user's customized bottom tab bar: tab order plus the ones they hid. This is device-local UI
 * preference, not budget financial data, so it never touches the Actual schema.
 */
data class TabBarLayout(val order: List<TabItem>, val hidden: Set<TabItem>) {
    /** Tabs to render, in display order, with hidden ones already filtered out. */
    val visibleTabs: List<TabItem> get() = order.filterNot { it in hidden }

    companion object {
        /**
         * Today's fixed bottom bar — Budget, Accounts, Add, Reports, Manage — with Home and
         * Transactions present but hidden. Every [TabItem] must appear in [order] (this is what
         * [TabBarLayoutPlanner.sanitize] always produces, since it appends any missing entry), so
         * this value is already sanitize-idempotent: [TabBarPreferences] persisting and re-reading
         * it round-trips unchanged.
         */
        fun default(): TabBarLayout = TabBarLayout(
            order = listOf(
                TabItem.HOME, TabItem.BUDGET, TabItem.ACCOUNTS, TabItem.ADD,
                TabItem.REPORTS, TabItem.TRANSACTIONS, TabItem.MANAGE,
            ),
            hidden = setOf(TabItem.HOME, TabItem.TRANSACTIONS),
        )
    }
}

/**
 * Pure reordering/visibility logic for [TabBarLayout], mirroring
 * [com.azimulkabir.actua.data.home.HomeLayoutPlanner]: no I/O here, so drag gestures can mutate an
 * in-memory copy on every step and persist only once, on drop.
 *
 * Manage is pinned and cannot be hidden or reordered: it is the only path back to reconfiguring
 * the tab bar itself, so hiding it would strand the user. Unlike Home's pinned "Ready to Budget"
 * section, Manage is not forced to a fixed position — its place in [TabBarLayout.order] is
 * whatever the input naturally has (so [TabBarLayout.default]'s Manage-last arrangement round-trips
 * unchanged); only its non-hideable, non-reorderable status is enforced. At least
 * [MIN_VISIBLE_TABS] tabs must remain visible at all times, and at most [MAX_VISIBLE_TABS] can be
 * shown at once.
 */
object TabBarLayoutPlanner {
    private val PINNED = TabItem.MANAGE

    /**
     * Repairs a layout against the current [TabItem] entries: unknown/duplicate entries are
     * dropped, newly introduced [TabItem]s (including [PINNED] itself, if entirely absent) are
     * appended in their declared (default) order rather than corrupting the user's saved
     * arrangement, [PINNED] is never hidden, and the visible-tab count is clamped back into
     * [MIN_VISIBLE_TABS]..[MAX_VISIBLE_TABS] — below the minimum by un-hiding entries, above the
     * maximum by hiding entries from the end of the reorderable order (in [TabBarLayout.order]).
     */
    fun sanitize(layout: TabBarLayout): TabBarLayout {
        val known = TabItem.entries
        val existing = layout.order.filter { it in known }.distinct()
        val missing = known.filterNot { it in existing }
        val order = existing + missing
        val hidden = layout.hidden.filterNot { it == PINNED }.toMutableSet()

        var visibleCount = order.size - hidden.size
        val unhideCandidates = order.filterNot { it == PINNED }.filter { it in hidden }
        var unhideIndex = 0
        while (visibleCount < MIN_VISIBLE_TABS && unhideIndex < unhideCandidates.size) {
            hidden.remove(unhideCandidates[unhideIndex])
            visibleCount += 1
            unhideIndex += 1
        }

        val hideCandidates = order.filterNot { it == PINNED }.filter { it !in hidden }.asReversed()
        var hideIndex = 0
        while (visibleCount > MAX_VISIBLE_TABS && hideIndex < hideCandidates.size) {
            hidden.add(hideCandidates[hideIndex])
            visibleCount -= 1
            hideIndex += 1
        }

        return TabBarLayout(order, hidden)
    }

    /** Moves [tabId] to just before [targetId] (end of the reorderable tabs when null). */
    fun moveTab(order: List<TabItem>, tabId: TabItem, targetId: TabItem?): List<TabItem>? {
        if (tabId == PINNED || targetId == PINNED) return null
        if (tabId == targetId) return null
        if (tabId !in order) return null
        val rest = order.filterNot { it == tabId }
        val insertAt = targetId?.let { id -> rest.indexOf(id) }?.takeIf { it >= 0 } ?: rest.size
        return rest.toMutableList().apply { add(insertAt, tabId) }
    }

    /** Moves [tabId] one place up its reorderable order (Manage excluded). */
    fun moveTabUp(order: List<TabItem>, tabId: TabItem): List<TabItem>? {
        val reorderable = order.filterNot { it == PINNED }
        val index = reorderable.indexOf(tabId)
        if (index <= 0) return null
        return moveTab(order, tabId, reorderable[index - 1])
    }

    /** Moves [tabId] one place down its reorderable order (Manage excluded). */
    fun moveTabDown(order: List<TabItem>, tabId: TabItem): List<TabItem>? {
        val reorderable = order.filterNot { it == PINNED }
        val index = reorderable.indexOf(tabId)
        if (index < 0 || index >= reorderable.size - 1) return null
        return moveTab(order, tabId, reorderable.getOrNull(index + 2))
    }

    /** Whether [tabId] sits at a different index in [current] than in [original]. */
    fun hasMoved(original: List<TabItem>, current: List<TabItem>, tabId: TabItem): Boolean =
        original.indexOf(tabId) != current.indexOf(tabId)

    /**
     * Hides or shows [tabId]. Refuses (returns [layout] unchanged) if [tabId] is [PINNED], if
     * hiding it would drop the visible tab count below [MIN_VISIBLE_TABS], or if showing it would
     * raise the visible tab count above [MAX_VISIBLE_TABS].
     */
    fun setHidden(layout: TabBarLayout, tabId: TabItem, hidden: Boolean): TabBarLayout {
        if (tabId == PINNED) return layout
        if (hidden && layout.visibleTabs.size <= MIN_VISIBLE_TABS) return layout
        if (!hidden && tabId in layout.hidden && layout.visibleTabs.size >= MAX_VISIBLE_TABS) return layout
        val updated = layout.hidden.toMutableSet().apply { if (hidden) add(tabId) else remove(tabId) }
        return layout.copy(hidden = updated)
    }
}

/**
 * JSON encoding for [TabBarLayout], stored as a single SharedPreferences string. [SCHEMA_VERSION]
 * is carried in the payload so a future schema change can migrate explicitly instead of guessing
 * from shape; today every payload is read through [TabBarLayoutPlanner.sanitize], which already
 * handles the one migration this feature needs (tab definitions changing) without a version bump.
 */
object TabBarLayoutCodec {
    private const val SCHEMA_VERSION = 1

    fun encode(layout: TabBarLayout): String {
        val sanitized = TabBarLayoutPlanner.sanitize(layout)
        return JSONObject()
            .put("version", SCHEMA_VERSION)
            .put("order", JSONArray(sanitized.order.map { it.name }))
            .put("hidden", JSONArray(sanitized.hidden.map { it.name }))
            .toString()
    }

    fun decode(raw: String?): TabBarLayout {
        if (raw == null) return TabBarLayout.default()
        val decoded = runCatching {
            val json = JSONObject(raw)
            val order = json.getJSONArray("order").toTabList()
            val hidden = json.optJSONArray("hidden")?.toTabList()?.toSet() ?: emptySet()
            TabBarLayout(order, hidden)
        }.getOrDefault(TabBarLayout.default())
        return TabBarLayoutPlanner.sanitize(decoded)
    }

    private fun JSONArray.toTabList(): List<TabItem> =
        (0 until length()).mapNotNull { index -> runCatching { TabItem.valueOf(getString(index)) }.getOrNull() }
}
