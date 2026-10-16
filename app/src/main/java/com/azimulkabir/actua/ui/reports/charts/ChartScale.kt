package com.azimulkabir.actua.ui.reports.charts

import androidx.compose.runtime.compositionLocalOf

/** Multiplier for chart heights; the full-screen widget view provides a value above 1. */
val LocalChartScale = compositionLocalOf { 1f }
