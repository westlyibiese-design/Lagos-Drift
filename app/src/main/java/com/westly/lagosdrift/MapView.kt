package com.westly.lagosdrift

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import kotlin.math.hypot

/**
 * The big map screen: drag one finger to scroll, pinch with two fingers to zoom, tap a world name on the
 * left to look at Lagos, Neribo City or Neribo Desert, tap anywhere on the map to drop a pin and tap
 * TRAVEL to go there, tap PLAYER POSITION in the legend to jump back to the car, tap BACK to close.
 * This class owns the view (world, centre and zoom), the pin, the touch gestures, the legend and the buttons.
 * MapRenderer draws the world itself.
 */
class MapView {
    /** World point shown at the middle of the screen. */
    var centreX = 0f
        private set
    var centreZ = 0f
        private set

    /** Pixels per metre. */
    var scale = 1.4f
        private set

    /** True for one frame when BACK was tapped. */
    var closeRequested = false
        private set

    /** The world the map is showing (Districts.WORLD_LAGOS, WORLD_CITY or WORLD_DESERT). */
    var mapWorld = Districts.WORLD_LAGOS
        private set

    /** The pin the player dropped: which world and where. */
    var hasPin = false
        private set
    var pinWorld = Districts.WORLD_LAGOS
        private set
    var pinX = 0f
        private set
    var pinZ = 0f
        private set

    /** True for one frame when TRAVEL was tapped; the pin says where to. */
    var travelRequested = false
        private set

    private var screenW = 1f
    private var screenH = 1f
    private var minScale = 0.6f

    // Button, tab and legend layout, in screen pixels (y up).
    private var backX = 0f
    private var backY = 0f
    private var backW = 0f
    private var backH = 0f
    private var travelX = 0f
    private var legendX = 0f
    private var legendY = 0f
    private var legendW = 0f
    private var rowH = 0f
    private var pad = 0f
    private var tabX = 0f
    private var tabW = 0f
    private var tabH = 0f
    private var tabGap = 0f
    private var tabTop = 0f

    // Gesture state.
    private var wasOneFinger = false
    private var lastX = 0f
    private var lastY = 0f
    private var pinchDistance = 0f
    private var fingersBefore = 0
    private var pressTime = 0f
    private var moved = 0f
    private var pressX = 0f
    private var pressY = 0f
    private var recentre = false
    private var carWorldNow = Districts.WORLD_LAGOS

    fun layout(width: Float, height: Float, safeLeft: Float, safeRight: Float) {
        screenW = width
        screenH = height
        minScale = height * 0.95f / (TownLayout.MAX_Z - TownLayout.MIN_Z)
        scale = scale.coerceIn(minScale, MAX_SCALE)

        val margin = height * 0.04f
        backW = height * 0.24f
        backH = height * 0.10f
        backX = width - safeRight - margin - backW
        backY = margin
        travelX = backX - margin - backW * 1.25f

        rowH = height * 0.055f
        pad = height * 0.015f
        legendW = height * 0.60f
        legendX = safeLeft + margin
        legendY = margin

        tabX = safeLeft + margin
        tabW = height * 0.50f
        tabH = height * 0.085f
        tabGap = height * 0.012f
        tabTop = height - height * 0.15f
    }

    /** Opens the map in [world], looking at the car. */
    fun open(world: Int, carX: Float, carZ: Float) {
        mapWorld = world
        hasPin = false
        centreX = carX
        centreZ = carZ
        scale = 1.4f.coerceIn(minScale, MAX_SCALE)
        clampCentre()
        wasOneFinger = false
        pinchDistance = 0f
        fingersBefore = 0
        closeRequested = false
        travelRequested = false
    }

