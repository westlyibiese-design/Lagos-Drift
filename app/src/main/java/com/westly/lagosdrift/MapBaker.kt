package com.westly.lagosdrift

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.loader.G3dModelLoader
import com.badlogic.gdx.graphics.g3d.model.data.ModelMesh
import com.badlogic.gdx.graphics.g3d.model.data.ModelMeshPart
import com.badlogic.gdx.graphics.g3d.model.data.ModelNode
import com.badlogic.gdx.graphics.glutils.PixmapTextureData
import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.UBJsonReader
import kotlin.math.abs
import kotlin.math.floor

/**
 * Bakes the road of the whole network, once, into a small top-down picture for the two maps.
 *
 * The road chunks are loaded by distance while you drive, so the ground data of the far parts is not
 * there yet. So a background thread reads every chunk file again (only the model data, no GL objects,
 * so it is safe off the game thread), picks the drivable triangles with exactly the same rules as
 * RoadGround (the road pictures with an upward normal, and the tunnel floor inside the tunnel corridor),
 * and paints them into a byte mask, [CELL] metres per pixel. When the thread is done, the game thread
 * turns the mask into a Pixmap and a Texture (light grey road, transparent everywhere else) in [update].
 *
 * The picture covers the area inside WorldLimits: pixel (column, row) is the world point
 * (MIN_X + column * CELL, MIN_Z + row * CELL), so row 0 is the north edge (smallest z).
 */
class MapBaker {
    /** The baked road picture, or null while the thread is still working (or if baking failed). */
    var texture: Texture? = null
        private set

    /** The exact text of a baking problem, or null. The game shows it on screen. */
    @Volatile
    var errorText: String? = null
        private set

    /** True when the picture is ready to draw. */
    val ready: Boolean get() = texture != null

    @Volatile
    private var finished = false
    @Volatile
    private var rgba: ByteArray? = null
    private var started = false
    private var pixmap: Pixmap? = null

    /** Starts the background thread. Does nothing when it has been started before. */
    fun start() {
        if (started) return
        started = true
        val thread = Thread({ bake() }, "map-baker")
        thread.isDaemon = true
        thread.priority = Thread.NORM_PRIORITY - 1
        thread.start()
    }

    /** Call every frame on the game thread: makes the texture once the thread has finished. */
    fun update() {
        if (texture != null || !finished) return
        val bytes = rgba ?: return
        rgba = null
        try {
            val map = Pixmap(COLS, ROWS, Pixmap.Format.RGBA8888)
            val buffer = map.pixels
            buffer.clear()
            buffer.put(bytes)
            buffer.position(0)
            // Managed, so the picture comes back by itself if the graphics context is ever lost.
            val tex = Texture(PixmapTextureData(map, null, false, false, true))
            tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
            pixmap = map
            texture = tex
        } catch (e: Exception) {
            errorText = "Map picture error: ${describe(e)}"
            Gdx.app.error("LagosDrift", errorText ?: "")
        }
    }

    fun dispose() {
        texture?.dispose()
        texture = null
        pixmap?.dispose()
        pixmap = null
    }

    // ------------------------------------------------------------------ the background thread

    private fun bake() {
        try {
            val mask = ByteArray(COLS * ROWS)
            val loader = G3dModelLoader(UBJsonReader(), InternalFileHandleResolver())
            val files = chunkFiles()
            for (name in files) {
                try {
                    paintChunk(loader, "$FOLDER/$name", mask)
                } catch (e: Exception) {
                    // One bad chunk must not stop the rest of the map; the text is shown on screen.
                    errorText = "Map bake error ($FOLDER/$name): ${describe(e)}"
                    Gdx.app.error("LagosDrift", errorText ?: "")
                }
            }

            // Light grey road. Every pixel carries the road colour, only the alpha differs, so the
            // soft edge between road and sand does not get a dark fringe when the picture is scaled.
            val out = ByteArray(COLS * ROWS * 4)
            for (i in 0 until COLS * ROWS) {
                out[4 * i] = ROAD_R
                out[4 * i + 1] = ROAD_G
                out[4 * i + 2] = ROAD_B
                out[4 * i + 3] = if (mask[i].toInt() != 0) 255.toByte() else 0
            }
            rgba = out
        } catch (e: Throwable) {
            errorText = "Map bake error: ${describe(e)}"
            Gdx.app.error("LagosDrift", errorText ?: "")
        } finally {
            finished = true
        }
    }

