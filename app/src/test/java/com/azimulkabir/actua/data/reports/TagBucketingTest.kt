package com.azimulkabir.actua.data.reports

import com.azimulkabir.actua.data.budget.model.ActualTag
import org.junit.Assert.assertEquals
import org.junit.Test

/** Actual 26.11 `tagGroups.test.ts` and `tagScope.ts`, ported (#1415). */
class TagBucketingTest {
    private val red = ActualTag("red-id", "red")
    private val circle = ActualTag("circle-id", "circle")
    private val tags = listOf(red, circle)

    @Test fun `a row goes to one canonical bucket for its in-scope tags`() {
        val buckets = TagBucketing(tags, null)
        assertEquals(TagBucket("tag-group:circle-id:red-id", "#circle + #red"), buckets.bucketOf("#red #circle #red"))
        assertEquals(TagBucket("tag-group:red-id", "#red"), buckets.bucketOf("#red #unmanaged"))
        // An escaped ##tag and a longer tag that merely starts with the name are not the tag.
        assertEquals(TagBucketing.UNTAGGED, buckets.bucketOf("#redder and ##red"))
        assertEquals(TagBucketing.UNTAGGED, buckets.bucketOf(null))
        assertEquals(TagBucketing.UNTAGGED, buckets.bucketOf(""))
    }

    @Test fun `a selected scope reduces a combination to the selected tag and ignores unrelated tags`() {
        val buckets = TagBucketing(tags, """{"tagScope":{"mode":"selected","tagIds":["red-id"]}}""")
        assertEquals(TagBucket("tag-group:red-id", "#red"), buckets.bucketOf("#red #circle"))
        assertEquals(TagBucketing.UNTAGGED, buckets.bucketOf("#circle"))
    }

    @Test fun `an empty, unknown or unreadable scope means all tags`() {
        listOf(
            null, "", "not json", """{}""", """{"tagScope":{"mode":"all"}}""",
            """{"tagScope":{"mode":"selected","tagIds":[]}}""", """{"tagScope":{"mode":"selected"}}""",
        ).forEach { metadata ->
            assertEquals(metadata, listOf("red", "circle"), TagBucketing(tags, metadata).scope.map { it.tag })
        }
    }

    @Test fun `hidden tags and ids that no longer exist are left out of the scope`() {
        val hidden = ActualTag("hidden-id", "secret", hidden = true)
        assertEquals(listOf("red"), TagBucketing(listOf(red, hidden), null).scope.map { it.tag })
        val selected = """{"tagScope":{"mode":"selected","tagIds":["circle-id","gone","hidden-id","red-id"]}}"""
        // Scope order follows the stored ids.
        assertEquals(listOf("circle", "red"), TagBucketing(listOf(red, circle, hidden), selected).scope.map { it.tag })
    }

    @Test fun `empty buckets are one per tag then Untagged`() {
        assertEquals(listOf("#red", "#circle", "Untagged"), TagBucketing(tags, null).emptyBucketNames)
    }

    @Test fun `tag names in a bucket sort naturally`() {
        val t2 = ActualTag("t2", "trip2"); val t10 = ActualTag("t10", "trip10")
        assertEquals("#trip2 + #trip10", TagBucketing.bucket(listOf(t10, t2)).name)
        assertEquals("tag-group:t2:t10", TagBucketing.bucket(listOf(t10, t2)).id)
    }

    @Test fun `extractTags returns distinct tags in order and honours the escape`() {
        assertEquals(listOf("one", "two"), TagBucketing.extractTags("a #one b #two #one ##three"))
        assertEquals(emptyList<String>(), TagBucketing.extractTags("no tags here"))
    }
}
