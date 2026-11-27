package com.azimulkabir.actua.data.importing

import com.azimulkabir.actua.model.Account
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FinancialCapturePatternTest {
    private val today = LocalDate.of(2026, 11, 27)
    private val sample = "Example Bank: Card ending 1234, Nov 26 at 12:30, purchase of CNY 25.00 at Tenpay-Meituan"
    private val example = FinancialCapturePattern(
        name = "Example Bank",
        expression = """Card ending (?<account>\d{4}).*?(?<date>[A-Za-z]{3} \d{1,2}).*?purchase of CNY (?<amount>[\d,.]+) at (?<payee>.+)""",
        sourcePackage = "example.bank",
        direction = CaptureDirection.EXPENSE,
        dateFormat = "MMM d",
    )

    @Test fun exampleExtractsAmountDatePayeeAndAccountForItsSource() {
        assertNull(capturePatternProblem(example))
        val profile = FinancialMessageProfile(patterns = listOf(example))
        val parsed = FinancialMessageParser.parse(sample, "Example Bank", today, profile, "example.bank")
        val row = parsed.candidates.single()
        assertEquals("Example Bank", parsed.matchedPattern)
        assertEquals(-2_500L, row.amountCents)
        assertEquals(20261126, row.date)
        assertEquals("Tenpay-Meituan", row.payee)
        assertEquals("1234", row.accountHint)
        val card = Account(name = "Example card 1234", balance = 0, type = "credit")
        assertEquals(card, prefilledImportAccount(row.accountHint, row.sourceLabel, listOf(card)) { null })
        assertEquals("Example Bank", row.sourceLabel)
        assertTrue(FinancialMessageParser.parse(sample, "Example Bank", today, profile, "other.bank").candidates.isEmpty())
        assertTrue(FinancialMessageParser.parse(sample, "Example Bank", today, profile).candidates.isEmpty())
    }

    @Test fun firstUsableRuleWinsAndAnInvalidCaptureFallsThrough() {
        val text = "EUR 1.234,56"
        val unusable = FinancialCapturePattern("Wrong separator", """EUR (?<amount>[\d.,]+)""", direction = CaptureDirection.INCOME, groupingSeparator = null)
        val usable = unusable.copy(name = "Comma decimals", decimalSeparator = ',', groupingSeparator = '.')
        val later = usable.copy(name = "Later", direction = CaptureDirection.EXPENSE)
        val result = FinancialMessageParser.parse(text, today = today, profile = FinancialMessageProfile(patterns = listOf(unusable, usable, later)))
        assertEquals("Comma decimals", result.matchedPattern)
        assertEquals(123_456L, result.candidates.single().amountCents)
    }

    @Test fun substringKeywordsWorkInUnspacedWordingAndInTheDirectionGroup() {
        val text = "账户收到一笔入账：EUR 25.00"
        val default = FinancialMessageProfile(creditKeywords = setOf("入账"))
        assertTrue(FinancialMessageParser.parse(text, today = today, profile = default).candidates.isEmpty())
        val substring = default.copy(substringKeywords = true)
        assertEquals(2_500L, FinancialMessageParser.parse(text, today = today, profile = substring).candidates.single().amountCents)
        val pattern = FinancialCapturePattern("Chinese", """(?<direction>账户收到一笔入账)：EUR (?<amount>[\d.]+)""")
        val parsed = FinancialMessageParser.parse(
            text, today = today, profile = substring.copy(patterns = listOf(pattern)),
        )
        assertEquals("Chinese", parsed.matchedPattern)
        assertEquals(2_500L, parsed.candidates.single().amountCents)
    }

    @Test fun yearlessDatesUseTheMostRecentValidPastOccurrence() {
        assertEquals(LocalDate.of(2025, 12, 2), captureDate("Dec 2", "MMM d", today))
        assertEquals(LocalDate.of(2096, 2, 29), captureDate("Feb 29", "MMM d", LocalDate.of(2104, 2, 28)))
        assertEquals(LocalDate.of(2026, 11, 26), captureDate("11/26/2026", "MM/dd/yyyy", today))
        assertEquals(LocalDate.of(2026, 11, 26), captureDate("2026-11-26", "", today))
        assertNull(captureDate("02/30/2026", "MM/dd/yyyy", today))
        assertNull(captureDate("Feb 30", "MMM d", today))
    }

    @Test fun expandedTextAndTextLinesAreAvailableToBothParsers() {
        val profile = FinancialMessageProfile(patterns = listOf(example))
        val big = notificationCaptureText("Example Bank", "Card ending 1234", sample.substringAfter(": "), emptyList())
        assertEquals(1, Regex("Card ending 1234").findAll(big).count())
        assertEquals(-2_500L, FinancialMessageParser.parse(big, "Example Bank", today, profile, "example.bank").candidates.single().amountCents)
        val lines = notificationCaptureText("Example Bank", null, null, listOf("Card ending 1234", "Spent USD 5.00 at Store"))
        assertEquals(-500L, FinancialMessageParser.parse(lines, "Example Bank", today).candidates.single().amountCents)
    }

    @Test fun legacyFallbackIsTheSameWhenNoRuleApplies() {
        val input = "Paid USD 5.00 to Store on 26/11/2026"
        val fallback = FinancialMessageParser.parse(input, today = today)
        val invalid = example.copy(sourcePackage = null, expression = """\(.*(?<amount>""")
        val actual = FinancialMessageParser.parse(input, today = today, profile = FinancialMessageProfile(patterns = listOf(invalid)))
        assertEquals(fallback, actual)
    }

    @Test fun regexValidationAndJsonRoundTripPreserveAllSettings() {
        val global = example.copy(name = "Global", sourcePackage = null, groupingSeparator = null, decimalSeparator = ',')
        val encoded = CapturePatternJson.encode(listOf(example, global))
        assertEquals(listOf(example, global), CapturePatternJson.decode(encoded))
        val missingGroup = global.copy(expression = """EUR (\d+)""")
        val unsupported = global.copy(expression = """(?<=EUR )(?<amount>\d+)""")
        assertEquals(CapturePatternProblem.AMOUNT_GROUP, capturePatternProblem(missingGroup))
        assertEquals(CapturePatternProblem.EXPRESSION, capturePatternProblem(unsupported))
        assertEquals(CapturePatternProblem.DATE_FORMAT, capturePatternProblem(global.copy(dateFormat = "HH:mm")))
        val failure = assertThrows(InvalidCapturePattern::class.java) {
            CapturePatternJson.decode(CapturePatternJson.encode(listOf(example, unsupported)))
        }
        assertEquals(1, failure.index)
        assertEquals(CapturePatternProblem.EXPRESSION, failure.problem)
    }

    @Test fun automationTextUsesGlobalRulesAndCanSelectAnAppByPackage() {
        val profile = FinancialMessageProfile(patterns = listOf(example, example.copy(name = "Global", sourcePackage = null)))
        val message = mapOf(AutomationIntent.EXTRA_TEXT to sample)
        val global = parseAutomationExtras(message, today, profile)
        assertEquals("Global", global.matchedPattern)
        val app = parseAutomationExtras(message + (AutomationIntent.EXTRA_SOURCE to "example.bank"), today, profile)
        assertEquals("Example Bank", app.matchedPattern)
        assertEquals("1234", app.candidates.single().accountHint)
        val explicit = parseAutomationExtras(message + (AutomationIntent.EXTRA_AMOUNT to "1.25"), today, profile)
        assertNull(explicit.matchedPattern)
        assertEquals(125L, explicit.candidates.single().amountCents)
    }
}
