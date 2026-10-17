package com.azimulkabir.actua.data.importing

import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.Charset

/**
 * Parses OFX 1.x (SGML) and OFX 2.x (XML) statement files, including Quicken QFX files (#1149).
 * Extracts transactions (<STMTTRN>) and populates the stable transaction ID (<FITID>) into [ImportCandidate.financialId].
 */
object OfxStatementParser {
    fun parse(bytes: ByteArray): ImportParseResult {
        val content = decodeOfxBytes(bytes)
        return parseContent(content)
    }

    internal fun decodeOfxBytes(bytes: ByteArray): String {
        val previewLength = minOf(bytes.size, 2048)
        val headerPreview = String(bytes, 0, previewLength, Charsets.ISO_8859_1)

        val encoding = Regex("""(?m)^ENCODING:(.+)""").find(headerPreview)?.groupValues?.get(1)?.trim()?.uppercase()
        val charset = Regex("""(?m)^CHARSET:(.+)""").find(headerPreview)?.groupValues?.get(1)?.trim()?.uppercase()

        if (encoding == "UTF-8") return String(bytes, Charsets.UTF_8)
        if (charset == "8859-1" || charset == "ISO-8859-1") return String(bytes, Charsets.ISO_8859_1)
        if (charset == "1252" || charset == "WINDOWS-1252") return runCatching {
            String(bytes, Charset.forName("windows-1252"))
        }.getOrDefault(String(bytes, Charsets.ISO_8859_1))

        return runCatching { String(bytes, Charsets.UTF_8) }.getOrDefault(String(bytes, Charsets.ISO_8859_1))
    }

    internal fun parseContent(content: String): ImportParseResult {
        val candidates = mutableListOf<ImportCandidate>()
        val problems = mutableListOf<ImportProblem>()

        val pattern = Regex("""<STMTTRN>([\s\S]*?)(?:</STMTTRN>|(?=<STMTTRN>)|(?=</BANKTRANLIST>)|(?=</INVTRANLIST>)|$)""", RegexOption.IGNORE_CASE)
        val matches = pattern.findAll(content).toList()

        if (matches.isEmpty()) {
            return ImportParseResult(emptyList(), listOf(ImportProblem(1, "No transactions found in OFX/QFX statement")))
        }

        matches.forEachIndexed { index, match ->
            val sourceRow = index + 1
            val block = match.groupValues[1]
            runCatching {
                val dtPosted = tagValue("DTPOSTED", block) ?: error("Missing DTPOSTED date")
                val date = parseOfxDate(dtPosted)
                val trnAmt = tagValue("TRNAMT", block) ?: error("Missing TRNAMT amount")
                val amountCents = parseOfxAmount(trnAmt)
                require(amountCents != 0L) { "Amount is zero" }

                val name = tagValue("NAME", block)?.let(::htmlToPlain)?.trim()
                val memo = tagValue("MEMO", block)?.let(::htmlToPlain)?.trim()
                val checkNum = tagValue("CHECKNUM", block)?.trim()
                val fitId = tagValue("FITID", block)?.trim()

                val payee = name?.ifBlank { null } ?: memo?.ifBlank { null } ?: "Unknown"
                val notes = if (name != null && name.isNotBlank() && !memo.isNullOrBlank()) memo else ""

                candidates += ImportCandidate(
                    sourceRow = sourceRow,
                    date = date,
                    payee = payee,
                    notes = notes,
                    amountCents = amountCents,
                    reference = checkNum?.takeIf(String::isNotEmpty) ?: fitId?.takeIf(String::isNotEmpty),
                    financialId = fitId?.takeIf(String::isNotEmpty),
                )
            }.onFailure {
                problems += ImportProblem(sourceRow, it.message ?: "Could not parse transaction")
            }
        }

        return ImportParseResult(candidates, problems)
    }

    private fun tagValue(tag: String, block: String): String? {
        val regex = Regex("""<$tag>([^<\r\n]+)(?:</$tag>)?""", RegexOption.IGNORE_CASE)
        return regex.find(block)?.groupValues?.get(1)?.trim()
    }

    internal fun parseOfxDate(raw: String): Int {
        val digits = raw.takeWhile { it.isDigit() }
        require(digits.length >= 8) { "Invalid OFX date format: $raw" }
        val year = digits.substring(0, 4).toInt()
        val month = digits.substring(4, 6).toInt()
        val day = digits.substring(6, 8).toInt()
        require(month in 1..12 && day in 1..31) { "Date values out of range: $raw" }
        return year * 10_000 + month * 100 + day
    }

    internal fun parseOfxAmount(raw: String): Long {
        val clean = raw.trim().replace(",", "").replace("+", "")
        return BigDecimal(clean).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
    }

    internal fun htmlToPlain(value: String): String {
        val regex = Regex("&(lt|gt|#39|#039|quot|amp|#038);")
        return regex.replace(value) { m ->
            when (m.groupValues[1]) {
                "lt" -> "<"
                "gt" -> ">"
                "#39", "#039" -> "'"
                "quot" -> "\""
                "amp", "#038" -> "&"
                else -> m.value
            }
        }
    }
}
