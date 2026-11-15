package com.azimulkabir.actua.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import android.net.Uri
import androidx.compose.runtime.mutableIntStateOf

/** Flow state retains its original navigation-composition lifetime and saveability. */
internal class ImportNavigationState(
    val sharedImportText: MutableState<String?>,
    val sharedImportFile: MutableState<android.net.Uri?>,
    val capturedReviewRequest: MutableState<Int>,
    val capturedReviewReturnsToImport: MutableState<Boolean>,
    val pendingCaptureCount: MutableState<Int>,
) {
    fun openReview(fromImport: Boolean, onDetailChange: (DetailDestination) -> Unit) {
        capturedReviewRequest.value += 1
        capturedReviewReturnsToImport.value = fromImport
        onDetailChange(DetailDestination.ReviewCaptured)
    }

    fun returnFromReview(onDetailChange: (DetailDestination) -> Unit) {
        onDetailChange(if (capturedReviewReturnsToImport.value) DetailDestination.ImportTransactions else DetailDestination.Main)
        capturedReviewReturnsToImport.value = false
    }
}

@Composable
internal fun rememberImportNavigationState(): ImportNavigationState =
    ImportNavigationState(
        sharedImportText = remember { mutableStateOf<String?>(null) },
        sharedImportFile = remember { mutableStateOf<Uri?>(null) },
        capturedReviewRequest = remember { mutableIntStateOf(0) },
        capturedReviewReturnsToImport = remember { mutableStateOf(false) },
        pendingCaptureCount = remember { mutableIntStateOf(0) },
    )
