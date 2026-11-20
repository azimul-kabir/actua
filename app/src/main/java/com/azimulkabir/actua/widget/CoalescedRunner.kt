package com.azimulkabir.actua.widget

import java.util.concurrent.Executor

/**
 * Runs [task] on [executor] for a burst of [request] calls with at most one pass waiting behind the
 * running one. A request made before a queued pass starts is absorbed by it, because the pass reads
 * current state when it runs. A request made while a pass is running queues exactly one more, so the
 * last state always gets a pass and none is lost. [executor] must run tasks one at a time.
 */
internal class CoalescedRunner(
    private val executor: Executor,
    private val task: () -> Unit,
) {
    private var queued = false

    fun request() {
        synchronized(this) {
            if (queued) return
            queued = true
        }
        executor.execute {
            synchronized(this) { queued = false }
            task()
        }
    }
}
