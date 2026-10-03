package com.westly.lagosdrift

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Draws the world as a flat map from TownLayout: a small turning map in the corner and the big
 * north-up map that scrolls and zooms (see MapView). Land is tinted by district, roads by surface,
 * built-up areas are white blocks that turn into single buildings when you zoom in, and
 * potholes show as dark dots when you zoom right in.
 * Call between shapeRenderer.begin(Filled) and end().
 */
class MapRenderer {
    private val ikoyiLand = Color(0.17f, 0.46f, 0.22f, 1f)
    private val ikoroduLand = Color(0.36f, 0.42f, 0.20f, 1f)
    private val sand = Color(0.90f, 0.82f, 0.55f, 1f)
    private val water = Color(0.34f, 0.46f, 0.60f, 1f)
    private val ikoyiBlock = Color(0.92f, 0.95f, 0.98f, 1f)
    private val ikoroduBlock = Color(0.95f, 0.86f, 0.66f, 1f)
    private val roadSmooth = Color(0.06f, 0.06f, 0.07f, 1f)
    private val roadWorn = Color(0.20f, 0.19f, 0.18f, 1f)
    private val roadRough = Color(0.55f, 0.40f, 0.20f, 1f)
    private val roadDirt = Color(0.68f, 0.56f, 0.34f, 1f)
    private val potholeDot = Color(0.10f, 0.08f, 0.06f, 1f)
    private val miniBackground = Color(0.12f, 0.30f, 0.18f, 1f)

    // Current drawing mode, set by drawMini / drawFull.
    private var rotating = false
    private var carX = 0f
    private var carZ = 0f
    private var sinYaw = 0f
    private var cosYaw = 1f
    private var scale = 1f
    private var originX = 0f      // mini: screen position of the car. full: screen x of world x = 0
    private var originY = 0f      // mini: screen position of the car. full: screen y of world z = 0
    private var outX = 0f
    private var outY = 0f

    // Only things that land inside this screen rectangle are drawn.
    private var clipX0 = 0f
    private var clipY0 = 0f
    private var clipX1 = 1f
    private var clipY1 = 1f

    /** Small map that turns so the car always points up. size is the square's side in pixels. */
    fun drawMini(shapes: ShapeRenderer, car: Vector3, yawDegrees: Float, x: Float, y: Float, size: Float) {
        rotating = true
        carX = car.x
        carZ = car.z
        val rad = yawDegrees * MathUtils.degreesToRadians
        sinYaw = sin(rad)
        cosYaw = cos(rad)
        scale = size / MINI_SPAN
        originX = x + size / 2f
        originY = y + size / 2f
        clipX0 = x
        clipY0 = y
        clipX1 = x + size
        clipY1 = y + size

        shapes.setColor(miniBackground)
        shapes.rect(x, y, size, size)
        val reach = MINI_SPAN * 0.85f
        drawWorld(shapes, car.x - reach, car.x + reach, car.z - reach, car.z + reach)

        // The car: always pointing up, with a white outline.
        val s = size * 0.07f
        val cx = originX
        val cy = originY
        shapes.setColor(1f, 1f, 1f, 1f)
        shapes.triangle(cx, cy + s * 1.25f, cx - s * 0.8f, cy - s * 0.95f, cx + s * 0.8f, cy - s * 0.95f)
        shapes.setColor(0.9f, 0.1f, 0.1f, 1f)
        shapes.triangle(cx, cy + s, cx - s * 0.6f, cy - s * 0.7f, cx + s * 0.6f, cy - s * 0.7f)
    }

    /** A thin frame round the small map. */
    fun drawFrame(shapes: ShapeRenderer, x: Float, y: Float, size: Float) {
        val t = size * 0.012f
        shapes.setColor(1f, 1f, 1f, 0.9f)
        shapes.rect(x, y, size, t)
        shapes.rect(x, y + size - t, size, t)
        shapes.rect(x, y, t, size)
        shapes.rect(x + size - t, y, t, size)
    }

