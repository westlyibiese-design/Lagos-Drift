package com.westly.lagosdrift

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer

/**
 * On-screen driving buttons for landscape: steer left and right on the left side,
 * brake and gas on the right side. Works with several fingers at once.
 */
class TouchControls {
    /** -1 = left, 0 = straight, +1 = right. */
    var steer = 0f
        private set
    var throttle = false
        private set
    var brake = false
        private set

    private class Button {
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

    private val leftButton = Button()
    private val rightButton = Button()
    private val gasButton = Button()
    private val brakeButton = Button()

    fun layout(width: Int, height: Int, insetLeft: Float, insetRight: Float) {
        val h = height.toFloat()
        val w = width.toFloat()
        val margin = h * 0.07f
        val gap = h * 0.04f
        val steerR = h * 0.10f
        val gasR = h * 0.13f
        val brakeR = h * 0.095f
        val rightEdge = w - insetRight - margin

        leftButton.place(insetLeft + margin + steerR, margin + steerR, steerR)
        rightButton.place(leftButton.x + steerR * 2f + gap, margin + steerR, steerR)
        gasButton.place(rightEdge - gasR, margin + gasR, gasR)
        brakeButton.place(gasButton.x - gasR - gap - brakeR, margin + brakeR, brakeR)
    }

    /** Reads the fingers currently on the screen. Call once per frame. */
    fun update() {
        leftButton.pressed = false
        rightButton.pressed = false
        gasButton.pressed = false
        brakeButton.pressed = false

        val screenHeight = Gdx.graphics.height.toFloat()
        for (pointer in 0 until 5) {
            if (!Gdx.input.isTouched(pointer)) continue
            val px = Gdx.input.getX(pointer).toFloat()
            val py = screenHeight - Gdx.input.getY(pointer).toFloat()
            if (leftButton.contains(px, py)) leftButton.pressed = true
            if (rightButton.contains(px, py)) rightButton.pressed = true
            if (gasButton.contains(px, py)) gasButton.pressed = true
            if (brakeButton.contains(px, py)) brakeButton.pressed = true
        }

        steer = (if (rightButton.pressed) 1f else 0f) - (if (leftButton.pressed) 1f else 0f)
        throttle = gasButton.pressed
        brake = brakeButton.pressed
    }

    fun drawShapes(shapes: ShapeRenderer) {
        circle(shapes, leftButton)
        circle(shapes, rightButton)
        circle(shapes, gasButton)
        circle(shapes, brakeButton)

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
        font.data.setScale(textScale * 1.4f)
        font.setColor(1f, 1f, 1f, 0.9f)
        label(batch, font, layout, "GAS", gasButton)
        label(batch, font, layout, "BRAKE", brakeButton)
        font.data.setScale(textScale)
        font.setColor(1f, 1f, 1f, 0.8f)
    }

    private fun circle(shapes: ShapeRenderer, button: Button) {
        shapes.setColor(1f, 1f, 1f, if (button.pressed) 0.42f else 0.20f)
        shapes.circle(button.x, button.y, button.r, 40)
    }

    private fun label(batch: SpriteBatch, font: BitmapFont, layout: GlyphLayout, text: String, button: Button) {
        layout.setText(font, text)
        font.draw(batch, layout, button.x - layout.width / 2f, button.y + layout.height / 2f)
    }
}
