package com.westly.lagosdrift

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The two houses of the player. Each stands in a walled yard on flat sand beside the road, with a gate that
 * faces the road, a garage and a bodyguard at the gate:
 *   HOME 1 - the loft (free_loft_18_mini_office), its garage holds the Silverado (the game puts the drivable
 *            truck there: see [truckX], [truckZ], [truckYaw]).
 *   HOME 2 - the minimalist glass house (interior_15), its garage holds the yellow Danfo.
 * The houses are baked models (see BakedScene); the walls, the gate and the garage are made here from boxes.
 *
 * Everything is placed in the yard's own frame: x is sideways, and z runs from the gate (z = -YARD_HALF_Z)
 * to the back wall (z = +YARD_HALF_Z), so the front of everything faces -Z like the cars. The frame is
 * turned to the world by [Home.yaw], which makes -Z point from the yard to the road.
 *
 * The ground rules (RoadGround): the floors of the house are ground, the sand inside the walls of the house,
 * the yard walls and the garage walls is not, and the doorway of the house lets the sand through again. So you
 * can walk or drive through the gate, walk into the house by its door, and the walls stop you everywhere else.
 */
class Homes(private val guardModel: Model, private val danfoModel: Model?) {
    private class Home(
        val label: String, val tag: String,
        val x: Float, val z: Float, val groundY: Float,
        val fx: Float, val fz: Float,
        /** Where the house stands in the yard (the z of the middle of its floor). */
        val houseZ: Float,
        /** The house's outline in its own frame: middle (x, z) and half sizes; the door is in the front edge. */
        val blockX: Float, val blockZ: Float, val blockHalfX: Float, val blockHalfZ: Float,
        val doorZ: Float,
        val parkedDanfo: Boolean
    ) {
        val yaw: Float = Math.toDegrees(atan2((-fx).toDouble(), (-fz).toDouble())).toFloat()
        val sinYaw: Float = sin(Math.toRadians(yaw.toDouble())).toFloat()
        val cosYaw: Float = cos(Math.toRadians(yaw.toDouble())).toFloat()
        val floorY: Float = groundY + FLOOR_RAISE

        fun worldX(lx: Float, lz: Float): Float = x + lx * cosYaw + lz * sinYaw
        fun worldZ(lx: Float, lz: Float): Float = z - lx * sinYaw + lz * cosYaw

        lateinit var house: ModelInstance
        lateinit var structure: ModelInstance
        lateinit var guard: ModelInstance
        var danfo: ModelInstance? = null
        var guardX = 0f
        var guardZ = 0f
        var guardY = 0f
        var danfoX = 0f
        var danfoZ = 0f
        var danfoY = 0f
    }

    private val homes = ArrayList<Home>()
    private val models = ArrayList<Model>()
    private val tmpMatrix = Matrix4()
    private val gate = Vector3()

    // Things that are in the way: x, z, radius (the bodyguards, and the parked Danfo as two circles).
    private val obstacles = ArrayList<FloatArray>()

    /** Where the drivable Silverado stands: in the garage of the first house, facing out. */
    var truckX = 0f
        private set
    var truckZ = 0f
        private set
    var truckYaw = 0f
        private set

    val count: Int
        get() = homes.size

    init {
        homes.add(
            Home(
                "HOME 1", "loft", -461.6f, -1162.7f, 22.82f, 0.9978f, 0.0666f,
                10f, 0f, 0f, 7.85f, 5.95f, -5.85f, false
            )
        )
        homes.add(
            Home(
                "HOME 2", "modern", -413.6f, -1048.7f, 22.37f, -0.9978f, -0.0666f,
                -0.5f, 0f, 5.96f, 15.3f, 11.5f, -5.47f, true
            )
        )
        for ((i, h) in homes.withIndex()) build(h, i == 0)
    }

