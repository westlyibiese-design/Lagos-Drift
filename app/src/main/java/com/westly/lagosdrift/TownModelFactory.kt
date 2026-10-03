package com.westly.lagosdrift

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Builds the model for ONE 100 m chunk of the world: ground, roads (with their surface colour),
 * pavements, potholes, street lights, buildings, market stalls and trees.
 *
 * Colours are stored in the vertices, so a whole chunk is only a few draw calls. A chunk is built
 * either "detailed" (windows, doors, balconies, lamp heads, dashes) for chunks near the car, or
 * simple (walls and roofs only) for chunks far away.
 */
object TownModelFactory {
    private val trim = Color(0.96f, 0.96f, 0.96f, 1f)
    private val tank = Color(0.14f, 0.18f, 0.28f, 1f)
    private val door = Color(0.30f, 0.20f, 0.14f, 1f)
    private val glass = Color(0.30f, 0.46f, 0.64f, 1f)
    private val trunk = Color(0.40f, 0.27f, 0.16f, 1f)
    private val palmTrunk = Color(0.62f, 0.50f, 0.34f, 1f)
    private val frond = Color(0.15f, 0.55f, 0.20f, 1f)
    private val pole = Color(0.22f, 0.23f, 0.26f, 1f)
    private val lampHead = Color(1.0f, 0.96f, 0.72f, 1f)
    private val dashFresh = Color(0.95f, 0.95f, 0.90f, 1f)
    private val dashFaded = Color(0.74f, 0.73f, 0.67f, 1f)
    private val stallPost = Color(0.30f, 0.30f, 0.32f, 1f)

    /** Hands out parts, starting a fresh one every [every] items so no part gets too big. */
    private class PartSource(private val b: ModelBuilder, private val name: String, private val every: Int) {
        private var part: MeshPartBuilder? = null
        private var used = 0
        private var serial = 0

        fun next(): MeshPartBuilder {
            val existing = part
            if (existing != null && used < every) {
                used++
                return existing
            }
            val fresh = b.vertexPart("$name${serial++}")
            part = fresh
            used = 1
            return fresh
        }
    }

    /** Height of the top of a road surface. Better roads sit a little higher, so crossings look right. */
    fun roadTop(surface: Int): Float = when (surface) {
        TownLayout.SURFACE_SMOOTH -> 0.08f
        TownLayout.SURFACE_WORN -> 0.06f
        TownLayout.SURFACE_POTHOLED -> 0.04f
        else -> 0.02f
    }

    fun buildChunk(chunk: TownLayout.Chunk, detailed: Boolean): Model {
        val b = ModelBuilder()
        b.begin()
        b.node()

        addGround(b, chunk)
        addRoads(b, chunk, detailed)
        addPavements(b, chunk)
        addPotholes(b, chunk, detailed)
        addLamps(b, chunk, detailed)

        val buildings = PartSource(b, "buildings", 8)
        for (h in chunk.houses) {
            val part = buildings.next()
            if (h.type == TownLayout.TYPE_STALL) addStall(part, h, detailed) else addBuilding(part, h, detailed)
        }

        val trees = PartSource(b, "trees", 24)
        for (t in chunk.trees) addTree(trees.next(), t, detailed)

        return b.end()
    }

    // ------------------------------------------------------------------ helpers

    /** A box covering a world rectangle, cut off at the chunk edges. */
    private fun MeshPartBuilder.rectBox(
        chunk: TownLayout.Chunk, x0: Float, z0: Float, x1: Float, z1: Float, y: Float, height: Float
    ) {
        val a = max(x0, chunk.minX)
        val c = min(x1, chunk.minX + TownLayout.CHUNK)
        val d = max(z0, chunk.minZ)
        val e = min(z1, chunk.minZ + TownLayout.CHUNK)
        if (c - a < 0.05f || e - d < 0.05f) return
        boxAt((a + c) / 2f, y, (d + e) / 2f, c - a, height, e - d)
    }

