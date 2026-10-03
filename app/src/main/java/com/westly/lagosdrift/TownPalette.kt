package com.westly.lagosdrift

import com.badlogic.gdx.graphics.Color

/**
 * The colours used for buildings, roads and trees. Ikoyi is clean and pale; Ikorodu is bright,
 * faded and weathered. Indexes stored in TownLayout.House are looked up through wall(), roof()
 * and accent(), so each district reads its own list.
 */
object TownPalette {
    val IKOYI_WALLS = arrayOf(
        Color(0.97f, 0.97f, 0.95f, 1f),  // white
        Color(0.93f, 0.90f, 0.82f, 1f),  // cream
        Color(0.80f, 0.82f, 0.84f, 1f),  // light grey
        Color(0.55f, 0.72f, 0.82f, 1f),  // glass blue
        Color(0.86f, 0.80f, 0.68f, 1f),  // sand stone
        Color(0.62f, 0.68f, 0.72f, 1f)   // steel glass
    )
    val IKORODU_WALLS = arrayOf(
        Color(0.98f, 0.85f, 0.25f, 1f),  // sunny yellow
        Color(0.20f, 0.72f, 0.78f, 1f),  // turquoise
        Color(0.95f, 0.55f, 0.20f, 1f),  // orange
        Color(0.92f, 0.40f, 0.55f, 1f),  // pink
        Color(0.45f, 0.78f, 0.35f, 1f),  // lime green
        Color(0.30f, 0.50f, 0.85f, 1f),  // blue
        Color(0.90f, 0.30f, 0.28f, 1f),  // red
        Color(0.88f, 0.84f, 0.72f, 1f),  // dusty cream
        Color(0.62f, 0.45f, 0.78f, 1f),  // purple
        Color(0.70f, 0.66f, 0.60f, 1f)   // bare cement
    )

    val IKOYI_ROOFS = arrayOf(
        Color(0.25f, 0.27f, 0.32f, 1f),  // charcoal
        Color(0.90f, 0.90f, 0.88f, 1f),  // white slab
        Color(0.70f, 0.30f, 0.22f, 1f),  // terracotta
        Color(0.35f, 0.37f, 0.40f, 1f)   // slate
    )
    val IKORODU_ROOFS = arrayOf(
        Color(0.56f, 0.28f, 0.18f, 1f),  // rusty zinc
        Color(0.63f, 0.65f, 0.67f, 1f),  // new zinc
        Color(0.42f, 0.44f, 0.46f, 1f),  // old zinc
        Color(0.36f, 0.30f, 0.26f, 1f)   // brown
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

    // Ground and road surfaces.
    val IKOYI_GROUND = Color(0.28f, 0.58f, 0.27f, 1f)
    val IKORODU_GROUND = Color(0.52f, 0.50f, 0.30f, 1f)
    val SAND = Color(0.90f, 0.82f, 0.55f, 1f)
    val WATER = Color(0.15f, 0.52f, 0.78f, 1f)
    val FAR_GROUND = Color(0.38f, 0.52f, 0.30f, 1f)

    val ROAD_SMOOTH = Color(0.26f, 0.26f, 0.29f, 1f)
    val ROAD_WORN = Color(0.36f, 0.35f, 0.34f, 1f)
    val ROAD_POTHOLED = Color(0.41f, 0.38f, 0.34f, 1f)
    val ROAD_DIRT = Color(0.58f, 0.43f, 0.27f, 1f)
    val PAVEMENT = Color(0.72f, 0.70f, 0.66f, 1f)
    val DASH = Color(0.95f, 0.95f, 0.90f, 1f)
    val POTHOLE = Color(0.12f, 0.10f, 0.09f, 1f)
    val PUDDLE = Color(0.38f, 0.45f, 0.50f, 1f)

    fun wall(h: TownLayout.House): Color =
        if (h.district == Districts.IKOYI) IKOYI_WALLS[h.wallColor % IKOYI_WALLS.size]
        else IKORODU_WALLS[h.wallColor % IKORODU_WALLS.size]

    fun roof(h: TownLayout.House): Color =
        if (h.district == Districts.IKOYI) IKOYI_ROOFS[h.roofColor % IKOYI_ROOFS.size]
        else IKORODU_ROOFS[h.roofColor % IKORODU_ROOFS.size]

    fun accent(index: Int): Color = ACCENTS[index % ACCENTS.size]

    fun road(surface: Int): Color = when (surface) {
        TownLayout.SURFACE_SMOOTH -> ROAD_SMOOTH
        TownLayout.SURFACE_WORN -> ROAD_WORN
        TownLayout.SURFACE_POTHOLED -> ROAD_POTHOLED
        else -> ROAD_DIRT
    }
}
