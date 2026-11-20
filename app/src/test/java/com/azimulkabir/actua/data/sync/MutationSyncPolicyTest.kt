package com.azimulkabir.actua.data.sync

import androidx.work.WorkInfo.State
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MutationSyncPolicyTest {
    @Test
    fun queuesWhenNothingIsPending() {
        assertTrue(MutationSyncPolicy.shouldEnqueue(emptyList()))
        assertTrue(MutationSyncPolicy.shouldEnqueue(listOf(State.SUCCEEDED, State.FAILED, State.CANCELLED)))
    }

    @Test
    fun appendsOneRunBehindARunningSync() {
        assertTrue(MutationSyncPolicy.shouldEnqueue(listOf(State.RUNNING)))
    }

    @Test
    fun skipsWhenARunIsWaitingToStart() {
        assertFalse(MutationSyncPolicy.shouldEnqueue(listOf(State.ENQUEUED)))
        assertFalse(MutationSyncPolicy.shouldEnqueue(listOf(State.SUCCEEDED, State.ENQUEUED)))
    }

    @Test
    fun skipsWhenARunIsAlreadyAppendedBehindTheRunningOne() {
        assertFalse(MutationSyncPolicy.shouldEnqueue(listOf(State.RUNNING, State.BLOCKED)))
    }
}
