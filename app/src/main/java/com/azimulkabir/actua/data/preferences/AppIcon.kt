package com.azimulkabir.actua.data.preferences

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.edit
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.azimulkabir.actua.R

/**
 * The launcher icons a supporter can choose from (playstore build). Each one is an
 * `activity-alias` in the playstore manifest; exactly one is enabled at a time.
 */
enum class AppIcon(val aliasName: String, @StringRes val labelRes: Int, @DrawableRes val iconRes: Int) {
    Default("LauncherDefault", R.string.settings_app_icon_default, R.mipmap.actua_launcher),
    Dark("LauncherDark", R.string.settings_app_icon_dark, R.mipmap.actua_launcher_dark),
    Mono("LauncherMono", R.string.settings_app_icon_mono, R.mipmap.actua_launcher_mono),
    Light("LauncherLight", R.string.settings_app_icon_light, R.mipmap.actua_launcher_light),
}

private const val NAMESPACE = "com.azimulkabir.actua"
private const val PENDING = "pending"

/** Reads and flips the enabled state of each icon's alias, and remembers an icon waiting to be applied. */
interface AppIconComponents {
    fun isEnabled(icon: AppIcon): Boolean
    fun setEnabled(icon: AppIcon, enabled: Boolean)
    var pending: AppIcon?
}

private class PackageManagerAppIconComponents(private val context: Context) : AppIconComponents {
    private val preferences = context.getSharedPreferences("app_icon", Context.MODE_PRIVATE)

    override var pending: AppIcon?
        get() = preferences.getString(PENDING, null)?.let { name -> AppIcon.entries.firstOrNull { it.name == name } }
        set(value) = preferences.edit { if (value == null) remove(PENDING) else putString(PENDING, value.name) }

    // Alias class names live in the code namespace, which a debug applicationId suffix doesn't change.
    private fun component(icon: AppIcon) = ComponentName(context.packageName, "$NAMESPACE.${icon.aliasName}")

    override fun isEnabled(icon: AppIcon): Boolean =
        when (context.packageManager.getComponentEnabledSetting(component(icon))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
            // Untouched: the manifest decides, and only the default alias starts enabled.
            else -> icon == AppIcon.Default
        }

    override fun setEnabled(icon: AppIcon, enabled: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            component(icon),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}

/**
 * Chooses the launcher icon. Supporter extra: reset to [AppIcon.Default] when support lapses.
 *
 * Disabling the alias the running app was launched through closes the app, so a choice is only
 * remembered by [choose] and applied by [applyPending] once the app has gone to the background.
 */
class AppIcons(private val components: AppIconComponents) {
    constructor(context: Context) : this(PackageManagerAppIconComponents(context.applicationContext))

    private fun enabledIcon(): AppIcon = AppIcon.entries.firstOrNull { components.isEnabled(it) } ?: AppIcon.Default

    /** The icon the user has chosen: the pending one, else the enabled one. */
    fun current(): AppIcon = components.pending ?: enabledIcon()

    /** Whether the chosen icon is still waiting for [applyPending]. */
    val hasPending: Boolean get() = components.pending != null

    /** Remembers [icon] as the choice; nothing changes in the launcher until [applyPending]. */
    fun choose(icon: AppIcon) {
        components.pending = if (icon == enabledIcon()) null else icon
    }

    fun resetToDefault() = choose(AppIcon.Default)

    /**
     * Makes the chosen icon the launcher icon. The new alias is enabled before the old ones are
     * disabled, so the app is never briefly without a launcher entry. Call when the app stops.
     */
    fun applyPending() {
        val icon = components.pending ?: return
        components.pending = null
        if (!components.isEnabled(icon)) components.setEnabled(icon, true)
        AppIcon.entries.filter { it != icon && components.isEnabled(it) }.forEach { components.setEnabled(it, false) }
    }

    companion object {
        const val RESET_ID = "app_icon"
    }
}
