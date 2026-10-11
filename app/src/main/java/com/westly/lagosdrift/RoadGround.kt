package com.westly.lagosdrift

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.model.Node
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.FloatArray as FloatList
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The single source of truth about the ground. It keeps the drivable triangles of every loaded road
 * chunk (the Glen Canyon road surface and the tunnel floor), in a grid over the XZ plane with cells of
 * about 8 m, and answers: how high is the road here, how does it lean, is there road here at all.
 *
 * A chunk's triangles are added when the chunk is loaded and removed when it is freed (see RoadWorld).
 * Because the world has bridges, ramps and roads on different levels, a question always carries a
 * reference height: the answer is the highest surface that is not more than [STEP_UP] above it.
 *
 * Everything runs on the game thread and nothing is allocated while answering.
 *
 * The car asks a slightly different question ([carHeightAt]): besides the road it may also stand on the
 * desert ground beside it, and inside the tunnel it always has a floor. The scanned tunnel floor has
 * holes and a gap at the mouth, so a fitted floor fills them in, and rubble heaps taller than the car
 * can climb are not solid. Traffic and the walking player keep using the road-only questions.
 */
object RoadGround {
    // Where the Glen Canyon road joins the tunnel, and the way the tunnel runs (towards -Z, south).
    const val JOIN_X = -418.9f
    const val JOIN_Z = -1238.5f
    const val TUNNEL_DIR_X = 0.0666f
    const val TUNNEL_DIR_Z = -0.9978f
    const val TUNNEL_LENGTH = 740f
    const val TUNNEL_HALF_WIDTH = 6f

    /** How far above the reference height a surface may be and still count (a step or a kerb). */
    const val STEP_UP = 1.0f

    /** What a wheel stands on (see [lastKind]): used by the car for its suspension, speed and sound. */
    const val SURFACE_ROAD = 1
    const val SURFACE_TUNNEL = 2
    const val SURFACE_TERRAIN = 3

    private const val CELL = 8f
    private const val INV_CELL = 1f / CELL
    private const val STRIDE = 13        // x1 y1 z1 x2 y2 z2 x3 y3 z3 invDenominator nx ny nz
    private const val EDGE_TOLERANCE = 0.004f
    private const val NO_LIMIT = 100000f

    private const val KIND_NONE = 0
    private const val KIND_GLEN = 1
    private const val KIND_TUNNEL = 2
    private const val KIND_TERRAIN = 3
    private const val KIND_DECK = 4

    // The tunnel floor the car can always drive on, where the scan has holes: it covers the width of the
    // tunnel (sideways from the corridor axis) from the mouth to the end of the scanned floor.
    private const val VIRTUAL_SIDE_MIN = -2.2f
    private const val VIRTUAL_SIDE_MAX = 5.6f
    private const val VIRTUAL_END = 705f

    // Rubble taller than this above the fitted floor is not solid for the car (it drives through it).
    private const val RUBBLE_MAX = 1.0f

    // The desert ground is flat in the model, so it gets small bumps and dips of its own.
    private const val TERRAIN_BUMP = 0.16f
    private const val TERRAIN_SINK = 0.04f

    // The road pictures that are drivable asphalt (the material ids match the picture names).
    private val GLEN_ROAD_NUMBERS = arrayOf("06", "07", "09", "10", "11", "12")

    // The concrete of the dam: the roadway on top of it (plain concrete, hazard stripes, paving). It is
    // road like the asphalt, but only its flat faces count, so walls, rails and roofs are not ground.
    private val GLEN_DECK_NUMBERS = arrayOf("05", "13", "15", "16", "18")

    // The desert sand (01) and the dirt ground (03) can be driven on. The lake (19), the canyon wall (02),
    // the bushes and the rest cannot.
    private val GLEN_TERRAIN_NUMBERS = arrayOf("01", "03")

