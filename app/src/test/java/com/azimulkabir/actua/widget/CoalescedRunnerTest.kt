package com.azimulkabir.actua.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.Executor

class CoalescedRunnerTest {
    private class ManualExecutor : Executor {
        val pending = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { pending.addLast(command) }
        fun runNext() = pending.removeFirst().run()
        fun drain() { while (pending.isNotEmpty()) runNext() }
    }

    @Test
    fun aBurstBeforeTheFirstPassRunsOnePass() {
        val executor = ManualExecutor()
        var passes = 0
        val runner = CoalescedRunner(executor) { passes++ }

        repeat(50) { runner.request() }
        executor.drain()

        assertEquals(1, passes)
    }

    @Test
    fun aRequestDuringAPassQueuesExactlyOneMorePass() {
        val executor = ManualExecutor()
        var passes = 0
        lateinit var runner: CoalescedRunner
        runner = CoalescedRunner(executor) {
            passes++
            if (passes == 1) repeat(10) { runner.request() }
        }

        runner.request()
        executor.drain()

        assertEquals(2, passes)
    }

    @Test
    fun theLastStateIsSeenByTheFinalPass() {
        val executor = ManualExecutor()
        var state = 0
        val shown = mutableListOf<Int>()
        val runner = CoalescedRunner(executor) { shown += state }

        (1..5).forEach { state = it; runner.request() }
        executor.drain()

        assertEquals(listOf(5), shown)
    }

    @Test
    fun aLaterBurstRunsAgainAfterTheQueueIsIdle() {
        val executor = ManualExecutor()
        var passes = 0
        val runner = CoalescedRunner(executor) { passes++ }

        runner.request(); executor.drain()
        runner.request(); runner.request(); executor.drain()

        assertEquals(2, passes)
    }
}
