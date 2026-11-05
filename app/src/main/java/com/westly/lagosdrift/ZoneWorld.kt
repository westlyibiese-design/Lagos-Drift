package com.westly.lagosdrift

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * The two new places after the Futatsugoya tunnel: the forest, and after it the island.
 *
 * Both are baked into assets/zones/ (see CREDITS.md there). They are built in a frame that follows the
 * tunnel: "along" is the distance from the tunnel join (the same number as RoadGround's corridor), and
 * "side" is how far to the side of the tunnel axis (the number RoadGround.inTunnel uses). The forest ground
 * starts at along [ALONG0], just where the scanned tunnel ends, and at the height of the tunnel floor, so
 * driving out of the tunnel puts you straight onto the forest road.
 *
 * FOREST: one baked tile (a stretch of forest road with its trees) repeated [FOREST_TILES] times. Every second
 * tile is turned round end to end, so the road, the ground height and the trees meet exactly at each join.
 * The forest ground is added to RoadGround as floor triangles, so the car and the walking player ride on it.
 * Every tree trunk is a circle that stops the car and the person; the small leaves, grass and bushes are not.
 * Where the forest ground ends there is no ground, so the edge of the forest is a wall too.
 *
 * ISLAND: one baked model that starts where the last forest tile ends: its track, hills and beach are floor
 * triangles, the water is not (the car cannot drive into the sea), and its trees are circles.
 */
class ZoneWorld {
    private class Tile(val instance: ModelInstance, val cx: Float, val cy: Float, val cz: Float)

    private class Loaded(val model: Model, val floors: FloatArray, val circles: FloatArray)

    private val models = ArrayList<Model>()
    private val tiles = ArrayList<Tile>()
    private var island: Tile? = null
    private var apron: Tile? = null

    /** Tree circles in the world: x, z, radius, over and over. */
    private var circles = FloatArray(0)
    private var circleCount = 0

    init {
        // The scanned tunnel floor stops a few metres before the tunnel's end. A flat strip of dirt, level with
        // that floor, carries the car from there to the start of the forest ground.
        val apronModel = ModelBuilder().createRect(
            worldX(APRON_MIN_SIDE, APRON_FROM), FOREST_Y, worldZ(APRON_MIN_SIDE, APRON_FROM),
            worldX(APRON_MAX_SIDE, APRON_FROM), FOREST_Y, worldZ(APRON_MAX_SIDE, APRON_FROM),
            worldX(APRON_MAX_SIDE, ALONG0), FOREST_Y, worldZ(APRON_MAX_SIDE, ALONG0),
            worldX(APRON_MIN_SIDE, ALONG0), FOREST_Y, worldZ(APRON_MIN_SIDE, ALONG0),
            0f, 1f, 0f,
            Material(ColorAttribute.createDiffuse(0.30f, 0.25f, 0.20f, 1f), IntAttribute.createCullFace(GL20.GL_NONE)),
            (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong()
        )
        models.add(apronModel)
        apron = Tile(
            ModelInstance(apronModel),
            worldX(0f, (APRON_FROM + ALONG0) / 2f), FOREST_Y, worldZ(0f, (APRON_FROM + ALONG0) / 2f)
        )
        RoadGround.addFloors(
            "forest_apron",
            floatArrayOf(
                worldX(APRON_MIN_SIDE, APRON_FROM), FOREST_Y, worldZ(APRON_MIN_SIDE, APRON_FROM),
                worldX(APRON_MAX_SIDE, APRON_FROM), FOREST_Y, worldZ(APRON_MAX_SIDE, APRON_FROM),
                worldX(APRON_MAX_SIDE, ALONG0), FOREST_Y, worldZ(APRON_MAX_SIDE, ALONG0),
                worldX(APRON_MIN_SIDE, APRON_FROM), FOREST_Y, worldZ(APRON_MIN_SIDE, APRON_FROM),
                worldX(APRON_MAX_SIDE, ALONG0), FOREST_Y, worldZ(APRON_MAX_SIDE, ALONG0),
                worldX(APRON_MIN_SIDE, ALONG0), FOREST_Y, worldZ(APRON_MIN_SIDE, ALONG0)
            )
        )

        val forest = loadZone("forest")
        models.add(forest.model)
        val circleList = ArrayList<FloatArray>()
        for (k in 0 until FOREST_TILES) {
            val mirrored = k % 2 == 1
            val start = ALONG0 + k * TILE_LENGTH
            val instance = ModelInstance(forest.model)
            instance.transform.set(frameMatrix(if (mirrored) start + TILE_LENGTH else start, mirrored))
            val centre = TILE_LENGTH / 2f
            val cs = FOREST_CENTRE_SIDE
            tiles.add(
                Tile(
                    instance, worldX(cs, start + centre), FOREST_Y + 4f, worldZ(cs, start + centre)
                )
            )
            RoadGround.addFloors("forest_$k", floorsToWorld(forest.floors, start, mirrored))
            circleList.add(circlesToWorld(forest.circles, start, mirrored))
        }

        val isl = loadZone("island")
        models.add(isl.model)
        val islandInstance = ModelInstance(isl.model)
        islandInstance.transform.set(frameMatrix(FOREST_END, false))
        island = Tile(
            islandInstance,
            worldX(ISLAND_CENTRE_SIDE, FOREST_END + ISLAND_CENTRE_ALONG), FOREST_Y,
            worldZ(ISLAND_CENTRE_SIDE, FOREST_END + ISLAND_CENTRE_ALONG)
        )
        RoadGround.addFloors("island", floorsToWorld(isl.floors, FOREST_END, false))
        circleList.add(circlesToWorld(isl.circles, FOREST_END, false))

        var total = 0
        for (c in circleList) total += c.size
        circles = FloatArray(total)
        var at = 0
        for (c in circleList) {
            System.arraycopy(c, 0, circles, at, c.size)
            at += c.size
        }
        circleCount = total / 3
    }

    // ------------------------------------------------------------------ the frame

    /** The model frame (side, up, along) placed in the world: its along axis runs the way the tunnel runs, or back. */
    private fun frameMatrix(alongOrigin: Float, mirrored: Boolean): FloatArray {
        val sign = if (mirrored) -1f else 1f
        return floatArrayOf(
            SX, 0f, SZ, 0f,
            0f, 1f, 0f, 0f,
            sign * AX, 0f, sign * AZ, 0f,
            worldX(0f, alongOrigin), FOREST_Y, worldZ(0f, alongOrigin), 1f
        )
    }

    private fun floorsToWorld(local: FloatArray, start: Float, mirrored: Boolean): FloatArray {
        val out = FloatArray(local.size)
        var i = 0
        while (i + 2 < local.size) {
            val s = local[i]
            val y = local[i + 1]
            val a = if (mirrored) TILE_LENGTH - local[i + 2] else local[i + 2]
            out[i] = worldX(s, start + a)
            out[i + 1] = FOREST_Y + y
            out[i + 2] = worldZ(s, start + a)
            i += 3
        }
        return out
    }

    private fun circlesToWorld(local: FloatArray, start: Float, mirrored: Boolean): FloatArray {
        val out = FloatArray(local.size)
        var i = 0
        while (i + 2 < local.size) {
            val a = if (mirrored) TILE_LENGTH - local[i + 1] else local[i + 1]
            out[i] = worldX(local[i], start + a)
            out[i + 1] = worldZ(local[i], start + a)
            out[i + 2] = local[i + 2]
            i += 3
        }
        return out
    }

    // ------------------------------------------------------------------ loading a baked zone

    private fun loadZone(tag: String): Loaded {
        val bytes = Gdx.files.internal("zones/$tag.bin").readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        val materialCount = buffer.int
        val materials = ArrayList<Material>()
        val textures = ArrayList<Texture>()
        for (i in 0 until materialCount) {
            val r = buffer.float
            val g = buffer.float
            val b = buffer.float
            val a = buffer.float
            val blend = buffer.int
            val nameLength = buffer.int
            val nameBytes = ByteArray(nameLength)
            buffer.get(nameBytes)
            val file = String(nameBytes, Charsets.UTF_8)

            val material = Material()
            material.set(ColorAttribute.createDiffuse(r, g, b, 1f))
            if (file.isNotEmpty()) {
                val texture = Texture(Gdx.files.internal("zones/$file"), true)
                texture.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear)
                texture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat)
                textures.add(texture)
                material.set(TextureAttribute.createDiffuse(texture))
            }
            material.set(IntAttribute.createCullFace(GL20.GL_NONE))
            // 2 = cut-out leaves (no sorting needed; the alpha number is the cut-off), 1 = see-through.
            if (blend == 2) {
                material.set(FloatAttribute.createAlphaTest(a.coerceIn(0.05f, 0.9f)))
            } else if (blend == 1) {
                material.set(BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, a.coerceIn(0.2f, 1f)))
            }
            materials.add(material)
        }

        val partCount = buffer.int
        val builder = ModelBuilder()
        builder.begin()
        val meshes = ArrayList<Mesh>()
        for (i in 0 until partCount) {
            val materialIndex = buffer.int
            val vertexCount = buffer.int
            val indexCount = buffer.int

            val vertices = FloatArray(vertexCount * FLOATS_PER_VERTEX)
            buffer.asFloatBuffer().get(vertices)
            buffer.position(buffer.position() + vertices.size * 4)
            val indices = ShortArray(indexCount)
            buffer.asShortBuffer().get(indices)
            buffer.position(buffer.position() + indexCount * 2)

            val mesh = Mesh(
                true, vertexCount, indexCount,
                VertexAttribute.Position(), VertexAttribute.Normal(), VertexAttribute.TexCoords(0)
            )
            mesh.setVertices(vertices)
            mesh.setIndices(indices)
            meshes.add(mesh)

            builder.node()
            builder.part("zone$i", mesh, GL20.GL_TRIANGLES, materials[materialIndex])
        }
        val model = builder.end()
        for (m in meshes) model.manageDisposable(m)
        for (t in textures) model.manageDisposable(t)

        val floorCount = buffer.int
        val floors = FloatArray(floorCount * 9)
        buffer.asFloatBuffer().get(floors)
        buffer.position(buffer.position() + floors.size * 4)

        val circleCount = buffer.int
        val circleData = FloatArray(circleCount * 3)
        buffer.asFloatBuffer().get(circleData)
        return Loaded(model, floors, circleData)
    }

    // ------------------------------------------------------------------ drawing

    fun render(batch: ModelBatch, environment: Environment, camera: Camera) {
        val ap = apron
        if (ap != null) {
            val dx = ap.cx - camera.position.x
            val dz = ap.cz - camera.position.z
            if (dx * dx + dz * dz < 250f * 250f) batch.render(ap.instance, environment)
        }
        for (t in tiles) {
            val dx = t.cx - camera.position.x
            val dz = t.cz - camera.position.z
            if (dx * dx + dz * dz > TILE_DRAW_RANGE * TILE_DRAW_RANGE) continue
            if (!camera.frustum.sphereInFrustum(t.cx, t.cy, t.cz, TILE_RADIUS)) continue
            batch.render(t.instance, environment)
        }
        val isl = island
        if (isl != null) {
            val dx = isl.cx - camera.position.x
            val dz = isl.cz - camera.position.z
            if (dx * dx + dz * dz < ISLAND_DRAW_RANGE * ISLAND_DRAW_RANGE &&
                camera.frustum.sphereInFrustum(isl.cx, isl.cy, isl.cz, ISLAND_RADIUS)
            ) batch.render(isl.instance, environment)
        }
    }

    // ------------------------------------------------------------------ the trees are solid

    /** Keeps a person out of the tree trunks. */
    fun pushPlayer(p: Vector3, radius: Float) {
        if (!near(p.x, p.z)) return
        var i = 0
        while (i < circleCount) {
            val o = i * 3
            i++
            val ex = p.x - circles[o]
            val limit = radius + circles[o + 2]
            if (ex > limit || ex < -limit) continue
            val ez = p.z - circles[o + 1]
            if (ez > limit || ez < -limit) continue
            val d2 = ex * ex + ez * ez
            if (d2 >= limit * limit) continue
            val d = maxOf(sqrt(d2), 0.001f)
            p.x += ex / d * (limit - d)
            p.z += ez / d * (limit - d)
        }
    }

    /** Keeps a car out of the tree trunks. A car is two circles, one near each end. */
    fun pushCar(car: CarController, carRadius: Float, carOffset: Float) {
        if (!near(car.position.x, car.position.z)) return
        val rad = car.yawDegrees * 0.017453292f
        val cfx = -sin(rad)
        val cfz = -cos(rad)
        var hit = false
        var i = 0
        while (i < circleCount) {
            val o = i * 3
            i++
            val dx = circles[o] - car.position.x
            val dz = circles[o + 1] - car.position.z
            if (dx * dx + dz * dz > 100f) continue
            for (a in -1..1 step 2) {
                val ax = car.position.x + cfx * carOffset * a
                val az = car.position.z + cfz * carOffset * a
                var ex = ax - circles[o]
                var ez = az - circles[o + 1]
                val limit = carRadius + circles[o + 2]
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

    /** True if a circle of [radius] at (x, z) would touch a tree trunk. */
    fun touchesTree(x: Float, z: Float, radius: Float): Boolean {
        var i = 0
        while (i < circleCount) {
            val o = i * 3
            i++
            val ex = x - circles[o]
            val ez = z - circles[o + 1]
            val limit = radius + circles[o + 2]
            if (ex * ex + ez * ez < limit * limit) return true
        }
        return false
    }

    /** Cheap test: is (x, z) anywhere near the forest or the island at all? */
    private fun near(x: Float, z: Float): Boolean {
        val a = along(x, z)
        return a > ALONG0 - 20f && a < FOREST_END + ISLAND_LENGTH + 40f
    }

    // ------------------------------------------------------------------ where am I

    /** Distance along the tunnel axis, from the tunnel join. */
    fun along(x: Float, z: Float): Float =
        (x - RoadGround.JOIN_X) * AX + (z - RoadGround.JOIN_Z) * AZ

    /** Distance to the side of the tunnel axis (the same number RoadGround.inTunnel uses). */
    fun side(x: Float, z: Float): Float =
        (x - RoadGround.JOIN_X) * SX + (z - RoadGround.JOIN_Z) * SZ

    /** True on the forest ground, or within [margin] metres of it. */
    fun inForest(x: Float, z: Float, margin: Float): Boolean {
        val a = along(x, z)
        val s = side(x, z)
        return a > ALONG0 - margin && a < FOREST_END + margin && s > FOREST_MIN_SIDE - margin && s < FOREST_MAX_SIDE + margin
    }

    /** "FOREST" or "ISLAND" while you are there, otherwise null. */
    fun nameAt(x: Float, z: Float): String? {
        if (inForest(x, z, 0f)) return "FOREST"
        val a = along(x, z)
        val s = side(x, z)
        if (a >= FOREST_END - 4f && a < FOREST_END + ISLAND_LENGTH && s > -150f && s < 720f) return "ISLAND"
        return null
    }

    /** Where you start again if the zombies get you: the start of the forest road, facing into the forest. */
    val entranceX: Float
        get() = worldX(ENTRANCE_SIDE, ALONG0 + ENTRANCE_ALONG)
    val entranceZ: Float
        get() = worldZ(ENTRANCE_SIDE, ALONG0 + ENTRANCE_ALONG)

    fun dispose() {
        for (m in models) m.dispose()
        models.clear()
        tiles.clear()
        island = null
        apron = null
        RoadGround.remove("island")
        RoadGround.remove("forest_apron")
        for (k in 0 until FOREST_TILES) RoadGround.remove("forest_$k")
    }

    companion object {
        // The tunnel axis (along) and the way to its side, the same numbers RoadGround uses.
        private const val AX = RoadGround.TUNNEL_DIR_X
        private const val AZ = RoadGround.TUNNEL_DIR_Z
        private const val SX = RoadGround.TUNNEL_DIR_Z
        private const val SZ = -RoadGround.TUNNEL_DIR_X

        private fun worldX(side: Float, along: Float): Float = RoadGround.JOIN_X + AX * along + SX * side
        private fun worldZ(side: Float, along: Float): Float = RoadGround.JOIN_Z + AZ * along + SZ * side

        /** Where the forest ground starts: just past the end of the scanned tunnel. */
        const val ALONG0 = 728f

        /** The height of the tunnel floor near its end (its lowest layer), so the forest road carries on level. */
        const val FOREST_Y = 20.1f

        // The dirt strip between the end of the scanned tunnel floor and the forest ground.
        private const val APRON_FROM = 712f
        private const val APRON_MIN_SIDE = -9f
        private const val APRON_MAX_SIDE = 13f

        /** Length of one baked forest tile, in metres (taken from the baked file). */
        const val TILE_LENGTH = 94.679f

        /** An even number, so the last tile ends with the road in the same place the first one began. */
        const val FOREST_TILES = 6
        const val FOREST_END = ALONG0 + FOREST_TILES * TILE_LENGTH

        // The forest ground is this wide to the side of the tunnel axis, as baked.
        const val FOREST_MIN_SIDE = -66.6f
        const val FOREST_MAX_SIDE = 29.0f
        private const val FOREST_CENTRE_SIDE = (FOREST_MIN_SIDE + FOREST_MAX_SIDE) / 2f

        private const val ENTRANCE_SIDE = 1.7f
        private const val ENTRANCE_ALONG = 8f

        // The baked island reaches about 450 m past its start and between -135 and 690 m to the side.
        private const val ISLAND_LENGTH = 460f
        private const val ISLAND_CENTRE_SIDE = 280f
        private const val ISLAND_CENTRE_ALONG = 220f
        private const val ISLAND_RADIUS = 520f

        private const val TILE_DRAW_RANGE = 190f
        private const val TILE_RADIUS = 80f
        private const val ISLAND_DRAW_RANGE = 800f
        private const val FLOATS_PER_VERTEX = 8

        const val FOREST_CREDIT = "Forest: a forest (3) with a road at night for game by dasy444 (CC BY 4.0)"
        const val ISLAND_CREDIT = "Island: Tropical Island Uphill Track - LowPoly by PropShop (CC BY 4.0)"
    }
}