    // Height of the tunnel floor (lower edge of the scan), every 10 m from the mouth. Measured from the
    // tunnel model; the first two values join it to the end of the Glen Canyon road.
    private val TUNNEL_FLOOR = floatArrayOf(
        22.65f, 23.70f, 24.52f, 24.72f, 24.92f, 24.97f, 25.24f, 25.41f, 25.47f, 25.60f,
        25.77f, 25.81f, 25.92f, 26.17f, 26.29f, 26.71f, 26.88f, 26.99f, 27.05f, 27.16f,
        27.24f, 27.34f, 27.34f, 27.38f, 27.59f, 27.77f, 27.83f, 27.96f, 28.09f, 28.10f,
        28.10f, 28.14f, 28.17f, 28.17f, 28.08f, 27.93f, 27.75f, 27.63f, 27.46f, 27.24f,
        27.03f, 26.82f, 26.66f, 26.36f, 26.16f, 25.85f, 25.65f, 25.40f, 25.17f, 24.93f,
        24.67f, 24.47f, 24.27f, 24.06f, 23.77f, 23.57f, 23.37f, 23.15f, 22.84f, 22.69f,
        22.46f, 22.22f, 21.98f, 21.78f, 21.55f, 21.35f, 21.11f, 20.92f, 20.70f, 20.45f,
        20.27f, 20.27f
    )

    private class Patch(
        val key: String,
        val tri: FloatArray,
        val minX: Float, val minZ: Float, val maxX: Float, val maxZ: Float,
        val cols: Int, val rows: Int,
        val cellStart: IntArray, val cellItems: IntArray
    )

    private class MeshData {
        var mesh: Mesh? = null
        var verts = FloatArray(0)
        var idx = ShortArray(0)
        var stride = 8
        var posOffset = 0
        var normalOffset = 3
    }

    private val patches = ArrayList<Patch>()
    private val terrainPatches = ArrayList<Patch>()

    // The answer of the last successful sample().
    private var sampleY = 0f
    private var sampleNx = 0f
    private var sampleNy = 1f
    private var sampleNz = 0f

    /** What the last successful [carHeightAt] found: one of the SURFACE_ values. */
    var lastKind = SURFACE_ROAD
        private set

    /** Number of ground triangles currently held. */
    val triangleCount: Int
        get() {
            var n = 0
            for (i in 0 until patches.size) n += patches[i].tri.size / STRIDE
            return n
        }

    // ------------------------------------------------------------------ the tunnel corridor

    /** True inside the tunnel: past the join, along its axis, and within 6 m sideways. */
    fun inTunnel(x: Float, z: Float): Boolean = inCorridor(x, z, 0f)

    private fun inCorridor(x: Float, z: Float, margin: Float): Boolean {
        val dx = x - JOIN_X
        val dz = z - JOIN_Z
        val along = dx * TUNNEL_DIR_X + dz * TUNNEL_DIR_Z
        val side = dx * TUNNEL_DIR_Z - dz * TUNNEL_DIR_X
        return along >= -margin && along <= TUNNEL_LENGTH + margin && abs(side) <= TUNNEL_HALF_WIDTH
    }

    // ------------------------------------------------------------------ adding and removing chunks

    /** Takes the drivable triangles out of a loaded chunk model. Replaces what [key] held before. */
    fun addModel(key: String, model: Model) {
        remove(key)
        val out = FloatList()
        val ground = FloatList()
        val data = MeshData()
        for (i in 0 until model.nodes.size) collectNode(model.nodes[i], out, ground, data)
        if (out.size > 0) patches.add(buildPatch(key, out.toArray()))
        if (ground.size > 0) terrainPatches.add(buildPatch(key, ground.toArray()))
    }

    fun remove(key: String) {
        var i = patches.size - 1
        while (i >= 0) {
            if (patches[i].key == key) patches.removeAt(i)
            i--
        }
        i = terrainPatches.size - 1
        while (i >= 0) {
            if (terrainPatches[i].key == key) terrainPatches.removeAt(i)
            i--
        }
    }

