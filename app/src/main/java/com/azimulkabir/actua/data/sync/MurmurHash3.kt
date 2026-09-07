package com.azimulkabir.actua.data.sync

/** MurmurHash3 x86 32-bit, matching Actual's JavaScript murmurhash v3. */
object MurmurHash3 {
    fun hash(value: String, seed: UInt = 0u): UInt = hash(value.encodeToByteArray(), seed)

    fun hash(bytes: ByteArray, seed: UInt = 0u): UInt {
        var hash = seed
        val blockCount = bytes.size / 4
        for (index in 0 until blockCount) {
            var block = littleEndianBlock(bytes, index * 4)
            block *= C1
            block = block.rotateLeft(15)
            block *= C2
            hash = hash xor block
            hash = hash.rotateLeft(13)
            hash = hash * 5u + 0xe6546b64u
        }

        val tail = blockCount * 4
        var block = 0u
        when (bytes.size and 3) {
            3 -> {
                block = block xor (bytes[tail + 2].toUByte().toUInt() shl 16)
                block = block xor (bytes[tail + 1].toUByte().toUInt() shl 8)
                block = block xor bytes[tail].toUByte().toUInt()
            }
            2 -> {
                block = block xor (bytes[tail + 1].toUByte().toUInt() shl 8)
                block = block xor bytes[tail].toUByte().toUInt()
            }
            1 -> block = block xor bytes[tail].toUByte().toUInt()
        }
        if ((bytes.size and 3) != 0) {
            block *= C1
            block = block.rotateLeft(15)
            block *= C2
            hash = hash xor block
        }

        hash = hash xor bytes.size.toUInt()
        hash = hash xor (hash shr 16)
        hash *= 0x85ebca6bu
        hash = hash xor (hash shr 13)
        hash *= 0xc2b2ae35u
        hash = hash xor (hash shr 16)
        return hash
    }

    private fun littleEndianBlock(bytes: ByteArray, offset: Int): UInt =
        bytes[offset].toUByte().toUInt() or
            (bytes[offset + 1].toUByte().toUInt() shl 8) or
            (bytes[offset + 2].toUByte().toUInt() shl 16) or
            (bytes[offset + 3].toUByte().toUInt() shl 24)

    private const val C1 = 0xcc9e2d51u
    private const val C2 = 0x1b873593u
}
