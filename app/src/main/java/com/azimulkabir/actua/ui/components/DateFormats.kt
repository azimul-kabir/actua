package com.azimulkabir.actua.ui.components

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val displayDateFormatter = DateTimeFormatter.ofPattern("dd-MMM-yy", Locale.ENGLISH)

fun formatDate(date: LocalDate): String = date.format(displayDateFormatter)

fun parseStoredDate(value: String): LocalDate? = runCatching {
    when {
        value.matches(Regex("\\d{8}")) -> LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE)
        value.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) -> LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE)
        else -> LocalDate.parse(value, displayDateFormatter)
    }
}.getOrNull()

fun formatStoredDate(value: String): String = parseStoredDate(value)?.let(::formatDate) ?: value

fun storageDate(date: LocalDate): String = date.format(DateTimeFormatter.BASIC_ISO_DATE)
