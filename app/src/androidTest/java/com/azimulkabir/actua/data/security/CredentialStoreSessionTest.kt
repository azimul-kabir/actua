package com.azimulkabir.actua.data.security

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CredentialStoreSessionTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var store: CredentialStore

    @Before
    fun setUp() {
        store = CredentialStore(context)
        store.clearCredentialsNow()
    }

    @After
    fun tearDown() = store.clearCredentialsNow()

    @Test
    fun expiringASessionForgetsOnlyTheToken() {
        store.saveConnection("https://actual.test", "synthetic-token", "http://192.168.1.2:5006")
        store.customHeaders = mapOf("CF-Access-Client-Id" to "synthetic-id")

        store.expireSession()

        assertNull(store.token())
        assertTrue(store.sessionExpired)
        assertEquals("https://actual.test", store.serverUrl)
        assertEquals("http://192.168.1.2:5006", store.fallbackServerUrl)
        assertEquals(mapOf("CF-Access-Client-Id" to "synthetic-id"), store.customHeaders)
    }

    @Test
    fun signingInAgainClearsTheExpiredState() {
        store.saveConnection("https://actual.test", "old-token")
        store.expireSession()

        store.saveConnection("https://actual.test", "new-token")

        assertFalse(store.sessionExpired)
        assertEquals("new-token", store.token())
    }
}
