package com.azimulkabir.actua.billing.website

import org.junit.Assert.*
import org.junit.Test

class SandboxSupportReturnTest {
    @Test fun acceptsOnlyExactSandboxNavigationInDirectTestBuilds() {
        assertTrue(SandboxSupportReturn.accepts("actua-supporter-sandbox://return", true, true))
        assertFalse(SandboxSupportReturn.accepts("actua-supporter-sandbox://return", true, false))
        assertFalse(SandboxSupportReturn.accepts("actua-supporter-sandbox://return", false, true))
        for (uri in listOf(null, "actua://return", "actua-supporter-sandbox://return?key=synthetic",
            "actua-supporter-sandbox://return#synthetic", "actua-supporter-sandbox://other")) {
            assertFalse(SandboxSupportReturn.accepts(uri, true, true))
        }
    }
}
