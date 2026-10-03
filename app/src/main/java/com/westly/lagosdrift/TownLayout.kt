package com.westly.lagosdrift

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Where the roads, pavements, street lights, houses, shops, towers and trees are.
 * The same lists are used to draw the town, to stop the car driving through it, to send
 * pedestrians along the pavements and to draw the map. The layout is fixed (same seed).
 *
 * North is -Z. The main road runs north-south through x = 0. Three cross roads run east-west
 * and end at the beach on the east side.
 */
object TownLayout {
    const val ROAD_HALF = 6f          // main road is 12 wide
    const val CROSS_HALF = 5f         // cross roads are 10 wide
    const val CROSS_MIN_X = -285f
    const val CROSS_MAX_X = 150f
    const val WALL_COLORS = 10
    const val ROOF_COLORS = 4
    const val ACCENT_COLORS = 6
    const val LEAF_COLORS = 5

    const val TYPE_BUNGALOW = 0
    const val TYPE_HOUSE = 1
    const val TYPE_SHOP = 2
    const val TYPE_TOWER = 3

    private const val MAIN_SETBACK = 11f
    private const val CROSS_SETBACK = 10f

    val crossRoadsZ = floatArrayOf(120f, -40f, -190f)

    /**
     * A building is a box. (facingX, facingZ) points from it toward its road.
     * hw and hd are half sizes along X and Z.
     */
    class House(
        val cx: Float, val cz: Float, val hw: Float, val hd: Float, val height: Float,
        val facingX: Int, val facingZ: Int, val type: Int,
        val wallColor: Int, val roofColor: Int, val accent: Int
    )

    /** kind: 0 = round-crowned tree, 1 = palm. */
    class Tree(val x: Float, val z: Float, val scale: Float, val kind: Int, val leafColor: Int)

    /** A straight pavement line that pedestrians walk along. */
    class Walkway(val x1: Float, val z1: Float, val x2: Float, val z2: Float) {
        val length: Float = hypot(x2 - x1, z2 - z1)
        val dirX: Float = (x2 - x1) / length
        val dirZ: Float = (z2 - z1) / length
    }

    /** A street light. (dirX, dirZ) points from the pole toward the road. */
    class Lamp(val x: Float, val z: Float, val dirX: Int, val dirZ: Int)

    private class Plan(val type: Int, val halfWidth: Float, val halfDepth: Float)

    val houses: List<House> = buildHouses()
    val trees: List<Tree> = buildTrees(houses)
    val walkways: List<Walkway> = buildWalkways()
    val lamps: List<Lamp> = buildLamps()

    fun nearCrossRoad(z: Float, margin: Float): Boolean =
        crossRoadsZ.any { abs(z - it) < CROSS_HALF + margin }

    private fun plan(rnd: Random, townCentre: Boolean): Plan {
        val roll = rnd.nextInt(10)
        val type = if (townCentre) {
            when (roll) {
                in 0..3 -> TYPE_SHOP
                in 4..6 -> TYPE_TOWER
                in 7..8 -> TYPE_HOUSE
                else -> TYPE_BUNGALOW
            }
        } else {
            when (roll) {
                in 0..3 -> TYPE_BUNGALOW
                in 4..7 -> TYPE_HOUSE
                8 -> TYPE_SHOP
                else -> TYPE_TOWER
            }
        }
        return when (type) {
            TYPE_SHOP -> Plan(type, 7f + rnd.nextFloat() * 2f, 5f + rnd.nextFloat())
            TYPE_TOWER -> Plan(type, 6.5f + rnd.nextFloat() * 1.5f, 6.5f + rnd.nextFloat() * 1.5f)
            TYPE_HOUSE -> Plan(type, 5.5f + rnd.nextFloat() * 2f, 5.5f + rnd.nextFloat() * 1.5f)
            else -> Plan(type, 5f + rnd.nextFloat() * 1.5f, 5f + rnd.nextFloat() * 1.5f)
        }
    }

    private fun makeHouse(
        rnd: Random, plan: Plan, cx: Float, cz: Float, hw: Float, hd: Float, fx: Int, fz: Int
    ): House {
        val height = when (plan.type) {
            TYPE_BUNGALOW -> 3.6f
            TYPE_HOUSE -> 6.8f
            TYPE_SHOP -> 4.4f
            else -> 12f + 3f * rnd.nextInt(3)
        }
        return House(
            cx, cz, hw, hd, height, fx, fz, plan.type,
            rnd.nextInt(WALL_COLORS), rnd.nextInt(ROOF_COLORS), rnd.nextInt(ACCENT_COLORS)
        )
    }

