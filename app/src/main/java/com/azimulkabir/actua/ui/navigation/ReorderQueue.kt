package com.azimulkabir.actua.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.azimulkabir.actua.data.ActuaRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class ReorderView<S>(val snapshot: S, val revision: Long = 0, val saving: Boolean = false)

/** Main-thread submissions, FIFO background writes, then one authoritative read after the queue drains. */
internal class ReorderQueue<S>(
    parentScope: CoroutineScope,
    initial: S,
    private val read: () -> S,
    private val onChanged: () -> Unit,
    private val onFailure: (Int?, Throwable?) -> Unit,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val repository: ActuaRepository? = null,
) {
    private data class Request<S>(val label: Int?, val write: (() -> Boolean)?, val optimistic: ((S) -> S)?)
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))
    private val requests = Channel<Request<S>>(Channel.UNLIMITED)
    private var worker: Job? = null
    private var closed = false
    private var confirmed = initial
    var view by mutableStateOf(ReorderView(initial))
        private set

    /** True means accepted into the queue; the authoritative snapshot determines the eventual order. */
    fun submit(label: Int, optimistic: (S) -> S, write: () -> Boolean): Boolean {
        if (closed || scope.coroutineContext[Job]?.isActive != true) return false
        val next = optimistic(view.snapshot)
        if (requests.trySend(Request(label, write, optimistic)).isFailure) return false
        view = view.copy(snapshot = next, saving = true)
        start()
        return true
    }

    fun refresh() {
        if (closed || scope.coroutineContext[Job]?.isActive != true || requests.trySend(Request<S>(null, null, null)).isFailure) return
        view = view.copy(saving = true)
        start()
    }

    private suspend fun <T> background(block: () -> T): T =
        if (repository != null) repository.withBackgroundOperation(dispatcher, block)
        else withContext(dispatcher) { block() }

    private fun start() {
        if (worker?.isActive == true) return
        val nextWorker = scope.launch(start = CoroutineStart.LAZY) {
            var request = requests.tryReceive().getOrNull()
            while (request != null) {
                val current = request
                current.write?.let { write ->
                    val result = try {
                        Result.success(background(write))
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Result.failure(error)
                    }
                    if (result.getOrDefault(false)) {
                        // Keep a projection of committed moves for recovery if the read below fails.
                        // Rejected moves never enter this baseline, even when later moves succeed.
                        confirmed = current.optimistic?.invoke(confirmed) ?: confirmed
                        onChanged()
                    } else onFailure(current.label, result.exceptionOrNull())
                }
                request = requests.tryReceive().getOrNull()
                if (request != null) continue
                val loaded = try {
                    background(read)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    onFailure(current.label, error)
                    confirmed
                }
                confirmed = loaded
                request = requests.tryReceive().getOrNull()
                // A newer move may have arrived during the read. Keep its optimistic state until it too
                // has been saved; never publish an older result over a newer gesture.
                if (request == null) {
                    view = ReorderView(loaded, view.revision + 1, saving = false)
                }
            }
        }
        worker = nextWorker
        nextWorker.start()
    }

    fun close(): Job? {
        closed = true
        requests.cancel()
        scope.cancel()
        return worker
    }
}
