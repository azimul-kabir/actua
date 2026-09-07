package com.azimulkabir.actua.data.sync

sealed class SyncEncodingException(message: String) : Exception(message) {
    data object InvalidProtobuf : SyncEncodingException("Invalid sync protobuf")
    data object EncryptionRequired : SyncEncodingException("The budget encryption key is required")
    data object InvalidTimestamp : SyncEncodingException("Invalid CRDT timestamp")
    data object InvalidMerkle : SyncEncodingException("Invalid sync Merkle tree")
}

interface MessageCipher {
    fun encrypt(plaintext: ByteArray): ByteArray
    fun decrypt(payload: ByteArray): ByteArray
}

class SyncEncoder(private val cipher: MessageCipher? = null) {
    fun encode(
        messages: List<CrdtMessage>,
        fileId: String,
        groupId: String,
        keyId: String?,
        since: String,
    ): ByteArray {
        val envelopes = messages.map { message ->
            val plaintext = SyncProtocol.encodeMessage(message)
            MessageEnvelope(
                timestamp = message.timestamp.toString(),
                encrypted = cipher != null,
                content = cipher?.encrypt(plaintext) ?: plaintext,
            )
        }
        return SyncProtocol.encodeRequest(envelopes, fileId, groupId, keyId.orEmpty(), since)
    }

    fun decode(data: ByteArray): DecodedSyncResponse {
        val response = SyncProtocol.decodeResponse(data)
        if (response.merkleJson.isBlank()) throw SyncEncodingException.InvalidMerkle
        val merkle = MerkleJson.decode(response.merkleJson)
        val messages = response.messages.map { envelope ->
            val content = if (envelope.encrypted) {
                cipher?.decrypt(envelope.content) ?: throw SyncEncodingException.EncryptionRequired
            } else envelope.content
            val decoded = SyncProtocol.decodeMessage(content)
            CrdtMessage(
                timestamp = HlcTimestamp.parse(envelope.timestamp) ?: throw SyncEncodingException.InvalidTimestamp,
                dataset = decoded.dataset,
                row = decoded.row,
                column = decoded.column,
                value = decoded.value,
            )
        }
        return DecodedSyncResponse(messages, merkle)
    }
}

data class DecodedSyncResponse(val messages: List<CrdtMessage>, val merkle: MerkleNode)