    /**
     * Reads the fingers. [ignore] says whether a touch belongs to a game button (such as MAP),
     * which the map must leave alone. [carWorld] and [carX], [carZ] are where PLAYER POSITION jumps to.
     */
    fun update(delta: Float, carWorld: Int, carX: Float, carZ: Float, ignore: (Float, Float) -> Boolean) {
        closeRequested = false
        travelRequested = false
        recentre = false
        carWorldNow = carWorld

        var count = 0
        var x0 = 0f
        var y0 = 0f
        var x1 = 0f
        var y1 = 0f
        for (pointer in 0 until 5) {
            if (!Gdx.input.isTouched(pointer)) continue
            val px = Gdx.input.getX(pointer).toFloat()
            val py = screenH - Gdx.input.getY(pointer).toFloat()
            if (ignore(px, py)) continue
            if (count == 0) {
                x0 = px
                y0 = py
            } else if (count == 1) {
                x1 = px
                y1 = py
            }
            count++
        }

        if (count == 1) {
            if (!wasOneFinger) {
                pressX = x0
                pressY = y0
                pressTime = 0f
                // Fingers left over from a pinch must not count as a tap.
                moved = if (fingersBefore >= 2) 1000f else 0f
            } else {
                val dx = x0 - lastX
                val dy = y0 - lastY
                moved += hypot(dx, dy)
                centreX -= dx / scale
                centreZ += dy / scale
            }
            pressTime += delta
            lastX = x0
            lastY = y0
            wasOneFinger = true
            pinchDistance = 0f
        } else if (count >= 2) {
            val distance = hypot(x1 - x0, y1 - y0)
            if (pinchDistance > 0f && distance > 1f) {
                val mx = (x0 + x1) / 2f
                val my = (y0 + y1) / 2f
                // Keep the point between the fingers where it is while zooming.
                val worldX = centreX + (mx - screenW / 2f) / scale
                val worldZ = centreZ - (my - screenH / 2f) / scale
                scale = (scale * distance / pinchDistance).coerceIn(minScale, MAX_SCALE)
                centreX = worldX - (mx - screenW / 2f) / scale
                centreZ = worldZ + (my - screenH / 2f) / scale
            }
            pinchDistance = distance
            wasOneFinger = false
            moved = 1000f   // a pinch is never a tap
        } else {
            // Fingers lifted: was that a tap?
            if (fingersBefore == 1 && moved < TAP_MOVE && pressTime < TAP_TIME) handleTap(pressX, pressY)
            wasOneFinger = false
            pinchDistance = 0f
        }
        fingersBefore = count

        if (recentre) {
            mapWorld = carWorld
            centreX = carX
            centreZ = carZ
        }
        clampCentre()
    }

    private fun inside(px: Float, py: Float, x: Float, y: Float, w: Float, h: Float): Boolean =
        px >= x && px <= x + w && py >= y && py <= y + h

    private fun tabY(index: Int): Float = tabTop - (index + 1) * tabH - index * tabGap

    private val travelVisible: Boolean get() = hasPin && pinWorld == mapWorld

    private fun handleTap(px: Float, py: Float) {
        if (inside(px, py, backX, backY, backW, backH)) {
            closeRequested = true
            return
        }
        if (travelVisible && inside(px, py, travelX, backY, backW * 1.25f, backH)) {
            travelRequested = true
            return
        }
        // The world tabs: look at another world.
        for (i in 0 until Districts.WORLD_COUNT) {
            if (inside(px, py, tabX, tabY(i), tabW, tabH)) {
                mapWorld = i
                scale = minScale
                centreX = (TownLayout.MIN_X + TownLayout.MAX_X) / 2f
                centreZ = (TownLayout.MIN_Z + TownLayout.MAX_Z) / 2f
                return
            }
        }
        // The legend: row 0 (PLAYER POSITION) jumps back to the car. Other taps on it do nothing.
        val legend = legendRows()
        val panelH = legend.size * rowH + pad * 2f
        if (inside(px, py, legendX, legendY, legendW, panelH)) {
            val rowY = rowBottom(0)
            if (px >= legendX && px <= legendX + legendW && py >= rowY && py <= rowY + rowH) recentre = true
            return
        }
        // Anywhere else on the map: drop the pin there.
        val wx = centreX + (px - screenW / 2f) / scale
        val wz = centreZ - (py - screenH / 2f) / scale
        if (wx >= TownLayout.MIN_X && wx <= TownLayout.MAX_X && wz >= TownLayout.MIN_Z && wz <= TownLayout.MAX_Z) {
            hasPin = true
            pinWorld = mapWorld
            pinX = wx
            pinZ = wz
        }
    }

    private fun clampCentre() {
        centreX = centreX.coerceIn(TownLayout.MIN_X, TownLayout.MAX_X)
        centreZ = centreZ.coerceIn(TownLayout.MIN_Z, TownLayout.MAX_Z)
    }

    // ------------------------------------------------------------------ projection

    fun screenX(wx: Float): Float = screenW / 2f + (wx - centreX) * scale
    fun screenY(wz: Float): Float = screenH / 2f + (centreZ - wz) * scale

