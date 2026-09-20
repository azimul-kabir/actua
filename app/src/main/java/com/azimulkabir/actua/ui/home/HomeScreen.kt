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
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.ui.components.ActuaScreenHeader

/** Home entry points for the first Home slice. Dashboard content follows in later slices. */
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onReportsClick: () -> Unit = {},
    returnToRootRequest: Int = 0,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(returnToRootRequest) {
        if (returnToRootRequest > 0) listState.animateScrollToItem(0)
    }
    LazyColumn(modifier = modifier.fillMaxSize(), state = listState) {
        item { ActuaScreenHeader(title = "Home") }
        item {
            ListItem(
                headlineContent = { Text("Reports") },
                supportingContent = { Text("Dashboards and financial insights") },
                leadingContent = { Icon(Icons.Outlined.BarChart, contentDescription = null) },
                modifier = Modifier.padding(horizontal = 8.dp).clickable(onClick = onReportsClick),
            )
        }
    }
}
