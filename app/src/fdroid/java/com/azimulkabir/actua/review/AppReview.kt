package com.azimulkabir.actua.review

import android.app.Activity
import android.content.Context

/** The open-source build has no store to rate on, so it never asks. */
fun appReviewAvailable(context: Context): Boolean = false

suspend fun launchAppReview(activity: Activity) = Unit
