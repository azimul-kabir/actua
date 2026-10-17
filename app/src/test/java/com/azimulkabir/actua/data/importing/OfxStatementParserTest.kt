package com.azimulkabir.actua.data.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfxStatementParserTest {

    @Test
    fun `parses standard OFX 1 SGML banking statement`() {
        val sgml = """
            OFXHEADER:100
            DATA:OFXSGML
            VERSION:102
            SECURITY:NONE
            ENCODING:USASCII
            CHARSET:1252
            COMPRESSION:NONE
            OLDFILEUID:NONE
            NEWFILEUID:NONE

            <OFX>
            <BANKMSGSRSV1>
            <STMTTRNRS>
            <STMTRS>
            <CURDEF>USD
            <BANKTRANLIST>
            <DTSTART>20190119120000
            <DTEND>20190124120000
            <STMTTRN>
            <TRNTYPE>DEBIT
            <DTPOSTED>20190123120000
            <TRNAMT>-30.00
            <FITID>FITID-001
            <NAME>Patient First
            <MEMO>Urgent care co-pay
            </STMTTRN>
            <STMTTRN>
            <TRNTYPE>CREDIT
            <DTPOSTED>20190124120000
            <TRNAMT>1500.50
            <FITID>FITID-002
            <NAME>ACME Payroll
            </STMTTRN>
            </BANKTRANLIST>
            </STMTRS>
            </STMTTRNRS>
            </BANKMSGSRSV1>
            </OFX>
        """.trimIndent()

        val result = OfxStatementParser.parse(sgml.toByteArray())
        assertEquals(0, result.problems.size)
        assertEquals(2, result.candidates.size)

        val first = result.candidates[0]
        assertEquals(20190123, first.date)
        assertEquals(-3000L, first.amountCents)
        assertEquals("Patient First", first.payee)
        assertEquals("Urgent care co-pay", first.notes)
        assertEquals("FITID-001", first.financialId)

        val second = result.candidates[1]
        assertEquals(20190124, second.date)
        assertEquals(150050L, second.amountCents)
        assertEquals("ACME Payroll", second.payee)
        assertEquals("", second.notes)
        assertEquals("FITID-002", second.financialId)
    }

    @Test
    fun `parses OFX 2 XML credit card statement with closing tags`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <?OFX OFXHEADER="200" VERSION="211" SECURITY="NONE" OLDFILEUID="NONE" NEWFILEUID="NONE"?>
            <OFX>
                <CREDITCARDMSGSRSV1>
                    <CCSTMTTRNRS>
                        <CCSTMTRS>
                            <BANKTRANLIST>
                                <STMTTRN>
                                    <TRNTYPE>DEBIT</TRNTYPE>
                                    <DTPOSTED>20231115143000.000[-5:EST]</DTPOSTED>
                                    <TRNAMT>-12.99</TRNAMT>
                                    <FITID>CC-987654</FITID>
                                    <NAME>Netflix.com</NAME>
                                    <CHECKNUM>1001</CHECKNUM>
                                </STMTTRN>
                            </BANKTRANLIST>
                        </CCSTMTRS>
                    </CCSTMTTRNRS>
                </CREDITCARDMSGSRSV1>
            </OFX>
        """.trimIndent()

        val result = OfxStatementParser.parse(xml.toByteArray())
        assertEquals(0, result.problems.size)
        assertEquals(1, result.candidates.size)

        val item = result.candidates[0]
        assertEquals(20231115, item.date)
        assertEquals(-1299L, item.amountCents)
        assertEquals("Netflix.com", item.payee)
        assertEquals("1001", item.reference)
        assertEquals("CC-987654", item.financialId)
    }

    @Test
    fun `unescapes HTML entities without double-unescaping`() {
        assertEquals("Hello, <world>!", OfxStatementParser.htmlToPlain("Hello, &lt;world&gt;!"))
        assertEquals("Hello, 'world'!", OfxStatementParser.htmlToPlain("Hello, &#39;world&#39;!"))
        assertEquals("Hello, \"world\"!", OfxStatementParser.htmlToPlain("Hello, &quot;world&quot;!"))
        assertEquals("Hello, &world&!", OfxStatementParser.htmlToPlain("Hello, &amp;world&amp;!"))
        assertEquals("Hello, &world&!", OfxStatementParser.htmlToPlain("Hello, &#038;world&#038;!"))
        assertEquals("Hello, &#038;world&#038;!", OfxStatementParser.htmlToPlain("Hello, &amp;#038;world&amp;#038;!"))
    }

    @Test
    fun `falls back to memo when payee name is absent`() {
        val sgml = """
            <OFX>
            <BANKTRANLIST>
            <STMTTRN>
            <TRNTYPE>DEBIT
            <DTPOSTED>20240210
            <TRNAMT>-50.00
            <FITID>MEMO-ONLY-1
            <MEMO>Gas Station Payment
            </STMTTRN>
            </BANKTRANLIST>
            </OFX>
        """.trimIndent()

        val result = OfxStatementParser.parse(sgml.toByteArray())
        assertEquals(1, result.candidates.size)
        assertEquals("Gas Station Payment", result.candidates.first().payee)
        assertEquals("", result.candidates.first().notes)
    }

    @Test
    fun `handles Windows-1252 encoded OFX file`() {
        val sgml = "CHARSET:1252\n<OFX><STMTTRN><DTPOSTED>20240301<TRNAMT>-4.50<FITID>W1<NAME>Café\n</STMTTRN></OFX>"
        val bytes = sgml.toByteArray(java.nio.charset.Charset.forName("windows-1252"))
        val result = OfxStatementParser.parse(bytes)
        assertEquals("Café", result.candidates.single().payee)
    }
}
