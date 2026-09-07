package com.azimulkabir.actua.data.sync

internal object MerkleJson {
    fun decode(value: String): MerkleNode = Parser(value).parse()

    fun encode(node: MerkleNode): String = buildString {
        append('{')
        var needsComma = false
        node.children.toSortedMap().forEach { (key, child) ->
            if (needsComma) append(',')
            append('"').append(key).append("\":").append(encode(child))
            needsComma = true
        }
        if (needsComma) append(',')
        append("\"hash\":").append(node.hash).append('}')
    }

    private class Parser(private val source: String) {
        private var index = 0

        fun parse(): MerkleNode {
            val node = node()
            whitespace()
            if (index != source.length) invalid()
            return node
        }

        private fun node(): MerkleNode {
            expect('{')
            var hash = 0
            val children = mutableMapOf<String, MerkleNode>()
            whitespace()
            if (peek() == '}') {
                index++
                return MerkleNode()
            }
            while (true) {
                val key = quoted()
                expect(':')
                if (key == "hash") hash = integer() else if (key in setOf("0", "1", "2")) {
                    children[key] = node()
                } else {
                    invalid()
                }
                whitespace()
                when (peek()) {
                    ',' -> index++
                    '}' -> {
                        index++
                        return MerkleNode(hash, children)
                    }
                    else -> invalid()
                }
            }
        }

        private fun quoted(): String {
            whitespace()
            expect('"')
            val start = index
            while (index < source.length && source[index] != '"') index++
            if (index >= source.length) invalid()
            return source.substring(start, index).also { index++ }
        }

        private fun integer(): Int {
            whitespace()
            val start = index
            if (peek() == '-') index++
            while (peek()?.isDigit() == true) index++
            return source.substring(start, index).toIntOrNull() ?: invalid()
        }

        private fun expect(character: Char) {
            whitespace()
            if (peek() != character) invalid()
            index++
        }

        private fun whitespace() {
            while (peek()?.isWhitespace() == true) index++
        }

        private fun peek(): Char? = source.getOrNull(index)
        private fun invalid(): Nothing = throw SyncEncodingException.InvalidMerkle
    }
}
