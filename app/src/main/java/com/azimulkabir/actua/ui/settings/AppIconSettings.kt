package com.azimulkabir.actua.ui.settings

import android.widget.ImageView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.preferences.AppIcon
import com.azimulkabir.actua.data.preferences.AppIcons
import com.azimulkabir.actua.ui.theme.Spacing

/**
 * The App icon page (Settings → Display → Supporter extras): a preview of every launcher icon,
 * with the chosen one marked. The launcher icon changes once the user leaves the app.
 */
@Composable
internal fun AppIconSettings(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val appIcons = remember { AppIcons(context) }
    var selected by remember { mutableStateOf(appIcons.current()) }
    Column(
        modifier.padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        AppIcon.entries.chunked(2).forEach { rowIcons ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                rowIcons.forEach { icon ->
                    AppIconTile(
                        icon = icon,
                        selected = icon == selected,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            selected = icon
                            appIcons.choose(icon)
                        },
                    )
                }
            }
        }
        Text(
            stringResource(R.string.settings_app_icon_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.xs),
        )
    }
}

@Composable
private fun AppIconTile(icon: AppIcon, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val label = stringResource(icon.labelRes)
    Surface(
        modifier = modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            AppIconPreview(icon, Modifier.size(72.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (selected) {
                    Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
                Text(label, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Draws the icon's adaptive launcher drawable, which Compose's painterResource can't load. */
@Composable
private fun AppIconPreview(icon: AppIcon, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(24.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))) {
        AndroidView(
            factory = { ImageView(it).apply { setImageDrawable(ContextCompat.getDrawable(it, icon.iconRes)) } },
            modifier = Modifier.size(72.dp),
        )
    }
}
