package com.azimulkabir.actua.ui.accounts

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Savings
import org.junit.Assert.assertSame
import org.junit.Test

class AccountTypeIconTest {
    @Test fun creditCardStatusWinsOverTheAccountType() {
        assertSame(Icons.Outlined.CreditCard, accountTypeIcon("checking", isCreditCard = true))
    }

    @Test fun freeTextTypesMapCaseInsensitively() {
        assertSame(Icons.Outlined.CreditCard, accountTypeIcon("Credit", false))
        assertSame(Icons.Outlined.Savings, accountTypeIcon("Savings", false))
        assertSame(Icons.AutoMirrored.Outlined.TrendingUp, accountTypeIcon("investment", false))
        assertSame(Icons.Outlined.Home, accountTypeIcon("Mortgage", false))
        assertSame(Icons.AutoMirrored.Outlined.ReceiptLong, accountTypeIcon("Debt", false))
        assertSame(Icons.AutoMirrored.Outlined.ReceiptLong, accountTypeIcon("Loan", false))
        assertSame(Icons.Outlined.Payments, accountTypeIcon("Cash", false))
    }

    @Test fun unknownTypesFallBackToABankIcon() {
        assertSame(Icons.Outlined.AccountBalance, accountTypeIcon("checking", false))
        assertSame(Icons.Outlined.AccountBalance, accountTypeIcon("Other", false))
        assertSame(Icons.Outlined.AccountBalance, accountTypeIcon("", false))
    }
}