    fun clear() {
        patches.clear()
        terrainPatches.clear()
    }

    private fun kindOf(materialId: String): Int {
        if (materialId.startsWith("m_tunnel_")) return KIND_TUNNEL
        if (materialId.startsWith("m_glen_")) {
            val number = materialId.substring(7)
            for (n in GLEN_ROAD_NUMBERS) if (number == n) return KIND_GLEN
            for (n in GLEN_DECK_NUMBERS) if (number == n) return KIND_DECK
            for (n in GLEN_TERRAIN_NUMBERS) if (number == n) return KIND_TERRAIN
        }
        return KIND_NONE
    }

    private fun collectNode(node: Node, out: FloatList, ground: FloatList, data: MeshData) {
        for (i in 0 until node.parts.size) {
            val part = node.parts[i]
            val kind = kindOf(part.material.id)
            if (kind == KIND_NONE) continue
            if (part.meshPart.primitiveType != GL20.GL_TRIANGLES) continue
            collectPart(part.meshPart.mesh, part.meshPart.offset, part.meshPart.size, kind, out, ground, data)
        }
        for (child in node.children) collectNode(child, out, ground, data)
    }

    private fun collectPart(
        mesh: Mesh, offset: Int, count: Int, kind: Int, roadOut: FloatList, ground: FloatList, data: MeshData
    ) {
        // Road and tunnel triangles go to the road list, the desert ground to its own list.
        val out = if (kind == KIND_TERRAIN) ground else roadOut
        if (data.mesh !== mesh) {
            data.mesh = mesh
            data.stride = mesh.vertexSize / 4
            data.verts = FloatArray(mesh.numVertices * data.stride)
            mesh.getVertices(data.verts)
            data.idx = ShortArray(mesh.numIndices)
            mesh.getIndices(data.idx, 0)
            data.posOffset = mesh.getVertexAttribute(VertexAttributes.Usage.Position).offset / 4
            data.normalOffset = mesh.getVertexAttribute(VertexAttributes.Usage.Normal).offset / 4
        }
        val v = data.verts
        val idx = data.idx
        val stride = data.stride
        for (t in 0 until count / 3) {
            val i0 = idx[offset + 3 * t].toInt() and 0xFFFF
            val i1 = idx[offset + 3 * t + 1].toInt() and 0xFFFF
            val i2 = idx[offset + 3 * t + 2].toInt() and 0xFFFF
            val p0 = i0 * stride + data.posOffset
            val p1 = i1 * stride + data.posOffset
            val p2 = i2 * stride + data.posOffset

            // Faces that look up are road, floor looks up too; ceilings and walls face down or sideways.
            // The vertex normals decide, never the winding (the materials are double-sided).
            val normalY = (v[i0 * stride + data.normalOffset + 1] +
                v[i1 * stride + data.normalOffset + 1] +
                v[i2 * stride + data.normalOffset + 1]) / 3f
            val x1 = v[p0]
            val y1 = v[p0 + 1]
            val z1 = v[p0 + 2]
            val x2 = v[p1]
            val y2 = v[p1 + 1]
            val z2 = v[p1 + 2]
            val x3 = v[p2]
            val y3 = v[p2 + 1]
            val z3 = v[p2 + 2]
            if (kind == KIND_GLEN) {
                if (normalY <= 0.6f) continue
            } else if (kind == KIND_DECK) {
                if (normalY <= 0.95f) continue    // only faces that are nearly level: the roadway, not the walls
            } else if (kind == KIND_TERRAIN) {
                if (normalY <= 0.85f) continue    // steeper than about 32 degrees is a wall, not ground
            } else {
                if (normalY <= 0.85f) continue
                if (!inCorridor((x1 + x2 + x3) / 3f, (z1 + z2 + z3) / 3f, 2f)) continue
            }

            // The triangle's own normal, turned to look up.
            val e1x = x2 - x1
            val e1y = y2 - y1
            val e1z = z2 - z1
            val e2x = x3 - x1
            val e2y = y3 - y1
            val e2z = z3 - z1
            var nx = e1y * e2z - e1z * e2y
            var ny = e1z * e2x - e1x * e2z
            var nz = e1x * e2y - e1y * e2x
            val len = sqrt(nx * nx + ny * ny + nz * nz)
            if (len < 1e-6f) continue
            nx /= len
            ny /= len
            nz /= len
            if (ny < 0f) {
                nx = -nx
                ny = -ny
                nz = -nz
            }
            val denominator = (z2 - z3) * (x1 - x3) + (x3 - x2) * (z1 - z3)
            if (abs(denominator) < 1e-6f) continue    // seen from above it is only a line

            out.add(x1); out.add(y1); out.add(z1)
            out.add(x2); out.add(y2); out.add(z2)
            out.add(x3); out.add(y3); out.add(z3)
            out.add(1f / denominator)
            out.add(nx); out.add(ny); out.add(nz)
        }
    }