    /** Puts a flat box on the front wall. width runs along the wall, thickness sticks out. */
    private fun MeshPartBuilder.facadeBoxAt(
        h: TownLayout.House, lateral: Float, y: Float, out: Float, thickness: Float, height: Float, width: Float
    ) {
        if (h.facingX != 0) {
            boxAt(h.cx + h.facingX * (h.hw + out), y, h.cz + lateral, thickness, height, width)
        } else {
            boxAt(h.cx + lateral, y, h.cz + h.facingZ * (h.hd + out), width, height, thickness)
        }
    }

    private fun lateralHalf(h: TownLayout.House): Float = if (h.facingX != 0) h.hd else h.hw

    // ------------------------------------------------------------------ ground and roads

    private fun addGround(b: ModelBuilder, chunk: TownLayout.Chunk) {
        val g = b.vertexPart("ground")
        val land = if (chunk.district == Districts.IKOYI) TownPalette.IKOYI_GROUND else TownPalette.IKORODU_GROUND
        val x0 = chunk.minX
        val x1 = chunk.minX + TownLayout.CHUNK
        val zc = chunk.minZ + TownLayout.CHUNK / 2f

        fun slice(from: Float, to: Float, color: Color, y: Float) {
            if (to - from < 0.05f) return
            g.setColor(color)
            g.boxAt((from + to) / 2f, y, zc, to - from, 0.2f, TownLayout.CHUNK)
        }

        if (x1 <= TownLayout.BEACH_X0) {
            slice(x0, x1, land, -0.1f)
        } else {
            slice(x0, min(x1, TownLayout.BEACH_X0), land, -0.1f)
            slice(max(x0, TownLayout.BEACH_X0), min(x1, TownLayout.BEACH_X1), TownPalette.SAND, -0.1f)
            slice(max(x0, TownLayout.BEACH_X1), x1, TownPalette.WATER, -0.12f)
        }
    }

    private fun addRoads(b: ModelBuilder, chunk: TownLayout.Chunk, detailed: Boolean) {
        if (chunk.roads.isEmpty()) return
        val p = b.vertexPart("roads")
        for (r in chunk.roads) {
            p.setColor(TownPalette.road(r.surface))
            p.rectBox(chunk, r.x0, r.z0, r.x1, r.z1, roadTop(r.surface) - 0.1f, 0.2f)
        }
        if (!detailed) return

        // Dashed centre lines on the good roads, left out where roads cross.
        for (r in chunk.roads) {
            if (r.surface < TownLayout.SURFACE_WORN) continue
            p.setColor(if (r.surface == TownLayout.SURFACE_SMOOTH) dashFresh else dashFaded)
            val lo = if (r.vertical) chunk.minZ else chunk.minX
            val hi = lo + TownLayout.CHUNK
            var t = ceil(max(r.start, lo) / 8f) * 8f
            val top = roadTop(r.surface)
            while (t < min(r.end, hi)) {
                var crossing = false
                for (o in chunk.roads) {
                    if (o === r) continue
                    val x = if (r.vertical) r.centre else t
                    val z = if (r.vertical) t else r.centre
                    if (x > o.x0 - 1.5f && x < o.x1 + 1.5f && z > o.z0 - 1.5f && z < o.z1 + 1.5f) {
                        crossing = true
                        break
                    }
                }
                if (!crossing) {
                    if (r.vertical) p.boxAt(r.centre, top + 0.01f, t, 0.25f, 0.02f, 3f)
                    else p.boxAt(t, top + 0.01f, r.centre, 3f, 0.02f, 0.25f)
                }
                t += 8f
            }
        }
    }

    private fun addPavements(b: ModelBuilder, chunk: TownLayout.Chunk) {
        var p: MeshPartBuilder? = null
        for (w in chunk.walkways) {
            if (!w.paved) continue
            var part = p
            if (part == null) {
                part = b.vertexPart("pavements")
                part.setColor(TownPalette.PAVEMENT)
                p = part
            }
            val x0 = min(w.x1, w.x2)
            val x1 = max(w.x1, w.x2)
            val z0 = min(w.z1, w.z2)
            val z1 = max(w.z1, w.z2)
            if (kotlin.math.abs(w.dirX) > 0.5f) part.rectBox(chunk, x0, z0 - 1.5f, x1, z0 + 1.5f, 0.08f, 0.16f)
            else part.rectBox(chunk, x0 - 1.5f, z0, x0 + 1.5f, z1, 0.08f, 0.16f)
        }
    }

