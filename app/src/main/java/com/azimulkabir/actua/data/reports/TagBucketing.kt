package com.azimulkabir.actua.data.reports

import com.azimulkabir.actua.data.budget.model.ActualTag
import org.json.JSONObject
import java.math.BigInteger
import java.text.Collator
import java.util.Locale

/** One group of a report split by tags: transactions that carry exactly the same set of in-scope tags. */
data class TagBucket(val id: String, val name: String)

/**
 * A custom report grouped by tags (Actual 26.11 `tagGroups.ts` and `tagScope.ts`). A transaction's
 * tags are the `#tags` in its notes that are in the report's tag scope; it goes to the bucket for that
 * exact set of tags ("#a + #b"), or to "Untagged" when it has none. A transaction with several tags is
 * counted once, in its combined bucket, so the buckets add up to the report total.
 *
 * The scope is the report's synced `metadata.tagScope`: `{"mode":"all"}`, or `{"mode":"selected",
 * "tagIds":[…]}`. An empty or unreadable scope means all tags, and hidden tags are never included.
 */
class TagBucketing(tags: List<ActualTag>, metadata: String?) {
    /** The tags the report splits by, in scope order. */
    val scope: List<ActualTag> = resolveScope(tags, metadata)
    private val byName = scope.associateBy { it.tag }

    fun bucketOf(notes: String?): TagBucket =
        bucket(extractTags(notes.orEmpty()).mapNotNull(byName::get))

    /** Upstream's "show empty" set: one bucket per in-scope tag, then Untagged. */
    val emptyBucketNames: List<String> = scope.map { bucket(listOf(it)).name } + UNTAGGED.name

    companion object {
        val UNTAGGED = TagBucket("tag-group:untagged", "Untagged")
        private val TAG = Regex("""(?<!#)#([^#\s]+)""")
        private val collator: Collator = Collator.getInstance(Locale.ENGLISH)

        /** Upstream's `extractTagsFromText`: the distinct `#tags` of [text] in order; a doubled `#` escapes one. */
        fun extractTags(text: String): List<String> = TAG.findAll(text).map { it.groupValues[1] }.distinct().toList()

        fun resolveScope(tags: List<ActualTag>, metadata: String?): List<ActualTag> {
            val visible = tags.filterNot { it.hidden }
            val scope = runCatching {
                metadata?.takeIf(String::isNotBlank)?.let { JSONObject(it).optJSONObject("tagScope") }
            }.getOrNull()
            val ids = if (scope?.optString("mode") == "selected") scope.optJSONArray("tagIds")?.let { array ->
                (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
            }.orEmpty() else emptyList()
            if (ids.isEmpty()) return visible
            val byId = visible.associateBy { it.id }
            return ids.mapNotNull(byId::get)
        }

        fun bucket(tags: List<ActualTag>): TagBucket {
            if (tags.isEmpty()) return UNTAGGED
            val sorted = tags.sortedWith { a, b -> naturalCompare(a.tag, b.tag).takeIf { it != 0 } ?: a.id.compareTo(b.id) }
            return TagBucket("tag-group:" + sorted.joinToString(":") { it.id }, sorted.joinToString(" + ") { "#${it.tag}" })
        }

        /** `Intl.Collator('en', { numeric: true })`: digit runs compare by value, the rest by the English collator. */
        internal fun naturalCompare(a: String, b: String): Int {
            val left = Regex("""\d+|\D+""").findAll(a).map { it.value }.toList()
            val right = Regex("""\d+|\D+""").findAll(b).map { it.value }.toList()
            for (index in 0 until minOf(left.size, right.size)) {
                val x = left[index]; val y = right[index]
                val result = if (x[0].isDigit() && y[0].isDigit()) BigInteger(x).compareTo(BigInteger(y)) else collator.compare(x, y)
                if (result != 0) return result
            }
            return left.size.compareTo(right.size)
        }
    }
}
