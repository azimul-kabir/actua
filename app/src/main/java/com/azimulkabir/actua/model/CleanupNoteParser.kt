package com.azimulkabir.actua.model

/** A single `#cleanup ...` note line before group names are resolved to `cleanup_groups.id`. */
data class CleanupNoteRow(val role: CleanupTarget.Role, val groupName: String?, val weight: Int = 1)

/**
 * Parses `#cleanup` directives from category notes, mirroring Actual's
 * `cleanup-template.pegjs` grammar:
 * - `#cleanup source` / `#cleanup <group> source` — a source row.
 * - `#cleanup sink [weight]` / `#cleanup [<group>] sink [weight]` — a weighted sink row.
 * - `#cleanup <group>` (bare) — an overspend row for that group.
 *
 * Matching the upstream parser, an unparseable `#cleanup` line is silently skipped rather than
 * surfaced as an error; this is the same "legacy engine" tolerance Actual documents.
 */
object CleanupNoteParser {
    private val prefix = Regex("""^#cleanup\b""", RegexOption.IGNORE_CASE)
    private val bareSource = Regex("""^source$""", RegexOption.IGNORE_CASE)
    private val bareSink = Regex("""^sink(?:\s+(\d+))?$""", RegexOption.IGNORE_CASE)
    private val groupSource = Regex("""^(.+?)\s+source$""", RegexOption.IGNORE_CASE)
    private val groupSink = Regex("""^(.*?)\s*sink(?:\s+(\d+))?$""", RegexOption.IGNORE_CASE)

    fun parse(note: String): List<CleanupNoteRow> = note.lineSequence()
        .map(String::trim)
        .filter { prefix.containsMatchIn(it) }
        .mapNotNull { line -> parseBody(line.replaceFirst(prefix, "").trim()) }
        .toList()

    private fun parseBody(body: String): CleanupNoteRow? {
        if (body.isEmpty()) return null
        if (bareSource.matches(body)) return CleanupNoteRow(CleanupTarget.Role.SOURCE, null)
        bareSink.matchEntire(body)?.let {
            return CleanupNoteRow(CleanupTarget.Role.SINK, null, weight(it.groupValues[1]))
        }
        groupSource.matchEntire(body)?.let {
            val name = it.groupValues[1].trim()
            return name.takeIf(String::isNotBlank)?.let { g -> CleanupNoteRow(CleanupTarget.Role.SOURCE, g) }
        }
        groupSink.matchEntire(body)?.let {
            val name = it.groupValues[1].trim().ifBlank { null }
            return CleanupNoteRow(CleanupTarget.Role.SINK, name, weight(it.groupValues[2]))
        }
        val group = body.trim()
        return group.takeIf(String::isNotBlank)?.let { CleanupNoteRow(CleanupTarget.Role.OVERSPEND, it) }
    }

    private fun weight(raw: String): Int = raw.toIntOrNull()?.takeIf { it > 0 } ?: 1
}
