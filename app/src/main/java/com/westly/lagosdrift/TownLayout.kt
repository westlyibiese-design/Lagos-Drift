package com.westly.lagosdrift

import kotlin.math.floor
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Where everything is in the big world: roads, pavements, potholes, street lights, houses,
 * shops, towers, market stalls and trees. The world is 1500 x 1600 metres, cut into 100 m
 * chunks so the game only draws, collides with and walks people around what is near the car.
 *
 * North is -Z. Ikoyi is the south half (z > 0) and Ikorodu the north half (z < 0). The beach
 * and lagoon are on the east side. The layout is fixed (same seeds every run).
 */
object TownLayout {
    const val MIN_X = -800f
    const val MAX_X = 700f
    const val MIN_Z = -800f
    const val MAX_Z = 800f

    /** The car stops at these edges. */
    const val DRIVE_MIN_X = -755f
    const val DRIVE_MAX_X = 635f
    const val DRIVE_MIN_Z = -755f
    const val DRIVE_MAX_Z = 755f

    const val BEACH_X0 = 640f
    const val BEACH_X1 = 670f

    const val CHUNK = 100f
    const val CHUNKS_X = 15
    const val CHUNKS_Z = 16

    // Road surfaces. A bigger number is a better road.
    const val SURFACE_GROUND = -1
    const val SURFACE_DIRT = 0
    const val SURFACE_POTHOLED = 1
    const val SURFACE_WORN = 2
    const val SURFACE_SMOOTH = 3

    const val TYPE_BUNGALOW = 0
    const val TYPE_HOUSE = 1
    const val TYPE_SHOP = 2
    const val TYPE_TOWER = 3
    const val TYPE_STALL = 4

    private const val ROAD_X_MIN = -760f
    private const val ROAD_X_MAX = 640f
    private const val ROAD_Z_MIN = -760f
    private const val ROAD_Z_MAX = 760f

    /**
     * A straight road. For a vertical road (running north-south) centre is its x and start/end are
     * z values; for a horizontal road centre is its z and start/end are x values.
     */
    class Road(
        val vertical: Boolean, val centre: Float, val half: Float,
        val start: Float, val end: Float, val surface: Int
    ) {
        val x0: Float = if (vertical) centre - half else start
        val x1: Float = if (vertical) centre + half else end
        val z0: Float = if (vertical) start else centre - half
        val z1: Float = if (vertical) end else centre + half
        val length: Float get() = end - start
    }

    /**
     * A building is a box. (facingX, facingZ) points from it toward its road.
     * hw and hd are half sizes along X and Z. A market stall is a small building too.
     */
    class House(
        val cx: Float, val cz: Float, val hw: Float, val hd: Float, val height: Float,
        val facingX: Int, val facingZ: Int, val type: Int,
        val wallColor: Int, val roofColor: Int, val accent: Int, val district: Int
    )

    /** kind: 0 = round-crowned tree, 1 = palm. */
    class Tree(val x: Float, val z: Float, val scale: Float, val kind: Int, val leafColor: Int)

    /** A straight line that pedestrians walk along. paved = a real pavement is drawn there. */
    class Walkway(val x1: Float, val z1: Float, val x2: Float, val z2: Float, val paved: Boolean) {
        val length: Float = hypot(x2 - x1, z2 - z1)
        val dirX: Float = (x2 - x1) / length
        val dirZ: Float = (z2 - z1) / length
    }

    /** A street light. (dirX, dirZ) points from the pole toward the road. */
    class Lamp(val x: Float, val z: Float, val dirX: Int, val dirZ: Int)

    /** A hole in the road. wet = full of dirty water. */
    class Pothole(val x: Float, val z: Float, val r: Float, val wet: Boolean)

