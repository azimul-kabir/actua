package com.azimulkabir.actua.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppIconsTest {

    private class FakeComponents(initial: AppIcon = AppIcon.Default) : AppIconComponents {
        val enabled = AppIcon.entries.associateWith { it == initial }.toMutableMap()
        val changes = mutableListOf<Pair<AppIcon, Boolean>>()
        override var pending: AppIcon? = null

        override fun isEnabled(icon: AppIcon) = enabled.getValue(icon)

        override fun setEnabled(icon: AppIcon, enabled: Boolean) {
            this.enabled[icon] = enabled
            changes += icon to enabled
        }
    }

    @Test
    fun theDefaultAliasIsCurrentUntilAnotherIsChosen() {
        assertEquals(AppIcon.Default, AppIcons(FakeComponents()).current())
    }

    @Test
    fun choosingAnIconChangesNothingUntilItIsApplied() {
        val components = FakeComponents()
        val icons = AppIcons(components)

        icons.choose(AppIcon.Dark)

        assertTrue(components.changes.isEmpty())
        assertEquals(AppIcon.Dark, icons.current())
        assertTrue(icons.hasPending)
    }

    @Test
    fun applyingEnablesTheNewAliasBeforeDisablingTheOldOne() {
        val components = FakeComponents()
        val icons = AppIcons(components)
        icons.choose(AppIcon.Dark)

        icons.applyPending()

        assertEquals(listOf(AppIcon.Dark to true, AppIcon.Default to false), components.changes)
        assertEquals(AppIcon.Dark, icons.current())
        assertEquals(1, components.enabled.values.count { it })
        assertFalse(icons.hasPending)
    }

    @Test
    fun choosingTheEnabledIconAgainCancelsAPendingChoice() {
        val components = FakeComponents(AppIcon.Mono)
        val icons = AppIcons(components)
        icons.choose(AppIcon.Light)

        icons.choose(AppIcon.Mono)
        icons.applyPending()

        assertNull(components.pending)
        assertTrue(components.changes.isEmpty())
        assertEquals(AppIcon.Mono, icons.current())
    }

    @Test
    fun applyingWithNothingPendingChangesNothing() {
        val components = FakeComponents(AppIcon.Light)
        AppIcons(components).applyPending()
        assertTrue(components.changes.isEmpty())
    }

    @Test
    fun resettingRestoresTheDefaultIconOnceApplied() {
        val components = FakeComponents(AppIcon.Light)
        val icons = AppIcons(components)

        icons.resetToDefault()
        assertEquals(AppIcon.Default, icons.current())
        icons.applyPending()

        assertEquals(listOf(AppIcon.Default to true, AppIcon.Light to false), components.changes)
    }

    @Test
    fun losingSupporterStatusRestoresTheDefaultIcon() {
        val components = FakeComponents(AppIcon.Dark)
        val icons = AppIcons(components)
        SupporterPerks.clearRegistrations()
        SupporterPerks.register(AppIcons.RESET_ID) { icons.resetToDefault() }
        val perks = SupporterPerks(object : SupporterPerksStore {
            override var isSupporter = true
            override var lastCheckedAt = 0L
        })

        perks.record(isSupporter = false)
        icons.applyPending()

        assertEquals(AppIcon.Default, icons.current())
        assertTrue(components.enabled.getValue(AppIcon.Default))
        SupporterPerks.clearRegistrations()
    }

    @Test
    fun onlyTheDefaultIconIsFreeForANonSupporter() {
        assertFalse(AppIcon.Default.lockedFor(isSupporter = false))
        AppIcon.entries.filter { it != AppIcon.Default }.forEach { assertTrue(it.lockedFor(isSupporter = false)) }
    }

    @Test
    fun noIconIsLockedForASupporter() {
        AppIcon.entries.forEach { assertFalse(it.lockedFor(isSupporter = true)) }
    }

    @Test
    fun everyIconHasAMatchingLauncherAliasInBothFlavorManifests() {
        for (flavor in listOf("direct", "playstore")) {
            val manifest = listOf(java.io.File("src/$flavor/AndroidManifest.xml"), java.io.File("app/src/$flavor/AndroidManifest.xml"))
                .first { it.exists() }.readText()
            val aliases = Regex("""<activity-alias\b[^>]*?android:name="\.(Launcher\w+)"[^>]*?android:icon="@mipmap/(\w+)"""")
                .findAll(manifest).associate { it.groupValues[1] to it.groupValues[2] }
            assertEquals("$flavor aliases", AppIcon.entries.map { it.aliasName }.toSet(), aliases.keys)
            for (icon in AppIcon.entries) {
                val mipmap = aliases.getValue(icon.aliasName)
                val adaptive = listOf(java.io.File("src/main/res/mipmap-anydpi/$mipmap.xml"), java.io.File("app/src/main/res/mipmap-anydpi/$mipmap.xml"))
                    .first { it.exists() }.readText()
                assertTrue("$flavor ${icon.name} has a themed icon layer", "<monochrome" in adaptive)
            }
        }
    }
}