    private fun buildPatch(key: String, tri: FloatArray): Patch {
        val count = tri.size / STRIDE
        var minX = Float.MAX_VALUE
        var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxZ = -Float.MAX_VALUE
        for (t in 0 until count) {
            val o = t * STRIDE
            for (k in 0 until 3) {
                val x = tri[o + 3 * k]
                val z = tri[o + 3 * k + 2]
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (z < minZ) minZ = z
                if (z > maxZ) maxZ = z
            }
        }
        val cols = ((maxX - minX) * INV_CELL).toInt() + 1
        val rows = ((maxZ - minZ) * INV_CELL).toInt() + 1
        val cellStart = IntArray(cols * rows + 1)

        // Every triangle goes into every cell its box touches: count first, then fill.
        for (pass in 0..1) {
            val cursor = if (pass == 1) cellStart.copyOf() else cellStart
            val items = if (pass == 1) IntArray(cellStart[cols * rows]) else null
            for (t in 0 until count) {
                val o = t * STRIDE
                var tMinX = Float.MAX_VALUE
                var tMaxX = -Float.MAX_VALUE
                var tMinZ = Float.MAX_VALUE
                var tMaxZ = -Float.MAX_VALUE
                for (k in 0 until 3) {
                    val x = tri[o + 3 * k]
                    val z = tri[o + 3 * k + 2]
                    if (x < tMinX) tMinX = x
                    if (x > tMaxX) tMaxX = x
                    if (z < tMinZ) tMinZ = z
                    if (z > tMaxZ) tMaxZ = z
                }
                val c0 = ((tMinX - minX) * INV_CELL).toInt().coerceIn(0, cols - 1)
                val c1 = ((tMaxX - minX) * INV_CELL).toInt().coerceIn(0, cols - 1)
                val r0 = ((tMinZ - minZ) * INV_CELL).toInt().coerceIn(0, rows - 1)
                val r1 = ((tMaxZ - minZ) * INV_CELL).toInt().coerceIn(0, rows - 1)
                for (r in r0..r1) {
                    for (c in c0..c1) {
                        val cell = r * cols + c
                        if (pass == 0) {
                            cellStart[cell + 1]++
                        } else {
                            items!![cursor[cell]] = t
                            cursor[cell]++
                        }
                    }
                }
            }
            if (pass == 0) {
                for (i in 1..cols * rows) cellStart[i] += cellStart[i - 1]
            } else {
                return Patch(key, tri, minX, minZ, maxX, maxZ, cols, rows, cellStart, items!!)
            }
        }
        throw IllegalStateException("unreachable")
    }

    // ------------------------------------------------------------------ questions

