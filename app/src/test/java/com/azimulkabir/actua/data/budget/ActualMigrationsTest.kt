package com.azimulkabir.actua.data.budget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActualMigrationsTest {
    private val known = BlankBudgetFactory.MIGRATIONS.toList()

    @Test fun everyMigrationAfterTheOldestBaseIsBundledOrPorted() {
        val missing = known.filter { it > ActualMigrations.OLDEST_BASE && !ActualMigrations.supports(it) }
        assertEquals(emptyList<Long>(), missing)
        known.filter { it > ActualMigrations.OLDEST_BASE }.forEach { id ->
            if (ActualMigrations.sql(id) == null) assertTrue("$id must be ported", ActualMigrations.supports(id))
        }
    }

    @Test fun pendingIsEveryMissingIdInOrderOnlyWhenTheBaseIsPresent() {
        assertEquals(emptyList<Long>(), ActualMigrations.pending(known.toSet()))

        val upTo2024 = known.filter { it <= 1720665000000 }.toSet()
        val pending = ActualMigrations.pending(upTo2024)
        assertEquals(known.filter { it > 1720665000000 }, pending)

        val gap = known.toSet() - 1780606215001 - 1738491452000
        assertEquals(listOf(1738491452000, 1780606215001), ActualMigrations.pending(gap))

        // Budgets older than Actual's JavaScript cache migration are left alone.
        assertEquals(emptyList<Long>(), ActualMigrations.pending(known.filter { it > ActualMigrations.OLDEST_BASE }.toSet()))
        // Ids from a newer Actual don't matter.
        assertEquals(emptyList<Long>(), ActualMigrations.pending(known.toSet() + 1799999999999))
    }

    @Test fun statementsDropCommentsAndTransactionControl() {
        val templates = ActualMigrations.statements(requireNotNull(ActualMigrations.sql(1754611200000)))
        assertEquals(2, templates.size)
        assertTrue(templates[0].startsWith("ALTER TABLE categories ADD COLUMN template_settings"))
        assertTrue(templates.none { "--" in it })

        // Upstream's tags migration ends with "COMMIT" and no semicolon.
        val tags = ActualMigrations.statements(requireNotNull(ActualMigrations.sql(1749799110000)))
        assertEquals(1, tags.size)
        assertTrue(tags.single().startsWith("CREATE TABLE tags"))

        assertEquals(emptyList<String>(), ActualMigrations.statements(requireNotNull(ActualMigrations.sql(1759842823172))))
        known.filter { it > ActualMigrations.OLDEST_BASE }.mapNotNull(ActualMigrations::sql).forEach { sql ->
            ActualMigrations.statements(sql).forEach { statement ->
                assertFalse(statement, statement.equals("COMMIT", true) || statement.startsWith("BEGIN", true))
            }
        }
        assertNotNull(ActualMigrations.sql(1787013118115))
    }

    @Test fun onlyStatementsThatWouldCollideWithExistingSchemaAreSkipped() {
        val tables = setOf("accounts", "cleanup_groups")
        val columns = setOf("accounts" to "last_sync")
        val indexes = setOf("idx_existing")
        fun run(statement: String) = ActualMigrations.shouldRun(
            statement, { it in tables }, { table, column -> (table to column) in columns }, { it in indexes },
        )

        assertFalse(run("ALTER TABLE accounts ADD COLUMN last_sync text"))
        assertTrue(run("ALTER TABLE accounts ADD COLUMN bank_sync_status text"))
        assertFalse(run("alter table accounts add last_sync text"))
        assertFalse(run("CREATE TABLE cleanup_groups\n  (id TEXT PRIMARY KEY)"))
        assertTrue(run("CREATE TABLE tags(id TEXT PRIMARY KEY)"))
        assertTrue(run("CREATE TABLE IF NOT EXISTS cleanup_groups (id TEXT PRIMARY KEY)"))
        assertFalse(run("CREATE INDEX idx_existing ON accounts(id)"))
        assertTrue(run("CREATE INDEX IF NOT EXISTS idx_existing ON accounts(id)"))
        assertFalse(run("DROP TABLE payee_rules"))
        assertTrue(run("DROP TABLE accounts"))
        assertTrue(run("UPDATE accounts SET account_sync_source = 'goCardless' WHERE account_id IS NOT NULL"))
    }
}