    /** Everything inside one 100 m square of the world. */
    class Chunk(val cx: Int, val cz: Int) {
        val minX: Float = MIN_X + cx * CHUNK
        val minZ: Float = MIN_Z + cz * CHUNK
        val district: Int = Districts.at(minZ + CHUNK / 2f)
        val roads = ArrayList<Road>()
        val houses = ArrayList<House>()
        val trees = ArrayList<Tree>()
        val lamps = ArrayList<Lamp>()
        val walkways = ArrayList<Walkway>()
        val potholes = ArrayList<Pothole>()
    }

    private class Plan(val type: Int, val halfWidth: Float, val halfDepth: Float, val height: Float)

    // The order of these lines matters: each list uses the ones above it.
    val roads: List<Road> = buildRoads()
    private val houseGrid = HashMap<Int, ArrayList<House>>()
    val houses: List<House> = buildHouses()
    val trees: List<Tree> = buildTrees()
    val lamps: List<Lamp> = buildLamps()
    val walkways: List<Walkway> = buildWalkways()
    val potholes: List<Pothole> = buildPotholes()
    val chunks: Array<Chunk> = Array(CHUNKS_X * CHUNKS_Z) { Chunk(it % CHUNKS_X, it / CHUNKS_X) }

    init {
        fillChunks()
    }

    fun cxOf(x: Float): Int = floor((x - MIN_X) / CHUNK).toInt()
    fun czOf(z: Float): Int = floor((z - MIN_Z) / CHUNK).toInt()

    fun chunkAt(cx: Int, cz: Int): Chunk? =
        if (cx < 0 || cz < 0 || cx >= CHUNKS_X || cz >= CHUNKS_Z) null else chunks[cz * CHUNKS_X + cx]

    // ---------------------------------------------------------------- roads

    private fun buildRoads(): List<Road> {
        val list = ArrayList<Road>()

        // Ikoyi: a tidy grid of wide smooth roads. The main road is x = 0.
        for (x in floatArrayOf(-600f, -450f, -300f, -150f, 0f, 150f, 300f, 450f, 600f)) {
            val half = if (x == 0f) 8f else if (x % 300f == 0f) 6f else 5f
            list.add(Road(true, x, half, 0f, ROAD_Z_MAX, SURFACE_SMOOTH))
        }
        for (z in floatArrayOf(40f, 190f, 340f, 490f, 640f)) {
            val half = if (z == 340f) 7f else 5f
            list.add(Road(false, z, half, ROAD_X_MIN, ROAD_X_MAX, SURFACE_SMOOTH))
        }

        // The border road between the two districts: already worn.
        list.add(Road(false, 0f, 7f, ROAD_X_MIN, ROAD_X_MAX, SURFACE_WORN))

        // Ikorodu: the main road carries on north but is broken up.
        list.add(Road(true, 0f, 6f, ROAD_Z_MIN, 0f, SURFACE_POTHOLED))
        val rnd = Random(404)
        var x = -715f
        var i = 0
        while (x < 600f) {
            if (kotlin.math.abs(x) > 45f) {
                if (i % 3 == 2) list.add(Road(true, x, 3.5f, ROAD_Z_MIN, 0f, SURFACE_DIRT))
                else list.add(Road(true, x, 4.5f, ROAD_Z_MIN, 0f, SURFACE_POTHOLED))
            }
            x += 100f + rnd.nextFloat() * 70f
            i++
        }
        var z = -95f
        i = 0
        while (z > -740f) {
            when {
                i % 4 == 1 -> list.add(Road(false, z, 6f, ROAD_X_MIN, ROAD_X_MAX, SURFACE_WORN))
                i % 3 == 2 -> list.add(Road(false, z, 3.5f, ROAD_X_MIN, ROAD_X_MAX, SURFACE_DIRT))
                else -> list.add(Road(false, z, 4.5f, ROAD_X_MIN, ROAD_X_MAX, SURFACE_POTHOLED))
            }
            z -= 105f + rnd.nextFloat() * 55f
            i++
        }
        return list
    }

