package com.azimulkabir.actua.billing

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SandboxPurchaseStoreTest {
    @Test fun encryptsRoundTripAndRejectsTampering() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = SandboxPurchaseStore(context)
        val file = java.io.File(context.noBackupFilesDir, "supporter-sandbox")
        val credential = "synthetic-test-purchase-credential"
        try {
            store.write(JSONObject().put("credential", credential))
            assertEquals(credential, store.read()!!.getString("credential"))
            assertFalse(String(file.readBytes(), Charsets.ISO_8859_1).contains(credential))
            val bytes = file.readBytes()
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
            file.writeBytes(bytes)
            assertNull(store.read())
        } finally { store.clear() }
        assertNull(store.read())
    }
}