    /** The chunk file names from chunks.json (the sky dome in it is not a road). */
    private fun chunkFiles(): List<String> {
        val names = ArrayList<String>()
        val root = JsonReader().parse(Gdx.files.internal("$FOLDER/chunks.json"))
        var e = root.child
        while (e != null) {
            val file = e.getString("file")
            if (file != "sky.g3db") names.add(file)
            e = e.next
        }
        return names
    }

    /** Reads one chunk's model data and paints its drivable triangles into [mask]. */
    private fun paintChunk(loader: G3dModelLoader, path: String, mask: ByteArray) {
        val data = loader.loadModelData(Gdx.files.internal(path))
        // Mesh parts by id, so a node's part can find its mesh.
        val partMesh = HashMap<String, ModelMesh>()
        val partData = HashMap<String, ModelMeshPart>()
        for (mesh in data.meshes) {
            for (part in mesh.parts) {
                partMesh[part.id] = mesh
                partData[part.id] = part
            }
        }
        for (node in data.nodes) paintNode(node, partMesh, partData, mask)
    }

    private fun paintNode(
        node: ModelNode, partMesh: HashMap<String, ModelMesh>, partData: HashMap<String, ModelMeshPart>, mask: ByteArray
    ) {
        val nodeParts = node.parts
        if (nodeParts != null) {
            for (np in nodeParts) {
                val kind = kindOf(np.materialId ?: continue)
                if (kind == KIND_NONE) continue
                val mesh = partMesh[np.meshPartId] ?: continue
                val part = partData[np.meshPartId] ?: continue
                paintPart(mesh, part, kind, mask)
            }
        }
        val children = node.children
        if (children != null) for (child in children) paintNode(child, partMesh, partData, mask)
    }

    private fun kindOf(materialId: String): Int {
        if (materialId.startsWith("m_tunnel_")) return KIND_TUNNEL
        if (materialId.startsWith("m_glen_")) {
            val number = materialId.substring(7)
            for (n in GLEN_ROAD_NUMBERS) if (number == n) return KIND_GLEN
        }
        return KIND_NONE
    }

    private fun paintPart(mesh: ModelMesh, part: ModelMeshPart, kind: Int, mask: ByteArray) {
        // Where the position and the normal sit inside one vertex (in floats).
        var stride = 0
        var posOffset = 0
        var normalOffset = 3
        for (attribute in mesh.attributes) {
            if (attribute.usage == VertexAttributes.Usage.Position) posOffset = stride
            if (attribute.usage == VertexAttributes.Usage.Normal) normalOffset = stride
            stride += attribute.numComponents
        }
        if (stride == 0) return
        val v = mesh.vertices
        val idx = part.indices

        var t = 0
        while (t + 2 < idx.size) {
            val i0 = idx[t].toInt() and 0xFFFF
            val i1 = idx[t + 1].toInt() and 0xFFFF
            val i2 = idx[t + 2].toInt() and 0xFFFF
            t += 3
            val p0 = i0 * stride + posOffset
            val p1 = i1 * stride + posOffset
            val p2 = i2 * stride + posOffset
            // The vertex normals decide what is road (never the winding: the materials are double-sided).
            val normalY = (v[i0 * stride + normalOffset + 1] +
                v[i1 * stride + normalOffset + 1] +
                v[i2 * stride + normalOffset + 1]) / 3f
            val x1 = v[p0]
            val z1 = v[p0 + 2]
            val x2 = v[p1]
            val z2 = v[p1 + 2]
            val x3 = v[p2]
            val z3 = v[p2 + 2]
            if (kind == KIND_GLEN) {
                if (normalY <= 0.6f) continue
            } else {
                if (normalY <= 0.85f) continue
                if (!inTunnelCorridor((x1 + x2 + x3) / 3f, (z1 + z2 + z3) / 3f)) continue
            }
            fillTriangle(mask, x1, z1, x2, z2, x3, z3)
        }
    }

