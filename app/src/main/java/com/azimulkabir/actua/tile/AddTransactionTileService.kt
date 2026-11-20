package com.azimulkabir.actua.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.azimulkabir.actua.MainActivity
import com.azimulkabir.actua.R
import com.azimulkabir.actua.widget.WidgetActions

/**
 * The "Add transaction" Quick Settings tile (#1362). A tap collapses the shade and opens the add-transaction
 * screen for the open budget, the same request as the Quick transaction widget's Expense button and the launcher
 * shortcut, so the app lock, a cold or warm start and "no budget open" are handled the same way.
 */
class AddTransactionTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        // An action tile, not a toggle: always shown as available.
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            // The label is short because the plus icon already says "add"; a screen reader needs the full phrase.
            contentDescription = getString(R.string.tile_add_transaction_description)
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        // On a locked device the keyguard is dismissed first; the app lock still applies once Actua opens.
        if (isLocked) unlockAndRun(::openAddTransaction) else openAddTransaction()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openAddTransaction() {
        val intent = addTransactionIntent(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // API 34+ only accepts a PendingIntent here.
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this, REQUEST_CODE, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    internal companion object {
        private const val REQUEST_CODE = 1362

        fun addTransactionIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java)
                .setAction(WidgetActions.ADD_EXPENSE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
