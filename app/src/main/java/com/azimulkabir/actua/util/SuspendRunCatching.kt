package com.azimulkabir.actua.util

import kotlinx.coroutines.CancellationException

/**
 * Like [runCatching], for a block that suspends. A cancelled coroutine must stop rather than carry on
 * with the failure branch, so [CancellationException] is rethrown and every other failure is captured.
 */
inline fun <R> suspendRunCatching(block: () -> R): Result<R> =
    try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }
