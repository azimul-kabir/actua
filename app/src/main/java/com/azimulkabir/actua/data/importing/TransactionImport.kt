package com.azimulkabir.actua.data.importing

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

data class ImportCandidate(
    val sourceRow: Int,
    val date: Int,
    val payee: String,
    val notes: String,
    val amountCents: Long,
    val reference: String? = null,
)

data class ImportProblem(val sourceRow: Int, val message: String)

data class ImportParseResult(
    val candidates: List<ImportCandidate>,
    val problems: List<ImportProblem>,
)

/** Source-independent boundary used by CSV now and notification/statement sources later. */
fun interface TransactionCandidateSource {
    fun parse(content: String): ImportParseResult
}

object CsvTransactionCandidateSource : TransactionCandidateSource {
    private val dateHeaders = setOf("date", "transaction date", "posted date", "posting date")
    private val payeeHeaders = setOf("payee", "description", "merchant", "narration", "details")
    private val notesHeaders = setOf("notes", "memo")
    private val referenceHeaders = setOf("reference", "transaction id", "id")

    override fun parse(content: String): ImportParseResult {
        val rows = parseCsv(content.removePrefix("\uFEFF"))
        if (rows.isEmpty()) return ImportParseResult(emptyList(), listOf(ImportProblem(1, "The file is empty")))
        val headers = rows.first().map { it.trim().lowercase(Locale.ROOT) }
        fun column(names: Set<String>) = headers.indexOfFirst { it in names }.takeIf { it >= 0 }
        val date = column(dateHeaders)
        val payee = column(payeeHeaders)
        val notes = column(notesHeaders)
        val reference = column(referenceHeaders)
        val amount = column(setOf("amount"))
        val debit = column(setOf("debit", "withdrawal", "outflow"))
        val credit = column(setOf("credit", "deposit", "inflow"))
        val missing = buildList {
            if (date == null) add("date")
            if (payee == null) add("description/payee")
            if (amount == null && debit == null && credit == null) add("amount or debit/credit")
        }
        if (missing.isNotEmpty()) return ImportParseResult(emptyList(), listOf(
            ImportProblem(1, "Missing required column${if (missing.size > 1) "s" else ""}: ${missing.joinToString()}")
        ))

        val candidates = mutableListOf<ImportCandidate>()
        val problems = mutableListOf<ImportProblem>()
        rows.drop(1).forEachIndexed { index, row ->
            val sourceRow = index + 2
            if (row.all(String::isBlank)) return@forEachIndexed
            runCatching {
                val parsedDate = parseDate(row.value(date!!))
                val parsedPayee = row.value(payee!!).trim().ifBlank { error("Description/payee is blank") }
                val cents = if (amount != null) parseMoney(row.value(amount)) else {
                    val creditCents = credit?.let { parseOptionalMoney(row.value(it)) } ?: 0
                    val debitCents = debit?.let { parseOptionalMoney(row.value(it)) } ?: 0
                    creditCents - debitCents
                }
                require(cents != 0L) { "Amount is blank or zero" }
                candidates += ImportCandidate(
                    sourceRow, parsedDate, parsedPayee, notes?.let { row.value(it).trim() }.orEmpty(), cents,
                    reference?.let { row.value(it).trim().takeIf(String::isNotEmpty) },
                )
            }.onFailure { problems += ImportProblem(sourceRow, it.message ?: "Could not parse row") }
        }
        return ImportParseResult(candidates, problems)
    }

    private fun List<String>.value(index: Int) = getOrElse(index) { "" }

    private fun parseMoney(value: String): Long {
        var clean = value.trim().replace(" ", "").replace(",", "")
        val negative = clean.startsWith("(") && clean.endsWith(")")
        clean = clean.trim('(', ')').replace(Regex("[^0-9.+-]"), "")
        require(clean.isNotBlank()) { "Amount is blank" }
        return BigDecimal(clean).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()
            .let { if (negative) -it else it }
    }

    private fun parseOptionalMoney(value: String) = if (value.isBlank()) 0L else parseMoney(value)

    private fun parseDate(value: String): Int {
        val formats = listOf("yyyy-MM-dd", "MM/dd/yyyy", "dd/MM/yyyy", "M/d/yyyy", "d/M/yyyy")
        val parsed = formats.firstNotNullOfOrNull { pattern ->
            try { LocalDate.parse(value.trim(), DateTimeFormatter.ofPattern(pattern)) }
            catch (_: DateTimeParseException) { null }
        } ?: error("Unsupported date: ${value.trim()}")
        return parsed.year * 10_000 + parsed.monthValue * 100 + parsed.dayOfMonth
    }

    private fun parseCsv(content: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < content.length) {
            val char = content[i]
            when {
                char == '"' && quoted && i + 1 < content.length && content[i + 1] == '"' -> { cell.append('"'); i++ }
                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> { row += cell.toString(); cell.clear() }
                (char == '\n' || char == '\r') && !quoted -> {
                    if (char == '\r' && i + 1 < content.length && content[i + 1] == '\n') i++
                    row += cell.toString(); cell.clear(); rows += row; row = mutableListOf()
                }
                else -> cell.append(char)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row += cell.toString(); rows += row }
        require(!quoted) { "Unclosed quoted field" }
        return rows
    }
}

object ImportDuplicateDetector {
    fun key(date: Int, amountCents: Long, payee: String): String =
        "$date|$amountCents|${payee.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")}"
}
