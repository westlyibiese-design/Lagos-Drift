package com.westly.lagosdrift

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import kotlin.math.hypot

/**
 * The big map screen: drag one finger to scroll, pinch with two fingers to zoom, tap
 * PLAYER POSITION in the legend to jump back to the car, tap BACK to close.
 * This class owns the view (centre and zoom), the touch gestures, the legend and the buttons.
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

    private var screenW = 1f
    private var screenH = 1f
    private var minScale = 0.6f

    // Button and legend layout, in screen pixels (y up).
    private var backX = 0f
    private var backY = 0f
    private var backW = 0f
    private var backH = 0f
    private var legendX = 0f
    private var legendY = 0f
    private var legendW = 0f
    private var rowH = 0f
    private var pad = 0f

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

        rowH = height * 0.055f
        pad = height * 0.015f
        legendW = height * 0.60f
        legendX = safeLeft + margin
        legendY = margin
    }

    /** Opens the map looking at the car. */
    fun open(carX: Float, carZ: Float) {
        centreX = carX
        centreZ = carZ
        scale = 1.4f.coerceIn(minScale, MAX_SCALE)
        clampCentre()
        wasOneFinger = false
        pinchDistance = 0f
        fingersBefore = 0
        closeRequested = false
    }

    /**
     * Reads the fingers. [ignore] says whether a touch belongs to a game button (such as MAP),
     * which the map must leave alone. [car] is where PLAYER POSITION jumps to.
     */
    fun update(delta: Float, carX: Float, carZ: Float, ignore: (Float, Float) -> Boolean) {
        closeRequested = false
        recentre = false

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
            centreX = carX
            centreZ = carZ
        }
        clampCentre()
    }

    private fun handleTap(px: Float, py: Float) {
        if (px >= backX && px <= backX + backW && py >= backY && py <= backY + backH) {
            closeRequested = true
            return
        }
        // Legend row 0 (PLAYER POSITION) jumps back to the car.
        if (px >= legendX && px <= legendX + legendW) {
            val rowY = rowBottom(0)
            if (py >= rowY && py <= rowY + rowH) recentre = true
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

    // ------------------------------------------------------------------ legend and buttons

    private fun rowBottom(index: Int): Float = legendY + pad + (LEGEND.size - 1 - index) * rowH

    private val roadSmooth = Color(0.06f, 0.06f, 0.07f, 1f)
    private val roadRough = Color(0.55f, 0.40f, 0.20f, 1f)
    private val ikoyiBlock = Color(0.92f, 0.95f, 0.98f, 1f)
    private val ikoroduBlock = Color(0.95f, 0.86f, 0.66f, 1f)

    /** Back button, legend panel and its swatches. Call between ShapeRenderer begin(Filled) and end(). */
    fun drawShapes(shapes: ShapeRenderer) {
        // Back button.
        shapes.setColor(0f, 0f, 0f, 0.55f)
        shapes.rect(backX, backY, backW, backH)

        // Legend panel.
        val panelH = LEGEND.size * rowH + pad * 2f
        shapes.setColor(0f, 0f, 0f, 0.55f)
        shapes.rect(legendX, legendY, legendW, panelH)

        val swatch = rowH * 0.5f
        for (i in LEGEND.indices) {
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

    /** BACK, the legend text and the district names. Call between SpriteBatch begin() and end(). */
    fun drawLabels(batch: SpriteBatch, font: BitmapFont, layout: GlyphLayout, textScale: Float) {
        // District names sit on the map; they fade out when you zoom right in.
        val alpha = ((3.2f - scale) / 1.2f).coerceIn(0f, 0.9f)
        if (alpha > 0.02f) {
            font.data.setScale(textScale * 1.7f)
            font.setColor(1f, 1f, 1f, alpha)
            drawCentred(batch, font, layout, Districts.NAMES[Districts.IKOYI], screenX(-50f), screenY(400f))
            drawCentred(batch, font, layout, Districts.NAMES[Districts.IKORODU], screenX(-50f), screenY(-400f))
        }

        font.data.setScale(textScale)
        font.setColor(1f, 1f, 1f, 0.95f)
        drawCentred(batch, font, layout, "BACK", backX + backW / 2f, backY + backH / 2f)

        font.data.setScale(textScale * 0.85f)
        for (i in LEGEND.indices) {
            layout.setText(font, LEGEND[i])
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
        val LEGEND = arrayOf("PLAYER POSITION", "IKOYI (CITY)", "IKORODU (TOWN)", "SMOOTH ROAD", "ROUGH ROAD / POTHOLES")
    }
}