    /** The big map, north at the top, showing whatever part of the world [view] is looking at. */
    fun drawFull(shapes: ShapeRenderer, view: MapView, car: Vector3, yawDegrees: Float) {
        val w = view.width
        val h = view.height
        shapes.setColor(0.04f, 0.06f, 0.09f, 1f)
        shapes.rect(0f, 0f, w, h)

        rotating = false
        scale = view.scale
        originX = view.originX
        originY = view.originY
        clipX0 = 0f
        clipY0 = 0f
        clipX1 = w
        clipY1 = h

        val halfW = w / 2f / scale
        val halfH = h / 2f / scale
        drawWorld(
            shapes,
            view.centreX - halfW, view.centreX + halfW,
            view.centreZ - halfH, view.centreZ + halfH
        )

        // The car, pointing the way it faces (north is up).
        project(car.x, car.z)
        val rad = yawDegrees * MathUtils.degreesToRadians
        val dirX = -sin(rad)
        val dirY = cos(rad)
        val s = h * 0.03f
        val px = outX
        val py = outY
        arrow(shapes, px, py, dirX, dirY, s * 1.25f, Color.WHITE)
        arrow(shapes, px, py, dirX, dirY, s, Color(0.9f, 0.1f, 0.1f, 1f))
    }

    private fun arrow(shapes: ShapeRenderer, x: Float, y: Float, dirX: Float, dirY: Float, s: Float, color: Color) {
        val len = sqrt(dirX * dirX + dirY * dirY)
        val dx = dirX / len
        val dy = dirY / len
        val perpX = -dy
        val perpY = dx
        shapes.setColor(color)
        shapes.triangle(
            x + dx * s, y + dy * s,
            x - dx * s * 0.7f + perpX * s * 0.6f, y - dy * s * 0.7f + perpY * s * 0.6f,
            x - dx * s * 0.7f - perpX * s * 0.6f, y - dy * s * 0.7f - perpY * s * 0.6f
        )
    }

    /** Draws the part of the world inside the given world rectangle. */
    private fun drawWorld(shapes: ShapeRenderer, minWx: Float, maxWx: Float, minWz: Float, maxWz: Float) {
        // Land by district, then the beach and the lagoon on the east side.
        shapes.setColor(ikoyiLand)
        fillRect(shapes, TownLayout.MIN_X, Districts.BORDER_Z, TownLayout.BEACH_X0, TownLayout.MAX_Z)
        shapes.setColor(ikoroduLand)
        fillRect(shapes, TownLayout.MIN_X, TownLayout.MIN_Z, TownLayout.BEACH_X0, Districts.BORDER_Z)
        shapes.setColor(sand)
        fillRect(shapes, TownLayout.BEACH_X0, TownLayout.MIN_Z, TownLayout.BEACH_X1, TownLayout.MAX_Z)
        shapes.setColor(water)
        fillRect(shapes, TownLayout.BEACH_X1, TownLayout.MIN_Z, TownLayout.MAX_X + 2000f, TownLayout.MAX_Z)

        val cx0 = TownLayout.cxOf(minWx).coerceIn(0, TownLayout.CHUNKS_X - 1)
        val cx1 = TownLayout.cxOf(maxWx).coerceIn(0, TownLayout.CHUNKS_X - 1)
        val cz0 = TownLayout.czOf(minWz).coerceIn(0, TownLayout.CHUNKS_Z - 1)
        val cz1 = TownLayout.czOf(maxWz).coerceIn(0, TownLayout.CHUNKS_Z - 1)
        val showBuildings = scale >= BUILDING_SCALE

        // Built-up areas: single buildings when zoomed in, white blocks when zoomed out.
        for (cz in cz0..cz1) {
            for (cx in cx0..cx1) {
                val chunk = TownLayout.chunkAt(cx, cz) ?: continue
                if (showBuildings) {
                    for (h in chunk.houses) {
                        shapes.setColor(TownPalette.wall(h))
                        fillRect(shapes, h.cx - h.hw, h.cz - h.hd, h.cx + h.hw, h.cz + h.hd)
                    }
                } else if (chunk.houses.size >= 5) {
                    shapes.setColor(if (chunk.district == Districts.IKOYI) ikoyiBlock else ikoroduBlock)
                    fillRect(shapes, chunk.minX + 3f, chunk.minZ + 3f, chunk.minX + TownLayout.CHUNK - 3f, chunk.minZ + TownLayout.CHUNK - 3f)
                }
            }
        }

        // Roads, cut at the chunk edges so each piece is drawn once, and never thinner than 2.5 px.
        val minHalf = 1.25f / scale
        for (cz in cz0..cz1) {
            for (cx in cx0..cx1) {
                val chunk = TownLayout.chunkAt(cx, cz) ?: continue
                val x0 = chunk.minX
                val x1 = chunk.minX + TownLayout.CHUNK
                val z0 = chunk.minZ
                val z1 = chunk.minZ + TownLayout.CHUNK
                for (r in chunk.roads) {
                    shapes.setColor(roadColor(r.surface))
                    val half = max(r.half, minHalf)
                    if (r.vertical) {
                        fillRect(shapes, max(r.centre - half, x0), max(r.start, z0), min(r.centre + half, x1), min(r.end, z1))
                    } else {
                        fillRect(shapes, max(r.start, x0), max(r.centre - half, z0), min(r.end, x1), min(r.centre + half, z1))
                    }
                }
            }
        }

        // Potholes as dark dots, only when zoomed right in.
        if (scale >= POTHOLE_SCALE) {
            shapes.setColor(potholeDot)
            val minR = 1.4f / scale
            for (cz in cz0..cz1) {
                for (cx in cx0..cx1) {
                    val chunk = TownLayout.chunkAt(cx, cz) ?: continue
                    for (p in chunk.potholes) {
                        val r = max(p.r, minR)
                        fillRect(shapes, p.x - r, p.z - r, p.x + r, p.z + r)
                    }
                }
            }
        }
    }

