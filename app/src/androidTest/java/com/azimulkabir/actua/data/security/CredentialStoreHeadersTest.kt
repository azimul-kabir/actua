package com.azimulkabir.actua.data.security

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CredentialStoreHeadersTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val raw = context.getSharedPreferences("connection", Context.MODE_PRIVATE)
    private lateinit var store: CredentialStore

    @Before
    fun setUp() {
        store = CredentialStore(context)
        store.clearCredentialsNow()
    }

    @After
    fun tearDown() = store.clearCredentialsNow()

    @Test
    fun headerValuesAreNeverStoredInPlaintext() {
        val headers = mapOf("CF-Access-Client-Id" to "synthetic-id", "CF-Access-Client-Secret" to "synthetic-secret")

        store.customHeaders = headers

        assertEquals(headers, CredentialStore(context).customHeaders)
        assertNoPlaintext("synthetic-secret", "synthetic-id")
    }

    @Test
    fun plaintextHeadersFromOlderVersionsAreEncryptedOnFirstRead() {
        raw.edit().putString("custom_headers", """{"CF-Access-Client-Secret":"legacy-secret"}""").commit()

        assertEquals(mapOf("CF-Access-Client-Secret" to "legacy-secret"), store.customHeaders)

        assertFalse(raw.contains("custom_headers"))
        assertNoPlaintext("legacy-secret")
        assertEquals(mapOf("CF-Access-Client-Secret" to "legacy-secret"), CredentialStore(context).customHeaders)
    }

    @Test
    fun clearingHeadersRemovesTheEncryptedValue() {
        store.customHeaders = mapOf("X-Test" to "synthetic")

        store.customHeaders = emptyMap()

        assertTrue(store.customHeaders.isEmpty())
        assertFalse(raw.contains("custom_headers_encrypted"))
        assertFalse(raw.contains("custom_headers_iv"))
    }

    @Test
    fun unreadableEncryptedHeadersAreTreatedAsNone() {
        // What a device transfer would leave behind: ciphertext without the Keystore key that made it.
        raw.edit().putString("custom_headers_encrypted", "AAAA").putString("custom_headers_iv", "AAAAAAAAAAAAAAAA").commit()

        assertTrue(store.customHeaders.isEmpty())
    }

    private fun assertNoPlaintext(vararg secrets: String) {
        val stored = raw.all.values.joinToString("\n") { it.toString() }
        secrets.forEach { assertFalse("$it is stored in plaintext", stored.contains(it)) }
    }
}