    /** The same corridor RoadGround uses for the tunnel floor: past the join, along the axis, 6 m sideways. */
    private fun inTunnelCorridor(x: Float, z: Float): Boolean {
        val dx = x - RoadGround.JOIN_X
        val dz = z - RoadGround.JOIN_Z
        val along = dx * RoadGround.TUNNEL_DIR_X + dz * RoadGround.TUNNEL_DIR_Z
        val side = dx * RoadGround.TUNNEL_DIR_Z - dz * RoadGround.TUNNEL_DIR_X
        return along >= -2f && along <= RoadGround.TUNNEL_LENGTH + 2f && abs(side) <= RoadGround.TUNNEL_HALF_WIDTH
    }

    // ------------------------------------------------------------------ painting

    /** Paints a triangle (seen from above) into the mask: its inside, and its three edges so thin ones never vanish. */
    private fun fillTriangle(mask: ByteArray, x1: Float, z1: Float, x2: Float, z2: Float, x3: Float, z3: Float) {
        val ax = (x1 - WorldLimits.MIN_X) * INV_CELL
        val az = (z1 - WorldLimits.MIN_Z) * INV_CELL
        val bx = (x2 - WorldLimits.MIN_X) * INV_CELL
        val bz = (z2 - WorldLimits.MIN_Z) * INV_CELL
        val cx = (x3 - WorldLimits.MIN_X) * INV_CELL
        val cz = (z3 - WorldLimits.MIN_Z) * INV_CELL

        val c0 = floor(minOf(ax, bx, cx)).toInt().coerceAtLeast(0)
        val c1 = floor(maxOf(ax, bx, cx)).toInt().coerceAtMost(COLS - 1)
        val r0 = floor(minOf(az, bz, cz)).toInt().coerceAtLeast(0)
        val r1 = floor(maxOf(az, bz, cz)).toInt().coerceAtMost(ROWS - 1)

        val den = (bz - cz) * (ax - cx) + (cx - bx) * (az - cz)
        if (abs(den) > 1e-9f) {
            val inv = 1f / den
            for (r in r0..r1) {
                for (c in c0..c1) {
                    val px = c + 0.5f
                    val pz = r + 0.5f
                    val l1 = ((bz - cz) * (px - cx) + (cx - bx) * (pz - cz)) * inv
                    val l2 = ((cz - az) * (px - cx) + (ax - cx) * (pz - cz)) * inv
                    val l3 = 1f - l1 - l2
                    if (l1 >= -EDGE && l2 >= -EDGE && l3 >= -EDGE) mask[r * COLS + c] = 1
                }
            }
        }
        line(mask, ax, az, bx, bz)
        line(mask, bx, bz, cx, cz)
        line(mask, cx, cz, ax, az)
    }

    private fun line(mask: ByteArray, x0: Float, z0: Float, x1: Float, z1: Float) {
        val steps = maxOf(abs(x1 - x0), abs(z1 - z0)).toInt() + 1
        for (k in 0..steps) {
            val f = k.toFloat() / steps
            val c = floor(x0 + (x1 - x0) * f).toInt()
            val r = floor(z0 + (z1 - z0) * f).toInt()
            if (c in 0 until COLS && r in 0 until ROWS) mask[r * COLS + c] = 1
        }
    }

    private fun describe(t: Throwable): String {
        val sb = StringBuilder(t.toString())
        var cause = t.cause
        var depth = 0
        while (cause != null && depth < 4) {
            sb.append(" | caused by ").append(cause.toString())
            cause = cause.cause
            depth++
        }
        return sb.toString()
    }

    companion object {
        const val FOLDER = "roads/glen_tunnel"

        /** Metres per picture pixel. */
        const val CELL = 3f

        /** Picture size: the area inside WorldLimits (2000 m by 2400 m) at [CELL] metres per pixel, rounded up. */
        const val COLS = 667
        const val ROWS = 800

        private const val INV_CELL = 1f / CELL
        private const val EDGE = 0.15f          // a pixel centre this close outside a triangle still counts
        private const val KIND_NONE = 0
        private const val KIND_GLEN = 1
        private const val KIND_TUNNEL = 2
        private val GLEN_ROAD_NUMBERS = arrayOf("06", "07", "09", "10", "11", "12")

        // Light grey road, as bytes (200, 200, 200).
        private const val ROAD_R: Byte = -56
        private const val ROAD_G: Byte = -56
        private const val ROAD_B: Byte = -56
    }
}
