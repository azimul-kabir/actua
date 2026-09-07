package com.azimulkabir.actua.data.sync

data class MessageEnvelope(
    val timestamp: String,
    val encrypted: Boolean,
    val content: ByteArray,
) {
    override fun equals(other: Any?): Boolean = other is MessageEnvelope &&
        timestamp == other.timestamp && encrypted == other.encrypted && content.contentEquals(other.content)
    override fun hashCode(): Int = 31 * (31 * timestamp.hashCode() + encrypted.hashCode()) + content.contentHashCode()
}

data class SyncResponsePayload(val messages: List<MessageEnvelope>, val merkleJson: String)
data class SyncRequestPayload(
    val messages: List<MessageEnvelope>,
    val fileId: String,
    val groupId: String,
    val keyId: String,
    val since: String,
)

internal object SyncProtocol {
    fun encodeEncryptedData(iv: ByteArray, authTag: ByteArray, data: ByteArray): ByteArray =
        ProtoWriter().apply {
            bytes(1, iv)
            bytes(2, authTag)
            bytes(3, data)
        }.toByteArray()

    fun decodeEncryptedData(bytes: ByteArray): EncryptedData {
        var iv = byteArrayOf()
        var authTag = byteArrayOf()
        var data = byteArrayOf()
        val reader = ProtoReader(bytes)
        while (!reader.exhausted) {
            val field = reader.nextField()
            when (field.number) {
                1 -> iv = reader.bytes()
                2 -> authTag = reader.bytes()
                3 -> data = reader.bytes()
                else -> reader.skip(field.wireType)
            }
        }
        return EncryptedData(iv, authTag, data)
    }

    fun encodeMessage(message: CrdtMessage): ByteArray = ProtoWriter().apply {
        string(1, message.dataset)
        string(2, message.row)
        string(3, message.column)
        string(4, message.value)
    }.toByteArray()

    fun decodeMessage(bytes: ByteArray): DecodedMessage {
        var dataset = ""
        var row = ""
        var column = ""
        var value = ""
        val reader = ProtoReader(bytes)
        while (!reader.exhausted) {
            val field = reader.nextField()
            when (field.number) {
                1 -> dataset = reader.string()
                2 -> row = reader.string()
                3 -> column = reader.string()
                4 -> value = reader.string()
                else -> reader.skip(field.wireType)
            }
        }
        return DecodedMessage(dataset, row, column, value)
    }

    fun encodeEnvelope(envelope: MessageEnvelope): ByteArray = ProtoWriter().apply {
        string(1, envelope.timestamp)
        boolean(2, envelope.encrypted)
        bytes(3, envelope.content)
    }.toByteArray()

    fun decodeEnvelope(bytes: ByteArray): MessageEnvelope {
        var timestamp = ""
        var encrypted = false
        var content = byteArrayOf()
        val reader = ProtoReader(bytes)
        while (!reader.exhausted) {
            val field = reader.nextField()
            when (field.number) {
                1 -> timestamp = reader.string()
                2 -> encrypted = reader.boolean()
                3 -> content = reader.bytes()
                else -> reader.skip(field.wireType)
            }
        }
        return MessageEnvelope(timestamp, encrypted, content)
    }

    fun encodeRequest(
        envelopes: List<MessageEnvelope>,
        fileId: String,
        groupId: String,
        keyId: String,
        since: String,
    ): ByteArray = ProtoWriter().apply {
        envelopes.forEach { bytes(1, encodeEnvelope(it)) }
        string(2, fileId)
        string(3, groupId)
        string(5, keyId)
        string(6, since)
    }.toByteArray()

    fun decodeRequest(bytes: ByteArray): SyncRequestPayload {
        val messages = mutableListOf<MessageEnvelope>()
        var fileId = ""
        var groupId = ""
        var keyId = ""
        var since = ""
        val reader = ProtoReader(bytes)
        while (!reader.exhausted) {
            val field = reader.nextField()
            when (field.number) {
                1 -> messages += decodeEnvelope(reader.bytes())
                2 -> fileId = reader.string()
                3 -> groupId = reader.string()
                5 -> keyId = reader.string()
                6 -> since = reader.string()
                else -> reader.skip(field.wireType)
            }
        }
        return SyncRequestPayload(messages, fileId, groupId, keyId, since)
    }

    fun encodeResponse(response: SyncResponsePayload): ByteArray = ProtoWriter().apply {
        response.messages.forEach { bytes(1, encodeEnvelope(it)) }
        string(2, response.merkleJson)
    }.toByteArray()

    fun decodeResponse(bytes: ByteArray): SyncResponsePayload {
        val messages = mutableListOf<MessageEnvelope>()
        var merkle = ""
        val reader = ProtoReader(bytes)
        while (!reader.exhausted) {
            val field = reader.nextField()
            when (field.number) {
                1 -> messages += decodeEnvelope(reader.bytes())
                2 -> merkle = reader.string()
                else -> reader.skip(field.wireType)
            }
        }
        return SyncResponsePayload(messages, merkle)
    }

    data class DecodedMessage(val dataset: String, val row: String, val column: String, val value: String)
    data class EncryptedData(val iv: ByteArray, val authTag: ByteArray, val data: ByteArray)
}
