package com.azimulkabir.actua.ui.transactions

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * Reads the English texts of `values/strings_transactions.xml` on the JVM, mirroring how
 * `StringResourceParityTest` parses `src/main/res`, so unit tests can assert the exact
 * user-facing strings (and the resource ids that point at them) without an Android context.
 */
internal object TransactionStringResources {
    private val texts: Map<String, String> by lazy {
        val file = File("src/main/res/values/strings_transactions.xml")
        check(file.exists()) { "missing ${file.path} — is the working directory the app module?" }
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        val map = mutableMapOf<String, String>()
        var node = root.firstChild
        while (node != null) {
            if (node is Element && node.tagName == "string") {
                map[node.getAttribute("name")] = node.textContent
            }
            node = node.nextSibling
        }
        map
    }

    /** The default-locale text of the string resource named [name]. */
    fun english(name: String): String = texts.getValue(name)
}