    /** Screen x of world x = 0 (used by the renderer). */
    val originX: Float get() = screenW / 2f - centreX * scale
    /** Screen y of world z = 0. */
    val originY: Float get() = screenH / 2f + centreZ * scale

    val width: Float get() = screenW
    val height: Float get() = screenH

    // ------------------------------------------------------------------ legend, tabs and buttons

    private fun legendRows(): Array<String> = when (mapWorld) {
        Districts.WORLD_CITY -> LEGEND_CITY
        Districts.WORLD_DESERT -> LEGEND_DESERT
        else -> LEGEND_LAGOS
    }

    private fun rowBottom(index: Int): Float = legendY + pad + (legendRows().size - 1 - index) * rowH

    private val roadSmooth = Color(0.06f, 0.06f, 0.07f, 1f)
    private val roadRough = Color(0.55f, 0.40f, 0.20f, 1f)
    private val ikoyiBlock = Color(0.92f, 0.95f, 0.98f, 1f)
    private val ikoroduBlock = Color(0.95f, 0.86f, 0.66f, 1f)
    private val tabOn = Color(0.16f, 0.42f, 0.80f, 0.92f)
    private val tabOff = Color(0f, 0f, 0f, 0.55f)
    private val travelColor = Color(0.10f, 0.55f, 0.28f, 0.95f)
    private val pinColor = Color(0.98f, 0.45f, 0.10f, 1f)

    /** Tabs, buttons, legend panel, pin and landmarks. Call between ShapeRenderer begin(Filled) and end(). */
    fun drawShapes(shapes: ShapeRenderer) {
        // Landmarks: a white diamond.
        when (mapWorld) {
            Districts.WORLD_CITY -> diamond(shapes, screenX(TownLayout.HOME_X), screenY(TownLayout.HOME_Z))
            Districts.WORLD_DESERT -> diamond(shapes, screenX(DamModel.X), screenY(DamModel.Z))
        }

        // The pin: an orange ball on a point.
        if (travelVisible) {
            val x = screenX(pinX)
            val y = screenY(pinZ)
            val r = screenH * 0.016f
            shapes.setColor(1f, 1f, 1f, 1f)
            shapes.triangle(x, y - r * 0.2f, x - r * 1.25f, y + r * 1.9f, x + r * 1.25f, y + r * 1.9f)
            shapes.circle(x, y + r * 2.4f, r * 1.55f, 20)
            shapes.setColor(pinColor)
            shapes.triangle(x, y, x - r * 0.9f, y + r * 1.8f, x + r * 0.9f, y + r * 1.8f)
            shapes.circle(x, y + r * 2.4f, r * 1.2f, 20)
        }

        // Back button.
        shapes.setColor(0f, 0f, 0f, 0.55f)
        shapes.rect(backX, backY, backW, backH)

        // Travel button, only while there is a pin in this world.
        if (travelVisible) {
            shapes.setColor(travelColor)
            shapes.rect(travelX, backY, backW * 1.25f, backH)
        }

        // World tabs.
        for (i in 0 until Districts.WORLD_COUNT) {
            shapes.setColor(if (i == mapWorld) tabOn else tabOff)
            shapes.rect(tabX, tabY(i), tabW, tabH)
        }

        // Legend panel.
        val legend = legendRows()
        val panelH = legend.size * rowH + pad * 2f
        shapes.setColor(0f, 0f, 0f, 0.55f)
        shapes.rect(legendX, legendY, legendW, panelH)

        val swatch = rowH * 0.5f
        for (i in legend.indices) {
            val cy = rowBottom(i) + rowH / 2f
            val sx = legendX + pad
            when (i) {
                0 -> {
                    // The same white-and-red arrow as on the map.
                    shapes.setColor(Color.WHITE)
                    shapes.triangle(sx + swatch / 2f, cy + swatch * 0.62f, sx, cy - swatch * 0.5f, sx + swatch, cy - swatch * 0.5f)
                    shapes.setColor(0.9f, 0.1f, 0.1f, 1f)
                    shapes.triangle(sx + swatch / 2f, cy + swatch * 0.45f, sx + swatch * 0.15f, cy - swatch * 0.38f, sx + swatch * 0.85f, cy - swatch * 0.38f)
                }
                1 -> block(shapes, ikoyiBlock, sx, cy, swatch)
                2 -> block(shapes, ikoroduBlock, sx, cy, swatch)
                3 -> block(shapes, roadSmooth, sx, cy, swatch)
                else -> block(shapes, roadRough, sx, cy, swatch)
            }
        }
    }