    private fun build(h: Home, first: Boolean) {
        // The house.
        val scene = BakedSceneLoader.load(h.tag)
        models.add(scene.model)
        h.house = ModelInstance(scene.model)
        h.house.transform.setToRotation(Vector3.Y, h.yaw)
            .setTranslation(h.worldX(0f, h.houseZ), h.floorY, h.worldZ(0f, h.houseZ))
        registerFloors(h, scene.floors)

        // Walls, gate and garage.
        val structure = buildStructure(h)
        models.add(structure)
        h.structure = ModelInstance(structure)
        h.structure.transform.setToRotation(Vector3.Y, h.yaw).setTranslation(h.x, h.floorY, h.z)
        registerBlocks(h)

        // The bodyguard stands inside the gate, next to the pillar, facing the road.
        h.guard = ModelInstance(guardModel)
        h.guardX = h.worldX(GUARD_X, GUARD_Z)
        h.guardZ = h.worldZ(GUARD_X, GUARD_Z)
        h.guardY = h.groundY
        val pose = HumanPose(h.guard)
        pose.update(0f, 0f, false)
        placeGuard(h)
        h.guard.calculateTransforms()
        obstacles.add(floatArrayOf(h.guardX, h.guardZ, GUARD_RADIUS))

        // The garage: the Silverado (drivable, placed by the game) at the first house, the Danfo at the second.
        val gx = h.worldX(GARAGE_X, GARAGE_Z)
        val gz = h.worldZ(GARAGE_X, GARAGE_Z)
        if (first) {
            truckX = gx
            truckZ = gz
            truckYaw = h.yaw
        } else if (h.parkedDanfo && danfoModel != null) {
            val d = ModelInstance(danfoModel)
            h.danfo = d
            h.danfoX = gx
            h.danfoZ = gz
            h.danfoY = h.groundY
            placeDanfo(h)
            val fwdX = -h.sinYaw
            val fwdZ = -h.cosYaw
            obstacles.add(floatArrayOf(gx + fwdX * DANFO_OFFSET, gz + fwdZ * DANFO_OFFSET, DANFO_RADIUS))
            obstacles.add(floatArrayOf(gx - fwdX * DANFO_OFFSET, gz - fwdZ * DANFO_OFFSET, DANFO_RADIUS))
        }
    }

    private fun placeGuard(h: Home) {
        h.guard.transform.idt()
            .translate(h.guardX, h.guardY, h.guardZ)
            .rotate(Vector3.Y, h.yaw)
            .scale(PlayerModelFactory.SCALE, PlayerModelFactory.SCALE, PlayerModelFactory.SCALE)
    }

    private fun placeDanfo(h: Home) {
        h.danfo?.transform?.setToRotation(Vector3.Y, h.yaw)?.setTranslation(h.danfoX, h.danfoY, h.danfoZ)
    }

    /** Once the ground is loaded: the bodyguard and the Danfo stand on it. */
    fun settle() {
        for (h in homes) {
            val gy = RoadGround.carHeightAt(h.guardX, h.guardZ, h.groundY + 1.5f, 6f)
            if (!gy.isNaN()) h.guardY = gy
            placeGuard(h)
            h.guard.calculateTransforms()
            if (h.danfo != null) {
                val dy = RoadGround.carHeightAt(h.danfoX, h.danfoZ, h.groundY + 1.5f, 6f)
                if (!dy.isNaN()) h.danfoY = dy
                placeDanfo(h)
            }
        }
    }

    // ------------------------------------------------------------------ the ground rules

    private fun registerFloors(h: Home, local: FloatArray) {
        val world = FloatArray(local.size)
        var i = 0
        while (i + 2 < local.size) {
            val lx = local[i]
            val ly = local[i + 1]
            val lz = local[i + 2] + h.houseZ
            world[i] = h.worldX(lx, lz)
            world[i + 1] = h.floorY + ly
            world[i + 2] = h.worldZ(lx, lz)
            i += 3
        }
        RoadGround.addFloors("home_" + h.tag, world)
    }

    private fun block(h: Home, lx: Float, lz: Float, hx: Float, hz: Float, door: Boolean = false) {
        RoadGround.addBlock(h.worldX(lx, lz), h.worldZ(lx, lz), hx, hz, h.yaw, door)
    }

    private fun registerBlocks(h: Home) {
        // The yard walls: back, both sides, and the front on both sides of the gate.
        block(h, 0f, YARD_HALF_Z, YARD_HALF_X + 0.6f, 0.6f)
        block(h, -YARD_HALF_X, 0f, 0.6f, YARD_HALF_Z + 0.6f)
        block(h, YARD_HALF_X, 0f, 0.6f, YARD_HALF_Z + 0.6f)
        val frontHalf = (YARD_HALF_X + 0.6f - GATE_HALF) / 2f
        val frontMid = GATE_HALF + frontHalf
        block(h, -frontMid, -YARD_HALF_Z, frontHalf, 0.6f)
        block(h, frontMid, -YARD_HALF_Z, frontHalf, 0.6f)
        // The garage: back wall and two side walls, open towards the gate.
        block(h, GARAGE_X, GARAGE_Z + GARAGE_D / 2f, GARAGE_W / 2f + 0.3f, 0.5f)
        block(h, GARAGE_X - GARAGE_W / 2f, GARAGE_Z, 0.5f, GARAGE_D / 2f)
        block(h, GARAGE_X + GARAGE_W / 2f, GARAGE_Z, 0.5f, GARAGE_D / 2f)
        // The house: no sand inside its outline, except in the doorway.
        block(h, h.blockX, h.houseZ + h.blockZ, h.blockHalfX, h.blockHalfZ)
        block(h, 0f, h.houseZ + h.doorZ + 0.4f, DOOR_HALF_WIDTH, 1.5f, true)
    }

