package com.azimulkabir.actua.data.sync

import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.security.BudgetEncryptionKeyStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.UUID

class EncryptionKeyManagerTest {
    private val info = ServerKeyInfo(
        id = "11111111-1111-1111-1111-111111111111",
        salt = "XfsOVKaxbU9A5bYyrQAasQrjy0Nvd3rllUW+ZRWCdTE=",
        test = """{"value":"q6+m+aF63TaiPBXc8fqlqmmUQ4VIJZzk4NFa/uPsmyQ=","meta":{"keyId":"11111111-1111-1111-1111-111111111111","algorithm":"aes-256-gcm","iv":"AqLh9LX6BdIiavoO","authTag":"ikPp5J5szWjUn3XlE1TQFg=="}}""",
    )

    @Test
    fun validatesActualFixtureAndRejectsWrongPassword() {
        val loaded = EncryptionKeyManager.deriveAndValidate("correct horse battery staple", info)
        assertEquals(info.id, loaded.keyId)
        assertThrows(EncryptionKeyException.InvalidPassword::class.java) {
            EncryptionKeyManager.deriveAndValidate("incorrect password", info)
        }
    }

    @Test
    fun derivedKeyIsWrappedByAndroidKeystore() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = BudgetEncryptionKeyStore(context)
        val fileId = "test-${UUID.randomUUID()}"
        val loaded = EncryptionKeyManager.deriveAndValidate("correct horse battery staple", info)
        try {
            store.store(fileId, loaded)
            assertEquals(loaded, store.load(fileId))
        } finally {
            store.remove(fileId)
        }
        assertNull(store.load(fileId))
    }
}
