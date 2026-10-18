package com.azimulkabir.actua.data.budget.gdrive

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GoogleDriveTokenStoreAndroidTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val tokenStore = GoogleDriveTokenStore(context)

    @Before
    @After
    fun cleanup() {
        tokenStore.clear()
    }

    @Test
    fun saveAndLoadTokensDecryptsCorrectlyFromKeystore() {
        tokenStore.saveTokens(
            accessToken = "ya29.test-access-token-12345",
            refreshToken = "1//test-refresh-token-67890",
            expiresInSeconds = 3600,
            accountEmail = "user@example.com",
        )

        val loaded = tokenStore.loadTokens()
        assertNotNull(loaded)
        assertEquals("ya29.test-access-token-12345", loaded!!.accessToken)
        assertEquals("1//test-refresh-token-67890", loaded.refreshToken)
        assertEquals("user@example.com", loaded.accountEmail)
        assertFalse(loaded.isExpired)
    }

    @Test
    fun tokenExpiresWhenWithinBufferWindow() {
        tokenStore.saveTokens(
            accessToken = "ya29.short-lived",
            refreshToken = "1//refresh-token",
            expiresInSeconds = 30, // Less than 60s buffer
        )

        val loaded = tokenStore.loadTokens()
        assertNotNull(loaded)
        assertTrue(loaded!!.isExpired)
    }

    @Test
    fun clearRemovesAllStoredTokens() {
        tokenStore.saveTokens("token-a", "token-r", 3600)
        assertNotNull(tokenStore.loadTokens())

        tokenStore.clear()
        assertNull(tokenStore.loadTokens())
    }
}
