package com.azimulkabir.actua.data.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class Camt053StatementParserTest {

    @Test
    fun `parses standard CAMT 053 XML statement`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.053.001.02">
                <BkToCstmrStmt>
                    <Stmt>
                        <Ntry>
                            <Amt Ccy="EUR">80.00</Amt>
                            <CdtDbtInd>DBIT</CdtDbtInd>
                            <BookgDt>
                                <Dt>2024-03-12</Dt>
                            </BookgDt>
                            <AcctSvcrRef>REF-367406715024072</AcctSvcrRef>
                            <NtryDtls>
                                <TxDtls>
                                    <RltdPties>
                                        <Cdtr>
                                            <Nm>Rega Swiss Rescue</Nm>
                                        </Cdtr>
                                    </RltdPties>
                                    <RmtInf>
                                        <Ustrd>Annual membership donation</Ustrd>
                                    </RmtInf>
                                </TxDtls>
                            </NtryDtls>
                        </Ntry>
                        <Ntry>
                            <Amt Ccy="EUR">250.00</Amt>
                            <CdtDbtInd>CRDT</CdtDbtInd>
                            <ValDt>
                                <DtTm>2024-03-13T10:30:00</DtTm>
                            </ValDt>
                            <AcctSvcrRef>REF-CRDT-999</AcctSvcrRef>
                            <NtryDtls>
                                <TxDtls>
                                    <RltdPties>
                                        <Dbtr>
                                            <Nm>John Doe Payer</Nm>
                                        </Dbtr>
                                    </RltdPties>
                                    <RmtInf>
                                        <Ustrd>Invoice 104 payment</Ustrd>
                                    </RmtInf>
                                </TxDtls>
                            </NtryDtls>
                        </Ntry>
                    </Stmt>
                </BkToCstmrStmt>
            </Document>
        """.trimIndent()

        val result = Camt053StatementParser.parse(xml.toByteArray())
        assertEquals(0, result.problems.size)
        assertEquals(2, result.candidates.size)

        val debit = result.candidates[0]
        assertEquals(20240312, debit.date)
        assertEquals(-8000L, debit.amountCents)
        assertEquals("Rega Swiss Rescue", debit.payee)
        assertEquals("Annual membership donation", debit.notes)
        assertEquals("REF-367406715024072", debit.financialId)

        val credit = result.candidates[1]
        assertEquals(20240313, credit.date)
        assertEquals(25000L, credit.amountCents)
        assertEquals("John Doe Payer", credit.payee)
        assertEquals("Invoice 104 payment", credit.notes)
        assertEquals("REF-CRDT-999", credit.financialId)
    }

    @Test
    fun `falls back to AddtlNtryInf when party name is not in TxDtls`() {
        val xml = """
            <Document>
                <BkToCstmrStmt>
                    <Stmt>
                        <Ntry>
                            <Amt Ccy="CHF">12.50</Amt>
                            <CdtDbtInd>DBIT</CdtDbtInd>
                            <BookgDt><Dt>2024-03-15</Dt></BookgDt>
                            <AcctSvcrRef>REF-FALLBACK-1</AcctSvcrRef>
                            <AddtlNtryInf>Kiosk Zurich HB</AddtlNtryInf>
                        </Ntry>
                    </Stmt>
                </BkToCstmrStmt>
            </Document>
        """.trimIndent()

        val result = Camt053StatementParser.parse(xml.toByteArray())
        assertEquals(1, result.candidates.size)
        val entry = result.candidates.single()
        assertEquals(20240315, entry.date)
        assertEquals(-1250L, entry.amountCents)
        assertEquals("Kiosk Zurich HB", entry.payee)
        assertEquals("REF-FALLBACK-1", entry.financialId)
    }

    @Test
    fun `respects ISO-8859-1 XML declaration encoding`() {
        val xml = """<?xml version="1.0" encoding="ISO-8859-1"?>
            <Document>
                <BkToCstmrStmt>
                    <Stmt>
                        <Ntry>
                            <Amt>42.00</Amt>
                            <CdtDbtInd>DBIT</CdtDbtInd>
                            <BookgDt><Dt>2024-03-18</Dt></BookgDt>
                            <AcctSvcrRef>GERMAN-BANK-1</AcctSvcrRef>
                            <AddtlNtryInf>Grüße aus München</AddtlNtryInf>
                        </Ntry>
                    </Stmt>
                </BkToCstmrStmt>
            </Document>
        """.trimIndent()

        val bytes = xml.toByteArray(Charset.forName("ISO-8859-1"))
        val result = Camt053StatementParser.parse(bytes)
        assertEquals(1, result.candidates.size)
        assertEquals("Grüße aus München", result.candidates.single().payee)
    }
}
