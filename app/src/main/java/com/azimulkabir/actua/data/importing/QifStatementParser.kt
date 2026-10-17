package com.azimulkabir.actua.data.importing

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Parses QIF (Quicken Interchange Format) statement files (#1149).
 * Supports standard account types (!Type:Bank, !Type:Cash, !Type:CCard, etc.) and
 * multi-format date and amount expressions.
 */
object QifStatementParser {
    fun parse(bytes: ByteArray): ImportParseResult {
        val text = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
        return parseContent(text)
    }

    internal fun parseContent(content: String): ImportParseResult {
        val lines = content.lines().map(String::trim).filter(String::isNotEmpty)
        if (lines.isEmpty()) {
            return ImportParseResult(emptyList(), listOf(ImportProblem(1, "The QIF file is empty")))
        }

        val firstLine = lines.first()
        val typeMatch = Regex("""^!Type:(.*)$""", RegexOption.IGNORE_CASE).find(firstLine)
        if (typeMatch == null) {
            return ImportParseResult(emptyList(), listOf(ImportProblem(1, "File does not appear to be a valid QIF file: $firstLine")))
        }

        val candidates = mutableListOf<ImportCandidate>()
        val problems = mutableListOf<ImportProblem>()

        var dateStr: String? = null
        var amountStr: String? = null
        var payeeStr: String? = null
        var memoStr: String? = null
        var numberStr: String? = null
        var sourceRow = 2

        fun commitRecord() {
            if (dateStr == null && amountStr == null && payeeStr == null && memoStr == null) return
            runCatching {
                val rawDate = dateStr ?: error("Missing transaction date")
                val parsedDate = parseQifDate(rawDate)
                val rawAmount = amountStr ?: error("Missing transaction amount")
                val amountCents = parseQifAmount(rawAmount)
                require(amountCents != 0L) { "Amount is zero" }

                val payee = payeeStr?.replace("&amp;", "&")?.trim()
                val memo = memoStr?.replace("&amp;", "&")?.trim().orEmpty()
                val finalPayee = payee?.ifBlank { null } ?: memo.ifBlank { null } ?: "Unknown"
                val finalNotes = if (payee != null && payee.isNotBlank()) memo else ""

                candidates += ImportCandidate(
                    sourceRow = sourceRow,
                    date = parsedDate,
                    payee = finalPayee,
                    notes = finalNotes,
                    amountCents = amountCents,
                    reference = numberStr?.trim()?.takeIf(String::isNotEmpty),
                )
            }.onFailure {
                problems += ImportProblem(sourceRow, it.message ?: "Could not parse QIF record")
            }

            dateStr = null
            amountStr = null
            payeeStr = null
            memoStr = null
            numberStr = null
        }

        for (i in 1 until lines.size) {
            val line = lines[i]
            if (line == "^") {
                commitRecord()
                sourceRow = i + 2
                continue
            }
            if (line.isEmpty()) continue
            val code = line[0]
            val value = line.substring(1).trim()
            when (code) {
                'D' -> dateStr = value
                'T' -> amountStr = value
                'P' -> payeeStr = value
                'M' -> memoStr = value
                'N' -> numberStr = value
            }
        }
        commitRecord()

        return ImportParseResult(candidates, problems)
    }

    internal fun parseQifDate(raw: String): Int {
        val normalized = raw.trim().replace("'", "/").replace("-", "/")
        val parts = normalized.split("/")
        if (parts.size == 3) {
            val p0 = parts[0].toIntOrNull()
            val p1 = parts[1].toIntOrNull()
            val p2 = parts[2].toIntOrNull()
            if (p0 != null && p1 != null && p2 != null) {
                if (parts[0].length == 4) {
                    return p0 * 10_000 + p1 * 100 + p2
                }
                val year = if (parts[2].length == 2) {
                    if (p2 < 50) 2000 + p2 else 1900 + p2
                } else p2

                if (p0 > 12 && p1 in 1..12) {
                    return year * 10_000 + p1 * 100 + p0
                }
                return year * 10_000 + p0 * 100 + p1
            }
        }

        val patterns = listOf("yyyy-MM-dd", "MM/dd/yyyy", "dd/MM/yyyy", "M/d/yyyy", "d/M/yyyy")
        for (pattern in patterns) {
            try {
                val parsed = LocalDate.parse(raw.trim(), DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))
                return parsed.year * 10_000 + parsed.monthValue * 100 + parsed.dayOfMonth
            } catch (_: DateTimeParseException) {}
        }
        error("Unsupported QIF date format: $raw")
    }

    internal fun parseQifAmount(raw: String): Long {
        var clean = raw.trim().replace(" ", "").replace(",", "")
        val negative = clean.startsWith("(") && clean.endsWith(")")
        clean = clean.trim('(', ')').replace(Regex("[^0-9.+-]"), "")
        require(clean.isNotBlank()) { "Amount is blank" }
        val cents = BigDecimal(clean).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
        return if (negative) -cents else cents
    }
}
