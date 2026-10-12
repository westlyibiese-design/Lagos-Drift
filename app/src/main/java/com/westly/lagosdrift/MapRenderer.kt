package com.westly.lagosdrift

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Draws the flat maps: a small turning map in the corner and the big north-up map that scrolls and
 * zooms (see MapView). Both show the sand land, the road (the picture MapBaker baked, light grey), the
 * player's arrow and the Camry when it is left parked.
 *
 * drawMini and drawFull open and close the ShapeRenderer and the SpriteBatch themselves (the road
 * picture needs the batch), so call them while neither is begun. [drawFrame] is the exception: call it
 * between shapeRenderer.begin(Filled) and end().
 */
class MapRenderer(private val baker: MapBaker) {
    private val sand = Color(0.90f, 0.82f, 0.55f, 1f)
    private val bigBackground = Color(0.04f, 0.06f, 0.09f, 1f)
    private val arrowRed = Color(0.9f, 0.1f, 0.1f, 1f)

    // The road picture as one quad: 4 vertices of x, y, colour, u, v. Made once, reused every frame.
    private val quad = FloatArray(20)
    private val whiteBits = Color.WHITE.toFloatBits()

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
        shapes: ShapeRenderer, batch: SpriteBatch, car: Vector3, yawDegrees: Float, x: Float, y: Float, size: Float,
        parked: List<Vector3>? = null, homes: List<Vector3>? = null
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

        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.setColor(sand)
        shapes.rect(x, y, size, size)
        shapes.end()

        drawRoad(batch)

        shapes.begin(ShapeRenderer.ShapeType.Filled)
        if (parked != null) for (p in parked) parkedMarker(shapes, p, size * 0.05f)
        if (homes != null) for (p in homes) homeMarker(shapes, p, size * 0.06f, true)

        // The player: always pointing up, with a white outline.
        val s = size * 0.07f
        val cx = originX
        val cy = originY
        shapes.setColor(1f, 1f, 1f, 1f)
        shapes.triangle(cx, cy + s * 1.25f, cx - s * 0.8f, cy - s * 0.95f, cx + s * 0.8f, cy - s * 0.95f)
        shapes.setColor(0.9f, 0.1f, 0.1f, 1f)
        shapes.triangle(cx, cy + s, cx - s * 0.6f, cy - s * 0.7f, cx + s * 0.6f, cy - s * 0.7f)
        shapes.end()
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
        shapes: ShapeRenderer, batch: SpriteBatch, view: MapView, car: Vector3, yawDegrees: Float,
        parked: List<Vector3>? = null, homes: List<Vector3>? = null
    ) {
        val w = view.width
        val h = view.height
        shapes.begin(ShapeRenderer.ShapeType.Filled)
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
        shapes.end()

        drawRoad(batch)

        shapes.begin(ShapeRenderer.ShapeType.Filled)
        if (parked != null) for (p in parked) parkedMarker(shapes, p, h * 0.022f)
        if (homes != null) for (p in homes) homeMarker(shapes, p, h * 0.028f, false)

        // The player, pointing the way they face (north is up).
        project(car.x, car.z)
        val rad = yawDegrees * MathUtils.degreesToRadians
        val dirX = -sin(rad)
        val dirY = cos(rad)
        val s = h * 0.03f
        val px = outX
        val py = outY
        arrow(shapes, px, py, dirX, dirY, s * 1.25f, Color.WHITE)
        arrow(shapes, px, py, dirX, dirY, s, arrowRed)
        shapes.end()
    }

    /**
     * Draws the baked road picture, in whatever mode (turning or north-up) was just set. The picture
     * covers a world rectangle, so its four corners are projected and it is drawn as one quad. Nothing
     * is drawn until MapBaker has made the picture.
     */
    private fun drawRoad(batch: SpriteBatch) {
        val texture = baker.texture ?: return
        val x0 = WorldLimits.MIN_X
        val z0 = WorldLimits.MIN_Z
        val x1 = x0 + MapBaker.COLS * MapBaker.CELL
        val z1 = z0 + MapBaker.ROWS * MapBaker.CELL
        // Row 0 of the picture is the north edge (z0), so (u, v) = (0, 0) is the corner (x0, z0).
        corner(0, x0, z0, 0f, 0f)
        corner(1, x0, z1, 0f, 1f)
        corner(2, x1, z1, 1f, 1f)
        corner(3, x1, z0, 1f, 0f)
        batch.begin()
        batch.draw(texture, quad, 0, quad.size)
        batch.end()
        // SpriteBatch.end() switches blending off; the buttons and the arrow drawn after this need it.
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
    }

    private fun corner(index: Int, wx: Float, wz: Float, u: Float, v: Float) {
        project(wx, wz)
        val o = index * 5
        quad[o] = outX
        quad[o + 1] = outY
        quad[o + 2] = whiteBits
        quad[o + 3] = u
        quad[o + 4] = v
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

    /**
     * A house: a green square with a white roof. On the small map a home that is further away than the
     * map shows is kept on the edge of the map, so it always tells you which way to drive.
     */
    private fun homeMarker(shapes: ShapeRenderer, home: Vector3, half: Float, keepOnEdge: Boolean) {
        project(home.x, home.z)
        var x = outX
        var y = outY
        val inside = x >= clipX0 && x <= clipX1 && y >= clipY0 && y <= clipY1
        if (!inside) {
            if (!keepOnEdge) return
            val pad = half * 1.6f
            x = x.coerceIn(clipX0 + pad, clipX1 - pad)
            y = y.coerceIn(clipY0 + pad, clipY1 - pad)
        }
        shapes.setColor(0.05f, 0.05f, 0.05f, 1f)
        shapes.rect(x - half * 1.25f, y - half * 1.1f, half * 2.5f, half * 2.2f)
        shapes.triangle(x - half * 1.6f, y + half * 0.7f, x + half * 1.6f, y + half * 0.7f, x, y + half * 2.0f)
        shapes.setColor(0.12f, 0.70f, 0.30f, 1f)
        shapes.rect(x - half, y - half * 0.85f, half * 2f, half * 1.7f)
        shapes.setColor(1f, 1f, 1f, 1f)
        shapes.triangle(x - half * 1.3f, y + half * 0.85f, x + half * 1.3f, y + half * 0.85f, x, y + half * 1.7f)
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