    private fun block(shapes: ShapeRenderer, color: Color, x: Float, cy: Float, size: Float) {
        shapes.setColor(color)
        shapes.rect(x, cy - size / 2f, size, size)
    }

    private fun diamond(shapes: ShapeRenderer, x: Float, y: Float) {
        val r = screenH * 0.016f
        shapes.setColor(0.05f, 0.05f, 0.05f, 1f)
        shapes.triangle(x, y + r * 1.3f, x - r * 1.3f, y, x + r * 1.3f, y)
        shapes.triangle(x, y - r * 1.3f, x - r * 1.3f, y, x + r * 1.3f, y)
        shapes.setColor(1f, 1f, 1f, 1f)
        shapes.triangle(x, y + r, x - r, y, x + r, y)
        shapes.triangle(x, y - r, x - r, y, x + r, y)
    }

    /** Tab names, BACK, TRAVEL, the legend text and the place names. Call between SpriteBatch begin() and end(). */
    fun drawLabels(batch: SpriteBatch, font: BitmapFont, layout: GlyphLayout, textScale: Float) {
        // Place names sit on the map; they fade out when you zoom right in.
        val alpha = ((3.2f - scale) / 1.2f).coerceIn(0f, 0.9f)
        if (alpha > 0.02f) {
            font.data.setScale(textScale * 1.7f)
            font.setColor(1f, 1f, 1f, alpha)
            when (mapWorld) {
                Districts.WORLD_CITY -> {
                    drawCentred(batch, font, layout, Districts.WORLD_NAMES[Districts.WORLD_CITY], screenX(0f), screenY(-60f))
                    drawCentred(batch, font, layout, "HOME", screenX(TownLayout.HOME_X), screenY(TownLayout.HOME_Z - 30f))
                }
                Districts.WORLD_DESERT -> {
                    drawCentred(batch, font, layout, Districts.WORLD_NAMES[Districts.WORLD_DESERT], screenX(0f), screenY(-60f))
                    drawCentred(batch, font, layout, DamModel.NAME, screenX(DamModel.X), screenY(DamModel.Z - 40f))
                }
                else -> {
                    drawCentred(batch, font, layout, Districts.NAMES[Districts.IKOYI], screenX(-50f), screenY(400f))
                    drawCentred(batch, font, layout, Districts.NAMES[Districts.IKORODU], screenX(-50f), screenY(-400f))
                }
            }
        }

        font.data.setScale(textScale)
        font.setColor(1f, 1f, 1f, 0.95f)
        drawCentred(batch, font, layout, "BACK", backX + backW / 2f, backY + backH / 2f)
        if (travelVisible) drawCentred(batch, font, layout, "TRAVEL HERE", travelX + backW * 0.625f, backY + backH / 2f)

        font.data.setScale(textScale * 0.95f)
        for (i in 0 until Districts.WORLD_COUNT) {
            drawCentred(batch, font, layout, Districts.WORLD_NAMES[i], tabX + tabW / 2f, tabY(i) + tabH / 2f)
        }

        font.data.setScale(textScale * 0.85f)
        val legend = legendRows()
        for (i in legend.indices) {
            layout.setText(font, legend[i])
            font.draw(
                batch, layout,
                legendX + pad * 2f + rowH * 0.5f,
                rowBottom(i) + rowH / 2f + layout.height / 2f
            )
        }
        font.data.setScale(textScale)
        font.setColor(1f, 1f, 1f, 0.8f)
    }

    private fun drawCentred(batch: SpriteBatch, font: BitmapFont, layout: GlyphLayout, text: String, x: Float, y: Float) {
        layout.setText(font, text)
        font.draw(batch, layout, x - layout.width / 2f, y + layout.height / 2f)
    }

    companion object {
        const val MAX_SCALE = 6f
        private const val TAP_MOVE = 24f
        private const val TAP_TIME = 0.4f
        private val LEGEND_LAGOS = arrayOf("PLAYER POSITION", "IKOYI (CITY)", "IKORODU (TOWN)", "SMOOTH ROAD", "ROUGH ROAD / POTHOLES")
        private val LEGEND_CITY = arrayOf("PLAYER POSITION", "NERIBO CITY", "HOMES AND SHOPS", "SMOOTH ROAD", "ROUGH ROAD / POTHOLES")
        private val LEGEND_DESERT = arrayOf("PLAYER POSITION", "TOWN", "HOUSES", "OLD HIGHWAY", "DIRT TRACK")
    }
}
