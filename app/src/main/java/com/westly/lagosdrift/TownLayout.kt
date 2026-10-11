package com.westly.lagosdrift

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.random.Random

/**
 * Where everything is in the big world: roads, pavements, potholes, street lights, houses,
 * shops, towers, market stalls and trees. The world is 1500 x 1600 metres, cut into 100 m
 * chunks so the game only draws, collides with and walks people around what is near the car.
 *
 * North is -Z. Ikoyi is the south half (z > 0) and Ikorodu the north half (z < 0). The beach
 * and lagoon are on the east side. The layout is fixed (same seeds every run).
 *
 * There are three worlds in the same 1500 x 1600 m frame (see Districts): Lagos, Neribo City and
 * Neribo Desert. [select] switches which one the rest of the game sees. Each world is built once,
 * the first time it is chosen, and kept, so going back to a world gives the very same objects.
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
    const val TYPE_VILLA = 5     // the player's house
    const val TYPE_GARAGE = 6    // the player's garage
    const val TYPE_POOL = 7      // a swimming pool or fountain: a low blue slab

    // The player's house in Neribo City, and where the car waits on the driveway (it faces east).
    const val HOME_X = 512f
    const val HOME_Z = -229f
    const val HOME_CAR_X = 487f
    const val HOME_CAR_Z = -212f
    const val HOME_CAR_YAW = -90f
    private const val HOME_BLOCK_X0 = 455f
    private const val HOME_BLOCK_Z0 = -295f
    private const val HOME_BLOCK_X1 = 595f
    private const val HOME_BLOCK_Z1 = -155f

    // Where the car starts in the desert: on the old highway, just south of the crossroads, facing north.
    const val DESERT_CAR_X = 3f
    const val DESERT_CAR_Z = 60f
    const val DESERT_CAR_YAW = 0f

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

    /** kind: 0 = round-crowned tree, 1 = palm, 2 = cactus, 3 = dry bush, 4 = rock. */
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

    // What the current world holds. These change when [select] is called. While a world is being
    // built the order matters: each list uses the ones before it.
    var roads: List<Road> = emptyList()
        private set
    var houses: List<House> = emptyList()
        private set
    var trees: List<Tree> = emptyList()
        private set
    var lamps: List<Lamp> = emptyList()
        private set
    var walkways: List<Walkway> = emptyList()
        private set
    var potholes: List<Pothole> = emptyList()
        private set
    var chunks: Array<Chunk> = arrayOf()
        private set

    /** Which world is selected (Districts.WORLD_LAGOS, WORLD_CITY or WORLD_DESERT). */
    var world: Int = Districts.WORLD_LAGOS
        private set

    private val houseGrid = HashMap<Int, ArrayList<House>>()

    /** Rectangles (x0, z0, x1, z1) where no ordinary building or random tree may go. */
    private var reserved: List<FloatArray> = emptyList()

    private class Snapshot(
        val roads: List<Road>, val houses: List<House>, val trees: List<Tree>, val lamps: List<Lamp>,
        val walkways: List<Walkway>, val potholes: List<Pothole>, val chunks: Array<Chunk>
    )

    private val snapshots = arrayOfNulls<Snapshot>(Districts.WORLD_COUNT)

    init {
        select(Districts.WORLD_LAGOS)
    }

    /** Makes [target] the world everything else reads from, building it the first time. */
    fun select(target: Int) {
        val w = target.coerceIn(0, Districts.WORLD_COUNT - 1)
        val saved = snapshots[w]
        if (saved == null) {
            generate(w)
            snapshots[w] = Snapshot(roads, houses, trees, lamps, walkways, potholes, chunks)
        } else {
            roads = saved.roads
            houses = saved.houses
            trees = saved.trees
            lamps = saved.lamps
            walkways = saved.walkways
            potholes = saved.potholes
            chunks = saved.chunks
        }
        world = w
        Districts.world = w
    }

    private fun generate(w: Int) {
        Districts.world = w
        houseGrid.clear()
        reserved = emptyList()
        when (w) {
            Districts.WORLD_CITY -> generateCity()
            Districts.WORLD_DESERT -> generateDesert()
            else -> generateLagos()
        }
        chunks = Array(CHUNKS_X * CHUNKS_Z) { Chunk(it % CHUNKS_X, it / CHUNKS_X) }
        fillChunks()
    }

    private fun generateLagos() {
        roads = buildRoads()
        houses = buildHouses()
        trees = buildTrees()
        lamps = buildLamps()
        walkways = buildWalkways()
        potholes = buildPotholes()
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
        for (z in reserved) {
            if (cx + hw > z[0] && cx - hw < z[2] && cz + hd > z[1] && cz - hd < z[3]) return false
        }
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
        val wall = if (district == Districts.CITY) {
            // Neribo City palette: the first four are towers and offices, the rest are homes.
            if (plan.type == TYPE_TOWER) rnd.nextInt(4) else 4 + rnd.nextInt(4)
        } else if (district == Districts.IKOYI) {
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
        for (r in reserved) {
            if (x > r[0] - margin && x < r[2] + margin && z > r[1] - margin && z < r[3] + margin) return true
        }
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
                    val ikoyi = Districts.isModern(Districts.at(probeZ))
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
            val ikoyi = Districts.isModern(Districts.at(z))
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
                    val ikoyi = Districts.isModern(Districts.at(probeZ))
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

    private fun buildPotholes(density: Float = 1f): List<Pothole> {
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
            val count = (road.length * perMetre * density).toInt()
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

    // ---------------------------------------------------------------- shared by the new worlds

    /**
     * Puts one building beside a road, if there is room, and adds it to the grid used for overlap
     * checks. [t] is where along the road the plot starts. Returns null when it does not fit.
     */
    private fun roadsideHouse(
        rnd: Random, road: Road, side: Int, t: Float, plan: Plan, setback: Float, district: Int
    ): House? {
        val along = plan.halfWidth
        val tc = t + along
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
        if (!fits(cx, cz, hw, hd, 3f, 2f)) return null
        val house = makeHouse(rnd, plan, district, cx, cz, hw, hd, fx, fz)
        addToGrid(house)
        return house
    }

    // ---------------------------------------------------------------- Neribo City

    /** A tidy grid of wide smooth roads. The two main boulevards cross at the middle (x = 0, z = 0). */
    private fun buildCityRoads(): List<Road> {
        val list = ArrayList<Road>()
        val lines = floatArrayOf(-600f, -450f, -300f, -150f, 0f, 150f, 300f, 450f, 600f)
        for (x in lines) {
            val half = if (x == 0f) 8f else if (abs(x) == 300f) 6f else 5f
            list.add(Road(true, x, half, ROAD_Z_MIN, ROAD_Z_MAX, SURFACE_SMOOTH))
        }
        for (z in lines) {
            val half = if (z == 0f) 8f else if (abs(z) == 300f) 6f else 5f
            list.add(Road(false, z, half, ROAD_X_MIN, ROAD_X_MAX, SURFACE_SMOOTH))
        }
        return list
    }

    /** Towers in the middle, then shops and flats, then houses and bungalows toward the edge. */
    private fun cityPlan(rnd: Random, x: Float, z: Float): Plan {
        val d = max(abs(x), abs(z))
        val roll = rnd.nextInt(100)
        if (d < 330f) {
            return when {
                roll < 60 -> Plan(
                    TYPE_TOWER, 8f + rnd.nextFloat() * 4f, 8f + rnd.nextFloat() * 4f, 24f + 4f * rnd.nextInt(6)
                )
                roll < 80 -> Plan(TYPE_SHOP, 8f + rnd.nextFloat() * 2f, 6f + rnd.nextFloat(), 6f)
                else -> Plan(TYPE_HOUSE, 7f + rnd.nextFloat() * 3f, 6.5f + rnd.nextFloat() * 2.5f, 7.4f)
            }
        }
        if (d < 480f) {
            return when {
                roll < 20 -> Plan(
                    TYPE_TOWER, 7.5f + rnd.nextFloat() * 3f, 7.5f + rnd.nextFloat() * 3f, 18f + 4f * rnd.nextInt(4)
                )
                roll < 45 -> Plan(TYPE_SHOP, 8f + rnd.nextFloat() * 2f, 6f + rnd.nextFloat(), 5f)
                else -> Plan(TYPE_HOUSE, 7f + rnd.nextFloat() * 3f, 6.5f + rnd.nextFloat() * 2.5f, 7.4f)
            }
        }
        return if (roll < 55) {
            Plan(TYPE_HOUSE, 7f + rnd.nextFloat() * 3f, 6.5f + rnd.nextFloat() * 2.5f, 7.4f)
        } else {
            Plan(TYPE_BUNGALOW, 6f + rnd.nextFloat() * 2f, 6f + rnd.nextFloat() * 2f, 4f)
        }
    }

    private fun buildCityHouses(): List<House> {
        val rnd = Random(31)
        val list = ArrayList<House>()
        for (road in roads) {
            for (side in intArrayOf(-1, 1)) {
                var t = road.start + 14f
                while (t < road.end - 14f) {
                    val probeX = if (road.vertical) road.centre else t
                    val probeZ = if (road.vertical) t else road.centre
                    val plan = cityPlan(rnd, probeX, probeZ)
                    val core = max(abs(probeX), abs(probeZ)) < 330f
                    val setback = if (core) 6f + rnd.nextFloat() * 4f else 11f + rnd.nextFloat() * 5f
                    val placeAt = t
                    t += plan.halfWidth * 2f + if (core) 3f + rnd.nextFloat() * 5f else 8f + rnd.nextFloat() * 10f
                    val house = roadsideHouse(rnd, road, side, placeAt, plan, setback, Districts.CITY) ?: continue
                    list.add(house)
                }
            }
        }
        return list
    }

    /** The player's house: a villa with a garage, a pool, and a driveway from the road to the garage door. */
    private fun addHome(list: ArrayList<House>) {
        val villa = House(512f, -229f, 15f, 11f, 7.6f, -1, 0, TYPE_VILLA, 0, 0, 3, Districts.CITY)
        val garage = House(506f, -212f, 9f, 6f, 3.8f, -1, 0, TYPE_GARAGE, 3, 0, 0, Districts.CITY)
        val pool = House(512f, -253f, 9f, 5f, 0.5f, 0, 1, TYPE_POOL, 0, 0, 0, Districts.CITY)
        for (h in listOf(villa, garage, pool)) {
            list.add(h)
            addToGrid(h)
        }
    }

    private fun homeTrees(): List<Tree> {
        val list = ArrayList<Tree>()
        // Palms either side of the driveway.
        val palmX = floatArrayOf(465f, 478f, 491f)
        for (x in palmX) {
            list.add(Tree(x, -221f, 1.2f, 1, 0))
            list.add(Tree(x, -203f, 1.2f, 1, 0))
        }
        // Shade trees round the garden.
        val gx = floatArrayOf(545f, 560f, 540f, 570f, 470f, 470f)
        val gz = floatArrayOf(-190f, -250f, -275f, -215f, -270f, -170f)
        for (i in gx.indices) list.add(Tree(gx[i], gz[i], 1.1f + 0.1f * (i % 3), 0, i % 2))
        return list
    }

    /** Four fountain pools and rows of trees in the park round the middle crossing. */
    private fun plazaFeatures(list: ArrayList<House>): List<Tree> {
        val result = ArrayList<Tree>()
        val spots = floatArrayOf(30f, 55f, 100f, 120f)
        var n = 0
        for (sx in intArrayOf(-1, 1)) {
            for (sz in intArrayOf(-1, 1)) {
                val fountain = House(sx * 75f, sz * 75f, 7f, 7f, 0.5f, 0, 1, TYPE_POOL, 1, 0, 0, Districts.CITY)
                list.add(fountain)
                addToGrid(fountain)
                for (a in spots) {
                    for (b in spots) {
                        n++
                        result.add(Tree(sx * a, sz * b, 0.9f + 0.1f * (n % 5), if (n % 2 == 0) 1 else 0, n % 2))
                    }
                }
            }
        }
        return result
    }

    private fun generateCity() {
        val base = buildCityRoads()
        roads = base
        // Nothing ordinary goes in the park in the middle or in the block that holds the player's house.
        reserved = listOf(
            floatArrayOf(-145f, -145f, 145f, 145f),
            floatArrayOf(HOME_BLOCK_X0, HOME_BLOCK_Z0, HOME_BLOCK_X1, HOME_BLOCK_Z1)
        )
        val list = ArrayList<House>(buildCityHouses())
        addHome(list)
        val parkTrees = plazaFeatures(list)
        houses = list
        trees = buildTrees() + homeTrees() + parkTrees
        lamps = buildLamps()
        walkways = buildWalkways()
        potholes = emptyList()
        // The driveway is added last so it gets no pavements and no street lights of its own.
        roads = base + Road(false, HOME_CAR_Z, 3f, HOME_BLOCK_X0, 497f, SURFACE_SMOOTH)
    }

    // ---------------------------------------------------------------- Neribo Desert

    private fun buildDesertRoads(): List<Road> {
        val list = ArrayList<Road>()
        // The old highway north-south, and the road across it: worn, with a few holes.
        list.add(Road(true, 0f, 6f, ROAD_Z_MIN, ROAD_Z_MAX, SURFACE_WORN))
        list.add(Road(false, 0f, 6f, ROAD_X_MIN, ROAD_X_MAX, SURFACE_WORN))
        // A square dirt track to race round.
        for (x in floatArrayOf(-420f, 420f)) list.add(Road(true, x, 4f, -420f, 420f, SURFACE_DIRT))
        for (z in floatArrayOf(-420f, 420f)) list.add(Road(false, z, 4f, -420f, 420f, SURFACE_DIRT))
        // Two rough tarmac side roads.
        list.add(Road(false, 220f, 5f, 0f, ROAD_X_MAX, SURFACE_POTHOLED))
        list.add(Road(true, -220f, 5f, ROAD_Z_MIN, 0f, SURFACE_POTHOLED))
        return list
    }

    private fun desertPlan(rnd: Random): Plan {
        val roll = rnd.nextInt(100)
        return when {
            roll < 45 -> Plan(TYPE_BUNGALOW, 5f + rnd.nextFloat() * 2f, 4.5f + rnd.nextFloat(), 3.6f)
            roll < 75 -> Plan(TYPE_HOUSE, 5.5f + rnd.nextFloat() * 1.5f, 5f + rnd.nextFloat(), 6.2f)
            roll < 95 -> Plan(TYPE_SHOP, 4.5f + rnd.nextFloat() * 1.5f, 4f + rnd.nextFloat(), 4f)
            else -> Plan(TYPE_TOWER, 3.2f, 3.2f, 13f)   // a water tower
        }
    }

    /** A small desert town where the two highways cross. */
    private fun buildDesertHouses(): List<House> {
        val rnd = Random(57)
        val list = ArrayList<House>()
        for (road in roads) {
            if (road.surface != SURFACE_WORN) continue
            for (side in intArrayOf(-1, 1)) {
                var t = -170f
                while (t < 170f) {
                    val plan = desertPlan(rnd)
                    val setback = 7f + rnd.nextFloat() * 7f
                    val placeAt = t
                    t += plan.halfWidth * 2f + 10f + rnd.nextFloat() * 18f
                    if (rnd.nextInt(100) < 25) continue
                    val house = roadsideHouse(rnd, road, side, placeAt, plan, setback, Districts.DESERT) ?: continue
                    list.add(house)
                }
            }
        }
        return list
    }

    /** Cacti, rocks and dry bushes scattered over the sand, away from roads and houses. */
    private fun buildDesertScenery(): List<Tree> {
        val rnd = Random(64)
        val list = ArrayList<Tree>()
        var cacti = 0
        var rocks = 0
        var bushes = 0
        var attempts = 0
        while ((cacti < 240 || rocks < 140 || bushes < 160) && attempts < 20000) {
            attempts++
            val x = MIN_X + 20f + rnd.nextFloat() * (BEACH_X0 - 10f - MIN_X - 20f)
            val z = MIN_Z + 20f + rnd.nextFloat() * (MAX_Z - MIN_Z - 40f)
            if (treeBlocked(x, z, 4f)) continue
            when (rnd.nextInt(3)) {
                0 -> if (cacti < 240) {
                    list.add(Tree(x, z, 0.9f + rnd.nextFloat() * 0.8f, 2, 0))
                    cacti++
                }
                1 -> if (rocks < 140) {
                    list.add(Tree(x, z, 0.8f + rnd.nextFloat() * 1.8f, 4, 0))
                    rocks++
                }
                else -> if (bushes < 160) {
                    list.add(Tree(x, z, 0.6f + rnd.nextFloat() * 0.5f, 3, 0))
                    bushes++
                }
            }
        }
        return list
    }

    private fun generateDesert() {
        roads = buildDesertRoads()
        houses = buildDesertHouses()
        trees = buildDesertScenery()
        lamps = emptyList()
        walkways = emptyList()
        potholes = buildPotholes(0.35f)
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