    /** True if a point is on a road other than [ignore], with a margin around the road. */
    private fun onOtherRoad(x: Float, z: Float, ignore: Road?, margin: Float): Boolean {
        for (r in roads) {
            if (r === ignore) continue
            if (x > r.x0 - margin && x < r.x1 + margin && z > r.z0 - margin && z < r.z1 + margin) return true
        }
        return false
    }

    // ---------------------------------------------------------------- houses

    private fun gridKey(gx: Int, gz: Int): Int = gx * 100 + gz

    private fun addToGrid(h: House) {
        val key = gridKey(cxOf(h.cx), czOf(h.cz))
        houseGrid.getOrPut(key) { ArrayList() }.add(h)
    }

    private fun hitsHouse(cx: Float, cz: Float, hw: Float, hd: Float, margin: Float): Boolean {
        val gx = cxOf(cx)
        val gz = czOf(cz)
        for (dx in -1..1) {
            for (dz in -1..1) {
                val list = houseGrid[gridKey(gx + dx, gz + dz)] ?: continue
                for (o in list) {
                    if (cx + hw + margin > o.cx - o.hw && cx - hw - margin < o.cx + o.hw &&
                        cz + hd + margin > o.cz - o.hd && cz - hd - margin < o.cz + o.hd
                    ) return true
                }
            }
        }
        return false
    }

    private fun fits(
        cx: Float, cz: Float, hw: Float, hd: Float, roadMargin: Float, houseMargin: Float
    ): Boolean {
        if (cx - hw < MIN_X + 30f || cx + hw > BEACH_X0 - 6f) return false
        if (cz - hd < MIN_Z + 30f || cz + hd > MAX_Z - 30f) return false
        for (r in roads) {
            if (cx + hw + roadMargin > r.x0 && cx - hw - roadMargin < r.x1 &&
                cz + hd + roadMargin > r.z0 && cz - hd - roadMargin < r.z1
            ) return false
        }
        return !hitsHouse(cx, cz, hw, hd, houseMargin)
    }

    private fun plan(rnd: Random, district: Int): Plan {
        val roll = rnd.nextInt(100)
        return if (district == Districts.IKOYI) {
            when {
                roll < 26 -> Plan(
                    TYPE_TOWER, 8f + rnd.nextFloat() * 3f, 8f + rnd.nextFloat() * 3f,
                    20f + 4f * rnd.nextInt(7)
                )
                roll < 66 -> Plan(TYPE_HOUSE, 7f + rnd.nextFloat() * 3f, 6.5f + rnd.nextFloat() * 2.5f, 7.4f)
                roll < 76 -> Plan(TYPE_SHOP, 8f + rnd.nextFloat() * 2f, 6f + rnd.nextFloat(), 5f)
                else -> Plan(TYPE_BUNGALOW, 6f + rnd.nextFloat() * 2f, 6f + rnd.nextFloat() * 2f, 4f)
            }
        } else {
            when {
                roll < 45 -> Plan(TYPE_BUNGALOW, 5f + rnd.nextFloat() * 4f, 4f + rnd.nextFloat() * 1.5f, 3.4f)
                roll < 65 -> Plan(TYPE_HOUSE, 5f + rnd.nextFloat() * 2.5f, 4.5f + rnd.nextFloat() * 1.5f, 6.8f)
                roll < 98 -> Plan(TYPE_SHOP, 3.5f + rnd.nextFloat() * 2f, 3.5f + rnd.nextFloat(), 3.8f)
                else -> Plan(TYPE_TOWER, 5.5f + rnd.nextFloat(), 5.5f + rnd.nextFloat(), 12f)
            }
        }
    }

