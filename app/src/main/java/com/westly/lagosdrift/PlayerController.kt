package com.westly.lagosdrift

import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.math.Vector3
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The player on foot. The game tells it which way to go (from the walking stick, or toward a spot
 * that was double-tapped) and how hard to push: a light push walks, a full push runs. The
 * character turns to face the way it walks, like a person, and can jump. The yaw follows the
 * car's rule: 0 faces -Z and a positive yaw turns left.
 *
 * The model has no skeleton, but it is baked in three pieces: the body and two legs. The legs swing
 * from the hips, one forward while the other goes back, and the body gives a small bob and sway.
 */
class PlayerController {
    val position = Vector3()

    var yawDegrees = 0f
        private set

    /** Forward speed in metres per second. */
    var speed = 0f
        private set

    private var verticalSpeed = 0f
    private var phase = 0f
    private val forward = Vector3()

    private val grounded: Boolean
        get() = position.y <= 0.001f

    /** Puts the player somewhere, facing a direction, standing still. */
    fun place(x: Float, z: Float, yaw: Float) {
        position.set(x, 0f, z)
        yawDegrees = yaw
        speed = 0f
        verticalSpeed = 0f
        phase = 0f
    }

    /**
     * [desiredYaw] is the direction to walk (degrees, car rule). [intensity] is 0 for standing
     * still up to 1 for a full run. [jump] starts a jump if the feet are on the ground.
     */
    fun update(delta: Float, desiredYaw: Float, intensity: Float, jump: Boolean) {
        val dt = min(delta, 0.05f)

        if (intensity > 0.01f) {
            val diff = wrap(desiredYaw - yawDegrees)
            val maxTurn = TURN_RATE * dt
            yawDegrees = wrap(yawDegrees + diff.coerceIn(-maxTurn, maxTurn))
        }

        val target = when {
            intensity <= 0.01f -> 0f
            intensity <= 0.7f -> WALK_SPEED * (intensity / 0.7f)
            else -> WALK_SPEED + (RUN_SPEED - WALK_SPEED) * ((intensity - 0.7f) / 0.3f)
        }
        speed += (target - speed) * min(1f, 9f * dt)
        if (abs(speed) < 0.02f && target == 0f) speed = 0f

        forward.set(0f, 0f, -1f).rotate(Vector3.Y, yawDegrees)
        position.mulAdd(forward, speed * dt)

        position.x = position.x.coerceIn(TownLayout.DRIVE_MIN_X, TownLayout.DRIVE_MAX_X)
        position.z = position.z.coerceIn(TownLayout.DRIVE_MIN_Z, TownLayout.DRIVE_MAX_Z)

        if (jump && grounded) verticalSpeed = JUMP_SPEED
        if (!grounded || verticalSpeed > 0f) {
            verticalSpeed -= GRAVITY * dt
            position.y += verticalSpeed * dt
            if (position.y <= 0f) {
                position.y = 0f
                verticalSpeed = 0f
            }
        }

        phase += abs(speed) * 3.2f * dt
        if (phase > TWO_PI * 100f) phase -= TWO_PI * 100f
    }

    /**
     * Sets where the model is drawn. [time] is seconds since the game started (for breathing when
     * standing still). The model's feet are its origin, so it leans from the feet.
     */
    fun applyTo(instance: ModelInstance, time: Float) {
        val onGround = grounded
        val moving = (abs(speed) / WALK_SPEED).coerceIn(0f, 1.6f)
        val bob = if (onGround) abs(sin(phase)) * 0.04f * min(moving, 1f) else 0f
        val roll = sin(phase) * 2.5f * min(moving, 1f)
        val lean = 4f * moving
        val breath = 1f + 0.012f * sin(time * 2.2f) * (1f - min(moving, 1f))
        val squash = 1f + 0.01f * cos(phase * 2f) * min(moving, 1f)
        val stretch = if (onGround) 1f else 1.07f
        val s = PlayerModelFactory.SCALE

        instance.transform.idt()
            .translate(position.x, position.y + bob, position.z)
            .rotate(Vector3.Y, yawDegrees)
            .rotate(Vector3.Z, roll)
            .rotate(Vector3.X, -lean)
            .scale(s / squash, s * squash * breath * stretch, s / squash)

        // Legs swing from the hips. A positive turn about X moves the foot forward (-Z).
        val swing = if (onGround) sin(phase) * LEG_SWING * min(moving, 1.3f) else AIR_LEG
        instance.getNode(PlayerModelFactory.LEG_A)?.rotation?.set(Vector3.X, swing)
        instance.getNode(PlayerModelFactory.LEG_B)?.rotation?.set(Vector3.X, -swing)
        instance.calculateTransforms()
    }

    private fun wrap(angle: Float): Float {
        var a = angle
        while (a > 180f) a -= 360f
        while (a < -180f) a += 360f
        return a
    }

    private companion object {
        const val WALK_SPEED = 3.2f      // brisk walk, metres per second
        const val RUN_SPEED = 5.8f
        const val TURN_RATE = 560f       // degrees per second
        const val JUMP_SPEED = 5.2f
        const val GRAVITY = 15f
        const val TWO_PI = 6.2831855f
        const val LEG_SWING = 32f        // degrees each way at a walk
        const val AIR_LEG = 20f          // legs apart while jumping
    }
}
