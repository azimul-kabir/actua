package com.azimulkabir.actua.data.receipts

/** Pure size maths for the compressed receipt photo; the Android decoding lives in [ReceiptPhotoStore]. */
object ReceiptPhotoSizing {
    /** The longest edge a kept photo is scaled down to, in pixels. */
    const val MAX_EDGE = 1600

    /** JPEG quality of a kept photo. */
    const val JPEG_QUALITY = 80

    /**
     * The decoder's power-of-two sample size: the largest one that still leaves the longest edge at least
     * [maxEdge], so decoding a large camera image never needs the full bitmap in memory.
     */
    fun sampleSize(width: Int, height: Int, maxEdge: Int = MAX_EDGE): Int {
        require(maxEdge > 0)
        var edge = maxOf(width, height)
        var sample = 1
        while (edge / 2 >= maxEdge) {
            edge /= 2
            sample *= 2
        }
        return sample
    }

    /** [width] by [height] scaled down to fit [maxEdge] on the longest edge, never up, keeping the ratio. */
    fun fitWithin(width: Int, height: Int, maxEdge: Int = MAX_EDGE): Pair<Int, Int> {
        require(width > 0 && height > 0 && maxEdge > 0)
        val longest = maxOf(width, height)
        if (longest <= maxEdge) return width to height
        val scale = maxEdge.toDouble() / longest
        return maxOf(1, Math.round(width * scale).toInt()) to maxOf(1, Math.round(height * scale).toInt())
    }
}
