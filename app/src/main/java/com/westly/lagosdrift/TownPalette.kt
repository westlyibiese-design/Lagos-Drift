package com.westly.lagosdrift

import com.badlogic.gdx.graphics.Color

/** The colours used for buildings and trees. Lengths match the counts in TownLayout. */
object TownPalette {
    val WALLS = arrayOf(
        Color(0.98f, 0.85f, 0.25f, 1f),  // sunny yellow
        Color(0.20f, 0.72f, 0.78f, 1f),  // turquoise
        Color(0.95f, 0.55f, 0.20f, 1f),  // orange
        Color(0.92f, 0.40f, 0.55f, 1f),  // pink
        Color(0.45f, 0.78f, 0.35f, 1f),  // lime green
        Color(0.30f, 0.50f, 0.85f, 1f),  // blue
        Color(0.90f, 0.30f, 0.28f, 1f),  // red
        Color(0.96f, 0.93f, 0.82f, 1f),  // cream
        Color(0.62f, 0.45f, 0.78f, 1f),  // purple
        Color(0.55f, 0.80f, 0.90f, 1f)   // sky blue
    )
    val ROOFS = arrayOf(
        Color(0.70f, 0.25f, 0.20f, 1f),  // terracotta
        Color(0.20f, 0.45f, 0.55f, 1f),  // teal
        Color(0.35f, 0.28f, 0.25f, 1f),  // brown
        Color(0.25f, 0.27f, 0.32f, 1f)   // charcoal
    )
    val ACCENTS = arrayOf(
        Color(0.85f, 0.15f, 0.15f, 1f),  // red
        Color(0.15f, 0.60f, 0.30f, 1f),  // green
        Color(0.15f, 0.35f, 0.80f, 1f),  // blue
        Color(0.95f, 0.50f, 0.10f, 1f),  // orange
        Color(0.55f, 0.20f, 0.70f, 1f),  // purple
        Color(0.98f, 0.80f, 0.15f, 1f)   // yellow
    )
    val LEAVES = arrayOf(
        Color(0.16f, 0.45f, 0.20f, 1f),  // deep green
        Color(0.30f, 0.62f, 0.25f, 1f),  // fresh green
        Color(0.65f, 0.40f, 0.80f, 1f),  // purple blossom
        Color(0.95f, 0.40f, 0.20f, 1f),  // flame tree
        Color(0.95f, 0.55f, 0.70f, 1f)   // pink blossom
    )
}