    private fun makeHouse(
        rnd: Random, plan: Plan, district: Int, cx: Float, cz: Float, hw: Float, hd: Float, fx: Int, fz: Int
    ): House {
        val wall = if (district == Districts.IKOYI) {
            if (plan.type == TYPE_TOWER) (if (rnd.nextBoolean()) 3 else 5)
            else intArrayOf(0, 0, 1, 1, 2, 4)[rnd.nextInt(6)]
        } else {
            rnd.nextInt(10)
        }
        return House(
            cx, cz, hw, hd, plan.height, fx, fz, plan.type,
            wall, rnd.nextInt(4), rnd.nextInt(6), district
        )
    }

    private fun buildHouses(): List<House> {
        val rnd = Random(11)
        val list = ArrayList<House>()

        for (road in roads) {
            for (side in intArrayOf(-1, 1)) {
                var t = road.start + 14f
                while (t < road.end - 14f) {
                    val probeZ = if (road.vertical) t else road.centre
                    val district = Districts.at(probeZ)
                    val plan = plan(rnd, district)
                    val along = plan.halfWidth
                    val tc = t + along
                    val setback = if (district == Districts.IKOYI) 11f + rnd.nextFloat() * 4f
                    else 6f + rnd.nextFloat() * 5f
                    val offset = road.half + setback + plan.halfDepth

                    val cx: Float
                    val cz: Float
                    val hw: Float
                    val hd: Float
                    val fx: Int
                    val fz: Int
                    if (road.vertical) {
                        cx = road.centre + side * offset
                        cz = tc
                        hw = plan.halfDepth
                        hd = along
                        fx = -side
                        fz = 0
                    } else {
                        cx = tc
                        cz = road.centre + side * offset
                        hw = along
                        hd = plan.halfDepth
                        fx = 0
                        fz = -side
                    }

                    t += along * 2f + if (district == Districts.IKOYI) 6f + rnd.nextFloat() * 8f
                    else 2f + rnd.nextFloat() * 6f

                    // Some Ikorodu plots are left empty.
                    if (district == Districts.IKORODU && rnd.nextInt(100) < 15) continue
                    if (!fits(cx, cz, hw, hd, 3f, 2f)) continue

                    val house = makeHouse(rnd, plan, district, cx, cz, hw, hd, fx, fz)
                    list.add(house)
                    addToGrid(house)
                }
            }
        }

        // Market stalls beside the broken roads of Ikorodu.
        for (road in roads) {
            if (road.surface == SURFACE_SMOOTH) continue
            for (side in intArrayOf(-1, 1)) {
                var t = road.start + 20f + rnd.nextFloat() * 20f
                while (t < road.end - 20f) {
                    val tt = t
                    val probeZ = if (road.vertical) tt else road.centre
                    t += 26f + rnd.nextFloat() * 40f
                    if (Districts.at(probeZ) != Districts.IKORODU) continue
                    if (rnd.nextInt(100) >= 55) continue

                    val offset = road.half + 2.3f
                    val cx: Float
                    val cz: Float
                    val hw: Float
                    val hd: Float
                    val fx: Int
                    val fz: Int
                    if (road.vertical) {
                        cx = road.centre + side * offset; cz = tt; hw = 1.0f; hd = 1.7f; fx = -side; fz = 0
                    } else {
                        cx = tt; cz = road.centre + side * offset; hw = 1.7f; hd = 1.0f; fx = 0; fz = -side
                    }
                    if (!fits(cx, cz, hw, hd, 0.6f, 0.8f)) continue
                    val stall = House(
                        cx, cz, hw, hd, 2.3f, fx, fz, TYPE_STALL,
                        rnd.nextInt(10), rnd.nextInt(4), rnd.nextInt(6), Districts.IKORODU
                    )
                    list.add(stall)
                    addToGrid(stall)
                }
            }
        }
        return list
    }

    // ---------------------------------------------------------------- trees

    private fun treeBlocked(x: Float, z: Float, margin: Float): Boolean {
        if (onOtherRoad(x, z, null, margin)) return true
        return hitsHouse(x, z, 0.2f, 0.2f, margin)
    }

