package com.azimulkabir.actua.data.sync

/**
 * Splits the messages of one sync pass into requests of about [MAX_BYTES], as Actual's client does
 * (actualbudget/actual#9480). The server rejects a `/sync` request over 20 MB by default, which a budget
 * with a long history (for example one imported into a new server) exceeds on its first full upload.
 */
internal object SyncBatches {
    const val MAX_BYTES = 4 * 1024 * 1024

    /** A generous allowance for the timestamp, protobuf framing and encryption. */
    private const val MESSAGE_OVERHEAD_BYTES = 100

    private fun size(message: CrdtMessage): Int =
        message.dataset.utf8Size() + message.row.utf8Size() + message.column.utf8Size() +
            message.value.utf8Size() + MESSAGE_OVERHEAD_BYTES

    private fun String.utf8Size(): Int = toByteArray(Charsets.UTF_8).size

    /**
     * Always at least one batch, so a pass with nothing to send still fetches the server's messages. A message
     * over the limit gets a batch of its own.
     */
    fun split(messages: List<CrdtMessage>, maxBytes: Int = MAX_BYTES): List<List<CrdtMessage>> {
        val batches = mutableListOf<MutableList<CrdtMessage>>(mutableListOf())
        var bytes = 0L
        for (message in messages) {
            val messageBytes = size(message)
            if (bytes > 0 && bytes + messageBytes > maxBytes) {
                batches += mutableListOf<CrdtMessage>()
                bytes = 0
            }
            batches.last() += message
            bytes += messageBytes
        }
        return batches
    }
}
