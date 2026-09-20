package com.azimulkabir.actua.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.azimulkabir.actua.ui.components.ActuaScreenHeader
import com.azimulkabir.actua.ui.components.ActuaSectionHeader

/**
 * Stable Home root. Dashboard slices fill these keyed sections independently, preserving this
 * list's scroll position when a tab is reselected or individual section data changes.
 */
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    projection: HomeDashboardProjection,
    onReportsClick: () -> Unit = {},
    returnToRootRequest: Int = 0,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(returnToRootRequest) {
        if (returnToRootRequest > 0) listState.animateScrollToItem(0)
    }
    LazyColumn(modifier = modifier.fillMaxSize(), state = listState) {
        item(key = "home-header") { ActuaScreenHeader(title = "Home") }
        HomeSection.entries.forEach { section ->
            item(key = section.name) {
                if (section == HomeSection.REPORTS) {
                    // Reports is already a complete authoritative destination. It remains
                    // reachable while the dashboard sections are delivered in focused slices.
                    ListItem(
                        headlineContent = { Text("Reports") },
                        supportingContent = { Text("Dashboards and financial insights") },
                        leadingContent = { Icon(Icons.Outlined.BarChart, contentDescription = null) },
                        modifier = Modifier.clickable(onClick = onReportsClick),
                    )
                } else {
                    HomeSectionPlaceholder(section)
                }
            }
        }
    }
}

@Composable
private fun HomeSectionPlaceholder(section: HomeSection) {
    // The model is consumed by the upcoming section composables. Keeping the shell lightweight
    // avoids a blocking read or a synthetic financial calculation on Home's first frame.
    ActuaSectionHeader(title = section.title)
}
