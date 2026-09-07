package com.azimulkabir.actua.data.sync

data class MerkleNode(
    val hash: Int = 0,
    val children: Map<String, MerkleNode> = emptyMap(),
)

data class MerkleTree(val root: MerkleNode = MerkleNode()) {
    fun inserting(timestamp: HlcTimestamp): MerkleTree =
        folding(timestamp.hash(), timestamp.millis)

    fun diff(other: MerkleTree): Long? {
        if (root.hash == other.root.hash) return null
        var left = root
        var right = other.root
        var path = ""
        while (true) {
            val keys = (left.children.keys + right.children.keys).sorted()
            var differingKey: String? = null
            for (key in keys) {
                val leftChild = left.children[key] ?: break
                val rightChild = right.children[key] ?: break
                if (leftChild.hash != rightChild.hash) {
                    differingKey = key
                    break
                }
            }
            if (differingKey == null) return keyToTimestamp(path)
            path += differingKey
            left = left.children.getValue(differingKey)
            right = right.children.getValue(differingKey)
        }
    }

    fun pruned(keepLast: Int = 2): MerkleTree = MerkleTree(pruneNode(root, keepLast))

    private fun folding(hash: Int, millis: Long): MerkleTree {
        val updatedRoot = root.copy(hash = root.hash xor hash)
        return MerkleTree(insertKey(updatedRoot, minuteKey(millis), hash))
    }

    private fun insertKey(node: MerkleNode, key: String, hash: Int): MerkleNode {
        if (key.isEmpty()) return node
        val branch = key.first().toString()
        val originalChild = node.children[branch] ?: MerkleNode()
        val changedChild = originalChild.copy(hash = originalChild.hash xor hash)
        val finalChild = insertKey(changedChild, key.drop(1), hash)
        return node.copy(children = node.children + (branch to finalChild))
    }

    private fun pruneNode(node: MerkleNode, keepLast: Int): MerkleNode {
        if (node.children.isEmpty()) return node
        val children = node.children.keys.sorted().takeLast(keepLast).associateWith {
            pruneNode(node.children.getValue(it), keepLast)
        }
        return node.copy(children = children)
    }

    private fun minuteKey(millis: Long): String = (millis / 1_000 / 60).toString(3)

    private fun keyToTimestamp(key: String): Long =
        key.padEnd(16, '0').toLongOrNull(3)?.times(60_000) ?: 0

    companion object {
        fun building(minuteBuckets: Map<Long, Int>): MerkleTree =
            minuteBuckets.entries.fold(MerkleTree()) { tree, (millis, hash) -> tree.folding(hash, millis) }
    }
}