    private fun addPotholes(b: ModelBuilder, chunk: TownLayout.Chunk, detailed: Boolean) {
        if (chunk.potholes.isEmpty()) return
        val p = b.vertexPart("potholes")
        for (h in chunk.potholes) {
            p.setColor(if (h.wet) TownPalette.PUDDLE else TownPalette.POTHOLE)
            val top = roadTop(RoadSurface.surfaceAt(h.x, h.z).coerceAtLeast(TownLayout.SURFACE_DIRT))
            p.cylinderAt(h.x, top + 0.02f, h.z, h.r * 2f, 0.04f, if (detailed) 10 else 6)
        }
    }

    private fun addLamps(b: ModelBuilder, chunk: TownLayout.Chunk, detailed: Boolean) {
        if (chunk.lamps.isEmpty()) return
        val p = b.vertexPart("lamps")
        for (l in chunk.lamps) {
            p.setColor(pole)
            p.cylinderAt(l.x, 2.6f, l.z, 0.18f, 5.2f, if (detailed) 8 else 5)
            if (!detailed) continue
            val armWidth = if (l.dirX != 0) 1.6f else 0.12f
            val armDepth = if (l.dirZ != 0) 1.6f else 0.12f
            p.boxAt(l.x + l.dirX * 0.8f, 5.15f, l.z + l.dirZ * 0.8f, armWidth, 0.12f, armDepth)
            p.setColor(lampHead)
            p.boxAt(l.x + l.dirX * 1.6f, 5.05f, l.z + l.dirZ * 1.6f, 0.5f, 0.15f, 0.5f)
        }
    }

    // ------------------------------------------------------------------ buildings

    private fun addBuilding(p: MeshPartBuilder, h: TownLayout.House, detailed: Boolean) {
        val ikoyi = h.district == Districts.IKOYI

        // Walls (towers get a plant room on top).
        p.setColor(TownPalette.wall(h))
        p.boxAt(h.cx, h.height / 2f, h.cz, h.hw * 2f, h.height, h.hd * 2f)
        if (h.type == TownLayout.TYPE_TOWER) {
            p.boxAt(h.cx, h.height + 1.5f, h.cz, h.hw * 1.1f, 2.2f, h.hd * 1.1f)
        }

        // Roof: pitched on bungalows and (in Ikorodu) houses, flat on everything else.
        p.setColor(TownPalette.roof(h))
        val pitched = h.type == TownLayout.TYPE_BUNGALOW || (h.type == TownLayout.TYPE_HOUSE && !ikoyi)
        if (pitched) {
            p.boxAt(h.cx, h.height + 0.1f, h.cz, h.hw * 2f, 0.2f, h.hd * 2f)
            val rise = if (h.type == TownLayout.TYPE_HOUSE) 2.6f else 2.0f
            p.gableRoof(h.cx, h.height + 0.1f, h.cz, h.hw, h.hd, rise, h.facingX != 0, 0.5f)
        } else {
            p.boxAt(h.cx, h.height + 0.2f, h.cz, h.hw * 2f + 0.6f, 0.4f, h.hd * 2f + 0.6f)
        }

        if (!detailed) return
        val half = lateralHalf(h)

        // Shop awning and sign.
        if (h.type == TownLayout.TYPE_SHOP) {
            p.setColor(TownPalette.accent(h.accent))
            p.facadeBoxAt(h, 0f, 3.1f, 1.2f, 2.4f, 0.18f, half * 1.8f)
            p.setColor(TownPalette.accent(h.accent + 2))
            p.facadeBoxAt(h, 0f, 3.95f, 0.1f, 0.3f, 0.6f, half * 1.5f)
        }

        // White balconies on houses and towers.
        p.setColor(trim)
        if (h.type == TownLayout.TYPE_HOUSE) {
            p.facadeBoxAt(h, 0f, 3.1f, 0.55f, 1.1f, 0.15f, half * 1.7f)
        } else if (h.type == TownLayout.TYPE_TOWER) {
            val floors = max(2, (h.height / 3f).toInt())
            for (f in 1 until floors) p.facadeBoxAt(h, 0f, 3f * f + 0.1f, 0.55f, 1.1f, 0.15f, half * 1.7f)
        }

        // Water tank on top of Ikorodu towers.
        if (h.type == TownLayout.TYPE_TOWER && !ikoyi) {
            p.setColor(tank)
            p.cylinderAt(h.cx + h.hw * 0.25f, h.height + 3.6f, h.cz, 2.2f, 2.0f, 12)
        }

        // Door.
        p.setColor(door)
        p.facadeBoxAt(h, 0f, 1.1f, 0.02f, 0.1f, 2.2f, if (h.type == TownLayout.TYPE_SHOP) 1.8f else 1.5f)

        // Windows.
        p.setColor(glass)
        if (h.type == TownLayout.TYPE_SHOP) {
            for (lat in floatArrayOf(-0.6f * half, 0.6f * half)) {
                p.facadeBoxAt(h, lat, 1.9f, 0.02f, 0.1f, 2.0f, half * 0.5f)
            }
            return
        }
        val floors = when (h.type) {
            TownLayout.TYPE_BUNGALOW -> 1
            TownLayout.TYPE_HOUSE -> 2
            else -> max(2, (h.height / 3f).toInt())
        }
        for (floor in 0 until floors) {
            val y = 1.8f + floor * 3f
            if (ikoyi && h.type == TownLayout.TYPE_TOWER) {
                // Glass towers: one long band of windows per floor.
                p.facadeBoxAt(h, 0f, y, 0.02f, 0.1f, 1.4f, half * 1.7f)
                continue
            }
            val offsets = if (floor == 0) floatArrayOf(-0.55f * half, 0.55f * half)
            else floatArrayOf(-0.55f * half, 0f, 0.55f * half)
            for (lat in offsets) p.facadeBoxAt(h, lat, y, 0.02f, 0.1f, 1.3f, 1.4f)
        }
    }

