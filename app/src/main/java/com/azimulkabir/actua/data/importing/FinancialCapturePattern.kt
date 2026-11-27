package com.azimulkabir.actua.data.importing

import com.google.re2j.Pattern
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.MonthDay
import java.time.chrono.IsoEra
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.ResolverStyle
import java.time.temporal.ChronoField
import java.util.Locale

enum class CaptureDirection { KEYWORDS, EXPENSE, INCOME }

data class FinancialCapturePattern(
    val name: String,
    val expression: String,
    val sourcePackage: String? = null,
    val direction: CaptureDirection = CaptureDirection.KEYWORDS,
    val dateFormat: String = "",
    val decimalSeparator: Char = '.',
    val groupingSeparator: Char? = ',',
) {
    internal val compiled by lazy {
        expression.takeIf { it.length <= MAX_EXPRESSION_LENGTH }
            ?.let { runCatching { Pattern.compile(it) }.getOrNull() }
    }
}

internal const val MAX_CAPTURE_PATTERNS = 32
internal const val MAX_CAPTURE_PATTERN_FILE_BYTES = 256 * 1024
private const val MAX_EXPRESSION_LENGTH = 768
private const val MAX_PATTERN_TEXT_LENGTH = 8192
private val androidPackageName = Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+")

internal enum class CapturePatternProblem { NAME, EXPRESSION, AMOUNT_GROUP, DATE_FORMAT, SEPARATORS, PACKAGE }

internal fun capturePatternProblem(rule: FinancialCapturePattern): CapturePatternProblem? {
    val expression = rule.compiled
    return when {
        rule.name.isBlank() || rule.name.length > 80 -> CapturePatternProblem.NAME
        rule.expression.isBlank() || expression == null -> CapturePatternProblem.EXPRESSION
        "amount" !in expression.namedGroups() -> CapturePatternProblem.AMOUNT_GROUP
        rule.dateFormat.length > 80 || !validDateFormat(rule.dateFormat) -> CapturePatternProblem.DATE_FORMAT
        !validSeparator(rule.decimalSeparator) || rule.decimalSeparator.isWhitespace() ||
            rule.groupingSeparator?.let { !validSeparator(it) || it == rule.decimalSeparator } == true ->
            CapturePatternProblem.SEPARATORS
        rule.sourcePackage?.let { it.length > 255 || !androidPackageName.matches(it) } == true ->
            CapturePatternProblem.PACKAGE
        else -> null
    }
}

private fun validSeparator(value: Char): Boolean = !value.isLetterOrDigit() && !value.isISOControl() &&
    !value.isSurrogate() && value !in "+-−"

private fun formatter(format: String, locale: Locale): DateTimeFormatter =
    DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(format)
        .parseDefaulting(ChronoField.ERA, IsoEra.CE.value.toLong())
        .toFormatter(locale).withResolverStyle(ResolverStyle.STRICT)

private fun validDateFormat(format: String): Boolean {
    if (format.isEmpty()) return true
    return runCatching {
        val formatter = formatter(format, Locale.ENGLISH)
        val sample = LocalDateTime.of(2026, 11, 26, 12, 0)
        MonthDay.from(formatter.parse(formatter.format(sample))) == MonthDay.from(sample)
    }.getOrDefault(false)
}

internal fun captureDate(value: String, format: String, today: LocalDate): LocalDate? {
    if (format.isEmpty()) return runCatching { LocalDate.parse(value) }.getOrNull()
    for (locale in listOf(Locale.getDefault(), Locale.ENGLISH).distinct()) {
        val parsed = runCatching { formatter(format, locale).parse(value) }.getOrNull() ?: continue
        if (parsed.isSupported(ChronoField.YEAR)) {
            return runCatching { LocalDate.from(parsed) }.getOrNull()
        }
        val monthDay = runCatching { MonthDay.from(parsed) }.getOrNull() ?: continue
        // Nine calendar years cover even the eight-year gap around a non-leap century.
        return (0..8).asSequence().map { today.year - it }
            .filter(monthDay::isValidYear).map(monthDay::atYear).firstOrNull { it <= today }
    }
    return null
}

private fun captureCents(value: String, decimal: Char, grouping: Char?): Long? {
    val magnitude = value.trim().let { if (it.firstOrNull() in listOf('+', '-', '−')) it.drop(1) else it }
    if (magnitude.length !in 1..128) return null
    val parts = magnitude.split(decimal, limit = 3)
    if (parts.size > 2) return null
    val whole = if (grouping == null) listOf(parts[0]) else parts[0].split(grouping)
    fun digits(text: String): String? {
        if (text.isEmpty()) return null
        return buildString {
            for (ch in text) append(ch.digitToIntOrNull()?.let { '0' + it } ?: return null)
        }
    }
    val integer = buildString { for (chunk in whole) append(digits(chunk) ?: return null) }
    val fraction = if (parts.size == 2) digits(parts[1]) ?: return null else ""
    val amount = (integer + "." + fraction).toBigDecimalOrNull() ?: return null
    return runCatching { amount.movePointRight(2).longValueExact() }.getOrNull()
}

internal fun parseCapturePattern(
    text: String,
    source: String,
    sourcePackage: String?,
    today: LocalDate,
    profile: FinancialMessageProfile,
): ImportParseResult? {
    if (text.length > MAX_PATTERN_TEXT_LENGTH) return null
    for (rule in profile.patterns.take(MAX_CAPTURE_PATTERNS)) {
        if (rule.sourcePackage != null && rule.sourcePackage != sourcePackage) continue
        val pattern = rule.compiled ?: continue
        val matcher = pattern.matcher(text)
        if (!matcher.find()) continue
        val groups = pattern.namedGroups()
        fun field(name: String): String? = groups[name]?.let { matcher.group(it) }?.trim()?.takeIf(String::isNotEmpty)
        val rawAmount = field("amount") ?: continue
        val cents = captureCents(rawAmount, rule.decimalSeparator, rule.groupingSeparator) ?: continue
        val rawDate = field("date")
        val parsedDate = if (rawDate == null) today else captureDate(rawDate, rule.dateFormat, today) ?: continue
        if (parsedDate.year !in 1..9999) continue
        val outgoing = when (rule.direction) {
            CaptureDirection.EXPENSE -> true
            CaptureDirection.INCOME -> false
            CaptureDirection.KEYWORDS -> profile.outgoing(FinancialMessageParser.directionWording(field("direction") ?: text, source))
                ?: continue
        }
        val candidate = ImportCandidate(
            sourceRow = 1,
            date = parsedDate.year * 10_000 + parsedDate.monthValue * 100 + parsedDate.dayOfMonth,
            payee = field("payee")?.take(200) ?: profile.fallbackPayee(source),
            notes = profile.importedNotes(source),
            amountCents = if (outgoing) -cents else cents,
            reference = field("reference")?.take(100),
            confidence = ImportConfidence.HIGH,
            sourceLabel = source,
            accountHint = field("account")?.take(100),
            keepPayeeCase = true,
        )
        return ImportParseResult(listOf(candidate), emptyList(), matchedPattern = rule.name)
    }
    return null
}
