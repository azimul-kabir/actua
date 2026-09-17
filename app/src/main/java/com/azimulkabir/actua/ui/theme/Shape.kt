package com.azimulkabir.actua.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * App-wide corner radii. Fields map to Material 3's shape scale so
 * `MaterialTheme.shapes.*` resolves to one of these everywhere by default:
 * text fields/buttons/chips use `small`, cards/dialogs use `medium`/`large`,
 * bottom sheets/large surfaces use `extraLarge`. Pill shapes are reserved for
 * chips and small toggle-style controls rather than cards or containers.
 */
val ActuaShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Fully rounded, pill shape reserved for chips and compact toggle controls. */
val PillShape = RoundedCornerShape(percent = 50)