    private fun addStall(p: MeshPartBuilder, h: TownLayout.House, detailed: Boolean) {
        p.setColor(TownPalette.wall(h))
        p.boxAt(h.cx, 0.45f, h.cz, h.hw * 2f, 0.9f, h.hd * 2f)
        p.setColor(TownPalette.accent(h.accent))
        p.boxAt(h.cx, 2.2f, h.cz, h.hw * 2f + 0.5f, 0.1f, h.hd * 2f + 0.5f)
        if (!detailed) return
        p.setColor(stallPost)
        for (sx in floatArrayOf(-1f, 1f)) {
            for (sz in floatArrayOf(-1f, 1f)) {
                p.boxAt(h.cx + sx * h.hw, 1.1f, h.cz + sz * h.hd, 0.08f, 2.2f, 0.08f)
            }
        }
        p.setColor(TownPalette.accent(h.accent + 3))
        p.boxAt(h.cx, 1.05f, h.cz, h.hw * 1.2f, 0.3f, h.hd * 1.2f)
    }

    // ------------------------------------------------------------------ trees

    private fun addTree(p: MeshPartBuilder, t: TownLayout.Tree, detailed: Boolean) {
        val s = t.scale
        if (t.kind == 0) {
            p.setColor(trunk)
            p.cylinderAt(t.x, 1.1f * s, t.z, 0.5f * s, 2.2f * s, if (detailed) 8 else 5)
            p.setColor(TownPalette.LEAVES[t.leafColor])
            p.coneAt(t.x, 2.0f * s + 2.25f * s, t.z, 3.4f * s, 4.5f * s, if (detailed) 9 else 6)
        } else {
            p.setColor(palmTrunk)
            p.cylinderAt(t.x, 2.75f * s, t.z, 0.38f * s, 5.5f * s, if (detailed) 8 else 5)
            p.setColor(frond)
            val leaves = if (detailed) 7 else 4
            for (i in 0 until leaves) {
                p.frondAt(t.x, 5.5f * s, t.z, i * (360f / leaves), 3.2f * s, 0.6f * s)
            }
        }
    }
}
