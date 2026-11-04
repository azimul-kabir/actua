package com.azimulkabir.actua.data.receipts

import android.content.Context

/** The `fdroid` flavor has no proprietary text recognition, so it has no receipt scanning. */
@Suppress("UNUSED_PARAMETER")
fun createReceiptScanner(context: Context): ReceiptScanner? = null
