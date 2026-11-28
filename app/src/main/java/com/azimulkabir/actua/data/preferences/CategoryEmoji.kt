package com.azimulkabir.actua.data.preferences

import android.icu.text.BreakIterator
import android.icu.text.UnicodeSet
import java.util.Locale

private val emoji = UnicodeSet("[[:Emoji:]-[0-9#*[:Regional_Indicator:][:Emoji_Modifier:]]]").freeze()
private val modifierBases = UnicodeSet("[:Emoji_Modifier_Base:]").freeze()
private val modifiers = UnicodeSet("[:Emoji_Modifier:]").freeze()
private val flags = Regex("[\\x{1F1E6}-\\x{1F1FF}]{2}")
private val keycap = Regex("[0-9#*]\\uFE0F?\\u20E3")
private val subdivisionFlag = Regex("\\x{1F3F4}[\\x{E0061}-\\x{E007A}]+\\x{E007F}")

internal fun isCategoryEmoji(value: String): Boolean {
    val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
    iterator.setText(value)
    if (iterator.next() != value.length || iterator.next() != BreakIterator.DONE) return false
    if (flags.matches(value) || keycap.matches(value) || subdivisionFlag.matches(value)) return true
    return value.split('\u200D').all { part ->
        val points = part.codePoints().toArray()
        when (points.size) {
            1 -> emoji.contains(points[0])
            2 -> (points[1] == 0xFE0F && emoji.contains(points[0])) ||
                (modifierBases.contains(points[0]) && modifiers.contains(points[1]))
            3 -> points[1] == 0xFE0F && modifierBases.contains(points[0]) && modifiers.contains(points[2])
            else -> false
        }
    }
}
