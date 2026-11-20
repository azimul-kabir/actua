package com.azimulkabir.actua.data.sync

import androidx.work.WorkInfo

internal object MutationSyncPolicy {
    /**
     * A new mutation needs its own sync run only when none is waiting to start. ENQUEUED covers a
     * run in its start delay or waiting for the network; BLOCKED is a run appended behind the one
     * that is running. Either will read the new mutation when it starts.
     */
    fun shouldEnqueue(states: List<WorkInfo.State>): Boolean =
        states.none { it == WorkInfo.State.ENQUEUED || it == WorkInfo.State.BLOCKED }
}
