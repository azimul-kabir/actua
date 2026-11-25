package com.azimulkabir.actua.data.preferences

import android.content.Context
import java.util.Locale

/**
 * The release's "What's new" text. The build copies each language's Google Play text
 * (`fastlane/metadata/android/<language>/changelogs/<versionCode>.txt`) into the app's
 * `whatsnew/` assets, so the store and the in-app card always say the same thing.
 */
object WhatsNewNotes {
    private const val DIRECTORY = "whatsnew"
    private const val FALLBACK = "en"

    /** The notes in the app's language, or English, as one entry per non-blank line; empty if none is bundled. */
    fun load(context: Context, locale: Locale = context.resources.configuration.locales[0]): List<String> = runCatching {
        val available = context.assets.list(DIRECTORY).orEmpty().toSet()
        val name = assetName(locale.language, locale.country, available) ?: return emptyList()
        context.assets.open("$DIRECTORY/$name").bufferedReader(Charsets.UTF_8).use { parse(it.readText()) }
    }.getOrDefault(emptyList())

    /** Picks `<language>-<COUNTRY>.txt`, then `<language>.txt`, then English, from the bundled [available] files. */
    fun assetName(language: String, country: String, available: Set<String>): String? =
        listOf("$language-$country.txt", "$language.txt", "$FALLBACK.txt").firstOrNull { it in available }

    fun parse(text: String): List<String> = text.lines().map(String::trim).filter(String::isNotEmpty)
}
