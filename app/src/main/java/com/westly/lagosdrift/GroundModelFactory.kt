package com.westly.lagosdrift

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The ground: grass, the main road and three cross roads with dashed lines, roadside posts,
 * a beach and lagoon on the east side, and hills in the distance.
 */
object GroundModelFactory {
    private const val BEACH_X = 167.5f
    private const val WATER_X = 437.5f

    fun build(): Model {
        val b = ModelBuilder()
        b.begin()
        b.node()

        b.colorPart("grass", Color(0.30f, 0.55f, 0.25f, 1f)).boxAt(0f, -0.10f, 0f, 1400f, 0.2f, 1400f)
        b.colorPart("sand", Color(0.85f, 0.78f, 0.55f, 1f)).boxAt(BEACH_X, -0.09f, 0f, 15f, 0.2f, 1400f)
        b.colorPart("water", Color(0.18f, 0.45f, 0.70f, 1f)).boxAt(WATER_X, -0.08f, 0f, 525f, 0.2f, 1400f)

        // Roads: the main road and the cross roads.
        val road = b.colorPart("road", Color(0.38f, 0.38f, 0.40f, 1f))
        road.boxAt(0f, -0.07f, 0f, TownLayout.ROAD_HALF * 2f, 0.2f, 600f)
        val crossWidth = TownLayout.CROSS_MAX_X - TownLayout.CROSS_MIN_X
        val crossCentre = (TownLayout.CROSS_MAX_X + TownLayout.CROSS_MIN_X) / 2f
        for (czr in TownLayout.crossRoadsZ) {
            road.boxAt(crossCentre, -0.07f, czr, crossWidth, 0.2f, TownLayout.CROSS_HALF * 2f)
        }

        // Dashed centre lines, left out where roads cross.
        val dashes = b.colorPart("dashes", Color(0.95f, 0.95f, 0.90f, 1f))
        var z = -288f
        while (z <= 288f) {
            if (!TownLayout.nearCrossRoad(z, 1.5f)) dashes.boxAt(0f, 0.04f, z, 0.25f, 0.02f, 3f)
            z += 8f
        }
        for (czr in TownLayout.crossRoadsZ) {
            var x = -280f
            while (x <= 145f) {
                if (abs(x) > TownLayout.ROAD_HALF + 1.5f) dashes.boxAt(x, 0.04f, czr, 3f, 0.02f, 0.25f)
                x += 8f
            }
        }

        // Roadside posts along the main road, one every 16 units, none at the crossings.
        val posts = b.colorPart("posts", Color(0.92f, 0.92f, 0.92f, 1f))
        var pz = -288f
        while (pz <= 288f) {
            if (!TownLayout.nearCrossRoad(pz, 2f)) {
                posts.boxAt(-7.5f, 0.5f, pz, 0.3f, 1f, 0.3f)
                posts.boxAt(7.5f, 0.5f, pz, 0.3f, 1f, 0.3f)
            }
            pz += 16f
        }

        // Hills far away on every side (some stand in the lagoon like islands).
        val rnd = Random(5)
        val hills = ArrayList<FloatArray>()
        for (i in 0 until 18) {
            val angle = (i / 18f) * 6.2832f + rnd.nextFloat() * 0.2f
            val radius = 430f + rnd.nextFloat() * 140f
            hills.add(
                floatArrayOf(
                    cos(angle) * radius, sin(angle) * radius,
                    200f + rnd.nextFloat() * 120f, 50f + rnd.nextFloat() * 60f
                )
            )
        }
        val hillColors = arrayOf(Color(0.25f, 0.45f, 0.28f, 1f), Color(0.40f, 0.55f, 0.62f, 1f))
        for (shade in 0..1) {
            val part = b.colorPart("hills$shade", hillColors[shade])
            for ((i, hill) in hills.withIndex()) {
                if (i % 2 == shade) part.coneAt(hill[0], hill[3] / 2f, hill[1], hill[2], hill[3], 10)
            }
        }

        return b.end()
    }
}
