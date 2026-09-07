package com.azimulkabir.actua.data.sync

import java.io.ByteArrayOutputStream

internal class ProtoWriter {
    private val output = ByteArrayOutputStream()

    fun string(field: Int, value: String) {
        if (value.isEmpty()) return
        bytes(field, value.encodeToByteArray())
    }

    fun boolean(field: Int, value: Boolean) {
        if (!value) return
        tag(field, VARINT)
        varint(1)
    }

    fun bytes(field: Int, value: ByteArray) {
        if (value.isEmpty()) return
        tag(field, LENGTH_DELIMITED)
        varint(value.size)
        output.write(value)
    }

    fun message(field: Int, block: ProtoWriter.() -> Unit) = bytes(field, ProtoWriter().apply(block).toByteArray())
    fun toByteArray(): ByteArray = output.toByteArray()

    private fun tag(field: Int, wireType: Int) = varint((field shl 3) or wireType)
    private fun varint(value: Int) {
        var remaining = value.toLong()
        while (remaining and -128L != 0L) {
            output.write(((remaining and 0x7f) or 0x80).toInt())
            remaining = remaining ushr 7
        }
        output.write(remaining.toInt())
    }

    companion object {
        private const val VARINT = 0
        private const val LENGTH_DELIMITED = 2
    }
}

internal class ProtoReader(private val data: ByteArray) {
    private var position = 0
    val exhausted: Boolean get() = position >= data.size

    fun nextField(): Field {
        val tag = varint()
        return Field(tag ushr 3, tag and 7)
    }

    fun boolean(): Boolean = varint() != 0
    fun string(): String = bytes().decodeToString()
    fun bytes(): ByteArray {
        val length = varint()
        if (length < 0 || position + length > data.size) throw SyncEncodingException.InvalidProtobuf
        return data.copyOfRange(position, position + length).also { position += length }
    }

    fun skip(wireType: Int) {
        when (wireType) {
            0 -> varint()
            1 -> advance(8)
            2 -> advance(varint())
            5 -> advance(4)
            else -> throw SyncEncodingException.InvalidProtobuf
        }
    }

    private fun advance(count: Int) {
        if (count < 0 || position + count > data.size) throw SyncEncodingException.InvalidProtobuf
        position += count
    }

    private fun varint(): Int {
        var result = 0L
        var shift = 0
        while (shift < 64 && position < data.size) {
            val byte = data[position++].toInt() and 0xff
            result = result or ((byte and 0x7f).toLong() shl shift)
            if (byte and 0x80 == 0) {
                if (result > Int.MAX_VALUE) throw SyncEncodingException.InvalidProtobuf
                return result.toInt()
            }
            shift += 7
        }
        throw SyncEncodingException.InvalidProtobuf
    }

    data class Field(val number: Int, val wireType: Int)
}
