package com.westly.lagosdrift

import kotlin.math.abs
import kotlin.random.Random

/**
 * Where the roads, houses and trees are. The same lists are used to draw the town and to
 * stop the car driving through it. The layout is fixed (same seed), so it is identical every run.
 *
 * North is -Z. The main road runs north-south through x = 0. Three cross roads run east-west
 * and end at the beach on the east side.
 */
object TownLayout {
    const val ROAD_HALF = 6f          // main road is 12 wide
    const val CROSS_HALF = 5f         // cross roads are 10 wide
    const val CROSS_MIN_X = -285f
    const val CROSS_MAX_X = 150f
    const val WALL_COLORS = 6
    const val ROOF_COLORS = 3

    private const val MAIN_SETBACK = 11f
    private const val CROSS_SETBACK = 10f

    val crossRoadsZ = floatArrayOf(120f, -40f, -190f)

    /** A house is a box. (facingX, facingZ) points from the house toward its road. */
    class House(
        val cx: Float, val cz: Float, val hw: Float, val hd: Float, val height: Float,
        val facingX: Int, val facingZ: Int, val wallColor: Int, val roofColor: Int
    )

    class Tree(val x: Float, val z: Float, val scale: Float, val leafColor: Int)

    val houses: List<House> = buildHouses()
    val trees: List<Tree> = buildTrees(houses)

    fun nearCrossRoad(z: Float, margin: Float): Boolean =
        crossRoadsZ.any { abs(z - it) < CROSS_HALF + margin }

    private fun makeHouse(rnd: Random, cx: Float, cz: Float, hw: Float, hd: Float, fx: Int, fz: Int): House {
        val height = when (rnd.nextInt(10)) {
            in 0..4 -> 4.5f
            in 5..7 -> 7.5f
            8 -> 10.5f
            else -> 13.5f
        }
        return House(cx, cz, hw, hd, height, fx, fz, rnd.nextInt(WALL_COLORS), rnd.nextInt(ROOF_COLORS))
    }

    private fun buildHouses(): List<House> {
        val rnd = Random(11)
        val list = ArrayList<House>()

        // Along the main road, both sides, facing it. Skip the cross-road crossings.
        for (side in intArrayOf(-1, 1)) {
            var z = -270f
            while (z < 265f) {
                val hw = 5f + rnd.nextFloat() * 2.5f
                val hd = 5f + rnd.nextFloat() * 2.5f
                val zc = z + hd
                if (!nearCrossRoad(zc, hd + 3f)) {
                    list.add(makeHouse(rnd, side * (MAIN_SETBACK + hw), zc, hw, hd, -side, 0))
                }
                z += hd * 2f + 5f + rnd.nextFloat() * 10f
            }
        }

        // Along each cross road, both sides, west part and east part (not near the main road).
        for (czr in crossRoadsZ) {
            for (side in intArrayOf(-1, 1)) {
                for (range in arrayOf(-270f to -38f, 38f to 135f)) {
                    val (xStart, xEnd) = range
                    var x = xStart
                    while (x < xEnd) {
                        val hw = 5f + rnd.nextFloat() * 2.5f
                        val hd = 5f + rnd.nextFloat() * 2.5f
                        val xc = x + hw
                        if (xc + hw <= xEnd) {
                            list.add(makeHouse(rnd, xc, czr + side * (CROSS_SETBACK + hd), hw, hd, 0, -side))
                        }
                        x += hw * 2f + 5f + rnd.nextFloat() * 10f
                    }
                }
            }
        }
        return list
    }

    private fun buildTrees(houses: List<House>): List<Tree> {
        val rnd = Random(23)
        val list = ArrayList<Tree>()
        var attempts = 0
        while (list.size < 170 && attempts < 4000) {
            attempts++
            val x = -290f + rnd.nextFloat() * 440f
            val z = -290f + rnd.nextFloat() * 580f
            val scale = 0.8f + rnd.nextFloat() * 0.7f
            val leaf = rnd.nextInt(2)
            if (abs(x) < 10f) continue
            if (nearCrossRoad(z, 8f)) continue
            if (houses.any {
                    x > it.cx - it.hw - 3f && x < it.cx + it.hw + 3f &&
                        z > it.cz - it.hd - 3f && z < it.cz + it.hd + 3f
                }
            ) continue
            list.add(Tree(x, z, scale, leaf))
        }
        return list
    }
}
