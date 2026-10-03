package com.azimulkabir.actua.ui.banksync

import com.azimulkabir.actua.data.network.EnableBankingAccount
import com.azimulkabir.actua.data.network.EnableBankingAuthResult
import com.azimulkabir.actua.data.network.EnableBankingAuthStart
import com.azimulkabir.actua.data.network.EnableBankingBank
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnableBankingFlowTest {
    private val bank = EnableBankingBank("Test Bank", "DE", null, emptyList())
    private val opened = mutableListOf<String>()
    private val errors = mutableListOf<String>()

    private fun flow(
        loadBanks: (String) -> List<EnableBankingBank> = { listOf(bank) },
        start: (EnableBankingBank) -> EnableBankingAuthStart = { EnableBankingAuthStart("https://bank.example/consent", "state-1") },
        await: (String) -> EnableBankingAuthResult = { EnableBankingAuthResult.Available(emptyList()) },
    ) = EnableBankingFlow(
        scope = CoroutineScope(Dispatchers.Unconfined), io = Dispatchers.Unconfined,
        loadBanks = loadBanks, startAuthorization = start, awaitAuthorization = await,
        openUrl = { opened += it }, onError = { errors += it },
    )

    @Test fun `loads the banks for a country`() {
        val flow = flow(loadBanks = { country -> if (country == "DE") listOf(bank) else emptyList() })
        flow.loadBanks("DE")
        assertEquals(listOf(bank), flow.banks)
        assertEquals(false, flow.banksLoading)
    }

    @Test fun `a bank list failure is reported and leaves no banks`() {
        val flow = flow(loadBanks = { error("Not configured") })
        flow.loadBanks("DE")
        assertTrue(flow.banks.isEmpty())
        assertEquals(listOf("Not configured"), errors)
        assertEquals(false, flow.banksLoading)
    }

    @Test fun `authorizing opens the bank consent page and offers the granted accounts to link`() {
        var polledState: String? = null
        val flow = flow(await = { state ->
            polledState = state
            EnableBankingAuthResult.Available(listOf(
                EnableBankingAccount("uid-1", "Main", "Test Bank", "DE00"),
                EnableBankingAccount("uid-2", "DE11", "Test Bank", null),
            ))
        })

        flow.authorize(bank)

        assertEquals(listOf("https://bank.example/consent"), opened)
        assertEquals("state-1", polledState)
        val accounts = (flow.discovery as DiscoveryState.Available).accounts
        assertEquals(listOf("uid-1", "uid-2"), accounts.map { it.id })
        assertEquals("DE00", accounts[0].subtitle)
        assertEquals("Test Bank", accounts[1].subtitle)
        assertEquals("Test Bank", flow.institutionFor("uid-1"))
        assertNull(flow.institutionFor("unknown"))
    }

    @Test fun `a refused or failed authorization is shown as an error`() {
        val refused = flow(await = { EnableBankingAuthResult.Error("Polling timed out") })
        refused.authorize(bank)
        assertEquals(DiscoveryState.Error("Polling timed out"), refused.discovery)

        val failed = flow(start = { error("bank unavailable") })
        failed.authorize(bank)
        assertEquals(DiscoveryState.Error("bank unavailable"), failed.discovery)
    }

    @Test fun `reset forgets discovered accounts`() {
        val flow = flow(await = { EnableBankingAuthResult.Available(listOf(EnableBankingAccount("uid-1", "Main", "Test Bank", null))) })
        flow.authorize(bank)
        flow.reset()
        assertEquals(DiscoveryState.Idle, flow.discovery)
        assertNull(flow.institutionFor("uid-1"))
    }
}
