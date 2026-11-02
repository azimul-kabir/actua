package com.azimulkabir.actua.data.sync

data class CrdtMessage(
    val timestamp: HlcTimestamp,
    val dataset: String,
    val row: String,
    val column: String,
    val value: String,
)

sealed interface CrdtValue {
    data object Null : CrdtValue
    data class Integer(val value: Long) : CrdtValue
    data class Decimal(val value: Double) : CrdtValue
    data class Text(val value: String) : CrdtValue

    companion object {
        /** Whether [value] carries a type tag this client can decode (`0:`, `N:` or `S:`). */
        fun isKnownFormat(value: String): Boolean =
            value.length >= 2 && value[1] == ':' && value[0] in "0NS"

        fun serialize(value: Any?): String = when (value) {
            null -> "0:"
            is Boolean -> "N:${if (value) 1 else 0}"
            is Byte, is Short, is Int, is Long -> "N:$value"
            is Float, is Double -> "N:$value"
            is String -> "S:$value"
            else -> "0:"
        }

        fun deserialize(value: String): CrdtValue {
            if (value.length < 2 || value[1] != ':') return Null
            val content = value.drop(2)
            return when (value.first()) {
                '0' -> Null
                'S' -> Text(content)
                'N' -> content.toLongOrNull()?.let(::Integer)
                    ?: content.toDoubleOrNull()?.let(::Decimal)
                    ?: Null
                else -> Null
            }
        }
    }
}
