package com.azimulkabir.actua.ui.home

import android.content.res.Resources
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.preferences.DisplayPreferences
import com.azimulkabir.actua.ui.theme.Spacing
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime

/** The time-of-day greeting, with [nickname] when set ("Good morning, Sam"). */
internal fun greetingLine(resources: Resources, hour: Int, nickname: String): String {
    val named = nickname.isNotEmpty()
    val (plain, withName) = when (GreetingPartOfDay.forHour(hour)) {
        GreetingPartOfDay.Morning -> R.string.home_greeting_morning to R.string.home_greeting_morning_named
        GreetingPartOfDay.Afternoon -> R.string.home_greeting_afternoon to R.string.home_greeting_afternoon_named
        GreetingPartOfDay.Evening -> R.string.home_greeting_evening to R.string.home_greeting_evening_named
    }
    return if (named) resources.getString(withName, nickname) else resources.getString(plain)
}

/** One complete translated phrase for [figure]. */
internal fun greetingFigurePhrase(resources: Resources, figure: GreetingFigure): String = when (figure) {
    is GreetingFigure.CategoriesOnBudget ->
        resources.getQuantityString(R.plurals.home_greeting_categories, figure.budgeted, figure.onTrack, figure.budgeted)
    is GreetingFigure.Uncategorized ->
        resources.getQuantityString(R.plurals.home_greeting_uncategorized, figure.count, figure.count)
    is GreetingFigure.BillsDueThisWeek ->
        resources.getQuantityString(R.plurals.home_greeting_bills_due, figure.count, figure.count)
    is GreetingFigure.AddedThisWeek ->
        resources.getQuantityString(R.plurals.home_greeting_added, figure.count, figure.count)
}

private const val SEPARATOR = " · "

/**
 * The greeting and every available figure, in the same surface as the other Home cards. Figures sit in two
 * equal columns whose tiles share a row height, so they stay tidy at large font scales; an odd last figure
 * takes the full width.
 */
@Composable
fun GreetingCard(
    figures: List<GreetingFigure>,
    nickname: String,
    modifier: Modifier = Modifier,
    horizontalInset: Dp = Spacing.screenHorizontal,
) {
    val resources = LocalContext.current.resources
    val greeting = greetingLine(resources, LocalTime.now().hour, nickname)
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = horizontalInset).testTag("greetingCard"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.FavoriteBorder, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(greeting, style = MaterialTheme.typography.titleMedium)
            }
            figures.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    pair.forEach { figure ->
                        Surface(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ) {
                            Text(
                                greetingFigurePhrase(resources, figure),
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The slim, once-a-day strip at the top of the start page. It is part of the screen's chrome, so it pushes
 * content down and collapses; it never overlays anything. Shown at most once per device-local day, and gone
 * after [AUTO_DISMISS_MILLIS], a close tap, any scroll ([scrollCount] changing) or when [eligible] turns false.
 */
@Composable
fun GreetingStrip(
    eligible: Boolean,
    figures: List<GreetingFigure>,
    nickname: String,
    budgetId: String?,
    scrollCount: Int,
    onOpenHome: () -> Unit,
) {
    val context = LocalContext.current
    val preferences = remember { DisplayPreferences(context) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    // A date change while the app is open is noticed when it returns to the foreground.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) today = LocalDate.now()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    var visible by remember { mutableStateOf(false) }
    var scrollAtShow by remember { mutableIntStateOf(scrollCount) }
    val canShow = eligible
    LaunchedEffect(canShow, today) {
        if (!canShow) { visible = false; return@LaunchedEffect }
        if (visible || !GreetingStripPolicy.shouldShow(preferences.greetingStripLastShown, today)) return@LaunchedEffect
        preferences.greetingStripLastShown = today.toString()
        scrollAtShow = scrollCount
        visible = true
        delay(AUTO_DISMISS_MILLIS)
        visible = false
    }
    LaunchedEffect(scrollCount) { if (visible && scrollCount != scrollAtShow) visible = false }

    val animate = remember { animationsEnabled(context) }
    val content: @Composable () -> Unit = {
        StripContent(figures, nickname, budgetId, today, onOpenHome) { visible = false }
    }
    if (animate) {
        // Fade only: animating the height would re-lay out the whole screen below on every frame.
        AnimatedVisibility(visible = visible && canShow, enter = fadeIn(tween(200)), exit = fadeOut(tween(150))) { content() }
    } else if (visible && canShow) {
        content()
    }
}

@Composable
private fun StripContent(
    figures: List<GreetingFigure>,
    nickname: String,
    budgetId: String?,
    today: LocalDate,
    onOpenHome: () -> Unit,
    onClose: () -> Unit,
) {
    val resources = LocalContext.current.resources
    val greeting = greetingLine(resources, LocalTime.now().hour, nickname)
    val phrases = remember(figures, today, budgetId, resources) {
        GreetingStripPolicy.dailyOrder(figures, today, budgetId).map { greetingFigurePhrase(resources, it) }
    }
    // Greeting on the first line, the day's first two figures on the second; the second line may wrap once.
    val shown = remember(phrases) { phrases.take(STRIP_FIGURES).joinToString(SEPARATOR) }
    val spoken = remember(phrases, greeting) { (listOf(greeting) + phrases).joinToString(". ") }
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("greetingStrip"),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            // The strip is the top bar, so it draws under the status bar and keeps its own row clear of it.
            modifier = Modifier.statusBarsPadding()
                .clickable(onClickLabel = stringResource(R.string.home_greeting_open_home), onClick = onOpenHome)
                .padding(start = Spacing.screenHorizontal, top = Spacing.xs, bottom = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).semantics { contentDescription = spoken }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.FavoriteBorder, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(Spacing.sm))
                    Text(greeting, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (shown.isNotEmpty()) {
                    Text(shown, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.home_greeting_dismiss), modifier = Modifier.size(16.dp))
            }
        }
    }
}

private const val STRIP_FIGURES = 2
private const val AUTO_DISMISS_MILLIS = 8_000L

/** False when the system animator scale is 0 ("remove animations"), so the strip then just disappears. */
private fun animationsEnabled(context: android.content.Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
