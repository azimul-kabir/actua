package com.azimulkabir.actua.data.importing

import java.io.StringReader
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.Charset
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

/**
 * Parses ISO 20022 CAMT.053 XML bank statement files (#1149).
 * Extracts transactions (<Ntry>), credit/debit indicators (<CdtDbtInd>), dates (<ValDt>/<BookgDt>),
 * related party payees (<Cdtr>/<Dbtr>), and preserves <AcctSvcrRef> into [ImportCandidate.financialId].
 */
object Camt053StatementParser {
    fun parse(bytes: ByteArray): ImportParseResult {
        val xmlString = decodeXmlBytes(bytes)
        return parseXml(xmlString)
    }

    internal fun decodeXmlBytes(bytes: ByteArray): String {
        val previewLength = minOf(bytes.size, 500)
        val preview = String(bytes, 0, previewLength, Charsets.ISO_8859_1)
        val encodingMatch = Regex("""encoding\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(preview)
        val declaredEncoding = encodingMatch?.groupValues?.get(1)?.trim()

        if (declaredEncoding != null && !declaredEncoding.equals("utf-8", ignoreCase = true)) {
            runCatching {
                return String(bytes, Charset.forName(declaredEncoding))
            }
        }
        return String(bytes, Charsets.UTF_8)
    }

    internal fun parseXml(xmlString: String): ImportParseResult {
        val candidates = mutableListOf<ImportCandidate>()
        val problems = mutableListOf<ImportProblem>()

        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            runCatching {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            }
        }
        val doc = runCatching {
            val normalizedXml = xmlString.replaceFirst(
                Regex("""encoding\s*=\s*["'][^"']+["']""", RegexOption.IGNORE_CASE),
                "encoding=\"UTF-8\"",
            )
            factory.newDocumentBuilder().parse(InputSource(StringReader(normalizedXml)))
        }.getOrElse {
            return ImportParseResult(emptyList(), listOf(ImportProblem(1, "Failed to parse CAMT XML: ${it.message}")))
        }

        val entries = doc.getElementsByTagName("Ntry")
        if (entries.length == 0) {
            return ImportParseResult(emptyList(), listOf(ImportProblem(1, "No <Ntry> entries found in CAMT.053 file")))
        }

        for (i in 0 until entries.length) {
            val sourceRow = i + 1
            val entry = entries.item(i) as? Element ?: continue

            runCatching {
                val acctSvcrRef = firstChildText(entry, "AcctSvcrRef")
                val ntryRef = firstChildText(entry, "NtryRef")
                val financialId = acctSvcrRef?.takeIf(String::isNotEmpty) ?: ntryRef?.takeIf(String::isNotEmpty)

                val cdtDbtInd = firstChildText(entry, "CdtDbtInd")?.uppercase()
                val isDebit = cdtDbtInd == "DBIT"

                val dateStr = firstChildText(entry, "ValDt")?.let(::extractDate)
                    ?: firstChildText(entry, "BookgDt")?.let(::extractDate)
                    ?: error("Missing entry date in ValDt or BookgDt")
                val parsedDate = parseIsoDate(dateStr)

                val addtlNtryInf = firstChildText(entry, "AddtlNtryInf")?.trim()

                val txDtlsNodes = entry.getElementsByTagName("TxDtls")
                if (txDtlsNodes.length > 1) {
                    for (t in 0 until txDtlsNodes.length) {
                        val txDtls = txDtlsNodes.item(t) as? Element ?: continue
                        val subAmtStr = firstChildText(txDtls, "Amt")
                            ?: firstChildText(entry, "Amt")
                            ?: error("Missing amount in subtransaction")
                        val subAmountCents = parseCamtAmount(subAmtStr, isDebit)
                        val subPayee = getPayeeFromTxDtls(txDtls, isDebit) ?: addtlNtryInf ?: "Unknown"
                        val subNotes = getNotesFromTxDtls(txDtls)
                            ?: addtlNtryInf?.takeUnless { it == subPayee }

                        candidates += ImportCandidate(
                            sourceRow = sourceRow,
                            date = parsedDate,
                            payee = subPayee,
                            notes = subNotes.orEmpty(),
                            amountCents = subAmountCents,
                            financialId = financialId?.let { "$it-$t" } ?: financialId,
                        )
                    }
                } else {
                    val amtStr = firstChildText(entry, "Amt") ?: error("Missing Amt in entry")
                    val amountCents = parseCamtAmount(amtStr, isDebit)
                    require(amountCents != 0L) { "Amount is zero" }

                    val singleTxDtls = if (txDtlsNodes.length == 1) txDtlsNodes.item(0) as? Element else null
                    var payee = singleTxDtls?.let { getPayeeFromTxDtls(it, isDebit) }
                    if (payee.isNullOrBlank() && !addtlNtryInf.isNullOrBlank()) {
                        payee = addtlNtryInf
                    }

                    var notes = singleTxDtls?.let { getNotesFromTxDtls(it) }
                    if (notes.isNullOrBlank() && !addtlNtryInf.isNullOrBlank() && addtlNtryInf != payee) {
                        notes = addtlNtryInf
                    }
                    if (notes.isNullOrBlank() && payee.isNullOrBlank() && !ntryRef.isNullOrBlank()) {
                        notes = ntryRef
                    }
                    if (payee != null && notes != null && payee.contains(notes)) {
                        notes = null
                    }

                    candidates += ImportCandidate(
                        sourceRow = sourceRow,
                        date = parsedDate,
                        payee = payee?.ifBlank { null } ?: "Unknown",
                        notes = notes.orEmpty(),
                        amountCents = amountCents,
                        financialId = financialId,
                    )
                }
            }.onFailure {
                problems += ImportProblem(sourceRow, it.message ?: "Could not parse CAMT entry")
            }
        }

        return ImportParseResult(candidates, problems)
    }

    private fun getPayeeFromTxDtls(txDtls: Element, isDebit: Boolean): String? {
        val partyTag = if (isDebit) "Cdtr" else "Dbtr"
        val parties = txDtls.getElementsByTagName(partyTag)
        if (parties.length > 0) {
            val party = parties.item(0) as? Element
            val name = firstChildText(party ?: txDtls, "Nm")?.trim()
            if (!name.isNullOrBlank()) return name
        }
        val altPartyTag = if (isDebit) "UltmtCdtr" else "UltmtDbtr"
        val altParties = txDtls.getElementsByTagName(altPartyTag)
        if (altParties.length > 0) {
            val party = altParties.item(0) as? Element
            val name = firstChildText(party ?: txDtls, "Nm")?.trim()
            if (!name.isNullOrBlank()) return name
        }
        return null
    }

    private fun getNotesFromTxDtls(txDtls: Element): String? {
        val ustrdList = mutableListOf<String>()
        val ustrdNodes = txDtls.getElementsByTagName("Ustrd")
        for (i in 0 until ustrdNodes.length) {
            val text = ustrdNodes.item(i)?.textContent?.trim().orEmpty()
            if (text.isNotEmpty()) ustrdList += text
        }
        return if (ustrdList.isNotEmpty()) ustrdList.joinToString(" ") else null
    }

    private fun firstChildText(element: Element, tagName: String): String? {
        val nodes = element.getElementsByTagName(tagName)
        if (nodes.length == 0) return null
        return nodes.item(0)?.textContent?.trim()?.takeIf(String::isNotEmpty)
    }

    private fun extractDate(dateElementText: String): String {
        val match = Regex("""\d{4}-\d{2}-\d{2}""").find(dateElementText)
        return match?.value ?: dateElementText.take(10)
    }

    private fun parseIsoDate(isoDate: String): Int {
        val parts = isoDate.split("-")
        require(parts.size == 3) { "Invalid ISO date: $isoDate" }
        val year = parts[0].toInt()
        val month = parts[1].toInt()
        val day = parts[2].toInt()
        return year * 10_000 + month * 100 + day
    }

    private fun parseCamtAmount(raw: String, isDebit: Boolean): Long {
        val clean = raw.trim().replace(",", ".")
        val cents = BigDecimal(clean).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
        return if (isDebit) -cents else cents
    }
}
