package com.azimulkabir.actua.review

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.Log
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewManagerFactory
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "AppReview"
private const val PLAY_STORE_PACKAGE = "com.android.vending"

/**
 * Only for installs that came from Google Play: the `-full` APK on GitHub is the same flavor but
 * has no Play listing to rate, so the review sheet would never open for it.
 */
fun appReviewAvailable(context: Context): Boolean {
    val packageManager = context.packageManager
    val installer = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstallerPackageName(context.packageName)
        }
    }.getOrNull()
    return installer == PLAY_STORE_PACKAGE
}

/**
 * Opens Google's in-app review sheet. Play decides whether to show it (it rate-limits requests and
 * never says if the user rated), so a failure or a skipped sheet is not an error for the caller.
 */
suspend fun launchAppReview(activity: Activity) {
    try {
        val manager = ReviewManagerFactory.create(activity)
        manager.launchReview(activity, manager.requestReview())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Log.w(TAG, "In-app review unavailable", error)
    }
}