    // ------------------------------------------------------------------ walls, gate, garage

    private fun buildStructure(h: Home): Model {
        val attrs = (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong()
        val mb = ModelBuilder()
        mb.begin()

        val wallMid = (WALL_TOP + WALL_BOTTOM) / 2f
        val wallHeight = WALL_TOP - WALL_BOTTOM
        val frontHalf = (YARD_HALF_X + 0.25f - (GATE_HALF + 0.1f)) / 2f
        val frontMid = GATE_HALF + 0.1f + frontHalf

        // Light concrete: the yard walls and the garage walls.
        val wall = mb.part("walls", GL20.GL_TRIANGLES, attrs, Material(ColorAttribute.createDiffuse(0.86f, 0.82f, 0.74f, 1f)))
        box(wall, 0f, wallMid, YARD_HALF_Z, YARD_HALF_X * 2f + 0.5f, wallHeight, WALL_THICKNESS)
        box(wall, -YARD_HALF_X, wallMid, 0f, WALL_THICKNESS, wallHeight, YARD_HALF_Z * 2f)
        box(wall, YARD_HALF_X, wallMid, 0f, WALL_THICKNESS, wallHeight, YARD_HALF_Z * 2f)
        box(wall, -frontMid, wallMid, -YARD_HALF_Z, frontHalf * 2f, wallHeight, WALL_THICKNESS)
        box(wall, frontMid, wallMid, -YARD_HALF_Z, frontHalf * 2f, wallHeight, WALL_THICKNESS)
        val garageMid = (GARAGE_TOP + WALL_BOTTOM) / 2f
        val garageHeight = GARAGE_TOP - WALL_BOTTOM
        box(wall, GARAGE_X, garageMid, GARAGE_Z + GARAGE_D / 2f, GARAGE_W, garageHeight, 0.3f)
        box(wall, GARAGE_X - GARAGE_W / 2f, garageMid, GARAGE_Z, 0.3f, garageHeight, GARAGE_D)
        box(wall, GARAGE_X + GARAGE_W / 2f, garageMid, GARAGE_Z, 0.3f, garageHeight, GARAGE_D)

        // Dark metal: the gate pillars, the beam over the gate and the garage roof.
        val dark = mb.part("metal", GL20.GL_TRIANGLES, attrs, Material(ColorAttribute.createDiffuse(0.20f, 0.20f, 0.22f, 1f)))
        box(dark, -GATE_HALF - 0.4f, 1.65f, -YARD_HALF_Z, 0.8f, 4.5f, 0.8f)
        box(dark, GATE_HALF + 0.4f, 1.65f, -YARD_HALF_Z, 0.8f, 4.5f, 0.8f)
        box(dark, 0f, 3.55f, -YARD_HALF_Z, GATE_HALF * 2f + 1.6f, 0.4f, 0.6f)
        box(dark, GARAGE_X, GARAGE_TOP + 0.15f, GARAGE_Z, GARAGE_W + 0.8f, 0.3f, GARAGE_D + 0.8f)

        // Plain concrete: the foundation round the house, so the floor never floats above the sand.
        val slab = mb.part("slab", GL20.GL_TRIANGLES, attrs, Material(ColorAttribute.createDiffuse(0.62f, 0.62f, 0.60f, 1f)))
        box(slab, h.blockX, -0.65f, h.houseZ + h.blockZ, h.blockHalfX * 2f + 0.3f, 1.1f, h.blockHalfZ * 2f + 0.3f)

        return mb.end()
    }

    private fun box(part: MeshPartBuilder, x: Float, y: Float, z: Float, w: Float, h: Float, d: Float) {
        part.setVertexTransform(tmpMatrix.setToTranslation(x, y, z))
        BoxShapeBuilder.build(part, w, h, d)
    }

    // ------------------------------------------------------------------ drawing and bumping

    fun render(batch: ModelBatch, environment: Environment, camera: Camera) {
        for (h in homes) {
            val dx = h.x - camera.position.x
            val dz = h.z - camera.position.z
            if (dx * dx + dz * dz > DRAW_RANGE * DRAW_RANGE) continue
            batch.render(h.house, environment)
            batch.render(h.structure, environment)
            batch.render(h.guard, environment)
            val d = h.danfo
            if (d != null) batch.render(d, environment)
        }
    }

    /** Keeps a person on foot out of the bodyguards and the parked Danfo. */
    fun pushPlayer(p: Vector3, radius: Float) {
        for (o in obstacles) {
            val ex = p.x - o[0]
            val ez = p.z - o[1]
            val limit = radius + o[2]
            val d2 = ex * ex + ez * ez
            if (d2 >= limit * limit) continue
            val d = maxOf(sqrt(d2), 0.001f)
            p.x += ex / d * (limit - d)
            p.z += ez / d * (limit - d)
        }
    }

    /** Keeps a car out of the bodyguards and the parked Danfo. A car is two circles, one near each end. */
    fun pushCar(car: CarController, carRadius: Float, carOffset: Float) {
        val rad = car.yawDegrees * 0.017453292f
        val cfx = -sin(rad)
        val cfz = -cos(rad)
        var hit = false
        for (o in obstacles) {
            val dx = o[0] - car.position.x
            val dz = o[1] - car.position.z
            if (dx * dx + dz * dz > 100f) continue
            for (a in -1..1 step 2) {
                val ax = car.position.x + cfx * carOffset * a
                val az = car.position.z + cfz * carOffset * a
                var ex = ax - o[0]
                var ez = az - o[1]
                val limit = carRadius + o[2]
                val d2 = ex * ex + ez * ez
                if (d2 >= limit * limit) continue
                var d = sqrt(d2)
                if (d < 0.001f) {
                    ex = 1f
                    ez = 0f
                    d = 1f
                }
                car.position.x += ex / d * (limit - d)
                car.position.z += ez / d * (limit - d)
                hit = true
            }
        }
        if (hit) car.bump(0.6f)
    }

    // ------------------------------------------------------------------ finding home

    /** The number of the house whose gate is nearest to (x, z). */
    fun nearest(x: Float, z: Float): Int {
        var best = 0
        var bestD = Float.MAX_VALUE
        for ((i, h) in homes.withIndex()) {
            val gx = h.worldX(0f, -YARD_HALF_Z)
            val gz = h.worldZ(0f, -YARD_HALF_Z)
            val d = (gx - x) * (gx - x) + (gz - z) * (gz - z)
            if (d < bestD) {
                bestD = d
                best = i
            }
        }
        return best
    }

    fun label(index: Int): String = homes[index].label

    /** The gate of a house, in the world (also where its marker is drawn on the maps). */
    fun gate(index: Int, out: Vector3 = gate): Vector3 {
        val h = homes[index]
        return out.set(h.worldX(0f, -YARD_HALF_Z), h.groundY, h.worldZ(0f, -YARD_HALF_Z))
    }

    /** "HOME 1" or "HOME 2" while you are in or near the yard of a house, otherwise null. */
    fun nameNear(x: Float, z: Float): String? {
        for (h in homes) {
            val dx = h.x - x
            val dz = h.z - z
            if (dx * dx + dz * dz < NEAR_RANGE * NEAR_RANGE) return h.label
        }
        return null
    }

    fun dispose() {
        for (m in models) m.dispose()
        models.clear()
    }

    private companion object {
        const val FLOOR_RAISE = 0.25f       // the floor of a house stands this far above the flat sand
        const val YARD_HALF_X = 31f
        const val YARD_HALF_Z = 25f
        const val GATE_HALF = 3.3f
        const val WALL_THICKNESS = 0.5f
        const val WALL_TOP = 2.4f
        const val WALL_BOTTOM = -0.9f       // the walls go down into the sand
        const val GUARD_X = -4.7f
        const val GUARD_Z = -22.6f
        const val GUARD_RADIUS = 0.45f
        const val GARAGE_X = -22.5f
        const val GARAGE_Z = -3f
        const val GARAGE_W = 7.4f
        const val GARAGE_D = 7f
        const val GARAGE_TOP = 2.6f
        const val DOOR_HALF_WIDTH = 1.6f
        const val DANFO_OFFSET = 1.3f
        const val DANFO_RADIUS = 1.25f
        const val DRAW_RANGE = 700f
        const val NEAR_RANGE = 42f
    }
}
