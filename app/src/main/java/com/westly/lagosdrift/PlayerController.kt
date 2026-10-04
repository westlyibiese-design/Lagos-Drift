package com.westly.lagosdrift

import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.math.Vector3
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The player on foot. Turn with the left and right buttons, walk forward with GO, back up with BACK.
 * The character is one solid model (no skeleton), so it walks with a chibi waddle: it bobs up and
 * down with every step, sways from side to side and leans into the walk. The yaw follows the car's
 * rule: 0 faces -Z and a positive yaw turns left.
 */
class PlayerController {
    val position = Vector3()

    var yawDegrees = 0f
        private set

    /** Forward speed in metres per second (negative = backing up). */
    var speed = 0f
        private set

    private var steerSmoothed = 0f
    private var phase = 0f
    private val forward = Vector3()

    /** Puts the player somewhere, facing a direction, standing still. */
    fun place(x: Float, z: Float, yaw: Float) {
        position.set(x, 0f, z)
        yawDegrees = yaw
        speed = 0f
        steerSmoothed = 0f
        phase = 0f
    }

    /** steerInput: -1 = left, +1 = right. */
    fun update(delta: Float, steerInput: Float, goForward: Boolean, goBack: Boolean) {
        val dt = min(delta, 0.05f)

        steerSmoothed += (steerInput - steerSmoothed) * min(1f, 10f * dt)
        yawDegrees -= steerSmoothed * TURN_RATE * dt
        if (yawDegrees > 180f) yawDegrees -= 360f
        if (yawDegrees < -180f) yawDegrees += 360f

        val target = when {
            goForward && !goBack -> WALK_SPEED
            goBack && !goForward -> -BACK_SPEED
            else -> 0f
        }
        speed += (target - speed) * min(1f, 9f * dt)
        if (abs(speed) < 0.02f && target == 0f) speed = 0f

        forward.set(0f, 0f, -1f).rotate(Vector3.Y, yawDegrees)
        position.mulAdd(forward, speed * dt)

        position.x = position.x.coerceIn(TownLayout.DRIVE_MIN_X, TownLayout.DRIVE_MAX_X)
        position.z = position.z.coerceIn(TownLayout.DRIVE_MIN_Z, TownLayout.DRIVE_MAX_Z)

        phase += abs(speed) * 3.2f * dt
        if (phase > TWO_PI * 100f) phase -= TWO_PI * 100f
    }

    /**
     * Sets where the model is drawn. [time] is seconds since the game started (for breathing when
     * standing still). The model's feet are its origin, so it leans from the feet.
     */
    fun applyTo(instance: ModelInstance, time: Float) {
        val moving = (abs(speed) / WALK_SPEED).coerceIn(0f, 1f)
        val bob = abs(sin(phase)) * 0.07f * moving
        val roll = sin(phase) * 7f * moving
        val lean = 6f * moving * (if (speed < 0f) -0.5f else 1f)
        val breath = 1f + 0.012f * sin(time * 2.2f) * (1f - moving)
        val squash = 1f + 0.025f * cos(phase * 2f) * moving
        val s = PlayerModelFactory.SCALE

        instance.transform.idt()
            .translate(position.x, position.y + bob, position.z)
            .rotate(Vector3.Y, yawDegrees)
            .rotate(Vector3.Z, roll)
            .rotate(Vector3.X, -lean)
            .scale(s / squash, s * squash * breath, s / squash)
    }

    private companion object {
        const val WALK_SPEED = 3.2f      // brisk walk, metres per second
        const val BACK_SPEED = 1.5f
        const val TURN_RATE = 130f       // degrees per second
        const val TWO_PI = 6.2831855f
    }
}
