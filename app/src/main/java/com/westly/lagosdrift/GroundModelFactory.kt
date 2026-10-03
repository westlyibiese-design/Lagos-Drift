package com.westly.lagosdrift

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The ground: grass, the main road and three cross roads with dashed lines, pavements,
 * street lights, a beach and lagoon on the east side, and hills in the distance.
 */
object GroundModelFactory {
    private const val BEACH_X = 167.5f
    private const val WATER_X = 437.5f

    fun build(): Model {
        val b = ModelBuilder()
        b.begin()
        b.node()

        b.colorPart("grass", Color(0.30f, 0.58f, 0.25f, 1f)).boxAt(0f, -0.10f, 0f, 1400f, 0.2f, 1400f)
        b.colorPart("sand", Color(0.90f, 0.82f, 0.55f, 1f)).boxAt(BEACH_X, -0.09f, 0f, 15f, 0.2f, 1400f)
        b.colorPart("water", Color(0.15f, 0.52f, 0.78f, 1f)).boxAt(WATER_X, -0.08f, 0f, 525f, 0.2f, 1400f)

        // Roads: the main road and the cross roads.
        val road = b.colorPart("road", Color(0.38f, 0.38f, 0.40f, 1f))
        road.boxAt(0f, -0.07f, 0f, TownLayout.ROAD_HALF * 2f, 0.2f, 600f)
        val crossWidth = TownLayout.CROSS_MAX_X - TownLayout.CROSS_MIN_X
        val crossCentre = (TownLayout.CROSS_MAX_X + TownLayout.CROSS_MIN_X) / 2f
        for (czr in TownLayout.crossRoadsZ) {
            road.boxAt(crossCentre, -0.07f, czr, crossWidth, 0.2f, TownLayout.CROSS_HALF * 2f)
        }

        // Pavements along the walkways, a little higher than the road.
        val pavement = b.colorPart("pavement", Color(0.72f, 0.70f, 0.66f, 1f))
        for (w in TownLayout.walkways) {
            val centreX = (w.x1 + w.x2) / 2f
            val centreZ = (w.z1 + w.z2) / 2f
            if (abs(w.dirX) > 0.5f) {
                pavement.boxAt(centreX, 0f, centreZ, w.length, 0.24f, 3f)
            } else {
                pavement.boxAt(centreX, 0f, centreZ, 3f, 0.24f, w.length)
            }
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

        // Street lights: poles and arms, then glowing lamp heads.
        val poles = b.colorPart("poles", Color(0.22f, 0.23f, 0.26f, 1f))
        for (l in TownLayout.lamps) {
            poles.cylinderAt(l.x, 2.6f, l.z, 0.18f, 5.2f, 8)
            val armX = l.x + l.dirX * 0.8f
            val armZ = l.z + l.dirZ * 0.8f
            val armWidth = if (l.dirX != 0) 1.6f else 0.12f
            val armDepth = if (l.dirZ != 0) 1.6f else 0.12f
            poles.boxAt(armX, 5.15f, armZ, armWidth, 0.12f, armDepth)
        }
        val lamps = b.colorPart("lamps", Color(1.0f, 0.96f, 0.72f, 1f))
        for (l in TownLayout.lamps) {
            lamps.boxAt(l.x + l.dirX * 1.6f, 5.05f, l.z + l.dirZ * 1.6f, 0.5f, 0.15f, 0.5f)
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
        val hillColors = arrayOf(Color(0.25f, 0.50f, 0.28f, 1f), Color(0.40f, 0.58f, 0.66f, 1f))
        for (shade in 0..1) {
            val part = b.colorPart("hills$shade", hillColors[shade])
            for ((i, hill) in hills.withIndex()) {
                if (i % 2 == shade) part.coneAt(hill[0], hill[3] / 2f, hill[1], hill[2], hill[3], 10)
            }
        }

        return b.end()
    }
}
