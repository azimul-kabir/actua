package com.azimulkabir.actua.data.sync

import java.util.UUID

sealed class HlcException(message: String) : Exception(message) {
    data object ClockDrift : HlcException("Maximum clock drift exceeded")
    data object CounterOverflow : HlcException("Timestamp counter overflow")
}

/** Thread-safe Hybrid Logical Clock matching Actual's five-minute drift rule. */
class HybridLogicalClock(
    val node: String = generateNodeId(),
    initial: HlcTimestamp? = null,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private var millis = initial?.millis ?: 0
    private var counter = initial?.counter ?: 0

    @Synchronized
    fun current(): HlcTimestamp = HlcTimestamp(millis, counter, node)

    @Synchronized
    fun advance(timestamp: HlcTimestamp) {
        if (timestamp.millis > millis || timestamp.millis == millis && timestamp.counter > counter) {
            millis = timestamp.millis
            counter = timestamp.counter
        }
    }

    @Synchronized
    fun send(): HlcTimestamp {
        val now = nowMillis()
        val nextMillis = maxOf(millis, now)
        val nextCounter = if (millis == nextMillis) counter.toLong() + 1 else 0
        validate(nextMillis, nextCounter, now)
        millis = nextMillis
        counter = nextCounter.toInt()
        return current()
    }

    @Synchronized
    fun receive(remote: HlcTimestamp): HlcTimestamp {
        val now = nowMillis()
        if (remote.millis - now > MAX_DRIFT) throw HlcException.ClockDrift
        val nextMillis = maxOf(millis, now, remote.millis)
        val nextCounter = when {
            nextMillis == millis && nextMillis == remote.millis -> maxOf(counter, remote.counter).toLong() + 1
            nextMillis == millis -> counter.toLong() + 1
            nextMillis == remote.millis -> remote.counter.toLong() + 1
            else -> 0
        }
        validate(nextMillis, nextCounter, now)
        millis = nextMillis
        counter = nextCounter.toInt()
        return current()
    }

    private fun validate(millis: Long, counter: Long, now: Long) {
        if (millis - now > MAX_DRIFT) throw HlcException.ClockDrift
        if (counter > HlcTimestamp.MAX_COUNTER) throw HlcException.CounterOverflow
    }

    companion object {
        private const val MAX_DRIFT = 5 * 60 * 1_000L
        fun generateNodeId(): String = UUID.randomUUID().toString().replace("-", "").takeLast(16).lowercase()
    }
}
