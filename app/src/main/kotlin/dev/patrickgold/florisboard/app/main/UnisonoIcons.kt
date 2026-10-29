/*
 * Copyright (C) 2026 Luxx1993 (diktat)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.app.main

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The line icons of Entwurf 3, taken path for path from the HTML references in docs/design/entwurf-3/
 * (24 × 24, round caps and joins). Rectangles and circles are written as paths. The stroke colour is a
 * placeholder: `Icon` tints it.
 */
object UnisonoIcons {
    val Home: ImageVector by lazy { icon("Home", "M4 11l8-7 8 7v8a1 1 0 0 1-1 1h-4v-6h-6v6H5a1 1 0 0 1-1-1z") }
    val Book: ImageVector by lazy { icon("Book", "M5 4h11a3 3 0 0 1 3 3v13H8a3 3 0 0 1-3-3z", "M5 17a3 3 0 0 1 3-3h11") }
    val Sparkle: ImageVector by lazy { icon("Sparkle", "M12 3l1.8 4.6L18 9.4l-4.2 1.8L12 16l-1.8-4.8L6 9.4l4.2-1.8z", "M19 15l.8 2 2 .8-2 .8-.8 2-.8-2-2-.8 2-.8z") }
    val Gear: ImageVector by lazy { icon("Gear", "M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0z", "M12 2.5v3M12 18.5v3M4.6 4.6l2.1 2.1M17.3 17.3l2.1 2.1M2.5 12h3M18.5 12h3M4.6 19.4l2.1-2.1M17.3 6.7l2.1-2.1") }
    val Mic: ImageVector by lazy { icon("Mic", "M12 3h0a3 3 0 0 1 3 3v5a3 3 0 0 1 -3 3h0a3 3 0 0 1 -3 -3v-5a3 3 0 0 1 3 -3z", "M5 11a7 7 0 0 0 14 0M12 18v3") }
    val Copy: ImageVector by lazy { icon("Copy", "M11.5 9h6 a2.5 2.5 0 0 1 2.5 2.5v6 a2.5 2.5 0 0 1 -2.5 2.5h-6 a2.5 2.5 0 0 1 -2.5 -2.5v-6 a2.5 2.5 0 0 1 2.5 -2.5z", "M15 9V6.5A2.5 2.5 0 0 0 12.5 4h-6A2.5 2.5 0 0 0 4 6.5v6A2.5 2.5 0 0 0 6.5 15H9") }
    val Chevron: ImageVector by lazy { icon("Chevron", "M9 6l6 6-6 6") }
    val Search: ImageVector by lazy { icon("Search", "M4.5 11a6.5 6.5 0 1 0 13 0a6.5 6.5 0 1 0 -13 0z", "M16 16l4.5 4.5") }
    val Arrow: ImageVector by lazy { icon("Arrow", "M5 12h14M13 6l6 6-6 6") }
    val Plus: ImageVector by lazy { icon("Plus", "M12 5v14M5 12h14") }
    val Globe: ImageVector by lazy { icon("Globe", "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0z", "M3 12h18M12 3c2.5 2.7 3.8 5.7 3.8 9s-1.3 6.3-3.8 9c-2.5-2.7-3.8-5.7-3.8-9S9.5 5.7 12 3z") }
    val Cloud: ImageVector by lazy { icon("Cloud", "M7 18a4.5 4.5 0 0 1-.6-9A6 6 0 0 1 18 8.5a4.8 4.8 0 0 1-1 9.5z") }
    val Wave: ImageVector by lazy { icon("Wave", "M4 12h2M8 8v8M12 5v14M16 8v8M20 12h0") }
    val AudioFile: ImageVector by lazy { icon("AudioFile", "M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z", "M14 3v5h5M10 17v-5l4-1v5", "M8 17a1 1 0 1 0 2 0a1 1 0 1 0 -2 0z", "M12 16a1 1 0 1 0 2 0a1 1 0 1 0 -2 0z") }
    val Keyboard: ImageVector by lazy { icon("Keyboard", "M5.5 6h13 a2.5 2.5 0 0 1 2.5 2.5v7 a2.5 2.5 0 0 1 -2.5 2.5h-13 a2.5 2.5 0 0 1 -2.5 -2.5v-7 a2.5 2.5 0 0 1 2.5 -2.5z", "M7 10h0M11 10h0M15 10h0M8 14h8") }
    val Archive: ImageVector by lazy { icon("Archive", "M4 7h16v12a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1z", "M3 4h18v3H3zM10 11h4") }
    val Note: ImageVector by lazy { icon("Note", "M5 4h14v11l-5 5H5z", "M14 20v-5h5", "M8 9h8M8 13h4") }
    val Clock: ImageVector by lazy { icon("Clock", "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0z", "M12 7v5l3 2") }

    private fun icon(name: String, vararg paths: String, stroke: Float = 1.6f): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            for (d in paths) {
                addPath(
                    pathData = addPathNodes(d),
                    fill = null,
                    stroke = SolidColor(androidx.compose.ui.graphics.Color.Black),
                    strokeLineWidth = stroke,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()
}
