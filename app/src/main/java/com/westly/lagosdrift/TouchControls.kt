package com.westly.lagosdrift

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import kotlin.math.hypot

/**
 * On-screen buttons for landscape: steer left and right on the left side, brake and gas on the
 * right side, and a column on the far right for map, camera view and zoom.
 * Works with several fingers at once. Two fingers pinching on empty screen also zoom.
 */
class TouchControls {
    /** -1 = left, 0 = straight, +1 = right. */
    var steer = 0f
        private set
    var throttle = false
        private set
    var brake = false
        private set

    /** True for one frame when the button is first pressed. */
    var cameraTapped = false
        private set
    var mapTapped = false
        private set

    var zoomInHeld = false
        private set
    var zoomOutHeld = false
        private set

    /** Zoom change this frame from a pinch. Below 1 means fingers spreading (zoom in). */
    var pinchRatio = 1f
        private set

    private class Button(val label: String) {
        var x = 0f
        var y = 0f
        var r = 0f
        var pressed = false

        fun place(x: Float, y: Float, r: Float) {
            this.x = x
            this.y = y
            this.r = r
        }

        // The touch area is a little bigger than the drawn circle.
        fun contains(px: Float, py: Float): Boolean {
            val dx = px - x
            val dy = py - y
            val reach = r * 1.15f
            return dx * dx + dy * dy <= reach * reach
        }
    }

    private val leftButton = Button("")
    private val rightButton = Button("")
    private val gasButton = Button("GAS")
    private val brakeButton = Button("BRAKE")
    private val mapButton = Button("MAP")
    private val cameraButton = Button("CAM")
    private val zoomInButton = Button("+")
    private val zoomOutButton = Button("-")
    private val allButtons = listOf(
        leftButton, rightButton, gasButton, brakeButton, mapButton, cameraButton, zoomInButton, zoomOutButton
    )

    private var cameraWasDown = false
    private var mapWasDown = false
    private var lastPinchDistance = 0f

    fun layout(width: Int, height: Int, insetLeft: Float, insetRight: Float) {
        val h = height.toFloat()
        val w = width.toFloat()
        val margin = h * 0.07f
        val gap = h * 0.04f
        val steerR = h * 0.10f
        val gasR = h * 0.13f
        val brakeR = h * 0.095f
        val smallR = h * 0.065f
        val tinyR = h * 0.05f
        val rightEdge = w - insetRight - margin

        leftButton.place(insetLeft + margin + steerR, margin + steerR, steerR)
        rightButton.place(leftButton.x + steerR * 2f + gap, margin + steerR, steerR)
        gasButton.place(rightEdge - gasR, margin + gasR, gasR)
        brakeButton.place(gasButton.x - gasR - gap - brakeR, margin + brakeR, brakeR)

        // Column on the far right, above the gas button.
        val columnX = rightEdge - smallR
        mapButton.place(columnX, h * 0.80f, smallR)
        cameraButton.place(columnX, h * 0.65f, smallR)
        zoomInButton.place(columnX, h * 0.505f, tinyR)
        zoomOutButton.place(columnX, h * 0.385f, tinyR)
    }

    /** Reads the fingers currently on the screen. Call once per frame. */
    fun update() {
        for (button in allButtons) button.pressed = false

        val screenHeight = Gdx.graphics.height.toFloat()
        var freeCount = 0
        var firstX = 0f
        var firstY = 0f
        var secondX = 0f
        var secondY = 0f

        for (pointer in 0 until 5) {
            if (!Gdx.input.isTouched(pointer)) continue
            val px = Gdx.input.getX(pointer).toFloat()
            val py = screenHeight - Gdx.input.getY(pointer).toFloat()
            var onButton = false
            for (button in allButtons) {
                if (button.contains(px, py)) {
                    button.pressed = true
                    onButton = true
                }
            }
            if (!onButton) {
                if (freeCount == 0) {
                    firstX = px
                    firstY = py
                } else if (freeCount == 1) {
                    secondX = px
                    secondY = py
                }
                freeCount++
            }
        }

        // Two fingers on empty screen = pinch zoom.
        if (freeCount == 2) {
            val distance = hypot(secondX - firstX, secondY - firstY)
            pinchRatio = if (lastPinchDistance > 0f && distance > 1f) {
                (lastPinchDistance / distance).coerceIn(0.9f, 1.1f)
            } else {
                1f
            }
            lastPinchDistance = distance
        } else {
            lastPinchDistance = 0f
            pinchRatio = 1f
        }

        steer = (if (rightButton.pressed) 1f else 0f) - (if (leftButton.pressed) 1f else 0f)
        throttle = gasButton.pressed
        brake = brakeButton.pressed
        zoomInHeld = zoomInButton.pressed
        zoomOutHeld = zoomOutButton.pressed

        cameraTapped = cameraButton.pressed && !cameraWasDown
        mapTapped = mapButton.pressed && !mapWasDown
        cameraWasDown = cameraButton.pressed
        mapWasDown = mapButton.pressed
    }

    fun drawShapes(shapes: ShapeRenderer) {
        for (button in allButtons) circle(shapes, button)

        // Steering arrows.
        shapes.setColor(1f, 1f, 1f, 0.85f)
        val s = leftButton.r * 0.5f
        shapes.triangle(
            leftButton.x - s * 0.8f, leftButton.y,
            leftButton.x + s * 0.5f, leftButton.y + s,
            leftButton.x + s * 0.5f, leftButton.y - s
        )
        shapes.triangle(
            rightButton.x + s * 0.8f, rightButton.y,
            rightButton.x - s * 0.5f, rightButton.y + s,
            rightButton.x - s * 0.5f, rightButton.y - s
        )
    }

    fun drawLabels(batch: SpriteBatch, font: BitmapFont, layout: GlyphLayout, textScale: Float) {
        font.setColor(1f, 1f, 1f, 0.9f)
        font.data.setScale(textScale * 1.4f)
        label(batch, font, layout, gasButton)
        label(batch, font, layout, brakeButton)
        font.data.setScale(textScale * 1.1f)
        label(batch, font, layout, mapButton)
        label(batch, font, layout, cameraButton)
        font.data.setScale(textScale * 1.6f)
        label(batch, font, layout, zoomInButton)
        label(batch, font, layout, zoomOutButton)
        font.data.setScale(textScale)
        font.setColor(1f, 1f, 1f, 0.8f)
    }

    private fun circle(shapes: ShapeRenderer, button: Button) {
        shapes.setColor(1f, 1f, 1f, if (button.pressed) 0.42f else 0.20f)
        shapes.circle(button.x, button.y, button.r, 40)
    }

    private fun label(batch: SpriteBatch, font: BitmapFont, layout: GlyphLayout, button: Button) {
        if (button.label.isEmpty()) return
        layout.setText(font, button.label)
        font.draw(batch, layout, button.x - layout.width / 2f, button.y + layout.height / 2f)
    }
}
