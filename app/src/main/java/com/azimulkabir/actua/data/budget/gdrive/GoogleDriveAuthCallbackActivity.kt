package com.azimulkabir.actua.data.budget.gdrive

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.azimulkabir.actua.MainActivity

/**
 * Transient redirect receiver activity for OAuth 2.0 PKCE redirect (RFC 8252).
 * Forwards authorization parameters to GoogleDriveAuthManager and returns to MainActivity.
 */
class GoogleDriveAuthCallbackActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        if (uri != null) {
            val code = uri.getQueryParameter("code")
            val state = uri.getQueryParameter("state")
            val error = uri.getQueryParameter("error")
            GoogleDriveAuthManager.handleRedirect(this, code, state, error)
        }
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(mainIntent)
        finish()
    }
}