    private fun roadColor(surface: Int): Color = when (surface) {
        TownLayout.SURFACE_SMOOTH -> roadSmooth
        TownLayout.SURFACE_WORN -> roadWorn
        TownLayout.SURFACE_POTHOLED -> roadRough
        else -> roadDirt
    }

    /** Fills a rectangle given in world coordinates (it may turn on the small map). */
    private fun fillRect(shapes: ShapeRenderer, x0: Float, z0: Float, x1: Float, z1: Float) {
        if (x1 <= x0 || z1 <= z0) return
        project(x0, z0)
        val ax = outX
        val ay = outY
        project(x1, z0)
        val bx = outX
        val by = outY
        project(x1, z1)
        val cx = outX
        val cy = outY
        project(x0, z1)
        val dx = outX
        val dy = outY

        // Skip rectangles that are completely off the screen (or off the small map).
        if (max(max(ax, bx), max(cx, dx)) < clipX0 || min(min(ax, bx), min(cx, dx)) > clipX1) return
        if (max(max(ay, by), max(cy, dy)) < clipY0 || min(min(ay, by), min(cy, dy)) > clipY1) return

        shapes.triangle(ax, ay, bx, by, cx, cy)
        shapes.triangle(ax, ay, cx, cy, dx, dy)
    }

    private fun project(wx: Float, wz: Float) {
        if (rotating) {
            val dx = wx - carX
            val dz = wz - carZ
            val right = dx * cosYaw - dz * sinYaw
            val up = -dx * sinYaw - dz * cosYaw
            outX = originX + right * scale
            outY = originY + up * scale
        } else {
            outX = originX + wx * scale
            outY = originY - wz * scale
        }
    }

    private companion object {
        const val MINI_SPAN = 200f      // metres shown across the small map
        const val BUILDING_SCALE = 1.6f // pixels per metre at which single buildings appear
        const val POTHOLE_SCALE = 2.4f  // pixels per metre at which potholes appear
    }
}