    private fun buildHouses(): List<House> {
        val rnd = Random(11)
        val list = ArrayList<House>()

        // Along the main road, both sides, facing it. Skip the cross-road crossings.
        for (side in intArrayOf(-1, 1)) {
            var z = -270f
            while (z < 265f) {
                val centre = crossRoadsZ.minOf { abs(z - it) } < 70f
                val plan = plan(rnd, centre)
                val hw = plan.halfDepth      // away from the road (X)
                val hd = plan.halfWidth      // along the road (Z)
                val zc = z + hd
                if (!nearCrossRoad(zc, hd + 3f)) {
                    list.add(makeHouse(rnd, plan, side * (MAIN_SETBACK + hw), zc, hw, hd, -side, 0))
                }
                z += hd * 2f + 5f + rnd.nextFloat() * 8f
            }
        }

        // Along each cross road, both sides, west part and east part (not near the main road).
        for (czr in crossRoadsZ) {
            for (side in intArrayOf(-1, 1)) {
                for (range in arrayOf(-270f to -38f, 38f to 135f)) {
                    val (xStart, xEnd) = range
                    var x = xStart
                    while (x < xEnd) {
                        val plan = plan(rnd, abs(x) < 100f)
                        val hw = plan.halfWidth      // along the road (X)
                        val hd = plan.halfDepth      // away from the road (Z)
                        val xc = x + hw
                        if (xc + hw <= xEnd) {
                            list.add(makeHouse(rnd, plan, xc, czr + side * (CROSS_SETBACK + hd), hw, hd, 0, -side))
                        }
                        x += hw * 2f + 5f + rnd.nextFloat() * 8f
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
            val kind = if (rnd.nextInt(10) < 4) 1 else 0
            // Most round trees are green; some are purple, flame-red or pink blossom.
            val leaf = if (rnd.nextInt(10) < 6) rnd.nextInt(2) else 2 + rnd.nextInt(3)
            if (abs(x) < 10f) continue
            if (nearCrossRoad(z, 8f)) continue
            if (houses.any {
                    x > it.cx - it.hw - 3f && x < it.cx + it.hw + 3f &&
                        z > it.cz - it.hd - 3f && z < it.cz + it.hd + 3f
                }
            ) continue
            list.add(Tree(x, z, scale, kind, leaf))
        }
        return list
    }

    /** The stretches of main road between the cross roads, as (zStart, zEnd) pairs. */
    private fun mainStretches(): List<Pair<Float, Float>> {
        val out = ArrayList<Pair<Float, Float>>()
        var start = -295f
        for (c in crossRoadsZ.sorted()) {
            out.add(start to (c - CROSS_HALF - 1f))
            start = c + CROSS_HALF + 1f
        }
        out.add(start to 295f)
        return out
    }

    private fun buildWalkways(): List<Walkway> {
        val list = ArrayList<Walkway>()
        for ((zStart, zEnd) in mainStretches()) {
            list.add(Walkway(-7.5f, zStart, -7.5f, zEnd))
            list.add(Walkway(7.5f, zStart, 7.5f, zEnd))
        }
        for (czr in crossRoadsZ) {
            for (side in intArrayOf(-1, 1)) {
                val z = czr + side * 6.5f
                list.add(Walkway(CROSS_MIN_X + 5f, z, -9f, z))
                list.add(Walkway(9f, z, CROSS_MAX_X - 3f, z))
            }
        }
        return list
    }

    private fun buildLamps(): List<Lamp> {
        val list = ArrayList<Lamp>()
        for (side in intArrayOf(-1, 1)) {
            var z = if (side > 0) -276f else -252f
            while (z < 280f) {
                if (!nearCrossRoad(z, 8f)) list.add(Lamp(side * 8.7f, z, -side, 0))
                z += 48f
            }
        }
        for (czr in crossRoadsZ) {
            for (side in intArrayOf(-1, 1)) {
                var x = if (side > 0) -270f else -246f
                while (x < 140f) {
                    if (abs(x) > 14f) list.add(Lamp(x, czr + side * 7.6f, 0, -side))
                    x += 48f
                }
            }
        }
        return list
    }
}
