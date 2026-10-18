package com.azimulkabir.actua.data.budget.gdrive

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GoogleDriveEncryptionStoreAndroidTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val encryptionStore = GoogleDriveEncryptionStore(context)

    @Before
    @After
    fun cleanup() {
        encryptionStore.clear()
    }

    @Test
    fun saveAndLoadPassphraseRoundTripViaKeystore() {
        val testPassphrase = "my-secure-cloud-backup-passphrase-2026".toCharArray()
        encryptionStore.savePassphrase(testPassphrase)

        assertTrue(encryptionStore.hasPassphrase())
        val loaded = encryptionStore.loadPassphrase()
        assertNotNull(loaded)
        assertArrayEquals(testPassphrase, loaded)
    }

    @Test
    fun clearWipesPassphrase() {
        encryptionStore.savePassphrase("passphrase-to-wipe".toCharArray())
        assertTrue(encryptionStore.hasPassphrase())

        encryptionStore.clear()
        assertFalse(encryptionStore.hasPassphrase())
        assertNull(encryptionStore.loadPassphrase())
    }

    @Test
    fun generateRandomPassphraseGeneratesReadableFormat() {
        val key = GoogleDriveEncryptionStore.generateRandomPassphrase()
        assertEquals(19, key.length) // 16 chars + 3 hyphens
        assertTrue(key.matches(Regex("^[a-z0-9]{4}-[a-z0-9]{4}-[a-z0-9]{4}-[a-z0-9]{4}$")))
    }
}
