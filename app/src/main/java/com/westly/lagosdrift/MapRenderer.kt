package com.westly.lagosdrift

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Draws the town as a flat map from TownLayout: a small turning map in the corner and a
 * full north-up map. Roads, buildings in their own colours, beach and lagoon.
 * Call between shapeRenderer.begin(Filled) and end().
 */
class MapRenderer {
    private val grass = Color(0.30f, 0.58f, 0.25f, 1f)
    private val road = Color(0.45f, 0.45f, 0.48f, 1f)
    private val sand = Color(0.90f, 0.82f, 0.55f, 1f)
    private val water = Color(0.15f, 0.52f, 0.78f, 1f)
    private val miniBackground = Color(0.12f, 0.30f, 0.18f, 1f)

    // Current drawing mode, set by drawMini / drawFull.
    private var rotating = false
    private var carX = 0f
    private var carZ = 0f
    private var sinYaw = 0f
    private var cosYaw = 1f
    private var scale = 1f
    private var originX = 0f      // screen position of the car (mini) or of world x = FULL_MIN_X (full)
    private var originY = 0f      // screen position of the car (mini) or of world z = FULL_MIN_Z (full)
    private var waterEndX = 600f
    private var outX = 0f
    private var outY = 0f

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
        waterEndX = 600f

        shapes.setColor(miniBackground)
        shapes.rect(x, y, size, size)
        drawWorld(shapes)

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

    /** Whole town, north at the top, centred on the screen. */
    fun drawFull(shapes: ShapeRenderer, car: Vector3, yawDegrees: Float, screenWidth: Float, screenHeight: Float) {
        shapes.setColor(0.04f, 0.06f, 0.09f, 0.92f)
        shapes.rect(0f, 0f, screenWidth, screenHeight)

        rotating = false
        scale = screenHeight * 0.86f / (FULL_MAX_Z - FULL_MIN_Z)
        val mapWidth = (FULL_MAX_X - FULL_MIN_X) * scale
        val mapHeight = (FULL_MAX_Z - FULL_MIN_Z) * scale
        val left = (screenWidth - mapWidth) / 2f
        val bottom = (screenHeight - mapHeight) / 2f
        originX = left
        originY = bottom + mapHeight   // screen y of the north edge (world z = FULL_MIN_Z)
        waterEndX = FULL_MAX_X

        shapes.setColor(grass)
        shapes.rect(left, bottom, mapWidth, mapHeight)
        drawWorld(shapes)

        // The car, pointing the way it faces (north is up).
        project(car.x, car.z)
        val rad = yawDegrees * MathUtils.degreesToRadians
        val dirX = -sin(rad)
        val dirY = cos(rad)
        val px = outX
        val py = outY
        val s = screenHeight * 0.03f
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

    private fun drawWorld(shapes: ShapeRenderer) {
        // Beach and lagoon on the east side.
        shapes.setColor(sand)
        fillRect(shapes, 160f, -600f, 175f, 600f)
        shapes.setColor(water)
        fillRect(shapes, 175f, -600f, waterEndX, 600f)

        // Roads.
        shapes.setColor(road)
        fillRect(shapes, -TownLayout.ROAD_HALF, -300f, TownLayout.ROAD_HALF, 300f)
        for (czr in TownLayout.crossRoadsZ) {
            fillRect(
                shapes, TownLayout.CROSS_MIN_X, czr - TownLayout.CROSS_HALF,
                TownLayout.CROSS_MAX_X, czr + TownLayout.CROSS_HALF
            )
        }

        // Buildings in their own colours.
        for (h in TownLayout.houses) {
            shapes.setColor(TownPalette.WALLS[h.wallColor])
            fillRect(shapes, h.cx - h.hw, h.cz - h.hd, h.cx + h.hw, h.cz + h.hd)
        }
    }

    /** Fills a rectangle given in world coordinates (it may turn on the small map). */
    private fun fillRect(shapes: ShapeRenderer, x0: Float, z0: Float, x1: Float, z1: Float) {
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
            outX = originX + (wx - FULL_MIN_X) * scale
            outY = originY - (wz - FULL_MIN_Z) * scale
        }
    }

    private companion object {
        const val MINI_SPAN = 200f      // metres shown across the small map
        const val FULL_MIN_X = -300f
        const val FULL_MAX_X = 200f
        const val FULL_MIN_Z = -300f
        const val FULL_MAX_Z = 300f
    }
}
