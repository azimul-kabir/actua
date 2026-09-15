package com.azimulkabir.actua.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManageTagsValidationTest {
    @Test fun validNamesFollowActualRules() {
        assertTrue(isValidManagedTagName("school"))
        assertTrue(isValidManagedTagName("School-2026"))
        assertFalse(isValidManagedTagName(""))
        assertFalse(isValidManagedTagName("two words"))
        assertFalse(isValidManagedTagName("#school"))
    }
}