    private fun buildTrees(): List<Tree> {
        val rnd = Random(23)
        val list = ArrayList<Tree>()

        // Trees along the roads: tidy rows of palms in Ikoyi, a few here and there in Ikorodu.
        for (road in roads) {
            if (road.surface == SURFACE_DIRT) continue
            for (side in intArrayOf(-1, 1)) {
                var t = road.start + 10f + rnd.nextFloat() * 10f
                while (t < road.end - 10f) {
                    val probeZ = if (road.vertical) t else road.centre
                    val ikoyi = Districts.at(probeZ) == Districts.IKOYI
                    val lateral = road.half + if (ikoyi) 4.6f else 3.2f
                    val x = if (road.vertical) road.centre + side * lateral else t
                    val z = if (road.vertical) t else road.centre + side * lateral
                    t += if (ikoyi) 17f + rnd.nextFloat() * 7f else 40f + rnd.nextFloat() * 50f

                    if (!ikoyi && rnd.nextInt(100) < 45) continue
                    if (treeBlocked(x, z, 1.5f)) continue
                    val kind = if (ikoyi) (if (rnd.nextInt(10) < 7) 1 else 0) else (if (rnd.nextInt(10) < 2) 1 else 0)
                    val leaf = if (ikoyi) rnd.nextInt(2)
                    else if (rnd.nextInt(10) < 6) rnd.nextInt(2) else 2 + rnd.nextInt(3)
                    list.add(Tree(x, z, 0.8f + rnd.nextFloat() * 0.7f, kind, leaf))
                }
            }
        }

        // Trees standing in the open.
        var added = 0
        var attempts = 0
        while (added < 300 && attempts < 8000) {
            attempts++
            val x = MIN_X + 20f + rnd.nextFloat() * (BEACH_X0 - 10f - MIN_X - 20f)
            val z = MIN_Z + 20f + rnd.nextFloat() * (MAX_Z - MIN_Z - 40f)
            val ikoyi = Districts.at(z) == Districts.IKOYI
            if (!ikoyi && rnd.nextInt(10) < 5) continue
            if (treeBlocked(x, z, 3f)) continue
            val scale = 0.8f + rnd.nextFloat() * 0.7f
            val kind = if (rnd.nextInt(10) < 4) 1 else 0
            val leaf = if (rnd.nextInt(10) < 6) rnd.nextInt(2) else 2 + rnd.nextInt(3)
            list.add(Tree(x, z, scale, kind, leaf))
            added++
        }
        return list
    }

    // ---------------------------------------------------------------- lamps

    private fun buildLamps(): List<Lamp> {
        val list = ArrayList<Lamp>()
        for (road in roads) {
            if (road.surface == SURFACE_DIRT) continue
            for (side in intArrayOf(-1, 1)) {
                var t = road.start + 20f + if (side > 0) 0f else 24f
                while (t < road.end - 10f) {
                    val probeZ = if (road.vertical) t else road.centre
                    val ikoyi = Districts.at(probeZ) == Districts.IKOYI
                    val step = if (ikoyi) 48f else 110f
                    val lateral = road.half + 2.7f
                    val x = if (road.vertical) road.centre + side * lateral else t
                    val z = if (road.vertical) t else road.centre + side * lateral
                    t += step
                    // Only one side of the road has lights in Ikorodu.
                    if (!ikoyi && side < 0) continue
                    if (onOtherRoad(x, z, road, 3.5f)) continue
                    if (road.vertical) list.add(Lamp(x, z, -side, 0)) else list.add(Lamp(x, z, 0, -side))
                }
            }
        }
        return list
    }

    // ---------------------------------------------------------------- walkways

