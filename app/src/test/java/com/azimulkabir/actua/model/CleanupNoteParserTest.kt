package com.azimulkabir.actua.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies `#cleanup` note parsing against Actual's `cleanup-template.pegjs` grammar
 * (`packages/loot-core/src/server/budget/actions.ts` cleanup-template-notes helpers,
 * commit `2fc69915`).
 */
class CleanupNoteParserTest {
    @Test fun parsesBareGlobalSourceAndSink() {
        assertEquals(
            listOf(CleanupNoteRow(CleanupTarget.Role.SOURCE, null)),
            CleanupNoteParser.parse("Paycheck\n#cleanup source"),
        )
        assertEquals(
            listOf(CleanupNoteRow(CleanupTarget.Role.SINK, null, weight = 1)),
            CleanupNoteParser.parse("#cleanup sink"),
        )
    }

    @Test fun parsesWeightedGlobalSink() {
        assertEquals(
            listOf(CleanupNoteRow(CleanupTarget.Role.SINK, null, weight = 3)),
            CleanupNoteParser.parse("#cleanup sink 3"),
        )
    }

    @Test fun parsesGroupScopedSourceAndSink() {
        assertEquals(
            listOf(CleanupNoteRow(CleanupTarget.Role.SOURCE, "Vacation")),
            CleanupNoteParser.parse("#cleanup Vacation source"),
        )
        assertEquals(
            listOf(CleanupNoteRow(CleanupTarget.Role.SINK, "Vacation", weight = 2)),
            CleanupNoteParser.parse("#cleanup Vacation sink 2"),
        )
    }

    @Test fun bareGroupNameIsAnOverspendRow() {
        assertEquals(
            listOf(CleanupNoteRow(CleanupTarget.Role.OVERSPEND, "Vacation")),
            CleanupNoteParser.parse("#cleanup Vacation"),
        )
    }

    @Test fun multipleDirectivesOnSeparateLinesAllParse() {
        val note = """
            Groceries
            #cleanup Vacation source
            #cleanup Vacation
        """.trimIndent()
        assertEquals(
            listOf(
                CleanupNoteRow(CleanupTarget.Role.SOURCE, "Vacation"),
                CleanupNoteRow(CleanupTarget.Role.OVERSPEND, "Vacation"),
            ),
            CleanupNoteParser.parse(note),
        )
    }

    @Test fun unparseableCleanupLineIsSilentlySkipped() {
        assertEquals(emptyList<CleanupNoteRow>(), CleanupNoteParser.parse("#cleanup"))
    }

    @Test fun linesWithoutTheCleanupPrefixAreIgnored() {
        assertEquals(emptyList<CleanupNoteRow>(), CleanupNoteParser.parse("source\nsink 3\nVacation"))
    }

    @Test fun matchingIsCaseInsensitive() {
        assertEquals(
            listOf(CleanupNoteRow(CleanupTarget.Role.SOURCE, null)),
            CleanupNoteParser.parse("#CLEANUP SOURCE"),
        )
    }
}
