package com.azimulkabir.actua.data.budget.gdrive

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class GoogleDriveAuthState(
    val isConnected: Boolean = false,
    val accountEmail: String? = null,
    val isAuthenticating: Boolean = false,
    val error: String? = null,
)

object GoogleDriveAuthManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pendingVerifier: String? = null
    private var pendingState: String? = null
    private var pendingClientId: String? = null

    private val _authState = MutableStateFlow(GoogleDriveAuthState())
    val authState: StateFlow<GoogleDriveAuthState> = _authState.asStateFlow()

    fun updateFromStorage(context: Context) {
        val tokenStore = GoogleDriveTokenStore(context)
        val tokens = tokenStore.loadTokens()
        val prefs = GoogleDrivePreferences(context)
        _authState.value = GoogleDriveAuthState(
            isConnected = tokens != null,
            accountEmail = tokens?.accountEmail,
            isAuthenticating = false,
            error = prefs.lastBackupError,
        )
    }

    fun startAuthorization(context: Context, customClientId: String? = null): Boolean {
        val prefs = GoogleDrivePreferences(context)
        if (!customClientId.isNullOrBlank()) {
            prefs.customClientId = customClientId.trim()
        }
        val clientId = prefs.getEffectiveClientId()
        val verifier = GoogleDriveConfig.generateCodeVerifier()
        val challenge = GoogleDriveConfig.generateCodeChallenge(verifier)
        val state = UUID.randomUUID().toString()

        pendingVerifier = verifier
        pendingState = state
        pendingClientId = clientId

        _authState.value = _authState.value.copy(
            isAuthenticating = true,
            error = null,
        )

        val uri = GoogleDriveConfig.buildAuthorizationUri(
            clientId = clientId,
            redirectUri = GoogleDriveConfig.REDIRECT_URI,
            codeChallenge = challenge,
            state = state,
        )

        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return runCatching {
            context.startActivity(intent)
            true
        }.getOrElse { error ->
            _authState.value = _authState.value.copy(
                isAuthenticating = false,
                error = error.message ?: "Could not open browser for authorization",
            )
            false
        }
    }

    fun handleRedirect(
        context: Context,
        code: String?,
        state: String?,
        error: String?,
    ) {
        val expectedState = pendingState
        val verifier = pendingVerifier
        val clientId = pendingClientId ?: GoogleDrivePreferences(context).getEffectiveClientId()

        pendingState = null
        pendingVerifier = null
        pendingClientId = null

        if (error != null) {
            val message = "Google authorization error: $error"
            GoogleDrivePreferences(context).lastBackupError = message
            _authState.value = _authState.value.copy(isAuthenticating = false, error = message)
            return
        }

        if (state.isNullOrBlank() || state != expectedState) {
            val message = "Authorization state mismatch; request was rejected."
            GoogleDrivePreferences(context).lastBackupError = message
            _authState.value = _authState.value.copy(isAuthenticating = false, error = message)
            return
        }

        if (code.isNullOrBlank() || verifier == null) {
            val message = "Missing authorization code or verifier."
            GoogleDrivePreferences(context).lastBackupError = message
            _authState.value = _authState.value.copy(isAuthenticating = false, error = message)
            return
        }

        val appContext = context.applicationContext
        scope.launch {
            try {
                val authClient = GoogleDriveAuthClient()
                val tokens = authClient.exchangeAuthorizationCode(
                    clientId = clientId,
                    redirectUri = GoogleDriveConfig.REDIRECT_URI,
                    code = code,
                    codeVerifier = verifier,
                )

                // Retrieve account email from Drive about endpoint
                val restClient = GoogleDriveRestClient(tokenProvider = { tokens.accessToken })
                val userInfo = runCatching { restClient.getUserInfo() }.getOrNull()
                val email = userInfo?.emailAddress

                val tokenStore = GoogleDriveTokenStore(appContext)
                tokenStore.saveTokens(
                    accessToken = tokens.accessToken,
                    refreshToken = tokens.refreshToken,
                    expiresInSeconds = tokens.expiresAtEpochSeconds - (System.currentTimeMillis() / 1000),
                    accountEmail = email,
                )

                val prefs = GoogleDrivePreferences(appContext)
                prefs.isEnabled = true
                prefs.lastBackupError = null

                _authState.value = GoogleDriveAuthState(
                    isConnected = true,
                    accountEmail = email,
                    isAuthenticating = false,
                    error = null,
                )
            } catch (e: Exception) {
                val message = e.message ?: "Failed to exchange authorization token"
                GoogleDrivePreferences(appContext).lastBackupError = message
                _authState.value = _authState.value.copy(
                    isAuthenticating = false,
                    error = message,
                )
            }
        }
    }
}
