package com.azimulkabir.actua.ui.navigation

import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.preferences.DisplayPreferences
import com.azimulkabir.actua.ui.banksync.DiscoveryState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SettingsProviderImportStateTest {
    @get:Rule val compose = createComposeRule()

    @Test fun support_request_survives_recreation_but_provider_and_import_requests_remain_transient() {
        val preferences = DisplayPreferences(InstrumentationRegistry.getInstrumentation().targetContext)
        lateinit var settings: SettingsPreferenceState
        lateinit var providers: BankSyncProviderState
        lateinit var imports: ImportNavigationState
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            settings = rememberSettingsPreferenceState(preferences)
            providers = rememberBankSyncProviderState()
            imports = rememberImportNavigationState()
        }
        compose.runOnIdle {
            assertEquals(preferences.privacyMode, settings.privacyMode.value)
            assertEquals(preferences.dateFormat, settings.dateFormat.value)
            settings.openSupportPage.value = true
            providers.goCardlessRequisitionId.value = "synthetic-requisition"
            providers.akahuDiscovery.value = DiscoveryState.Loading
            imports.sharedImportText.value = "synthetic import"
            imports.capturedReviewRequest.value = 2
            imports.capturedReviewReturnsToImport.value = true
        }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle {
            assertTrue(settings.openSupportPage.value)
            assertNull(providers.goCardlessRequisitionId.value)
            assertEquals(DiscoveryState.Idle, providers.akahuDiscovery.value)
            assertNull(imports.sharedImportText.value)
            assertEquals(0, imports.capturedReviewRequest.value)
            assertFalse(imports.capturedReviewReturnsToImport.value)
        }
    }
}
