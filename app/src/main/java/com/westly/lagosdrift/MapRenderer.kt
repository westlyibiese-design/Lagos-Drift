package com.westly.lagosdrift

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Draws the flat maps: a small turning map in the corner and the big north-up map that scrolls and
 * zooms (see MapView). Both show the player's arrow and the Camry when it is left parked.
 * Call between shapeRenderer.begin(Filled) and end().
 */
class MapRenderer {
    private val sand = Color(0.90f, 0.82f, 0.55f, 1f)
    private val bigBackground = Color(0.04f, 0.06f, 0.09f, 1f)

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
    fun drawMini(
        shapes: ShapeRenderer, car: Vector3, yawDegrees: Float, x: Float, y: Float, size: Float,
        parked: List<Vector3>? = null
    ) {
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

        shapes.setColor(sand)
        shapes.rect(x, y, size, size)

        if (parked != null) for (p in parked) parkedMarker(shapes, p, size * 0.05f)

        // The player: always pointing up, with a white outline.
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
    fun drawFull(
        shapes: ShapeRenderer, view: MapView, car: Vector3, yawDegrees: Float,
        parked: List<Vector3>? = null
    ) {
        val w = view.width
        val h = view.height
        shapes.setColor(bigBackground)
        shapes.rect(0f, 0f, w, h)

        rotating = false
        scale = view.scale
        originX = view.originX
        originY = view.originY
        clipX0 = 0f
        clipY0 = 0f
        clipX1 = w
        clipY1 = h

        // The sand land: the area inside the world limits.
        shapes.setColor(sand)
        project(WorldLimits.MIN_X, WorldLimits.MAX_Z)
        val left = outX.coerceIn(0f, w)
        val top = outY.coerceIn(0f, h)
        project(WorldLimits.MAX_X, WorldLimits.MIN_Z)
        val right = outX.coerceIn(0f, w)
        val bottom = outY.coerceIn(0f, h)
        if (right > left && top > bottom) shapes.rect(left, bottom, right - left, top - bottom)

        if (parked != null) for (p in parked) parkedMarker(shapes, p, h * 0.022f)

        // The player, pointing the way they face (north is up).
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

    /** A yellow square with a dark outline: your Camry, left parked. */
    private fun parkedMarker(shapes: ShapeRenderer, parked: Vector3, half: Float) {
        project(parked.x, parked.z)
        if (outX < clipX0 || outX > clipX1 || outY < clipY0 || outY > clipY1) return
        shapes.setColor(0.05f, 0.05f, 0.05f, 1f)
        shapes.rect(outX - half * 1.25f, outY - half * 1.25f, half * 2.5f, half * 2.5f)
        shapes.setColor(0.98f, 0.80f, 0.10f, 1f)
        shapes.rect(outX - half, outY - half, half * 2f, half * 2f)
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
    }
}