    /**
     * Finds the surface under (x, z): the highest one that is at most [STEP_UP] above [refY] and at most
     * [maxDrop] below it. Leaves its height and normal in the sample fields.
     */
    private fun sample(x: Float, z: Float, refY: Float, maxDrop: Float): Boolean =
        sampleIn(patches, x, z, refY, maxDrop, Float.MAX_VALUE)

    /** The same question for one list of patches. Surfaces higher than [capY] are ignored. */
    private fun sampleIn(
        list: ArrayList<Patch>, x: Float, z: Float, refY: Float, maxDrop: Float, capY: Float
    ): Boolean {
        var found = false
        var bestY = -Float.MAX_VALUE
        val maxY = min(refY + STEP_UP, capY)
        val minY = refY - maxDrop
        for (pi in 0 until list.size) {
            val p = list[pi]
            if (x < p.minX || x > p.maxX || z < p.minZ || z > p.maxZ) continue
            val col = ((x - p.minX) * INV_CELL).toInt().coerceIn(0, p.cols - 1)
            val row = ((z - p.minZ) * INV_CELL).toInt().coerceIn(0, p.rows - 1)
            val cell = row * p.cols + col
            val t = p.tri
            var k = p.cellStart[cell]
            val end = p.cellStart[cell + 1]
            while (k < end) {
                val o = p.cellItems[k] * STRIDE
                k++
                val x1 = t[o]
                val z1 = t[o + 2]
                val x2 = t[o + 3]
                val z2 = t[o + 5]
                val x3 = t[o + 6]
                val z3 = t[o + 8]
                val inv = t[o + 9]
                val l1 = ((z2 - z3) * (x - x3) + (x3 - x2) * (z - z3)) * inv
                val l2 = ((z3 - z1) * (x - x3) + (x1 - x3) * (z - z3)) * inv
                val l3 = 1f - l1 - l2
                if (l1 < -EDGE_TOLERANCE || l2 < -EDGE_TOLERANCE || l3 < -EDGE_TOLERANCE) continue
                val y = l1 * t[o + 1] + l2 * t[o + 4] + l3 * t[o + 7]
                if (y > maxY || y < minY || y <= bestY) continue
                bestY = y
                found = true
                sampleNx = t[o + 10]
                sampleNy = t[o + 11]
                sampleNz = t[o + 12]
            }
        }
        if (found) sampleY = bestY
        return found
    }

    /** Height of the best surface under (x, z) for a thing standing at [refY], or NaN when there is none. */
    fun heightAt(x: Float, z: Float, refY: Float, maxDrop: Float = NO_LIMIT): Float =
        if (sample(x, z, refY, maxDrop)) sampleY else Float.NaN

    /** Puts the surface normal (pointing up) into [out]. Returns false, leaving [out] alone, when there is no road. */
    fun normalAt(x: Float, z: Float, refY: Float, out: Vector3, maxDrop: Float = NO_LIMIT): Boolean {
        if (!sample(x, z, refY, maxDrop)) return false
        out.set(sampleNx, sampleNy, sampleNz)
        return true
    }

    fun isRoad(x: Float, z: Float, refY: Float, maxDrop: Float = NO_LIMIT): Boolean =
        sample(x, z, refY, maxDrop)

    /**
     * The road point nearest to (x, z), looking outwards in rings of 1 m up to [maxRadius]. [out] gets the
     * point with the height of the highest surface there. Returns false when there is no road that close.
     */
    fun findNearestRoad(x: Float, z: Float, maxRadius: Float, refY: Float, out: Vector3): Boolean {
        if (sample(x, z, refY, NO_LIMIT)) {
            out.set(x, sampleY, z)
            return true
        }
        var r = 1
        while (r <= maxRadius) {
            val steps = minOf(64, 8 * r)
            for (s in 0 until steps) {
                val a = s * 6.2831855f / steps
                val px = x + cos(a) * r
                val pz = z + sin(a) * r
                if (sample(px, pz, refY, NO_LIMIT)) {
                    out.set(px, sampleY, pz)
                    return true
                }
            }
            r++
        }
        return false
    }

