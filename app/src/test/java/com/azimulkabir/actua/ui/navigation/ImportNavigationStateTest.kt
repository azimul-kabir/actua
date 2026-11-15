package com.azimulkabir.actua.ui.navigation

import androidx.compose.runtime.mutableStateOf
import org.junit.Assert.*
import org.junit.Test

class ImportNavigationStateTest {
    private fun state() = ImportNavigationState(
        mutableStateOf(null), mutableStateOf(null), mutableStateOf(0), mutableStateOf(false), mutableStateOf(0),
    )

    @Test fun each_review_request_refreshes_queue_and_returns_to_its_origin() {
        for (fromImport in listOf(false, true)) {
            val state = state()
            repeat(2) { request ->
                state.openReview(fromImport) {
                    assertEquals(DetailDestination.ReviewCaptured, it)
                    assertEquals(request + 1, state.capturedReviewRequest.value)
                    assertEquals(fromImport, state.capturedReviewReturnsToImport.value)
                }
                state.returnFromReview {
                    assertEquals(if (fromImport) DetailDestination.ImportTransactions else DetailDestination.Main, it)
                }
                assertFalse(state.capturedReviewReturnsToImport.value)
            }
        }
    }

    @Test fun a_later_direct_review_does_not_reuse_an_import_return_target() {
        val state = state()
        state.openReview(true) {}
        state.returnFromReview {}
        state.openReview(false) {}
        state.returnFromReview { assertEquals(DetailDestination.Main, it) }
    }
}
