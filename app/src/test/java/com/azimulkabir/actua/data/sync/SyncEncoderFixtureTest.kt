package com.azimulkabir.actua.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SyncEncoderFixtureTest {
    private val timestamp = HlcTimestamp.parse(
        "2019-06-03T16:40:53.876Z-0000-9f66d38cba0ef956",
    )!!

    @Test
    fun innerMessageMatchesUpstreamBytes() {
        val message = CrdtMessage(timestamp, "accounts", "r1", "name", "S:Checking")
        assertEquals(
            "0a086163636f756e7473120272311a046e616d65220a533a436865636b696e67",
            SyncProtocol.encodeMessage(message).hex(),
        )
    }

    @Test
    fun syncRequestMatchesUpstreamBytes() {
        val message = CrdtMessage(timestamp, "accounts", "r1", "name", "S:Checking")
        val encoded = SyncEncoder().encode(
            messages = listOf(message),
            fileId = "file-1",
            groupId = "group-1",
            keyId = null,
            since = "2017-01-01T00:00:00.000Z-0000-0000000000000000",
        )
        val expected = "0a520a2e323031392d30362d30335431363a34303a35332e3837365a2d303030302d39663636" +
            "6433386362613065663935361a200a086163636f756e7473120272311a046e616d65220a533a436865636b" +
            "696e67120666696c652d311a0767726f75702d31322e323031372d30312d30315430303a30303a30302e3030" +
            "305a2d303030302d30303030303030303030303030303030"
        assertEquals(expected, encoded.hex())
    }

    @Test
    fun plaintextResponseRoundTrips() {
        val original = CrdtMessage(
            timestamp, "transactions", "8f6a9a52-906b-4e3c-bd09-621bd11b3c33", "amount", "N:-1050",
        )
        val envelope = MessageEnvelope(timestamp.toString(), false, SyncProtocol.encodeMessage(original))
        val response = SyncProtocol.encodeResponse(
            SyncResponsePayload(listOf(envelope), "{\"hash\":565800531,\"1\":{\"hash\":565800531}}"),
        )
        val decoded = SyncEncoder().decode(response)
        assertEquals(listOf(original), decoded.messages)
        assertEquals(565_800_531, decoded.merkle.hash)
        assertEquals(565_800_531, decoded.merkle.children["1"]?.hash)
    }

    @Test
    fun encryptedEnvelopeRequiresCipher() {
        val envelope = MessageEnvelope(timestamp.toString(), true, byteArrayOf(1, 2, 3))
        val response = SyncProtocol.encodeResponse(SyncResponsePayload(listOf(envelope), "{\"hash\":0}"))
        assertThrows(SyncEncodingException.EncryptionRequired::class.java) {
            SyncEncoder().decode(response)
        }
    }

    @Test
    fun crdtValuesMatchActualEncoding() {
        assertEquals("0:", CrdtValue.serialize(null))
        assertEquals("N:1", CrdtValue.serialize(true))
        assertEquals("N:-1050", CrdtValue.serialize(-1050))
        assertEquals("N:12.5", CrdtValue.serialize(12.5))
        assertEquals("S:Checking", CrdtValue.serialize("Checking"))
        assertEquals(CrdtValue.Integer(-1050), CrdtValue.deserialize("N:-1050"))
        assertEquals(CrdtValue.Decimal(12.5), CrdtValue.deserialize("N:12.5"))
        assertEquals(CrdtValue.Text("a:b"), CrdtValue.deserialize("S:a:b"))
    }

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }
}