    // ------------------------------------------------------------------ what the car drives on

    /**
     * Height of the ground under (x, z) for the player's car, or NaN when there is none. In order: the
     * road and the scanned tunnel floor (without rubble the car could not climb), then the fitted tunnel
     * floor where the scan has a hole, then the desert ground. [lastKind] says which one answered.
     */
    fun carHeightAt(x: Float, z: Float, refY: Float, maxDrop: Float): Float {
        val dx = x - JOIN_X
        val dz = z - JOIN_Z
        val along = dx * TUNNEL_DIR_X + dz * TUNNEL_DIR_Z
        val side = dx * TUNNEL_DIR_Z - dz * TUNNEL_DIR_X
        val inFloorZone = along >= 0f && along <= VIRTUAL_END && side >= VIRTUAL_SIDE_MIN && side <= VIRTUAL_SIDE_MAX
        val fitted = if (inFloorZone) floorProfile(along) else 0f
        val cap = if (inFloorZone) fitted + RUBBLE_MAX else Float.MAX_VALUE

        if (sampleIn(patches, x, z, refY, maxDrop, cap)) {
            lastKind = if (inCorridor(x, z, 0f)) SURFACE_TUNNEL else SURFACE_ROAD
            return sampleY
        }
        if (inFloorZone) {
            val y = fitted + tunnelRough(along, side)
            if (y <= refY + STEP_UP && y >= refY - maxDrop) {
                lastKind = SURFACE_TUNNEL
                return y
            }
        }
        if (sampleIn(terrainPatches, x, z, refY, maxDrop, Float.MAX_VALUE)) {
            lastKind = SURFACE_TERRAIN
            return sampleY + terrainRough(x, z)
        }
        return Float.NaN
    }

    /** Like [findNearestRoad], but the desert ground counts too. Used when the car is on the sand. */
    fun carFindNearest(x: Float, z: Float, maxRadius: Float, refY: Float, out: Vector3): Boolean {
        val h = carHeightAt(x, z, refY, NO_LIMIT)
        if (!h.isNaN()) {
            out.set(x, h, z)
            return true
        }
        var r = 1
        while (r <= maxRadius) {
            val steps = minOf(64, 8 * r)
            for (s in 0 until steps) {
                val a = s * 6.2831855f / steps
                val px = x + cos(a) * r
                val pz = z + sin(a) * r
                val ph = carHeightAt(px, pz, refY, NO_LIMIT)
                if (!ph.isNaN()) {
                    out.set(px, ph, pz)
                    return true
                }
            }
            r++
        }
        return false
    }

    /** The fitted tunnel floor height [along] metres in from the mouth. */
    private fun floorProfile(along: Float): Float {
        val f = (along * 0.1f).coerceIn(0f, TUNNEL_FLOOR.size - 1.001f)
        val i = f.toInt()
        return TUNNEL_FLOOR[i] + (TUNNEL_FLOOR[i + 1] - TUNNEL_FLOOR[i]) * (f - i)
    }

    /** Small fixed bumps on the fitted tunnel floor, like the rubble of the scanned part. */
    private fun tunnelRough(along: Float, side: Float): Float =
        0.05f * sin(along * 1.7f + side * 0.9f) +
            0.04f * sin(along * 3.1f - side * 1.3f) +
            0.03f * sin(along * 0.55f + side * 2.2f)

    /** Rolling ground plus a small washboard on the desert, the same every time for the same spot. */
    private fun terrainRough(x: Float, z: Float): Float {
        val rolling = sin(x * 0.21f + z * 0.13f) * 0.5f + sin(z * 0.37f - x * 0.29f) * 0.35f
        val washboard = sin(x * 1.3f + z * 0.9f) * 0.12f + sin(z * 1.9f - x * 1.1f) * 0.08f
        return (rolling + washboard) * TERRAIN_BUMP - TERRAIN_SINK
    }
}