    private fun buildWalkways(): List<Walkway> {
        val list = ArrayList<Walkway>()
        for (road in roads) {
            val cuts = roads.filter {
                it.vertical != road.vertical &&
                    road.centre >= it.start && road.centre <= it.end &&
                    it.centre > road.start && it.centre < road.end
            }.sortedBy { it.centre }
            val paved = road.surface >= SURFACE_WORN
            val lateral = road.half + if (paved) 1.5f else 0.9f

            for (side in intArrayOf(-1, 1)) {
                var cursor = road.start + 6f
                val ends = ArrayList<Pair<Float, Float>>()
                for (cut in cuts) {
                    val segEnd = cut.centre - cut.half - 1.5f
                    if (segEnd - cursor > 6f) ends.add(cursor to segEnd)
                    cursor = cut.centre + cut.half + 1.5f
                }
                if (road.end - 6f - cursor > 6f) ends.add(cursor to road.end - 6f)

                for ((a, b) in ends) {
                    if (road.vertical) {
                        val x = road.centre + side * lateral
                        list.add(Walkway(x, a, x, b, paved))
                    } else {
                        val z = road.centre + side * lateral
                        list.add(Walkway(a, z, b, z, paved))
                    }
                }
            }
        }
        return list
    }

    // ---------------------------------------------------------------- potholes

    private fun buildPotholes(): List<Pothole> {
        val rnd = Random(909)
        val list = ArrayList<Pothole>()
        for (road in roads) {
            val perMetre = when (road.surface) {
                SURFACE_WORN -> 1f / 60f
                SURFACE_POTHOLED -> 1f / 22f
                SURFACE_DIRT -> 1f / 14f
                else -> 0f
            }
            if (perMetre == 0f) continue
            val count = (road.length * perMetre).toInt()
            repeat(count) {
                val along = road.start + 8f + rnd.nextFloat() * (road.length - 16f)
                val across = (rnd.nextFloat() * 2f - 1f) * (road.half - 0.9f)
                val r = if (road.surface == SURFACE_DIRT) 0.4f + rnd.nextFloat() * 0.8f
                else 0.5f + rnd.nextFloat() * 1.1f
                val x = if (road.vertical) road.centre + across else along
                val z = if (road.vertical) along else road.centre + across
                list.add(Pothole(x, z, r, rnd.nextInt(100) < 35))
            }
        }
        return list
    }

    // ---------------------------------------------------------------- chunks

    private fun fillChunks() {
        for (r in roads) {
            val cx0 = cxOf(r.x0).coerceIn(0, CHUNKS_X - 1)
            val cx1 = cxOf(r.x1).coerceIn(0, CHUNKS_X - 1)
            val cz0 = czOf(r.z0).coerceIn(0, CHUNKS_Z - 1)
            val cz1 = czOf(r.z1).coerceIn(0, CHUNKS_Z - 1)
            for (cz in cz0..cz1) for (cx in cx0..cx1) chunks[cz * CHUNKS_X + cx].roads.add(r)
        }
        for (h in houses) chunkOf(h.cx, h.cz)?.houses?.add(h)
        for (t in trees) chunkOf(t.x, t.z)?.trees?.add(t)
        for (l in lamps) chunkOf(l.x, l.z)?.lamps?.add(l)
        for (p in potholes) chunkOf(p.x, p.z)?.potholes?.add(p)
        for (w in walkways) {
            val cx0 = cxOf(minOf(w.x1, w.x2) - 2f).coerceIn(0, CHUNKS_X - 1)
            val cx1 = cxOf(maxOf(w.x1, w.x2) + 2f).coerceIn(0, CHUNKS_X - 1)
            val cz0 = czOf(minOf(w.z1, w.z2) - 2f).coerceIn(0, CHUNKS_Z - 1)
            val cz1 = czOf(maxOf(w.z1, w.z2) + 2f).coerceIn(0, CHUNKS_Z - 1)
            for (cz in cz0..cz1) for (cx in cx0..cx1) chunks[cz * CHUNKS_X + cx].walkways.add(w)
        }
    }

    private fun chunkOf(x: Float, z: Float): Chunk? = chunkAt(cxOf(x), czOf(z))
}
