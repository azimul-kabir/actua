package com.azimulkabir.actua.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import com.azimulkabir.actua.data.home.HomeLayout
import com.azimulkabir.actua.data.home.HomeSection
import com.azimulkabir.actua.data.preferences.HomePreferences

/** Flow state retains its original navigation-composition lifetime and saveability. */
internal class HomeLayoutState(
    val homeLayout: MutableState<HomeLayout>,
    val homeCollapsed: MutableState<Set<HomeSection>>,
)

@Composable
internal fun rememberHomeLayoutState(homePreferences: HomePreferences): HomeLayoutState =
    HomeLayoutState(
        homeLayout = remember { mutableStateOf(homePreferences.layout()) },
        homeCollapsed = remember { mutableStateOf(homePreferences.collapsed()) },
    )
