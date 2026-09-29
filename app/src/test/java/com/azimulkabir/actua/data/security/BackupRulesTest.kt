package com.azimulkabir.actua.data.security

import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Credential stores must stay out of cloud backup and device-to-device transfer. */
class BackupRulesTest {
    private val credentialStores = setOf("connection.xml", "budget_encryption.xml", "trusted_server_certificates.xml")

    @Test
    fun `credential stores are excluded from cloud backup and device transfer`() {
        val rules = parse("src/main/res/xml/data_extraction_rules.xml")
        for (section in listOf("cloud-backup", "device-transfer")) {
            val node = rules.getElementsByTagName(section).item(0) as Element
            assertEquals(section, credentialStores, excludedSharedPrefs(node))
        }
    }

    @Test
    fun `credential stores are excluded from legacy full backup`() {
        assertEquals(credentialStores, excludedSharedPrefs(parse("src/main/res/xml/backup_rules.xml")))
    }

    private fun parse(path: String): Element =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path)).documentElement

    private fun excludedSharedPrefs(parent: Element): Set<String> {
        val excludes = parent.getElementsByTagName("exclude")
        return (0 until excludes.length).map { excludes.item(it) as Element }
            .filter { it.getAttribute("domain") == "sharedpref" }
            .map { it.getAttribute("path") }
            .toSet()
    }
}
